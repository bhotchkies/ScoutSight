import React, { useState, useRef, useEffect } from 'react'
import ScoutSelector from './components/ScoutSelector'
import SchedulePicker from './components/SchedulePicker'
import PrintSummary from './components/PrintSummary'
import SheetsConnect from './components/SheetsConnect'
import { toggleMorningSelection, toggleFreeTimeSelection } from './utils/scheduleUtils'
import { downloadJSON, downloadCSV, handleUploadFile } from './utils/scheduleIO'
import { sheetsGetAll, sheetsAcquireLock, sheetsReleaseLock, sheetsDropLock, sheetsPing } from './utils/sheetsIO'

export default function App() {
  const { scouts, campSchedule, campConfig } = window.SCOUT_SIGHT_DATA

  const [screen, setScreen]             = useState('select')  // 'select' | 'pick' | 'print' | 'connect'
  const [currentScoutIdx, setCurrentScoutIdx] = useState(0)
  const [doneScouts, setDoneScouts]     = useState(new Set())
  // selections: { [memberId]: { morning: [{classIdx, sessionIdx}], freeTime: [{ftIdx}] } }
  const [selections, setSelections]     = useState({})

  // Google Sheets sync state
  const [sheetsUrl, setSheetsUrl]       = useState(() => {
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
        setDoneScouts(new Set(Object.keys(data.selections ?? {})))
        setLocks(data.locks ?? {})
      })
      .catch(e => console.warn('Initial sheets load failed:', e.message))
      .finally(() => setSyncMsg(null))
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  // Poll for updates when connected and on the select screen.
  // Stops polling on 'pick' to avoid overwriting in-progress edits.
  useEffect(() => {
    if (!sheetsUrl || screen !== 'select') return
    const id = setInterval(async () => {
      try {
        const data = await sheetsGetAll(sheetsUrl)
        setSelections(data.selections ?? {})
        setDoneScouts(new Set(Object.keys(data.selections ?? {})))
        setLocks(data.locks ?? {})
      } catch (e) {
        console.warn('Sheets sync failed:', e.message)
      }
    }, 4000)
    return () => clearInterval(id)
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
      } catch (e) {
        setSyncMsg(null)
        alert(`Could not reach Google Sheet: ${e.message}`)
        return
      }
      setSyncMsg(null)
      editingMemberIdRef.current = scout.memberId
      // Ping every 20 s to keep the lock alive (lock expires at 60 s server-side)
      pingRef.current = setInterval(() => {
        sheetsPing(sheetsUrl, scout.memberId, deviceId).catch(() => {})
      }, 20000)
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
    setDoneScouts(prev => new Set([...prev, currentScout.memberId]))
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
      setDoneScouts(new Set(Object.keys(newSels)))
    })
  }

  function handleSheetsConnect(url, initialData) {
    setSheetsUrl(url)
    setSelections(initialData.selections ?? {})
    setDoneScouts(new Set(Object.keys(initialData.selections ?? {})))
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
