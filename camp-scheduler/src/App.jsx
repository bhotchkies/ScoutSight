import React, { useState } from 'react'
import ScoutSelector from './components/ScoutSelector'
import SchedulePicker from './components/SchedulePicker'
import PrintSummary from './components/PrintSummary'
import { toggleMorningSelection, toggleFreeTimeSelection } from './utils/scheduleUtils'

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
