import React from 'react'
import MorningClassGrid from './MorningClassGrid'
import FreeTimeClasses from './FreeTimeClasses'
import { groupFreeTimeByBadge } from '../utils/scheduleUtils'

export default function SchedulePicker({
  scout, scouts, campSchedule, campConfig, selections, scoutStatus,
  onMorningToggle, onFreeTimeToggle, onSave, onFinalize, onNotAttending, onBack
}) {
  const { dailyClasses, freeTimeClasses } = campSchedule
  const scoutSel = selections[scout.memberId] || { morning: [], freeTime: [] }

  const allMorning  = dailyClasses.map((dc, idx) => ({ dc, idx }))
  const allFtGroups = groupFreeTimeByBadge(freeTimeClasses)

  const totalSelections = scoutSel.morning.length + scoutSel.freeTime.length

  return (
    <div className="screen pick-screen">
      <header className="pick-header">
        <button className="btn-back" onClick={onBack} aria-label="Back to scout list">
          ← Scouts
        </button>
        <div className="pick-header-info">
          <span className="pick-scout-name">{scout.name}</span>
          {scout.patrol && <span className="pick-scout-patrol">{scout.patrol}</span>}
        </div>
        <div className="pick-header-right">
          {totalSelections > 0 && (
            <span className="selection-count">{totalSelections} selected</span>
          )}
          <div className="pick-actions">
            <button
              className={`btn-not-attending${scoutStatus === 'not_attending' ? ' btn-not-attending--active' : ''}`}
              onClick={onNotAttending}
              title="Clear selections and mark scout as not attending camp"
            >
              Not Attending
            </button>
            <button
              className={`btn-save-sched${scoutStatus === 'in_progress' ? ' btn-save-sched--active' : ''}`}
              onClick={onSave}
              title="Save and return — schedule is still in progress"
            >
              Save
            </button>
            <button
              className={`btn-finalize${scoutStatus === 'finalized' ? ' btn-finalize--active' : ''}`}
              onClick={onFinalize}
              title="Finalize schedule — mark as complete"
            >
              Finalize ✓
            </button>
          </div>
        </div>
      </header>

      <div className="pick-body">
        {allMorning.length === 0 && allFtGroups.length === 0 ? (
          <div className="empty-state">
            <div className="empty-icon">🏕️</div>
            <p>No classes scheduled for this camp.</p>
          </div>
        ) : (
          <>
            {allMorning.length > 0 && (
              <section className="class-section">
                <h2 className="section-heading">
                  Morning Classes
                  <span className="section-note">Select one time slot per class</span>
                </h2>
                <MorningClassGrid
                  entries={allMorning}
                  scout={scout}
                  scouts={scouts}
                  campSchedule={campSchedule}
                  selections={selections}
                  onToggle={onMorningToggle}
                />
              </section>
            )}

            {allFtGroups.length > 0 && (
              <section className="class-section">
                <h2 className="section-heading">
                  Evening Free-Time (7:00 PM)
                  <span className="section-note">Each offering runs one day; select any day</span>
                </h2>
                <FreeTimeClasses
                  groups={allFtGroups}
                  scout={scout}
                  scouts={scouts}
                  campSchedule={campSchedule}
                  selections={selections}
                  onToggle={onFreeTimeToggle}
                />
              </section>
            )}
          </>
        )}
      </div>
    </div>
  )
}
