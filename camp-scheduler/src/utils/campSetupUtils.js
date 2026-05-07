import { getDailySessionState } from './scheduleUtils'

function parseMinutes(t) {
  const [h, m] = t.split(':').map(Number)
  return h * 60 + m
}

/**
 * True if the scout should be auto-assigned to this MB daily class.
 * Skips if any badge is completed or ≥10% done.
 */
function shouldAssignMbClass(scout, dc) {
  for (const mb of dc.meritBadges) {
    if (scout.completedMeritBadges.includes(mb)) return false
    const prog = scout.meritBadgeProgress?.[mb]
    if (prog && prog.total > 0 && prog.done / prog.total >= 0.10) return false
  }
  return true
}

/** Why shouldAssignMbClass returned false (first failing badge), or null if it would pass. */
function mbSkipReason(scout, dc) {
  for (const mb of dc.meritBadges) {
    const name = mb.endsWith(' MB') ? mb.slice(0, -3) : mb
    if (scout.completedMeritBadges.includes(mb)) return `${name}: already earned`
    const prog = scout.meritBadgeProgress?.[mb]
    if (prog && prog.total > 0 && prog.done / prog.total >= 0.10) {
      return `${name}: ${Math.round(prog.done / prog.total * 100)}% done (≥10%)`
    }
  }
  return null
}

/**
 * Returns the earliest sessionIdx with no time/badge conflict for this scout,
 * or null if every session conflicts.
 */
function findEarliestSession(scout, classIdx, proxy, campSchedule) {
  const dc = campSchedule.dailyClasses[classIdx]
  const order = dc.sessions
    .map((s, i) => ({ i, start: parseMinutes(s.start) }))
    .sort((a, b) => a.start - b.start)
    .map(x => x.i)
  for (const sessionIdx of order) {
    if (getDailySessionState(scout, classIdx, sessionIdx, proxy, campSchedule) === 'available') {
      return sessionIdx
    }
  }
  return null
}

function stripRank(r) { return r.replace(' Rank', '') }
function stripMB(mb)   { return mb.endsWith(' MB') ? mb.slice(0, -3) : mb }

/**
 * Bulk camp setup: marks non-attending scouts, resets non-finalized attending scouts,
 * then auto-assigns rank classes and the best available MB choice per scout.
 *
 * @param {object[]} scouts
 * @param {object}   campSchedule
 * @param {object}   currentSelections
 * @param {object}   currentStatuses
 * @param {Set<string>} attendingIds
 * @param {number}   thresholdPct  assign rank class when pct remaining >= thresholdPct
 * @param {number[]} mbClassIdxs   up to 3 dailyClasses indices (-1 = no choice)
 * @returns {{ newSelections, newStatuses, summary }}
 */
export function runCampSetup(scouts, campSchedule, currentSelections, currentStatuses, attendingIds, thresholdPct, mbClassIdxs) {
  const newSelections = {}
  const newStatuses   = {}
  const summary = {
    attendingCount:            0,
    notAttendingCount:         0,
    finalizedNotOnListNames:   [],
    rankAssigned:              {},  // classLabel → count
    mbAssigned:                {},  // badgeName  → count
    noMbCount:                 0,
    decisions:                 [],  // per-scout debug entries
  }

  for (const scout of scouts) {
    const { memberId } = scout
    const isAttending  = attendingIds.has(memberId)
    const status       = currentStatuses[memberId]

    // ── Not attending ──────────────────────────────────────────
    if (!isAttending) {
      if (status === 'finalized') {
        summary.finalizedNotOnListNames.push(scout.name)
        if (currentSelections[memberId]) newSelections[memberId] = currentSelections[memberId]
        newStatuses[memberId] = 'finalized'
        summary.decisions.push({ name: scout.name, memberId, statusLabel: 'finalized — not on list (skipped)', rankNotes: [], mbNotes: [] })
      } else {
        newStatuses[memberId] = 'not_attending'
        summary.notAttendingCount++
        summary.decisions.push({ name: scout.name, memberId, statusLabel: 'not attending', rankNotes: [], mbNotes: [] })
      }
      continue
    }

    summary.attendingCount++

    // Finalized attending scouts are left untouched
    if (status === 'finalized') {
      if (currentSelections[memberId]) newSelections[memberId] = currentSelections[memberId]
      newStatuses[memberId] = 'finalized'
      summary.decisions.push({ name: scout.name, memberId, statusLabel: 'finalized — left untouched', rankNotes: [], mbNotes: [] })
      continue
    }

    // ── Build fresh selections ─────────────────────────────────
    const scoutSel  = { morning: [], freeTime: [] }
    const proxy     = { [memberId]: scoutSel }
    const rankNotes = []
    const mbNotes   = []

    // 1. Rank classes first (best time slots)
    for (let ci = 0; ci < campSchedule.dailyClasses.length; ci++) {
      const dc = campSchedule.dailyClasses[ci]
      if (dc.ranks.length === 0) continue

      const classLabel = dc.ranks.map(stripRank).join(' + ')

      // Compute qualification + debug note together so they can never disagree.
      // remaining falls back to total-done when the Java data omits it for unstarted ranks.
      const rankInfos = dc.ranks.map(rank => {
        if (scout.completedRanks.includes(rank))
          return { qualifies: false, note: `${stripRank(rank)}: already complete` }
        const prog = scout.campRankProgress?.[rank]
        if (!prog || prog.total === 0)
          return { qualifies: false, note: `${stripRank(rank)}: no camp coverage` }
        const remainingCount = prog.remaining?.length || (prog.total - (prog.done ?? 0))
        // Camp reqs appear complete but rank not yet awarded — enroll anyway.
        if (remainingCount <= 0)
          return { qualifies: true, note: `${stripRank(rank)}: rank not yet awarded ✓` }
        const pct = Math.round(remainingCount / prog.total * 100)
        const q = pct >= thresholdPct
        return {
          qualifies: q,
          note: q
            ? `${stripRank(rank)}: ${pct}% remaining ✓`
            : `${stripRank(rank)}: ${pct}% remaining (threshold ${thresholdPct}%)`,
        }
      })

      const qualifies  = rankInfos.some(r => r.qualifies)
      const rankDetails = rankInfos.map(r => r.note)

      if (!qualifies) {
        rankNotes.push(`${classLabel} → skipped (${rankDetails.join('; ')})`)
        continue
      }

      const si = findEarliestSession(scout, ci, proxy, campSchedule)
      if (si === null) {
        rankNotes.push(`${classLabel} → skipped (time conflict)`)
        continue
      }

      scoutSel.morning.push({ classIdx: ci, sessionIdx: si })
      const { start, end } = dc.sessions[si]
      rankNotes.push(`${classLabel} → assigned ${start}–${end}`)
      summary.rankAssigned[classLabel] = (summary.rankAssigned[classLabel] ?? 0) + 1
    }

    // 2. MB choices — 1st, 2nd, 3rd in priority order; stop at first success
    let mbAssigned = false
    const choiceLabels = ['1st', '2nd', '3rd']
    for (let choiceNum = 0; choiceNum < mbClassIdxs.length; choiceNum++) {
      const ci = mbClassIdxs[choiceNum]
      if (ci < 0) continue
      const dc = campSchedule.dailyClasses[ci]
      if (!dc?.meritBadges?.length) continue
      const badgeLabel = dc.meritBadges.map(stripMB).join(' + ')
      const label = `${badgeLabel} (${choiceLabels[choiceNum]} choice)`

      const skipReason = mbSkipReason(scout, dc)
      if (skipReason) {
        mbNotes.push(`${label} → skipped (${skipReason})`)
        continue
      }

      const si = findEarliestSession(scout, ci, proxy, campSchedule)
      if (si === null) {
        mbNotes.push(`${label} → skipped (time conflict)`)
        continue
      }

      scoutSel.morning.push({ classIdx: ci, sessionIdx: si })
      const { start, end } = dc.sessions[si]
      mbNotes.push(`${label} → assigned ${start}–${end}`)
      for (const mb of dc.meritBadges) {
        summary.mbAssigned[stripMB(mb)] = (summary.mbAssigned[stripMB(mb)] ?? 0) + 1
      }
      mbAssigned = true
    }

    if (!mbAssigned && mbClassIdxs.every(ci => ci < 0)) {
      mbNotes.push('no MB choices specified')
    } else if (!mbAssigned) {
      summary.noMbCount++
    }

    if (scoutSel.morning.length > 0) {
      newSelections[memberId] = scoutSel
      newStatuses[memberId]   = 'in_progress'
    }

    const statusLabel = scoutSel.morning.length > 0
      ? `in progress (${scoutSel.morning.length} class${scoutSel.morning.length !== 1 ? 'es' : ''} assigned)`
      : 'attending — no classes assigned'

    summary.decisions.push({ name: scout.name, memberId, statusLabel, rankNotes, mbNotes })
  }

  // IDs the caller passed in that don't match any scout in the roster
  const knownIds = new Set(scouts.map(s => s.memberId))
  summary.unmatchedIds = [...attendingIds].filter(id => !knownIds.has(id))

  return { newSelections, newStatuses, summary }
}
