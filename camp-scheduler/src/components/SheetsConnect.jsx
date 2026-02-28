import React, { useState } from 'react'
import { sheetsConnect } from '../utils/sheetsIO'

// ── Apps Script template ─────────────────────────────────────────────────────
// Troops paste this into Extensions → Apps Script in their Google Sheet,
// deploy as a Web App (Execute as: Me, Access: Anyone), and share the URL.

const APPS_SCRIPT = `// Camp Scheduler — Google Apps Script Backend
// Version 1.0
//
// SETUP:
// 1. In your Google Sheet go to Extensions → Apps Script
// 2. Delete all existing code and paste this entire script
// 3. Save (Ctrl+S / Cmd+S)
// 4. Click Deploy → New deployment
//    - Type: Web App
//    - Execute as: Me
//    - Who has access: Anyone
// 5. Click Deploy and copy the Web App URL
// 6. Share the URL with co-leaders

const SELECTIONS_SHEET = 'selections';
const LOCKS_SHEET      = 'locks';
const LOCK_EXPIRY_MS   = 60000; // 60 s — protects against closed/crashed tabs

function doGet(e) {
  try {
    const p = e.parameter;
    switch (p.action) {
      case 'getAll':      return respond(handleGetAll());
      case 'acquireLock': return respond(handleAcquireLock(p.scoutId, p.deviceId));
      case 'releaseLock': return respond(handleReleaseLock(p.scoutId, p.deviceId, p.selectionsJson));
      case 'dropLock':    return respond(handleDropLock(p.scoutId, p.deviceId));
      case 'ping':        return respond(handlePing(p.scoutId, p.deviceId));
      default:            return respond({ error: 'Unknown action: ' + p.action });
    }
  } catch (err) {
    return respond({ error: err.message });
  }
}

function handleGetAll() {
  const ss = SpreadsheetApp.getActiveSpreadsheet();

  // Selections: one row per scout
  const selSheet   = getOrCreate(ss, SELECTIONS_SHEET, ['ScoutId', 'SelectionsJson']);
  const selRows    = selSheet.getDataRange().getValues();
  const selections = {};
  for (let i = 1; i < selRows.length; i++) {
    const [id, json] = selRows[i];
    if (id && json) { try { selections[id] = JSON.parse(json); } catch (_) {} }
  }

  // Locks: active only (not expired)
  const lockSheet = getOrCreate(ss, LOCKS_SHEET, ['ScoutId', 'DeviceId', 'LockedAt']);
  const lockRows  = lockSheet.getDataRange().getValues();
  const now       = Date.now();
  const locks     = {};
  for (let i = 1; i < lockRows.length; i++) {
    const [sid, did, at] = lockRows[i];
    if (sid && did && at && (now - new Date(at).getTime() < LOCK_EXPIRY_MS)) {
      locks[sid] = did;
    }
  }
  return { selections, locks };
}

function handleAcquireLock(scoutId, deviceId) {
  const gl = LockService.getScriptLock();
  gl.waitLock(5000);
  try {
    const lockSheet = getOrCreate(
      SpreadsheetApp.getActiveSpreadsheet(), LOCKS_SHEET, ['ScoutId', 'DeviceId', 'LockedAt']
    );
    const rows = lockSheet.getDataRange().getValues();
    const now  = Date.now();
    const ts   = new Date().toISOString();
    for (let i = 1; i < rows.length; i++) {
      if (rows[i][0] !== scoutId) continue;
      const age = now - new Date(rows[i][2]).getTime();
      if (age < LOCK_EXPIRY_MS && rows[i][1] !== deviceId) {
        return { ok: false, lockedBy: rows[i][1] };
      }
      lockSheet.getRange(i + 1, 1, 1, 3).setValues([[scoutId, deviceId, ts]]);
      return { ok: true };
    }
    lockSheet.appendRow([scoutId, deviceId, ts]);
    return { ok: true };
  } finally {
    gl.releaseLock();
  }
}

function handleReleaseLock(scoutId, deviceId, selectionsJson) {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  if (selectionsJson && selectionsJson !== 'null') {
    const selSheet = getOrCreate(ss, SELECTIONS_SHEET, ['ScoutId', 'SelectionsJson']);
    const rows     = selSheet.getDataRange().getValues();
    let found      = false;
    for (let i = 1; i < rows.length; i++) {
      if (rows[i][0] === scoutId) {
        selSheet.getRange(i + 1, 1, 1, 2).setValues([[scoutId, selectionsJson]]);
        found = true;
        break;
      }
    }
    if (!found) selSheet.appendRow([scoutId, selectionsJson]);
  }
  removeLockRow(ss, scoutId, deviceId);
  return { ok: true };
}

function handleDropLock(scoutId, deviceId) {
  removeLockRow(SpreadsheetApp.getActiveSpreadsheet(), scoutId, deviceId);
  return { ok: true };
}

function handlePing(scoutId, deviceId) {
  const lockSheet = getOrCreate(
    SpreadsheetApp.getActiveSpreadsheet(), LOCKS_SHEET, ['ScoutId', 'DeviceId', 'LockedAt']
  );
  const rows = lockSheet.getDataRange().getValues();
  for (let i = 1; i < rows.length; i++) {
    if (rows[i][0] === scoutId && rows[i][1] === deviceId) {
      lockSheet.getRange(i + 1, 3).setValue(new Date().toISOString());
      return { ok: true };
    }
  }
  return { ok: false };
}

function removeLockRow(ss, scoutId, deviceId) {
  const lockSheet = getOrCreate(ss, LOCKS_SHEET, ['ScoutId', 'DeviceId', 'LockedAt']);
  const rows      = lockSheet.getDataRange().getValues();
  for (let i = 1; i < rows.length; i++) {
    if (rows[i][0] === scoutId && rows[i][1] === deviceId) {
      lockSheet.deleteRow(i + 1);
      return;
    }
  }
}

function getOrCreate(ss, name, headers) {
  let sheet = ss.getSheetByName(name);
  if (!sheet) { sheet = ss.insertSheet(name); sheet.appendRow(headers); }
  return sheet;
}

function respond(data) {
  return ContentService
    .createTextOutput(JSON.stringify(data))
    .setMimeType(ContentService.MimeType.JSON);
}`

// ── Component ────────────────────────────────────────────────────────────────

export default function SheetsConnect({ sheetsUrl, onConnect, onDisconnect, onBack }) {
  const [url, setUrl]           = useState(sheetsUrl ?? '')
  const [status, setStatus]     = useState('idle') // 'idle' | 'connecting' | 'error'
  const [errorMsg, setErrorMsg] = useState('')
  const [showSetup, setShowSetup]     = useState(false)
  const [showPrivacy, setShowPrivacy] = useState(false)
  const [copied, setCopied]     = useState(false)

  const connected = !!sheetsUrl

  async function handleConnect() {
    if (!url.trim()) return
    setStatus('connecting')
    setErrorMsg('')
    try {
      const data = await sheetsConnect(url.trim())
      localStorage.setItem('sheetsUrl', url.trim())
      setStatus('idle')
      onConnect(url.trim(), data)
    } catch (e) {
      setStatus('error')
      setErrorMsg(e.message)
    }
  }

  function handleDisconnect() {
    localStorage.removeItem('sheetsUrl')
    setUrl('')
    setStatus('idle')
    setErrorMsg('')
    onDisconnect()
  }

  function handleOffline() {
    localStorage.setItem('sheetsUrl', 'offline')
    onBack()
  }

  async function handleCopy() {
    try {
      await navigator.clipboard.writeText(APPS_SCRIPT)
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    } catch (_) {
      // fallback: select the pre block text
    }
  }

  return (
    <div className="screen connect-screen">
      <div className="connect-header no-print">
        <button className="btn-back" onClick={onBack}>← Back</button>
        <h1 className="connect-title">Google Sheets Sync</h1>
      </div>

      <div className="connect-body">
        <div className="connect-card">
          <div className={`connect-status ${connected ? 'connect-status--live' : 'connect-status--off'}`}>
            <span className={`connect-dot ${connected ? '' : 'connect-dot--off'}`} />
            {connected
              ? <>Live sync active &mdash; <span className="connect-url-preview">{sheetsUrl}</span></>
              : 'Not connected — working offline'
            }
          </div>

          <div className="connect-form">
            <label className="connect-label" htmlFor="sheets-url">
              Apps Script Web App URL
            </label>
            <input
              id="sheets-url"
              className="connect-input"
              type="url"
              placeholder="https://script.google.com/macros/s/…/exec"
              value={url}
              onChange={e => { setUrl(e.target.value); setStatus('idle'); setErrorMsg('') }}
              onKeyDown={e => e.key === 'Enter' && handleConnect()}
              disabled={status === 'connecting'}
            />
            {status === 'error' && <p className="connect-error">{errorMsg}</p>}
          </div>

          <div className="connect-btn-row">
            <button
              className="btn btn-primary"
              onClick={handleConnect}
              disabled={status === 'connecting' || !url.trim()}
            >
              {status === 'connecting' ? 'Connecting…' : connected ? 'Reconnect' : 'Connect'}
            </button>
            {connected && (
              <button className="btn btn-danger" onClick={handleDisconnect}>
                Disconnect
              </button>
            )}
            {!connected && (
              <button className="btn btn-io" onClick={handleOffline}>
                Use Offline
              </button>
            )}
          </div>
        </div>

        <div className="connect-setup">
          <button
            className="connect-setup-toggle"
            onClick={() => setShowSetup(s => !s)}
          >
            {showSetup ? '▾' : '▸'} Setup Instructions — how to create your troop's sheet
          </button>

          {showSetup && (
            <div className="connect-setup-body">
              <p className="connect-setup-intro">
                Each troop needs one Google Sheet with an Apps Script web app. A troop admin does
                this once, then shares the URL with co-leaders.
              </p>
              <ol className="connect-steps">
                <li>Create a new <strong>Google Sheet</strong> for your troop (name it anything).</li>
                <li>Click <strong>Extensions → Apps Script</strong>.</li>
                <li>Delete all existing code, paste the script below, and save <kbd>Ctrl+S</kbd>.</li>
                <li>
                  Click <strong>Deploy → New deployment</strong>.<br />
                  Set <em>Type</em> to <strong>Web App</strong>, <em>Execute as</em> to{' '}
                  <strong>Me</strong>, <em>Who has access</em> to <strong>Anyone</strong>.<br />
                  Click <strong>Deploy</strong>.
                </li>
                <li>Copy the <strong>Web App URL</strong> and share it with co-leaders.</li>
              </ol>

              <div className="connect-code-header">
                <span>Apps Script — paste in step 3</span>
                <button className="btn btn-copy" onClick={handleCopy}>
                  {copied ? '✓ Copied' : 'Copy'}
                </button>
              </div>
              <pre className="connect-code">{APPS_SCRIPT}</pre>
            </div>
          )}
        </div>
        <div className="connect-setup">
          <button
            className="connect-setup-toggle"
            onClick={() => setShowPrivacy(s => !s)}
          >
            {showPrivacy ? '▾' : '▸'} Privacy — what data is shared
          </button>

          {showPrivacy && (
            <div className="connect-setup-body">
              <dl className="privacy-dl">
                <dt>What stays on your device</dt>
                <dd>
                  Scout names, patrol assignments, ages, rank progress, and merit badge
                  completion records are loaded from your BSA advancement export and kept
                  in your browser only. They are never transmitted anywhere.
                </dd>

                <dt>What is sent to Google Sheets</dt>
                <dd>
                  When sync is enabled, only the <strong>BSA Member ID</strong> and each
                  scout's <strong>class schedule selections</strong> (badge/rank names and
                  session times) are sent. No names or personal details are included.
                </dd>

                <dt>Who controls the data</dt>
                <dd>
                  Your troop admin creates and owns the Google Sheet. Only people the admin
                  shares the Apps Script URL with can access it. ScoutSight has no
                  connection to your troop's sheet and receives no data.
                </dd>

                <dt>Offline mode</dt>
                <dd>
                  Choosing <em>Use Offline</em> means nothing is sent anywhere. Downloaded
                  CSV/JSON files contain BSA Member IDs and class selections only.
                </dd>
              </dl>
              <a
                className="privacy-full-link"
                href="camp_scheduler/privacy.html"
                target="_blank"
                rel="noopener noreferrer"
              >
                → Full privacy policy
              </a>
            </div>
          )}
        </div>

      </div>
    </div>
  )
}
