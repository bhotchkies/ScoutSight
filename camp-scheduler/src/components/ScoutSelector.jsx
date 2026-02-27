import React, { useRef } from 'react'

const RANK_ORDER = [
  'Scout Rank', 'Tenderfoot Rank', 'Second Class Rank',
  'First Class Rank', 'Star Scout Rank', 'Life Scout Rank', 'Eagle Scout Rank'
]

function currentRank(scout) {
  // Find the highest completed rank
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

export default function ScoutSelector({ scouts, campConfig, doneScouts, selections, onSelectScout, onPrint, onDownloadJSON, onDownloadCSV, onUpload }) {
  const fileInputRef = useRef(null)

  const anySelections = Object.values(selections).some(
    s => s.morning.length > 0 || s.freeTime.length > 0
  )

  // Group scouts by patrol
  const patrols = {}
  scouts.forEach((scout, idx) => {
    const patrol = scout.patrol || 'Unassigned'
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
          <button className="btn btn-io" onClick={onDownloadCSV}>↓ CSV</button>
          <button className="btn btn-io" onClick={onDownloadJSON}>↓ JSON</button>
          <button className="btn btn-io btn-io--upload" onClick={() => fileInputRef.current.click()}>
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
                const done = doneScouts.has(scout.memberId)
                const sel = selections[scout.memberId]
                const morningCount = sel?.morning.length ?? 0
                const ftCount = sel?.freeTime.length ?? 0
                const totalSelections = morningCount + ftCount
                return (
                  <button
                    key={scout.memberId}
                    className={`scout-card ${done ? 'scout-card--done' : ''}`}
                    onClick={() => onSelectScout(idx)}
                  >
                    {done && <span className="done-check">✓</span>}
                    <span className="scout-card-name">{scout.name}</span>
                    <span className="scout-card-rank">{shortRank(currentRank(scout))}</span>
                    {totalSelections > 0 && (
                      <span className="scout-card-count">
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
