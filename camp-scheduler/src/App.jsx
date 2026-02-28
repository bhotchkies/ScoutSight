import React, { useState, useRef, useEffect } from 'react'
import ScoutSelector from './components/ScoutSelector'
import SchedulePicker from './components/SchedulePicker'
import PrintSummary from './components/PrintSummary'
import SheetsConnect from './components/SheetsConnect'
import { toggleMorningSelection, toggleFreeTimeSelection } from './utils/scheduleUtils'
import { downloadJSON, downloadCSV, handleUploadFile } from './utils/scheduleIO'
import { sheetsGetAll, sheetsAcquireLock, sheetsReleaseLock, sheetsDropLock, sheetsPing } from './utils/sheetsIO'

/** Returns a Set of memberIds that have at least one morning or free-time selection. */
function doneFromSelections(sels) {
  return new Set(
    Object.entries(sels)
      .filter(([, s]) => (s?.morning?.length ?? 0) + (s?.freeTime?.length ?? 0) > 0)
      .map(([id]) => id)
  )
}

export default function App() {
  const { scouts, campSchedule, campConfig } = window.SCOUT_SIGHT_DATA

  const [screen, setScreen]             = useState('select')  // 'select' | 'pick' | 'print' | 'connect'
  const [currentScoutIdx, setCurrentScoutIdx] = useState(0)
  const [doneScouts, setDoneScouts]     = useState(new Set())
  // selections: { [memberId]: { morning: [{classIdx, sessionIdx}], freeTime: [{ftIdx}] } }
  const [selections, setSelections]     = useState({})

  // Google Sheets sync state
  const [sheetsUrl, setSheetsUrl]       = useState(() => {
    // Pre-seeded URL from a distribution package takes priority over localStorage
    const seeded = window.SCOUT_SIGHT_DATA?.sheetsUrl
    if (seeded) return seeded
    const s = localStorage.getItem('sheetsUrl')
    return (s && s !== 'offline') ? s : null
  })
  const [locks, setLocks]               = useState({})  // { [memberId]: deviceId }
  const [deviceId]                      = useState(() => {
    // sessionStorage: stable per-tab, so each browser tab has its own identity
    let id = sessionStorage.getItem('deviceId')
    if (!id) { id = crypto.randomUUID(); sessionStorage.setItem('deviceId', id) }
    return id
  })

  const [syncMsg, setSyncMsg]          = useState(null)  // null = idle; string = loading message

  const pingRef            = useRef(null)  // interval for keepalive pings while editing
  const editingMemberIdRef = useRef(null)  // memberId of the scout currently being edited

  const currentScout = scouts[currentScoutIdx]

  // On mount: if a sheetsUrl is already saved, load data immediately.
  useEffect(() => {
    if (!sheetsUrl) return
    setSyncMsg('Loading schedule data…')
    sheetsGetAll(sheetsUrl)
      .then(data => {
        setSelections(data.selections ?? {})
        setDoneScouts(doneFromSelections(data.selections ?? {}))
        setLocks(data.locks ?? {})
      })
      .catch(e => console.warn('Initial sheets load failed:', e.message))
      .finally(() => setSyncMsg(null))
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  // Release lock immediately if the user closes the tab or navigates away while editing.
  // keepalive: true lets the request survive page unload; GET avoids the Apps Script POST-redirect issue.
  useEffect(() => {
    if (!sheetsUrl || screen !== 'pick') return
    function handlePageHide() {
      const memberId = editingMemberIdRef.current
      if (!memberId) return
      clearInterval(pingRef.current)
      const qs = new URLSearchParams({ action: 'dropLock', scoutId: memberId, deviceId, t: Date.now() }).toString()
      fetch(`${sheetsUrl}?${qs}`, { keepalive: true }).catch(() => {})
    }
    window.addEventListener('pagehide', handlePageHide)
    return () => window.removeEventListener('pagehide', handlePageHide)
  }, [sheetsUrl, screen, deviceId])

  // Poll for updates when connected and on the select screen.
  // Stops polling on 'pick' to avoid overwriting in-progress edits.
  // Polls immediately on entering 'select' so released locks clear right away,
  // then repeats every 4 s.
  useEffect(() => {
    if (!sheetsUrl || screen !== 'select') return
    let active = true
    async function poll() {
      try {
        const data = await sheetsGetAll(sheetsUrl)
        if (!active) return
        setSelections(data.selections ?? {})
        setDoneScouts(doneFromSelections(data.selections ?? {}))
        setLocks(data.locks ?? {})
      } catch (e) {
        console.warn('Sheets sync failed:', e.message)
      }
    }
    poll()                          // immediate — clears stale locks on screen entry
    const id = setInterval(poll, 4000)
    return () => { active = false; clearInterval(id) }
  }, [sheetsUrl, screen])

  async function handleSelectScout(idx) {
    const scout = scouts[idx]
    if (sheetsUrl) {
      setSyncMsg(`Reserving ${scout.name}…`)
      try {
        const result = await sheetsAcquireLock(sheetsUrl, scout.memberId, deviceId)
        if (!result.ok) {
          setSyncMsg(null)
          alert(`${scout.name} is currently being edited by another device. Try again in a moment.`)
          return
        }
        // Re-verify: read the sheet back to confirm our lock is actually there.
        // If two devices both received ok:true, only the one whose deviceId is in
        // the sheet should proceed — the other backs off here.
        const verify = await sheetsGetAll(sheetsUrl)
        if (verify.locks[scout.memberId] !== deviceId) {
          setSyncMsg(null)
          alert(`${scout.name} is currently being edited by another device. Try again in a moment.`)
          return
        }
      } catch (e) {
        setSyncMsg(null)
        alert(`Could not reach Google Sheet: ${e.message}`)
        return
      }
      setSyncMsg(null)
      editingMemberIdRef.current = scout.memberId
      // Ping every 5 s to keep the lock alive (lock expires at 60 s server-side)
      pingRef.current = setInterval(() => {
        sheetsPing(sheetsUrl, scout.memberId, deviceId).catch(() => {})
      }, 5000)
    }
    setCurrentScoutIdx(idx)
    setScreen('pick')
  }

  async function handleDoneWithScout() {
    if (sheetsUrl && editingMemberIdRef.current) {
      clearInterval(pingRef.current)
      setSyncMsg(`Saving ${currentScout.name}'s schedule…`)
      try {
        await sheetsReleaseLock(
          sheetsUrl,
          editingMemberIdRef.current,
          deviceId,
          selections[editingMemberIdRef.current] ?? null
        )
      } catch (e) {
        console.warn('Release lock failed:', e.message)
      }
      setSyncMsg(null)
      editingMemberIdRef.current = null
    }
    setDoneScouts(prev => {
      const sel = selections[currentScout.memberId]
      const hasSelections = (sel?.morning?.length ?? 0) + (sel?.freeTime?.length ?? 0) > 0
      const next = new Set(prev)
      if (hasSelections) next.add(currentScout.memberId)
      else next.delete(currentScout.memberId)
      return next
    })
    setScreen('select')
  }

  async function handleBackToSelect() {
    if (sheetsUrl && editingMemberIdRef.current) {
      clearInterval(pingRef.current)
      try {
        await sheetsDropLock(sheetsUrl, editingMemberIdRef.current, deviceId)
      } catch (e) {
        console.warn('Drop lock failed:', e.message)
      }
      editingMemberIdRef.current = null
    }
    setScreen('select')
  }

  function handlePrint() {
    setScreen('print')
  }

  function handleBackFromPrint() {
    setScreen('select')
  }

  function handleMorningToggle(classIdx, sessionIdx) {
    setSelections(prev => toggleMorningSelection(prev, currentScout.memberId, classIdx, sessionIdx))
  }

  function handleFreeTimeToggle(ftIdx) {
    setSelections(prev => toggleFreeTimeSelection(prev, currentScout.memberId, ftIdx))
  }

  function handleDownloadJSON() {
    downloadJSON(selections, scouts, campSchedule, campConfig)
  }

  function handleDownloadCSV() {
    downloadCSV(selections, scouts, campSchedule, campConfig)
  }

  function handleUpload(file) {
    handleUploadFile(file, campSchedule, scouts, newSels => {
      setSelections(newSels)
      setDoneScouts(doneFromSelections(newSels))
    })
  }

  function handleSheetsConnect(url, initialData) {
    setSheetsUrl(url)
    setSelections(initialData.selections ?? {})
    setDoneScouts(doneFromSelections(initialData.selections ?? {}))
    setLocks(initialData.locks ?? {})
    setScreen('select')
  }

  function handleSheetsDisconnect() {
    setSheetsUrl(null)
    setLocks({})
  }

  return (
    <div className="app">
      {syncMsg && (
        <div className="sync-overlay" role="status" aria-live="polite">
          <div className="sync-card">
            <div className="sync-spinner" />
            <p className="sync-msg">{syncMsg}</p>
          </div>
        </div>
      )}
      {screen === 'select' && (
        <ScoutSelector
          scouts={scouts}
          campConfig={campConfig}
          doneScouts={doneScouts}
          selections={selections}
          locks={locks}
          deviceId={deviceId}
          connected={!!sheetsUrl}
          onSelectScout={handleSelectScout}
          onPrint={handlePrint}
          onDownloadJSON={handleDownloadJSON}
          onDownloadCSV={handleDownloadCSV}
          onUpload={handleUpload}
          onOpenSync={() => setScreen('connect')}
        />
      )}
      {screen === 'pick' && (
        <SchedulePicker
          scout={currentScout}
          scouts={scouts}
          campSchedule={campSchedule}
          campConfig={campConfig}
          selections={selections}
          onMorningToggle={handleMorningToggle}
          onFreeTimeToggle={handleFreeTimeToggle}
          onDone={handleDoneWithScout}
          onBack={handleBackToSelect}
        />
      )}
      {screen === 'print' && (
        <PrintSummary
          scouts={scouts}
          campSchedule={campSchedule}
          selections={selections}
          onBack={handleBackFromPrint}
        />
      )}
      {screen === 'connect' && (
        <SheetsConnect
          sheetsUrl={sheetsUrl}
          onConnect={handleSheetsConnect}
          onDisconnect={handleSheetsDisconnect}
          onBack={() => setScreen('select')}
        />
      )}
    </div>
  )
}
