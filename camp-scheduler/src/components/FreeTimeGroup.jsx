import React from 'react'
import {
  getFreeTimeColor,
  getFreeTimeSessionState,
  getFreeTimeOccupancy,
  isFreeTimeEligible
} from '../utils/scheduleUtils'

const DAY_SHORT = {
  Monday: 'Mon', Tuesday: 'Tue', Wednesday: 'Wed',
  Thursday: 'Thu', Friday: 'Fri', Saturday: 'Sat', Sunday: 'Sun'
}

function stripMB(name) {
  return name.endsWith(' MB') ? name.slice(0, -3) : name
}

export default function FreeTimeGroup({ group, scout, campSchedule, selections, onToggle }) {
  const { freeTimeClasses } = campSchedule

  // Card color based on first entry
  const firstFt = freeTimeClasses[group.entries[0].ftIdx]
  const cardColor = getFreeTimeColor(firstFt, scout)

  const displayName = group.badgeName.split(' + ').map(stripMB).join(' + ')

  return (
    <div className={`class-card class-card--${cardColor}`}>
      <div className="class-card-header">
        <span className="class-type-tag class-type-tag--freetime">Free Time</span>
        <h3 className="class-card-name">{displayName}</h3>
        {cardColor === 'partial' && (
          <span className="card-status-chip card-status-chip--partial">Partial</span>
        )}
      </div>

      <div className="session-list">
        {group.entries.map(({ ftIdx, day }) => {
          const ft = freeTimeClasses[ftIdx]
          if (!isFreeTimeEligible(ft, scout)) return null
          const state = getFreeTimeSessionState(scout, ftIdx, selections, campSchedule)
          const occupancy = getFreeTimeOccupancy(selections, ftIdx, scout.memberId)

          return (
            <button
              key={ftIdx}
              className={`session-btn session-btn--${state}`}
              onClick={() => state !== 'conflict' && onToggle(ftIdx)}
              disabled={state === 'conflict'}
              aria-pressed={state === 'selected'}
              aria-label={`${day} — ${state}`}
            >
              <span className="session-time">{DAY_SHORT[day] ?? day}</span>
              {occupancy > 0 && (
                <span className="occupancy-badge" title={`${occupancy} other scout${occupancy > 1 ? 's' : ''}`}>
                  {occupancy}
                </span>
              )}
              {state === 'selected' && <span className="session-check">✓</span>}
            </button>
          )
        })}
      </div>
    </div>
  )
}
