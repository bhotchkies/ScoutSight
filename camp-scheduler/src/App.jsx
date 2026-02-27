import React, { useState } from 'react'
import ScoutSelector from './components/ScoutSelector'
import SchedulePicker from './components/SchedulePicker'
import PrintSummary from './components/PrintSummary'
import { toggleMorningSelection, toggleFreeTimeSelection } from './utils/scheduleUtils'
import { downloadJSON, downloadCSV, handleUploadFile } from './utils/scheduleIO'

export default function App() {
  const { scouts, campSchedule, campConfig } = window.SCOUT_SIGHT_DATA

  const [screen, setScreen] = useState('select')         // 'select' | 'pick' | 'print'
  const [currentScoutIdx, setCurrentScoutIdx] = useState(0)
  const [doneScouts, setDoneScouts] = useState(new Set()) // Set<memberId>
  // selections: { [memberId]: { morning: [{classIdx, sessionIdx}], freeTime: [{ftIdx}] } }
  const [selections, setSelections] = useState({})

  const currentScout = scouts[currentScoutIdx]

  function handleSelectScout(idx) {
    setCurrentScoutIdx(idx)
    setScreen('pick')
  }

  function handleDoneWithScout() {
    setDoneScouts(prev => new Set([...prev, currentScout.memberId]))
    setScreen('select')
  }

  function handleBackToSelect() {
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

  return (
    <div className="app">
      {screen === 'select' && (
        <ScoutSelector
          scouts={scouts}
          campConfig={campConfig}
          doneScouts={doneScouts}
          selections={selections}
          onSelectScout={handleSelectScout}
          onPrint={handlePrint}
          onDownloadJSON={handleDownloadJSON}
          onDownloadCSV={handleDownloadCSV}
          onUpload={handleUpload}
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
    </div>
  )
}
