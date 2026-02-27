import React from 'react'
import { buildScoutSummary } from '../utils/scheduleUtils'

export default function ScoutScheduleCard({ scout, campSchedule, selections }) {
  const { morningItems, freeItems } = buildScoutSummary(scout, selections, campSchedule)

  return (
    <div className="print-scout-card">
      <div className="print-card-header">
        <span className="print-scout-name">{scout.name}</span>
        {scout.patrol && <span className="print-scout-patrol">{scout.patrol}</span>}
      </div>

      {morningItems.length === 0 && freeItems.length === 0 ? (
        <p className="print-empty">No classes selected</p>
      ) : (
        <table className="print-schedule-table">
          <tbody>
            {morningItems.map((item, i) => (
              <tr key={i}>
                <td className="print-time">{item.time}</td>
                <td className="print-class">{item.label}</td>
              </tr>
            ))}
            {freeItems.map((item, i) => (
              <tr key={`ft-${i}`}>
                <td className="print-time">{item.day} 3:45</td>
                <td className="print-class">{item.label} <em className="print-ft-label">(free time)</em></td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  )
}
