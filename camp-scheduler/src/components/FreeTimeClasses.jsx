import React, { useState, useCallback } from 'react'
import {
  getFreeTimeColor,
  getFreeTimeSessionState,
  getFreeTimeOccupancy,
  isFreeTimeGroupComplete
} from '../utils/scheduleUtils'

const DAY_ORDER = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday']
const DAY_SHORT = {
  Sunday: 'Sun', Monday: 'Mon', Tuesday: 'Tue', Wednesday: 'Wed',
  Thursday: 'Thu', Friday: 'Fri', Saturday: 'Sat'
}

function stripMB(name) { return name.endsWith(' MB') ? name.slice(0, -3) : name }

const EAGLE_BADGES = new Set(window.SCOUT_SIGHT_DATA.eagleRequiredBadges ?? [])

export default function FreeTimeClasses({ groups, scout, scouts, campSchedule, selections, onToggle }) {
  const { freeTimeClasses } = campSchedule
  const [tooltip, setTooltip] = useState(null)
  const [reqTooltip, setReqTooltip] = useState(null) // { title, items: [{id,text}], x, y }

  const showTooltip = useCallback((e, names) => {
    if (names.length === 0) return
    const r = e.currentTarget.getBoundingClientRect()
    setTooltip({ names, x: r.left + r.width / 2, y: r.top })
  }, [])

  const hideTooltip = useCallback(() => setTooltip(null), [])

  const showReqTooltip = useCallback((e, title, remaining) => {
    if (!remaining?.length) return
    const r = e.currentTarget.getBoundingClientRect()
    setReqTooltip({ title, items: remaining, x: r.left + r.width / 2, y: r.top })
  }, [])

  const hideReqTooltip = useCallback(() => setReqTooltip(null), [])

  // Collect all days that appear across all groups, sorted by calendar order
  const allDays = [...new Set(
    groups.flatMap(g => g.entries.map(e => e.day))
  )].sort((a, b) => DAY_ORDER.indexOf(a) - DAY_ORDER.indexOf(b))

  return (
    <div className="schedule-grid-wrapper">
      <table className="schedule-table">
        <colgroup>
          <col className="schedule-col-label" />
          {allDays.map((_, i) => <col key={i} />)}
        </colgroup>

        <thead>
          <tr>
            <th className="schedule-th-label">Badge</th>
            {allDays.map(day => (
              <th key={day} className="schedule-th-time">
                <span className="schedule-th-start">{DAY_SHORT[day] ?? day}</span>
              </th>
            ))}
          </tr>
        </thead>

        <tbody>
          {groups.map(group => {
            const isComplete  = isFreeTimeGroupComplete(group, scout, freeTimeClasses)
            const firstFt     = freeTimeClasses[group.entries[0].ftIdx]
            const color       = isComplete ? 'done' : getFreeTimeColor(firstFt, scout)
            const displayName = group.badgeName.split(' + ').map(stripMB).join(' / ')
            const isEagle     = group.badgeName.split(' + ').some(mb => EAGLE_BADGES.has(mb))

            // Progress pills for partial badges
            const pills = isComplete ? [] : group.badgeName.split(' + ').flatMap(mb => {
              if (!scout.partialMeritBadges.includes(mb)) return []
              const prog = scout.meritBadgeProgress?.[mb]
              if (!prog || prog.total === 0) return []
              const pct = Math.round(prog.remaining.length / prog.total * 100)
              const mbBadges = group.badgeName.split(' + ')
              return [{ label: `${pct}% left`, name: stripMB(mb), remaining: prog.remaining,
                        showName: mbBadges.length > 1 }]
            })

            // day → ftIdx lookup for this group
            const dayMap = Object.fromEntries(group.entries.map(e => [e.day, e.ftIdx]))

            return (
              <tr key={group.badgeName} className={`schedule-row${isComplete ? ' schedule-row--done' : ''}`}>
                <td className={`schedule-row-label schedule-row-label--${color}`}>
                  <span className="schedule-row-name">
                    {isEagle && (
                      <img
                        src="images/icons/eagle_required.png"
                        alt="Eagle required"
                        className="eagle-icon"
                        title="Eagle-required merit badge"
                      />
                    )}
                    {displayName}
                  </span>
                  {pills.map((pill, pi) => (
                    <span
                      key={pi}
                      className="schedule-progress-pill"
                      onMouseEnter={e => showReqTooltip(e,
                        pill.showName ? `${pill.name} — remaining` : 'Remaining requirements',
                        pill.remaining)}
                      onMouseLeave={hideReqTooltip}
                    >
                      {pill.showName ? `${pill.name}: ${pill.label}` : pill.label}
                    </span>
                  ))}
                  {isComplete && <span className="schedule-done-badge">✓ Done</span>}
                </td>

                {allDays.map(day => {
                  const ftIdx = dayMap[day]

                  if (ftIdx === undefined) {
                    return <td key={day} className="schedule-cell schedule-cell--empty" />
                  }

                  if (isComplete) {
                    return (
                      <td key={day} className="schedule-cell">
                        <button
                          className="schedule-btn schedule-btn--done"
                          disabled
                          aria-label={`${displayName} ${day} — already completed`}
                        >
                          <span className="schedule-btn-time">{DAY_SHORT[day] ?? day}</span>
                        </button>
                      </td>
                    )
                  }

                  const state     = getFreeTimeSessionState(scout, ftIdx, selections, campSchedule)
                  const selectors = scouts.filter(s =>
                    s.memberId !== scout.memberId &&
                    selections[s.memberId]?.freeTime.some(sel => sel.ftIdx === ftIdx)
                  ).map(s => s.name)

                  return (
                    <td key={day} className="schedule-cell">
                      <button
                        className={`schedule-btn schedule-btn--${state}`}
                        onClick={() => state !== 'conflict' && onToggle(ftIdx)}
                        disabled={state === 'conflict'}
                        aria-pressed={state === 'selected'}
                        aria-label={`${displayName} ${day} — ${state}`}
                        onMouseEnter={e => showTooltip(e, selectors)}
                        onMouseLeave={hideTooltip}
                      >
                        <span className="schedule-btn-time">{DAY_SHORT[day] ?? day}</span>
                        {selectors.length > 0 && (
                          <span className="schedule-btn-occ">{selectors.length}</span>
                        )}
                        {state === 'selected' && <span className="schedule-btn-check">✓</span>}
                      </button>
                    </td>
                  )
                })}
              </tr>
            )
          })}
        </tbody>
      </table>
      {tooltip && (
        <div
          className="schedule-tooltip"
          style={{ left: tooltip.x, top: tooltip.y }}
        >
          <div className="schedule-tooltip-title">Also selected by:</div>
          {tooltip.names.map(name => (
            <div key={name} className="schedule-tooltip-name">{name}</div>
          ))}
        </div>
      )}
      {reqTooltip && (
        <div
          className="schedule-req-tooltip"
          style={{ left: reqTooltip.x, top: reqTooltip.y }}
        >
          <div className="schedule-req-tooltip-title">{reqTooltip.title}</div>
          {reqTooltip.items.map(item => (
            <div key={item.id} className="schedule-req-tooltip-item">
              <span className="schedule-req-tooltip-id">{item.id}</span>
              <span className="schedule-req-tooltip-text">{item.text}</span>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
