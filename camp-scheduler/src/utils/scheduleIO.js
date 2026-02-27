/**
 * Schedule import/export utilities.
 *
 * Download formats:
 *   JSON — structured, includes both indices and human-readable names for robust re-import.
 *   CSV  — flat, human-readable, Google Sheets compatible; columns: MemberId, ScoutName,
 *          Type (morning|freetime), Names (badge/rank joined by " + "), TimeOrDay.
 *
 * Re-import strategy for morning classes:
 *   1. Validate stored classIdx/sessionIdx — if they still point to the matching names+time, use them.
 *   2. Otherwise fall back to name+time lookup across all dailyClasses.
 *   3. Unresolved entries are collected as warnings.
 *
 * Re-import strategy for free-time classes:
 *   1. Validate stored ftIdx — if names and day still match, use it.
 *   2. Otherwise fall back to name+day lookup.
 */

// ── Helpers ─────────────────────────────────────────────────

function slugify(name) {
  return name.replace(/\s+/g, '-').toLowerCase()
}

function downloadBlob(content, filename, mimeType) {
  const blob = new Blob([content], { type: mimeType + ';charset=utf-8;' })
  const url  = URL.createObjectURL(blob)
  const a    = document.createElement('a')
  a.href     = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}

/**
 * Minimal CSV field escaper — wraps in quotes if the value contains
 * commas, double-quotes, or newlines.
 */
function csvEscape(val) {
  const s = String(val ?? '')
  return (s.includes(',') || s.includes('"') || s.includes('\n'))
    ? '"' + s.replace(/"/g, '""') + '"'
    : s
}

/**
 * Parses a single CSV line, handling quoted fields and escaped quotes.
 * Returns an array of field strings.
 */
function parseCsvLine(line) {
  const fields = []
  let i = 0
  while (i < line.length) {
    if (line[i] === '"') {
      i++ // skip opening quote
      let field = ''
      while (i < line.length) {
        if (line[i] === '"' && line[i + 1] === '"') { field += '"'; i += 2 }
        else if (line[i] === '"') { i++; break }
        else { field += line[i++] }
      }
      fields.push(field)
      if (i < line.length && line[i] === ',') i++
    } else {
      const end = line.indexOf(',', i)
      if (end === -1) { fields.push(line.slice(i).trim()); break }
      fields.push(line.slice(i, end).trim())
      i = end + 1
    }
  }
  return fields
}

// ── Resolution helpers ───────────────────────────────────────

/**
 * Resolves a morning entry to { classIdx, sessionIdx }.
 * Tries index validation first, then falls back to name+time matching.
 */
function resolveMorning(entry, campSchedule, warnings, scoutLabel) {
  const { dailyClasses } = campSchedule
  const entryNames = entry.names ?? []
  const entryTime  = entry.time  ?? ''

  // 1. Try stored indices — validate names and time still match
  if (entry.classIdx != null && entry.sessionIdx != null) {
    const dc   = dailyClasses[entry.classIdx]
    const sess = dc?.sessions[entry.sessionIdx]
    if (dc && sess) {
      const time    = `${sess.start}-${sess.end}`
      const dcNames = [...dc.meritBadges, ...dc.ranks]
      const namesOk = entryNames.length === 0 || entryNames.every(n => dcNames.includes(n))
      if (namesOk && (entryTime === '' || time === entryTime)) {
        return { classIdx: entry.classIdx, sessionIdx: entry.sessionIdx }
      }
    }
  }

  // 2. Fall back: scan by name + time
  for (let ci = 0; ci < dailyClasses.length; ci++) {
    const dc      = dailyClasses[ci]
    const dcNames = [...dc.meritBadges, ...dc.ranks]
    if (!entryNames.every(n => dcNames.includes(n))) continue
    for (let si = 0; si < dc.sessions.length; si++) {
      const sess = dc.sessions[si]
      if (`${sess.start}-${sess.end}` === entryTime) return { classIdx: ci, sessionIdx: si }
    }
  }

  warnings.push(`${scoutLabel}: could not match morning class "${entryNames.join(' + ')}" at ${entryTime}`)
  return null
}

/**
 * Resolves a free-time entry to { ftIdx }.
 * Tries index validation first, then falls back to name+day matching.
 */
function resolveFreeTime(entry, campSchedule, warnings, scoutLabel) {
  const { freeTimeClasses } = campSchedule
  const entryNames = entry.names ?? []
  const entryDay   = entry.day  ?? ''

  // 1. Try stored index — validate names and day still match
  if (entry.ftIdx != null) {
    const ft = freeTimeClasses[entry.ftIdx]
    if (ft) {
      const namesOk = entryNames.length === 0 || entryNames.every(n => ft.meritBadges.includes(n))
      const dayOk   = entryDay === '' || ft.day === entryDay
      if (namesOk && dayOk) return { ftIdx: entry.ftIdx }
    }
  }

  // 2. Fall back: scan by name + day
  for (let fi = 0; fi < freeTimeClasses.length; fi++) {
    const ft = freeTimeClasses[fi]
    if (ft.day !== entryDay) continue
    if (entryNames.every(n => ft.meritBadges.includes(n))) return { ftIdx: fi }
  }

  warnings.push(`${scoutLabel}: could not match free-time class "${entryNames.join(' + ')}" on ${entryDay}`)
  return null
}

function showWarnings(warnings) {
  if (warnings.length === 0) return
  const preview = warnings.slice(0, 5).join('\n')
  const extra   = warnings.length > 5 ? `\n…and ${warnings.length - 5} more` : ''
  alert(`Loaded with ${warnings.length} unresolved entry(s):\n\n${preview}${extra}`)
}

// ── Downloads ────────────────────────────────────────────────

export function downloadJSON(selections, scouts, campSchedule, campConfig) {
  const { dailyClasses, freeTimeClasses } = campSchedule

  const data = {
    version:    1,
    camp:       campConfig.campName,
    exportedAt: new Date().toISOString(),
    selections: {}
  }

  for (const scout of scouts) {
    const sel = selections[scout.memberId]
    if (!sel || (sel.morning.length === 0 && sel.freeTime.length === 0)) continue

    data.selections[scout.memberId] = {
      name: scout.name,
      morning: sel.morning.map(({ classIdx, sessionIdx }) => {
        const dc   = dailyClasses[classIdx]
        const sess = dc.sessions[sessionIdx]
        return {
          classIdx,
          sessionIdx,
          names: [...dc.meritBadges, ...dc.ranks],
          time:  `${sess.start}-${sess.end}`
        }
      }),
      freeTime: sel.freeTime.map(({ ftIdx }) => {
        const ft = freeTimeClasses[ftIdx]
        return { ftIdx, names: ft.meritBadges, day: ft.day }
      })
    }
  }

  downloadBlob(
    JSON.stringify(data, null, 2),
    `${slugify(campConfig.campName)}-schedule.json`,
    'application/json'
  )
}

export function downloadCSV(selections, scouts, campSchedule, campConfig) {
  const { dailyClasses, freeTimeClasses } = campSchedule
  const rows = ['MemberId,ScoutName,Type,Names,TimeOrDay']

  for (const scout of scouts) {
    const sel = selections[scout.memberId]
    if (!sel) continue

    for (const { classIdx, sessionIdx } of sel.morning) {
      const dc    = dailyClasses[classIdx]
      const sess  = dc.sessions[sessionIdx]
      const names = [...dc.meritBadges, ...dc.ranks].join(' + ')
      const time  = `${sess.start}-${sess.end}`
      rows.push([scout.memberId, scout.name, 'morning', names, time].map(csvEscape).join(','))
    }

    for (const { ftIdx } of sel.freeTime) {
      const ft    = freeTimeClasses[ftIdx]
      const names = ft.meritBadges.join(' + ')
      rows.push([scout.memberId, scout.name, 'freetime', names, ft.day].map(csvEscape).join(','))
    }
  }

  downloadBlob(
    rows.join('\n'),
    `${slugify(campConfig.campName)}-schedule.csv`,
    'text/csv'
  )
}

// ── Parsing (for upload) ─────────────────────────────────────

export function parseUploadedJSON(text, campSchedule) {
  const data = JSON.parse(text)
  if (!data.selections || typeof data.selections !== 'object') {
    throw new Error('Invalid format: missing "selections" object.')
  }

  const result   = {}
  const warnings = []

  for (const [memberId, scoutData] of Object.entries(data.selections)) {
    const morning  = []
    const freeTime = []
    const label    = scoutData.name ?? memberId

    for (const entry of (scoutData.morning ?? [])) {
      const r = resolveMorning(entry, campSchedule, warnings, label)
      if (r) morning.push(r)
    }
    for (const entry of (scoutData.freeTime ?? [])) {
      const r = resolveFreeTime(entry, campSchedule, warnings, label)
      if (r) freeTime.push(r)
    }
    if (morning.length > 0 || freeTime.length > 0) {
      result[memberId] = { morning, freeTime }
    }
  }

  showWarnings(warnings)
  return result
}

export function parseUploadedCSV(text, campSchedule, scouts) {
  const scoutMap = Object.fromEntries(scouts.map(s => [s.memberId, s.name]))
  const lines    = text.trim().split(/\r?\n/)
  const result   = {}
  const warnings = []

  // Skip header row (line 0)
  for (let i = 1; i < lines.length; i++) {
    const line = lines[i].trim()
    if (!line) continue

    const cols = parseCsvLine(line)
    if (cols.length < 5) continue

    const [memberId, , type, namesRaw, timeOrDay] = cols
    if (!memberId) continue

    const names = namesRaw.split(' + ').map(n => n.trim()).filter(Boolean)
    const label = scoutMap[memberId] ?? memberId

    if (!result[memberId]) result[memberId] = { morning: [], freeTime: [] }

    if (type === 'morning') {
      const r = resolveMorning({ names, time: timeOrDay }, campSchedule, warnings, label)
      if (r) result[memberId].morning.push(r)
    } else if (type === 'freetime') {
      const r = resolveFreeTime({ names, day: timeOrDay }, campSchedule, warnings, label)
      if (r) result[memberId].freeTime.push(r)
    }
  }

  // Remove scouts with nothing resolved
  for (const id of Object.keys(result)) {
    const sel = result[id]
    if (sel.morning.length === 0 && sel.freeTime.length === 0) delete result[id]
  }

  showWarnings(warnings)
  return result
}

/**
 * Top-level upload handler. Auto-detects JSON vs CSV by file extension,
 * falling back to content sniffing. Calls onLoad(newSelections) on success.
 */
export function handleUploadFile(file, campSchedule, scouts, onLoad) {
  const reader = new FileReader()
  reader.onload = e => {
    const text = e.target.result
    try {
      const isJson = file.name.toLowerCase().endsWith('.json')
        || (!file.name.toLowerCase().endsWith('.csv') && text.trim().startsWith('{'))

      const newSelections = isJson
        ? parseUploadedJSON(text, campSchedule)
        : parseUploadedCSV(text, campSchedule, scouts)

      const count = Object.keys(newSelections).length
      if (count === 0) {
        alert('No recognizable schedule data found in the uploaded file.')
        return
      }

      const confirmed = window.confirm(
        `Replace all current schedule data with selections for ${count} scout(s) from "${file.name}"?\n\nThis cannot be undone.`
      )
      if (confirmed) onLoad(newSelections)
    } catch (err) {
      alert(`Failed to load file: ${err.message}`)
    }
  }
  reader.readAsText(file)
}
