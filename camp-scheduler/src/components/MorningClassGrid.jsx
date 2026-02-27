import React, { useState, useCallback } from 'react'
import {
  getDailyClassColor,
  getDailySessionState,
  getMorningOccupancy,
  isDailyClassComplete
} from '../utils/scheduleUtils'

// ── Time slot grid constants ────────────────────────────────
// 30-minute boundary times, 9:00 – 12:00
const BOUNDARY_TIMES = ['9:00', '9:30', '10:00', '10:30', '11:00', '11:30', '12:00']

const GRID_SLOTS = [
  { start: '9:00',  end: '9:30'  },
  { start: '9:30',  end: '10:00' },
  { start: '10:00', end: '10:30' },
  { start: '10:30', end: '11:00' },
  { start: '11:00', end: '11:30' },
  { start: '11:30', end: '12:00' },
]

/**
 * Converts a session list into a flat array of cell descriptors that covers
 * all 6 grid slots.  Sessions span multiple slots; gaps become empty cells.
 *
 * @returns Array of { type:'session', sessionIdx, colspan } | { type:'empty' }
 */
function buildRowCells(sessions) {
  const cells = []
  let slotPos = 0 // current position in BOUNDARY_TIMES (0 = 9:00 … 6 = 12:00)

  // Sort by start time so we can walk left-to-right
  const sorted = sessions
    .map((s, i) => ({ start: s.start, end: s.end, origIdx: i }))
    .sort((a, b) => BOUNDARY_TIMES.indexOf(a.start) - BOUNDARY_TIMES.indexOf(b.start))

  for (const sess of sorted) {
    const startPos = BOUNDARY_TIMES.indexOf(sess.start)
    const endPos   = BOUNDARY_TIMES.indexOf(sess.end)

    // Fill any empty slots before this session
    while (slotPos < startPos) {
      cells.push({ type: 'empty' })
      slotPos++
    }

    const colspan = endPos - startPos
    cells.push({ type: 'session', sessionIdx: sess.origIdx, colspan })
    slotPos += colspan
  }

  // Fill trailing empty slots
  while (slotPos < 6) {
    cells.push({ type: 'empty' })
    slotPos++
  }

  return cells
}

// ── Label helpers ───────────────────────────────────────────
function stripMB(name)   { return name.endsWith(' MB')   ? name.slice(0, -3) : name }
function stripRank(name) { return name.endsWith(' Rank') ? name.slice(0, -5) : name }

const EAGLE_BADGES = new Set(window.SCOUT_SIGHT_DATA.eagleRequiredBadges ?? [])

// ── Progress helpers ─────────────────────────────────────────
/**
 * Returns an array of progress pill descriptors for a daily class row.
 * Each pill: { label, name, remaining: [{id, text}] }
 */
function getProgressPills(dc, scout) {
  const pills = []
  if (dc.ranks.length > 0) {
    for (const rank of dc.ranks) {
      if (scout.completedRanks.includes(rank)) continue
      const prog = scout.campRankProgress?.[rank]
      if (!prog || prog.total === 0 || !prog.remaining?.length) continue
      const pct = Math.round(prog.remaining.length / prog.total * 100)
      pills.push({ label: `${pct}% left at camp`, name: stripRank(rank), remaining: prog.remaining })
    }
  } else {
    for (const mb of dc.meritBadges) {
      if (!scout.partialMeritBadges.includes(mb)) continue
      const prog = scout.meritBadgeProgress?.[mb]
      if (!prog || prog.total === 0) continue
      const pct = Math.round(prog.remaining.length / prog.total * 100)
      pills.push({ label: `${pct}% left`, name: stripMB(mb), remaining: prog.remaining })
    }
  }
  return pills
}

// ── Component ───────────────────────────────────────────────
export default function MorningClassGrid({ entries, scout, scouts, campSchedule, selections, onToggle }) {
  const [tooltip, setTooltip] = useState(null)    // { names: string[], x, y }
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

  return (
    <div className="schedule-grid-wrapper">
      <table className="schedule-table">
        <colgroup>
          <col className="schedule-col-label" />
          {GRID_SLOTS.map((_, i) => <col key={i} />)}
        </colgroup>

        <thead>
          <tr>
            <th className="schedule-th-label">Class</th>
            {GRID_SLOTS.map((slot, i) => (
              <th key={i} className="schedule-th-time">
                <span className="schedule-th-start">{slot.start}</span>
                <span className="schedule-th-end">–{slot.end}</span>
              </th>
            ))}
          </tr>
        </thead>

        <tbody>
          {entries.map(({ dc, idx: classIdx }) => {
            const isComplete = isDailyClassComplete(dc, scout)
            const color      = isComplete ? 'done' : getDailyClassColor(dc, scout)
            const isRank     = dc.ranks.length > 0
            const labels     = isRank ? dc.ranks.map(stripRank) : dc.meritBadges.map(stripMB)
            const rowLabel   = labels.join(' / ')
            const cells      = buildRowCells(dc.sessions)
            const isEagle    = !isRank && dc.meritBadges.some(mb => EAGLE_BADGES.has(mb))
            const pills      = isComplete ? [] : getProgressPills(dc, scout)

            return (
              <tr key={classIdx} className={`schedule-row${isComplete ? ' schedule-row--done' : ''}`}>
                <td className={`schedule-row-label schedule-row-label--${color}`}>
                  {isRank && <span className="schedule-rank-tag">Rank</span>}
                  <span className="schedule-row-name">
                    {isEagle && (
                      <img
                        src="images/icons/eagle_required.png"
                        alt="Eagle required"
                        className="eagle-icon"
                        title="Eagle-required merit badge"
                      />
                    )}
                    {rowLabel}
                  </span>
                  {pills.map((pill, pi) => (
                    <span
                      key={pi}
                      className="schedule-progress-pill"
                      onMouseEnter={e => showReqTooltip(e,
                        pills.length > 1 ? `${pill.name} — remaining` : 'Remaining requirements',
                        pill.remaining)}
                      onMouseLeave={hideReqTooltip}
                    >
                      {pills.length > 1 ? `${pill.name}: ${pill.label}` : pill.label}
                    </span>
                  ))}
                  {isComplete && <span className="schedule-done-badge">✓ Done</span>}
                </td>

                {cells.map((cell, cellIdx) => {
                  if (cell.type === 'empty') {
                    return <td key={cellIdx} className="schedule-cell schedule-cell--empty" />
                  }

                  const { sessionIdx, colspan } = cell
                  const session   = dc.sessions[sessionIdx]
                  const timeLabel = `${session.start}–${session.end}`

                  if (isComplete) {
                    return (
                      <td key={cellIdx} className="schedule-cell" colSpan={colspan}>
                        <button
                          className="schedule-btn schedule-btn--done"
                          disabled
                          aria-label={`${rowLabel} ${timeLabel} — already completed`}
                        >
                          <span className="schedule-btn-time">{timeLabel}</span>
                        </button>
                      </td>
                    )
                  }

                  const state     = getDailySessionState(scout, classIdx, sessionIdx, selections, campSchedule)
                  const selectors = scouts.filter(s =>
                    s.memberId !== scout.memberId &&
                    selections[s.memberId]?.morning.some(
                      sel => sel.classIdx === classIdx && sel.sessionIdx === sessionIdx
                    )
                  ).map(s => s.name)

                  return (
                    <td key={cellIdx} className="schedule-cell" colSpan={colspan}>
                      <button
                        className={`schedule-btn schedule-btn--${state}`}
                        onClick={() => state !== 'conflict' && onToggle(classIdx, sessionIdx)}
                        disabled={state === 'conflict'}
                        aria-pressed={state === 'selected'}
                        aria-label={`${rowLabel} ${timeLabel} — ${state}`}
                        onMouseEnter={e => showTooltip(e, selectors)}
                        onMouseLeave={hideTooltip}
                      >
                        <span className="schedule-btn-time">{timeLabel}</span>
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
