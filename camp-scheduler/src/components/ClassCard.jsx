import React from 'react'
import {
  getDailyClassColor,
  getDailySessionState,
  getMorningOccupancy
} from '../utils/scheduleUtils'

function stripMB(name) {
  return name.endsWith(' MB') ? name.slice(0, -3) : name
}

function stripRank(name) {
  return name.endsWith(' Rank') ? name.slice(0, -5) : name
}

function formatTime(t) {
  return t.replace(':0', ':0') // keep as-is, already formatted nicely
}

export default function ClassCard({ dc, classIdx, scout, campSchedule, selections, onToggle }) {
  const cardColor = getDailyClassColor(dc, scout)

  const isRankClass = dc.ranks.length > 0
  const labels = isRankClass
    ? dc.ranks.map(stripRank)
    : dc.meritBadges.map(stripMB)

  return (
    <div className={`class-card class-card--${cardColor}`}>
      <div className="class-card-header">
        {isRankClass && <span className="class-type-tag class-type-tag--rank">Rank Skills</span>}
        <h3 className="class-card-name">{labels.join(' & ')}</h3>
        {cardColor === 'partial' && (
          <span className="card-status-chip card-status-chip--partial">Partial</span>
        )}
        {cardColor === 'nearly-done' && (
          <span className="card-status-chip card-status-chip--nearly-done">{'>'} 50% done</span>
        )}
      </div>

      <div className="session-list">
        {dc.sessions.map((session, sessionIdx) => {
          const state = getDailySessionState(scout, classIdx, sessionIdx, selections, campSchedule)
          const occupancy = getMorningOccupancy(selections, classIdx, sessionIdx, scout.memberId)
          const timeLabel = `${formatTime(session.start)}–${formatTime(session.end)}`

          return (
            <button
              key={sessionIdx}
              className={`session-btn session-btn--${state}`}
              onClick={() => state !== 'conflict' && onToggle(classIdx, sessionIdx)}
              disabled={state === 'conflict'}
              aria-pressed={state === 'selected'}
              aria-label={`${timeLabel} — ${state}`}
            >
              <span className="session-time">{timeLabel}</span>
              {occupancy > 0 && (
                <span className="occupancy-badge" title={`${occupancy} other scout${occupancy > 1 ? 's' : ''} selected this slot`}>
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
