import React from 'react'
import ScoutScheduleCard from './ScoutScheduleCard'
import TroopGrid from './TroopGrid'

export default function PrintSummary({ scouts, campSchedule, selections, onBack }) {
  const campName = window.SCOUT_SIGHT_DATA.campConfig.campName

  // Only show scouts who have at least one morning or free-time selection
  const scheduledScouts = scouts.filter(scout => {
    const sel = selections[scout.memberId]
    return sel && (sel.morning.length > 0 || sel.freeTime.length > 0)
  })

  function handlePrint() {
    window.print()
  }

  return (
    <div className="screen print-screen">
      {/* Controls — hidden when printing */}
      <div className="print-controls no-print">
        <button className="btn-back" onClick={onBack}>← Back</button>
        <h1 className="print-controls-title">{campName} — Schedule Summary</h1>
        <button className="btn btn-primary" onClick={handlePrint}>🖨 Print</button>
      </div>

      {/* Printable content */}
      <div className="print-content">
        <div className="print-title-row">
          <h1 className="print-main-title">{campName}</h1>
          <p className="print-main-sub">Camp Schedule — Troop 0600B</p>
        </div>

        {/* Individual schedule cards — 2-up on a page */}
        <div className="print-cards-grid">
          {scheduledScouts.map(scout => (
            <ScoutScheduleCard
              key={scout.memberId}
              scout={scout}
              campSchedule={campSchedule}
              selections={selections}
            />
          ))}
        </div>

        {/* Master troop grid — full width, page break before */}
        <div className="print-grid-section">
          <TroopGrid scouts={scheduledScouts} campSchedule={campSchedule} selections={selections} />
        </div>
      </div>
    </div>
  )
}
