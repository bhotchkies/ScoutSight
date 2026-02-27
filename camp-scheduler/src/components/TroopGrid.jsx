import React from 'react'

// ── Grid constants — must match MorningClassGrid ─────────────
const BOUNDARY_TIMES = ['9:00', '9:30', '10:00', '10:30', '11:00', '11:30', '12:00']
const GRID_SLOTS = [
  { start: '9:00',  end: '9:30'  },
  { start: '9:30',  end: '10:00' },
  { start: '10:00', end: '10:30' },
  { start: '10:30', end: '11:00' },
  { start: '11:00', end: '11:30' },
  { start: '11:30', end: '12:00' },
]

const DAY_ORDER = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday']
const DAY_SHORT = {
  Sunday: 'Sun', Monday: 'Mon', Tuesday: 'Tue', Wednesday: 'Wed',
  Thursday: 'Thu', Friday: 'Fri', Saturday: 'Sat'
}

function stripMB(n)   { return n.endsWith(' MB')   ? n.slice(0, -3) : n }
function stripRank(n) { return n.endsWith(' Rank') ? n.slice(0, -5) : n }

/**
 * Converts a scout's morning selections into a flat array of cell descriptors
 * that spans all 6 half-hour grid slots, with colspan for multi-slot sessions.
 * Returns: Array of { type:'empty' } | { type:'filled', colspan, label }
 */
function buildMorningCells(morningSelections, dailyClasses) {
  const sessions = morningSelections
    .map(({ classIdx, sessionIdx }) => {
      const dc   = dailyClasses[classIdx]
      const sess = dc.sessions[sessionIdx]
      const label = dc.meritBadges.length > 0
        ? dc.meritBadges.map(stripMB).join(' / ')
        : dc.ranks.map(stripRank).join(' / ')
      return { start: sess.start, end: sess.end, label }
    })
    .sort((a, b) => BOUNDARY_TIMES.indexOf(a.start) - BOUNDARY_TIMES.indexOf(b.start))

  const cells = []
  let slotPos = 0

  for (const sess of sessions) {
    const startPos = BOUNDARY_TIMES.indexOf(sess.start)
    const endPos   = BOUNDARY_TIMES.indexOf(sess.end)
    if (startPos < 0 || endPos < 0) continue

    while (slotPos < startPos) { cells.push({ type: 'empty' }); slotPos++ }

    const colspan = endPos - startPos
    cells.push({ type: 'filled', colspan, label: sess.label })
    slotPos += colspan
  }

  while (slotPos < 6) { cells.push({ type: 'empty' }); slotPos++ }

  return cells
}

export default function TroopGrid({ scouts, campSchedule, selections }) {
  const { dailyClasses, freeTimeClasses } = campSchedule

  // Scouts with at least one morning selection
  const morningScouts = scouts.filter(s => selections[s.memberId]?.morning?.length > 0)

  // Scouts with at least one free-time selection
  const ftScouts = scouts.filter(s => selections[s.memberId]?.freeTime?.length > 0)

  // Days that actually appear in scouts' free-time selections, sorted by calendar order
  const usedDays = [...new Set(
    ftScouts.flatMap(s =>
      (selections[s.memberId]?.freeTime ?? []).map(({ ftIdx }) => freeTimeClasses[ftIdx].day)
    )
  )].sort((a, b) => DAY_ORDER.indexOf(a) - DAY_ORDER.indexOf(b))

  return (
    <div className="troop-grid-wrap">

      {/* ── Morning classes grid ── */}
      {morningScouts.length > 0 && (
        <>
          <h2 className="print-section-title">Morning Classes</h2>
          <div className="tg-scroll-wrap">
            <table className="troop-grid tg-morning">
              <colgroup>
                <col className="tg-name-col" />
                {GRID_SLOTS.map((_, i) => <col key={i} className="tg-slot-col" />)}
              </colgroup>
              <thead>
                <tr>
                  <th className="tg-th-name">Scout</th>
                  {GRID_SLOTS.map((slot, i) => (
                    <th key={i} className="tg-th-slot">
                      <span className="tg-slot-start">{slot.start}</span>
                      <span className="tg-slot-end">–{slot.end}</span>
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {morningScouts.map(scout => {
                  const cells = buildMorningCells(
                    selections[scout.memberId]?.morning ?? [],
                    dailyClasses
                  )
                  return (
                    <tr key={scout.memberId}>
                      <td className="tg-scout-name">{scout.name}</td>
                      {cells.map((cell, ci) =>
                        cell.type === 'empty'
                          ? <td key={ci} className="tg-cell" />
                          : <td key={ci} className="tg-cell tg-cell--filled" colSpan={cell.colspan}>
                              {cell.label}
                            </td>
                      )}
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        </>
      )}

      {/* ── Free-time classes grid ── */}
      {ftScouts.length > 0 && usedDays.length > 0 && (
        <>
          <h2 className="print-section-title tg-ft-title">Free-Time Classes</h2>
          <div className="tg-scroll-wrap">
            <table className="troop-grid tg-freetime">
              <colgroup>
                <col className="tg-name-col" />
                {usedDays.map(day => <col key={day} className="tg-day-col" />)}
              </colgroup>
              <thead>
                <tr>
                  <th className="tg-th-name">Scout</th>
                  {usedDays.map(day => (
                    <th key={day} className="tg-th-slot">{DAY_SHORT[day] ?? day}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {ftScouts.map(scout => {
                  const ftMap = {}
                  ;(selections[scout.memberId]?.freeTime ?? []).forEach(({ ftIdx }) => {
                    const ft = freeTimeClasses[ftIdx]
                    ftMap[ft.day] = ft.meritBadges.map(stripMB).join(' / ')
                  })
                  return (
                    <tr key={scout.memberId}>
                      <td className="tg-scout-name">{scout.name}</td>
                      {usedDays.map(day => (
                        <td
                          key={day}
                          className={`tg-cell tg-cell--freetime${ftMap[day] ? ' tg-cell--filled' : ''}`}
                        >
                          {ftMap[day] ?? ''}
                        </td>
                      ))}
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        </>
      )}

    </div>
  )
}
