import React, { useRef } from 'react'

const RANK_ORDER = [
  'Scout Rank', 'Tenderfoot Rank', 'Second Class Rank',
  'First Class Rank', 'Star Scout Rank', 'Life Scout Rank', 'Eagle Scout Rank'
]

function currentRank(scout) {
  let highest = null
  for (const rankName of RANK_ORDER) {
    if (scout.completedRanks.includes(rankName)) highest = rankName
  }
  return highest ?? 'New Scout'
}

function shortRank(fullName) {
  return fullName
    .replace(' Rank', '')
    .replace('Eagle Scout', 'Eagle')
}

/** Returns the CSS modifier and icon for a scout's status. */
function statusStyle(status) {
  switch (status) {
    case 'finalized':    return { cls: 'scout-card--finalized',    icon: '✓', iconCls: 'status-badge--finalized' }
    case 'in_progress':  return { cls: 'scout-card--in-progress',  icon: '●', iconCls: 'status-badge--in-progress' }
    case 'not_attending': return { cls: 'scout-card--not-attending', icon: '✕', iconCls: 'status-badge--not-attending' }
    default:             return { cls: '', icon: null, iconCls: '' }
  }
}

export default function ScoutSelector({
  scouts, campConfig, scoutStatuses, selections,
  locks, deviceId, connected,
  onSelectScout, onPrint, onDownloadJSON, onDownloadCSV, onUpload, onOpenSync
}) {
  const fileInputRef = useRef(null)

  const anySelections = Object.values(selections).some(
    s => s.morning.length > 0 || s.freeTime.length > 0
  )

  function handleBackToReports(e) {
    if (!connected && anySelections) {
      const ok = window.confirm(
        'You have unsaved selections.\n\n' +
        'In offline mode, selections are stored in this browser tab only. ' +
        'Download a JSON backup before leaving or your data will be lost.\n\n' +
        'Leave anyway?'
      )
      if (!ok) e.preventDefault()
    }
  }

  function resolvePatrol(patrolStr) {
    if (!patrolStr) return 'Unassigned'
    const parts = patrolStr.split(',').map(p => p.trim()).filter(Boolean)
    if (parts.length === 0) return 'Unassigned'
    const other  = parts.find(p => p.toLowerCase() !== 'ranger' && p.toLowerCase() !== 'senior')
    if (other)   return other
    if (parts.some(p => p.toLowerCase() === 'senior')) return 'Senior'
    return 'Ranger'
  }

  const patrols = {}
  scouts.forEach((scout, idx) => {
    const patrol = resolvePatrol(scout.patrol)
    if (!patrols[patrol]) patrols[patrol] = []
    patrols[patrol].push({ scout, idx })
  })
  const sortedPatrols = Object.entries(patrols).sort(([a], [b]) => a.localeCompare(b))

  return (
    <div className="screen select-screen">
      <header className="select-header">
        <div className="select-header-inner">
          <div className="camp-badge">⛺</div>
          <div>
            <h1 className="select-title">{campConfig.campName}</h1>
            <p className="select-subtitle">Camp Schedule Picker</p>
          </div>
        </div>
        <div className="header-actions">
          <a
            className="btn-back-to-reports"
            href="index.html"
            onClick={handleBackToReports}
            title="Return to ScoutSight advancement reports"
          >
            ← Reports
          </a>
          <button
            className={`btn btn-io ${connected ? 'btn-io--live' : ''}`}
            onClick={onOpenSync}
            title={connected ? 'Live sync active — click to manage' : 'Connect to Google Sheet for multi-device sync'}
          >
            {connected ? '● Live' : '⟲ Sync'}
          </button>
          <button className="btn btn-io" onClick={onDownloadCSV}>↓ CSV</button>
          <button className="btn btn-io" onClick={onDownloadJSON}>↓ JSON</button>
          <button
            className="btn btn-io btn-io--upload"
            onClick={() => fileInputRef.current.click()}
            disabled={connected}
            title={connected ? 'Disconnect from Live Sync before uploading' : 'Upload a saved JSON or CSV file'}
          >
            ↑ Upload
          </button>
          <input
            ref={fileInputRef}
            type="file"
            accept=".json,.csv"
            style={{ display: 'none' }}
            onChange={e => { if (e.target.files[0]) { onUpload(e.target.files[0]); e.target.value = '' } }}
          />
          <button
            className="btn btn-print"
            onClick={onPrint}
            disabled={!anySelections}
            title={anySelections ? 'View printable summary' : 'No scouts have made selections yet'}
          >
            Print Summary
          </button>
          <a
            className="btn-privacy-link"
            href="camp_scheduler/privacy.html"
            target="_blank"
            rel="noopener noreferrer"
            title="Privacy information — what data is shared"
          >
            Privacy
          </a>
        </div>
      </header>

      <div className="select-body">
        <p className="select-instructions">
          Each Scout selects their schedule one at a time. Click a Scout's name to begin.
        </p>

        {sortedPatrols.map(([patrol, members]) => (
          <section key={patrol} className="patrol-section">
            <h2 className="patrol-name">{patrol}</h2>
            <div className="scout-grid">
              {members.map(({ scout, idx }) => {
                const status = scoutStatuses[scout.memberId]
                const locked = locks?.[scout.memberId] && locks[scout.memberId] !== deviceId
                const sel    = selections[scout.memberId]
                const morningCount    = sel?.morning?.length ?? 0
                const ftCount         = sel?.freeTime?.length ?? 0
                const totalSelections = morningCount + ftCount
                const { cls, icon, iconCls } = statusStyle(status)

                return (
                  <button
                    key={scout.memberId}
                    className={`scout-card ${cls} ${locked ? 'scout-card--locked' : ''}`}
                    onClick={() => !locked && onSelectScout(idx)}
                    disabled={locked}
                  >
                    {icon   && <span className={`status-badge ${iconCls}`}>{icon}</span>}
                    {locked && <span className="locked-badge">In use</span>}
                    <span className="scout-card-name">{scout.name}</span>
                    <span className="scout-card-rank">{shortRank(currentRank(scout))}</span>
                    {status === 'not_attending' && (
                      <span className="scout-card-count scout-card-count--not-attending">Not Attending</span>
                    )}
                    {status !== 'not_attending' && totalSelections > 0 && (
                      <span className={`scout-card-count${status ? ` scout-card-count--${status.replace('_', '-')}` : ''}`}>
                        {morningCount > 0 && `${morningCount} morning`}
                        {morningCount > 0 && ftCount > 0 && ' · '}
                        {ftCount > 0 && `${ftCount} free-time`}
                      </span>
                    )}
                  </button>
                )
              })}
            </div>
          </section>
        ))}
      </div>
    </div>
  )
}
