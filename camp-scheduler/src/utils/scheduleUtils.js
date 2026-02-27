// ---------------------------------------------------------------------------
// Time parsing helpers
// ---------------------------------------------------------------------------

function parseMinutes(timeStr) {
  const [h, m] = timeStr.split(':').map(Number)
  return h * 60 + m
}

export function timeSlotsOverlap(slot1, slot2) {
  const s1 = parseMinutes(slot1.start), e1 = parseMinutes(slot1.end)
  const s2 = parseMinutes(slot2.start), e2 = parseMinutes(slot2.end)
  return s1 < e2 && s2 < e1
}

// ---------------------------------------------------------------------------
// Eligibility — whether a class should be shown for a scout at all
// ---------------------------------------------------------------------------

export function isDailyClassEligible(dc, scout) {
  if (dc.meritBadges.length > 0) {
    return dc.meritBadges.some(mb => !scout.completedMeritBadges.includes(mb))
  }
  if (dc.ranks.length > 0) {
    return dc.ranks.some(rank => !scout.completedRanks.includes(rank))
  }
  return false
}

/** Returns true when every badge/rank in the class is already completed. */
export function isDailyClassComplete(dc, scout) {
  if (dc.meritBadges.length > 0) {
    return dc.meritBadges.every(mb => scout.completedMeritBadges.includes(mb))
  }
  if (dc.ranks.length > 0) {
    return dc.ranks.every(rank => scout.completedRanks.includes(rank))
  }
  return false
}

export function isFreeTimeEligible(ft, scout) {
  return ft.meritBadges.every(mb => !scout.completedMeritBadges.includes(mb))
}

/** Returns true when every badge in the free-time group is already completed. */
export function isFreeTimeGroupComplete(group, scout, freeTimeClasses) {
  const badges = freeTimeClasses[group.entries[0].ftIdx].meritBadges
  return badges.every(mb => scout.completedMeritBadges.includes(mb))
}

// ---------------------------------------------------------------------------
// Card color — background tint based on completion status
// ---------------------------------------------------------------------------

/** Returns 'partial' | 'nearly-done' | 'available' */
export function getDailyClassColor(dc, scout) {
  if (dc.meritBadges.length > 0) {
    const anyPartial = dc.meritBadges.some(mb => scout.partialMeritBadges.includes(mb))
    return anyPartial ? 'partial' : 'available'
  }
  if (dc.ranks.length > 0) {
    const anyNearlyDone = dc.ranks.some(rank => {
      if (scout.completedRanks.includes(rank)) return false
      const prog = scout.campRankProgress?.[rank]
      if (!prog || prog.total === 0) return false
      return prog.done / prog.total > 0.5
    })
    return anyNearlyDone ? 'nearly-done' : 'available'
  }
  return 'available'
}

export function getFreeTimeColor(ft, scout) {
  const anyPartial = ft.meritBadges.some(mb => scout.partialMeritBadges.includes(mb))
  return anyPartial ? 'partial' : 'available'
}

// ---------------------------------------------------------------------------
// Session state — for an individual morning session button
// 'selected' | 'conflict' | 'available'
// ---------------------------------------------------------------------------

export function getDailySessionState(scout, classIdx, sessionIdx, selections, campSchedule) {
  const { dailyClasses, freeTimeClasses } = campSchedule
  const dc = dailyClasses[classIdx]
  const scoutSel = selections[scout.memberId] || { morning: [], freeTime: [] }

  // Is this exact session already selected?
  if (scoutSel.morning.some(s => s.classIdx === classIdx && s.sessionIdx === sessionIdx)) {
    return 'selected'
  }

  const thisNames = [...dc.meritBadges, ...dc.ranks]

  // Same badge/rank selected elsewhere (different class or different session of same class)?
  const badgeConflict = scoutSel.morning.some(s => {
    const other = dailyClasses[s.classIdx]
    const otherNames = [...other.meritBadges, ...other.ranks]
    return otherNames.some(name => thisNames.includes(name))
  }) || scoutSel.freeTime.some(s => {
    const ft = freeTimeClasses[s.ftIdx]
    return ft.meritBadges.some(name => thisNames.includes(name))
  })
  if (badgeConflict) return 'conflict'

  // Time slot conflict with another selected morning class?
  const thisSlot = dc.sessions[sessionIdx]
  const timeConflict = scoutSel.morning.some(s => {
    if (s.classIdx === classIdx) return false // same class, different session — badge rule handles
    const otherSlot = dailyClasses[s.classIdx].sessions[s.sessionIdx]
    return timeSlotsOverlap(thisSlot, otherSlot)
  })
  if (timeConflict) return 'conflict'

  return 'available'
}

// ---------------------------------------------------------------------------
// Free-time session state
// ---------------------------------------------------------------------------

export function getFreeTimeSessionState(scout, ftIdx, selections, campSchedule) {
  const { dailyClasses, freeTimeClasses } = campSchedule
  const ft = freeTimeClasses[ftIdx]
  const scoutSel = selections[scout.memberId] || { morning: [], freeTime: [] }

  if (scoutSel.freeTime.some(s => s.ftIdx === ftIdx)) return 'selected'

  // Same badge selected in morning or other free-time
  const badgeConflict = ft.meritBadges.some(mb => {
    const inMorning = scoutSel.morning.some(s => dailyClasses[s.classIdx].meritBadges.includes(mb))
    const inOtherFt = scoutSel.freeTime.some(s => freeTimeClasses[s.ftIdx].meritBadges.includes(mb))
    return inMorning || inOtherFt
  })
  if (badgeConflict) return 'conflict'

  // Same day+time conflict
  const dayTimeConflict = scoutSel.freeTime.some(s => {
    const other = freeTimeClasses[s.ftIdx]
    return other.day === ft.day && other.time === ft.time
  })
  if (dayTimeConflict) return 'conflict'

  return 'available'
}

// ---------------------------------------------------------------------------
// Occupancy — how many OTHER scouts picked a given slot
// ---------------------------------------------------------------------------

export function getMorningOccupancy(allSelections, classIdx, sessionIdx, excludeMemberId) {
  return Object.entries(allSelections)
    .filter(([id]) => id !== excludeMemberId)
    .reduce((n, [, sel]) =>
      n + (sel.morning.some(s => s.classIdx === classIdx && s.sessionIdx === sessionIdx) ? 1 : 0)
    , 0)
}

export function getFreeTimeOccupancy(allSelections, ftIdx, excludeMemberId) {
  return Object.entries(allSelections)
    .filter(([id]) => id !== excludeMemberId)
    .reduce((n, [, sel]) =>
      n + (sel.freeTime.some(s => s.ftIdx === ftIdx) ? 1 : 0)
    , 0)
}

// ---------------------------------------------------------------------------
// Free-time grouping — group freeTimeClasses entries by badge name
// Returns: Array<{ badgeName, entries: [{ftIdx, day, time}] }>
// ---------------------------------------------------------------------------

export function groupFreeTimeByBadge(freeTimeClasses) {
  const map = new Map()
  freeTimeClasses.forEach((ft, ftIdx) => {
    const key = ft.meritBadges.join(' + ')
    if (!map.has(key)) map.set(key, { badgeName: key, entries: [] })
    map.get(key).entries.push({ ftIdx, day: ft.day, time: ft.time })
  })
  // Sort each group's entries by day order
  const DAY_ORDER = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday']
  for (const group of map.values()) {
    group.entries.sort((a, b) => DAY_ORDER.indexOf(a.day) - DAY_ORDER.indexOf(b.day))
  }
  return Array.from(map.values())
}

// ---------------------------------------------------------------------------
// Selection toggle helpers
// ---------------------------------------------------------------------------

export function toggleMorningSelection(prev, memberId, classIdx, sessionIdx) {
  const old = prev[memberId] || { morning: [], freeTime: [] }
  const alreadyIdx = old.morning.findIndex(s => s.classIdx === classIdx && s.sessionIdx === sessionIdx)
  const morning = alreadyIdx >= 0
    ? old.morning.filter((_, i) => i !== alreadyIdx)
    : [...old.morning, { classIdx, sessionIdx }]
  return { ...prev, [memberId]: { ...old, morning } }
}

export function toggleFreeTimeSelection(prev, memberId, ftIdx) {
  const old = prev[memberId] || { morning: [], freeTime: [] }
  const alreadyIdx = old.freeTime.findIndex(s => s.ftIdx === ftIdx)
  const freeTime = alreadyIdx >= 0
    ? old.freeTime.filter((_, i) => i !== alreadyIdx)
    : [...old.freeTime, { ftIdx }]
  return { ...prev, [memberId]: { ...old, freeTime } }
}

// ---------------------------------------------------------------------------
// Selection summary — for print cards
// ---------------------------------------------------------------------------

export function buildScoutSummary(scout, selections, campSchedule) {
  const { dailyClasses, freeTimeClasses } = campSchedule
  const scoutSel = selections[scout.memberId] || { morning: [], freeTime: [] }

  const morningItems = scoutSel.morning.map(({ classIdx, sessionIdx }) => {
    const dc = dailyClasses[classIdx]
    const session = dc.sessions[sessionIdx]
    const label = dc.meritBadges.length > 0
      ? dc.meritBadges.join(' / ')
      : dc.ranks.join(' / ')
    return { label, time: `${session.start}–${session.end}`, sortKey: session.start }
  }).sort((a, b) => a.sortKey.localeCompare(b.sortKey))

  const freeItems = scoutSel.freeTime.map(({ ftIdx }) => {
    const ft = freeTimeClasses[ftIdx]
    return { label: ft.meritBadges.join(' + '), day: ft.day, time: ft.time }
  })

  return { morningItems, freeItems }
}

// ---------------------------------------------------------------------------
// Unique time slots across all daily classes (for troop grid columns)
// ---------------------------------------------------------------------------

export function getAllTimeSlots(campSchedule) {
  const seen = new Set()
  const slots = []
  campSchedule.dailyClasses.forEach(dc => {
    dc.sessions.forEach(s => {
      const key = `${s.start}–${s.end}`
      if (!seen.has(key)) { seen.add(key); slots.push(s) }
    })
  })
  slots.sort((a, b) => parseMinutes(a.start) - parseMinutes(b.start))
  return slots
}

export function getFreeDays(campSchedule) {
  const seen = new Set()
  const days = []
  campSchedule.freeTimeClasses.forEach(ft => {
    if (!seen.has(ft.day)) { seen.add(ft.day); days.push(ft.day) }
  })
  return days
}
