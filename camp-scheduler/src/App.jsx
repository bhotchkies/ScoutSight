import React, { useState, useRef, useEffect } from 'react'
import ScoutSelector from './components/ScoutSelector'
import SchedulePicker from './components/SchedulePicker'
import PrintSummary from './components/PrintSummary'
import SheetsConnect from './components/SheetsConnect'
import { toggleMorningSelection, toggleFreeTimeSelection } from './utils/scheduleUtils'
import { downloadJSON, downloadCSV, handleUploadFile } from './utils/scheduleIO'
import { sheetsGetAll, sheetsAcquireLock, sheetsReleaseLock, sheetsDropLock, sheetsPing } from './utils/sheetsIO'

/**
 * Extracts { morning, freeTime } selections from raw Sheets data, stripping the
 * embedded status field and skipping entries with no class picks.
 */
function selectionsFromData(rawData) {
  const result = {}
  for (const [id, s] of Object.entries(rawData)) {
    if (!s) continue
    const morning  = s.morning  ?? []
    const freeTime = s.freeTime ?? []
    if (morning.length > 0 || freeTime.length > 0) {
      result[id] = { morning, freeTime }
    }
  }
  return result
}

/**
 * Extracts scout statuses from raw Sheets data.
 * For entries without an explicit status field, derives 'in_progress'
 * from the presence of selections (backward compatibility).
 */
function statusesFromData(rawData) {
  const result = {}
  for (const [id, s] of Object.entries(rawData)) {
    if (!s) continue
    if (s.status) {
      result[id] = s.status
    } else if ((s.morning?.length ?? 0) + (s.freeTime?.length ?? 0) > 0) {
      result[id] = 'in_progress'
    }
  }
  return result
}

export default function App() {
  const { scouts, campSchedule, campConfig } = window.SCOUT_SIGHT_DATA

  const [screen, setScreen]               = useState('select')  // 'select' | 'pick' | 'print' | 'connect'
  const [currentScoutIdx, setCurrentScoutIdx] = useState(0)
  // scoutStatuses: { [memberId]: 'in_progress' | 'finalized' | 'not_attending' }
  const [scoutStatuses, setScoutStatuses] = useState({})
  // selections: { [memberId]: { morning: [{classIdx, sessionIdx}], freeTime: [{ftIdx}] } }
  const [selections, setSelections]       = useState({})

  // Google Sheets sync state
  const [sheetsUrl, setSheetsUrl]         = useState(() => {
    const seeded = window.SCOUT_SIGHT_DATA?.sheetsUrl
    if (seeded) return seeded
    const s = localStorage.getItem('sheetsUrl')
    return (s && s !== 'offline') ? s : null
  })
  const [locks, setLocks]                 = useState({})  // { [memberId]: deviceId }
  const [deviceId]                        = useState(() => {
    let id = sessionStorage.getItem('deviceId')
    if (!id) { id = crypto.randomUUID(); sessionStorage.setItem('deviceId', id) }
    return id
  })

  const [syncMsg, setSyncMsg]             = useState(null)

  const pingRef            = useRef(null)
  const editingMemberIdRef = useRef(null)

  const currentScout = scouts[currentScoutIdx]

  // On mount: if a sheetsUrl is already saved, load data immediately.
  useEffect(() => {
    if (!sheetsUrl) return
    setSyncMsg('Loading schedule data…')
    sheetsGetAll(sheetsUrl)
      .then(data => {
        const raw = data.selections ?? {}
        setSelections(selectionsFromData(raw))
        setScoutStatuses(statusesFromData(raw))
        setLocks(data.locks ?? {})
      })
      .catch(e => console.warn('Initial sheets load failed:', e.message))
      .finally(() => setSyncMsg(null))
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  // Release lock immediately if the user closes/navigates away while editing.
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

  // Poll for updates on the select screen (stops while editing to avoid clobbering).
  useEffect(() => {
    if (!sheetsUrl || screen !== 'select') return
    let active = true
    async function poll() {
      try {
        const data = await sheetsGetAll(sheetsUrl)
        if (!active) return
        const raw = data.selections ?? {}
        setSelections(selectionsFromData(raw))
        setScoutStatuses(statusesFromData(raw))
        setLocks(data.locks ?? {})
      } catch (e) {
        console.warn('Sheets sync failed:', e.message)
      }
    }
    poll()
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
      pingRef.current = setInterval(() => {
        sheetsPing(sheetsUrl, scout.memberId, deviceId).catch(() => {})
      }, 5000)
    }
    setCurrentScoutIdx(idx)
    setScreen('pick')
  }

  /**
   * Shared helper: write selections + status to Sheets, update local state, return to select screen.
   * forcedSelections overrides the current selections map (used for Not Attending to write empty array).
   */
  async function saveAndRelease(status, forcedSelections) {
    const memberId = currentScout.memberId
    const sels = forcedSelections ?? selections[memberId] ?? { morning: [], freeTime: [] }

    if (sheetsUrl && editingMemberIdRef.current) {
      clearInterval(pingRef.current)
      setSyncMsg(`Saving ${currentScout.name}…`)
      try {
        await sheetsReleaseLock(sheetsUrl, memberId, deviceId, sels, status)
      } catch (e) {
        console.warn('Release lock failed:', e.message)
      }
      setSyncMsg(null)
      editingMemberIdRef.current = null
    }

    // When forced selections are provided (not_attending), update or clear local selections.
    if (forcedSelections) {
      setSelections(prev => {
        const next = { ...prev }
        if (forcedSelections.morning.length === 0 && forcedSelections.freeTime.length === 0) {
          delete next[memberId]
        } else {
          next[memberId] = forcedSelections
        }
        return next
      })
    }

    setScoutStatuses(prev => {
      const next = { ...prev }
      if (status === null) delete next[memberId]
      else next[memberId] = status
      return next
    })
    setScreen('select')
  }

  async function handleSaveScout() {
    const sel = selections[currentScout.memberId]
    const hasSelections = (sel?.morning?.length ?? 0) + (sel?.freeTime?.length ?? 0) > 0
    await saveAndRelease(hasSelections ? 'in_progress' : null)
  }

  async function handleFinalizeScout() {
    await saveAndRelease('finalized')
  }

  async function handleNotAttending() {
    const firstName = currentScout.name.split(' ')[0]
    const ok = window.confirm(
      `Mark ${firstName} as Not Attending?\n\nThis will clear all their selections.`
    )
    if (!ok) return
    await saveAndRelease('not_attending', { morning: [], freeTime: [] })
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

  function handlePrint() { setScreen('print') }
  function handleBackFromPrint() { setScreen('select') }

  function handleMorningToggle(classIdx, sessionIdx) {
    setSelections(prev => toggleMorningSelection(prev, currentScout.memberId, classIdx, sessionIdx))
  }

  function handleFreeTimeToggle(ftIdx) {
    setSelections(prev => toggleFreeTimeSelection(prev, currentScout.memberId, ftIdx))
  }

  function handleDownloadJSON() {
    downloadJSON(selections, scoutStatuses, scouts, campSchedule, campConfig)
  }

  function handleDownloadCSV() {
    downloadCSV(selections, scouts, campSchedule, campConfig)
  }

  function handleUpload(file) {
    handleUploadFile(file, campSchedule, scouts, (newSels, newStatuses) => {
      setSelections(newSels)
      setScoutStatuses(newStatuses)
    })
  }

  function handleSheetsConnect(url, initialData) {
    setSheetsUrl(url)
    const raw = initialData.selections ?? {}
    setSelections(selectionsFromData(raw))
    setScoutStatuses(statusesFromData(raw))
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
          scoutStatuses={scoutStatuses}
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
          scoutStatus={scoutStatuses[currentScout.memberId]}
          onMorningToggle={handleMorningToggle}
          onFreeTimeToggle={handleFreeTimeToggle}
          onSave={handleSaveScout}
          onFinalize={handleFinalizeScout}
          onNotAttending={handleNotAttending}
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
