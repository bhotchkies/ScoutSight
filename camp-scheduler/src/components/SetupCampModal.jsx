import React, { useState, useMemo } from 'react'
import { runCampSetup } from '../utils/campSetupUtils'

const EAGLE_SET = new Set(window.SCOUT_SIGHT_DATA?.eagleRequiredBadges ?? [])

function mbClassLabel(dc) {
  return dc.meritBadges.map(mb => {
    const name = mb.endsWith(' MB') ? mb.slice(0, -3) : mb
    return EAGLE_SET.has(mb) ? `★ ${name}` : name
  }).join(' + ')
}

/**
 * Modal for bulk camp setup: sets attending list, rank threshold, and MB priority choices,
 * then auto-assigns morning sessions for all non-finalized scouts.
 */
export default function SetupCampModal({ scouts, campSchedule, selections, scoutStatuses, onApply, onClose }) {
  const [attendingText, setAttendingText] = useState('')
  const [threshold, setThreshold]         = useState(50)
  const [choices, setChoices]             = useState([-1, -1, -1])
  const [result, setResult]               = useState(null)
  const [loading, setLoading]             = useState(false)

  const mbClasses = useMemo(() => (
    campSchedule.dailyClasses
      .map((dc, i) => ({ classIdx: i, dc, label: mbClassLabel(dc) }))
      .filter(({ dc }) => dc.meritBadges.length > 0)
      .sort((a, b) => {
        const aE = a.dc.meritBadges.some(mb => EAGLE_SET.has(mb))
        const bE = b.dc.meritBadges.some(mb => EAGLE_SET.has(mb))
        return (aE === bE) ? 0 : aE ? -1 : 1
      })
  ), [campSchedule])

  function setChoice(i, val) {
    setChoices(prev => { const n = [...prev]; n[i] = val; return n })
  }

  async function handleRun() {
    const attendingIds = new Set(
      attendingText.split(/[\s,\n]+/).map(s => s.trim()).filter(Boolean)
    )
    if (attendingIds.size === 0) {
      alert('Enter at least one Scout ID in the attending list.')
      return
    }
    const { newSelections, newStatuses, summary } = runCampSetup(
      scouts, campSchedule, selections, scoutStatuses,
      attendingIds, threshold, choices
    )
    setLoading(true)
    try {
      await onApply(newSelections, newStatuses)
    } finally {
      setLoading(false)
    }
    setResult(summary)
  }

  // ── Loading view ─────────────────────────────────────────────
  if (loading) {
    return (
      <div className="modal-overlay">
        <div className="modal-card modal-card--loading">
          <div className="modal-spinner" />
          <p className="modal-loading-msg">Setting up camp…</p>
        </div>
      </div>
    )
  }

  // ── Summary view ─────────────────────────────────────────────
  if (result) {
    const rankEntries = Object.entries(result.rankAssigned)
    const mbEntries   = Object.entries(result.mbAssigned)
    return (
      <div className="modal-overlay" onClick={onClose}>
        <div className="modal-card" onClick={e => e.stopPropagation()}>
          <h2 className="modal-title">Setup Complete</h2>
          <div className="setup-summary">
            <p><strong>{result.attendingCount}</strong> scout{result.attendingCount !== 1 ? 's' : ''} marked as attending</p>
            <p><strong>{result.notAttendingCount}</strong> scout{result.notAttendingCount !== 1 ? 's' : ''} marked as not attending</p>

            {rankEntries.length > 0 && (
              <div className="summary-section">
                <p className="summary-section-label">Rank class assignments:</p>
                {rankEntries.map(([label, count]) => (
                  <p key={label} className="summary-item">• {label}: <strong>{count}</strong></p>
                ))}
              </div>
            )}

            {mbEntries.length > 0 && (
              <div className="summary-section">
                <p className="summary-section-label">Merit badge assignments:</p>
                {mbEntries.map(([name, count]) => (
                  <p key={name} className="summary-item">• {name}: <strong>{count}</strong></p>
                ))}
              </div>
            )}

            {result.noMbCount > 0 && (
              <p className="summary-warning">
                ⚠ {result.noMbCount} attending scout{result.noMbCount !== 1 ? 's' : ''} could not be
                assigned any merit badge choice (time conflict or already in progress).
              </p>
            )}

            {result.unmatchedIds?.length > 0 && (
              <div className="summary-warning">
                <p>⚠ {result.unmatchedIds.length} ID{result.unmatchedIds.length !== 1 ? 's' : ''} in the attending list did not match any scout in the roster:</p>
                <ul className="summary-names">
                  {result.unmatchedIds.map(id => <li key={id}>{id}</li>)}
                </ul>
              </div>
            )}

            {result.finalizedNotOnListNames.length > 0 && (
              <div className="summary-warning">
                <p>⚠ {result.finalizedNotOnListNames.length} finalized scout{result.finalizedNotOnListNames.length !== 1 ? 's' : ''} not on the attending list — update manually if needed:</p>
                <ul className="summary-names">
                  {result.finalizedNotOnListNames.map(name => <li key={name}>{name}</li>)}
                </ul>
              </div>
            )}

            {result.decisions?.length > 0 && (
              <div className="summary-section summary-debug">
                <p className="summary-section-label">Per-scout details</p>
                <div className="debug-scroll">
                  {result.decisions.map(d => (
                    <div key={d.memberId} className="debug-entry">
                      <p className="debug-name">{d.name} <span className="debug-id">#{d.memberId}</span> — <span className="debug-status">{d.statusLabel}</span></p>
                      {d.rankNotes.map((n, i) => <p key={i} className="debug-note debug-note--rank">{n}</p>)}
                      {d.mbNotes.map((n, i)   => <p key={i} className="debug-note debug-note--mb">{n}</p>)}
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
          <div className="modal-footer">
            <button className="btn btn-primary" onClick={onClose}>Done</button>
          </div>
        </div>
      </div>
    )
  }

  // ── Form view ─────────────────────────────────────────────────
  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-card" onClick={e => e.stopPropagation()}>
        <h2 className="modal-title">⛺ Setup Camp</h2>

        <div className="modal-field">
          <label className="modal-label">
            Attending Scout IDs
            <span className="modal-label-hint">(comma, space, or newline separated)</span>
          </label>
          <textarea
            className="modal-textarea"
            value={attendingText}
            onChange={e => setAttendingText(e.target.value)}
            placeholder="1234567, 2345678&#10;3456789"
            rows={5}
          />
        </div>

        <div className="modal-field">
          <label className="modal-label">
            Auto-assign rank class when % left at camp is over
          </label>
          <select className="modal-select" value={threshold} onChange={e => setThreshold(Number(e.target.value))}>
            <option value={0}>Any — everyone with camp rank work</option>
            <option value={50}>50% or more remaining</option>
            <option value={75}>75% or more remaining</option>
          </select>
        </div>

        <div className="modal-field">
          <label className="modal-label">
            Morning merit badge priority
            <span className="modal-label-hint">(★ = Eagle-required; falls back to next choice if no slot available)</span>
          </label>
          {['1st choice', '2nd choice', '3rd choice'].map((label, i) => (
            <div key={i} className="modal-choice-row">
              <span className="modal-choice-label">{label}</span>
              <select
                className="modal-select modal-select--choice"
                value={choices[i]}
                onChange={e => setChoice(i, Number(e.target.value))}
              >
                <option value={-1}>(none)</option>
                {mbClasses.map(({ classIdx, label }) => (
                  <option key={classIdx} value={classIdx}>{label}</option>
                ))}
              </select>
            </div>
          ))}
        </div>

        <div className="modal-footer">
          <button className="btn btn-secondary" onClick={onClose}>Cancel</button>
          <button className="btn btn-primary" onClick={handleRun}>Run Setup</button>
        </div>
      </div>
    </div>
  )
}
