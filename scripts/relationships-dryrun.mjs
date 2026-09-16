#!/usr/bin/env node
// Reconciliation dry-run for issue #5 (Migrate GWS-only relationship data to troopOS).
//
// Takes the GWS-side relationship export CSV (adultEmail,scoutEmail,kind,verified —
// no header, produced by the pre-repoint arExportCsv() at commit 0be3b42, or hand-
// maintained the same way) and checks each row against live troopOS data:
//   - does the adult email resolve to a troopOS user?
//   - does the scout email resolve to a troopOS user?
//   - does a relationship already exist for that resolved id pair?
// Read-only — never writes to troopOS. Rerun any time to see how the gap has changed
// (e.g. after fixing troopOS userType/pendingTypeAssignment issues, see issue #7).
//
// Usage:
//   TROOPOS_TOKEN=<session token from admin.html/troop600.com> \
//     node scripts/relationships-dryrun.mjs path/to/gws_export.csv
//
// The token is never written anywhere by this script — pass it via env var only.

const TROOPOS_BASE = 'https://troop600.com';
const token = process.env.TROOPOS_TOKEN;
if (!token) { console.error('Set TROOPOS_TOKEN env var'); process.exit(1); }

const fs = await import('node:fs');
const csvPath = process.argv[2];
if (!csvPath) { console.error('Usage: TROOPOS_TOKEN=... node scripts/relationships-dryrun.mjs <csv-path>'); process.exit(1); }

const lines = fs.readFileSync(csvPath, 'utf8').split(/\r?\n/).filter(Boolean);
const rows = lines.map(l => l.split(','));

const headers = { Authorization: 'Bearer ' + token };
const [usersResp, relResp] = await Promise.all([
  fetch(TROOPOS_BASE + '/private/tables/users', { headers }),
  fetch(TROOPOS_BASE + '/private/tables/relationships', { headers }),
]);

if (!usersResp.ok) { console.error('users fetch failed', usersResp.status, await usersResp.text()); process.exit(1); }
if (!relResp.ok)   { console.error('relationships fetch failed', relResp.status, await relResp.text()); process.exit(1); }

const users = await usersResp.json();
const relationships = await relResp.json();

const byEmail = new Map();
for (const u of users) {
  if (u.email) byEmail.set(String(u.email).toLowerCase(), u);
}

const existingPairs = new Set(relationships.map(r => r.relationshipId));

let ok = 0, fail = 0;
const results = [];

for (let i = 0; i < rows.length; i++) {
  const [adultEmail, scoutEmail, kind, verified] = rows[i];
  const adult = byEmail.get(String(adultEmail || '').toLowerCase());
  const scout = byEmail.get(String(scoutEmail || '').toLowerCase());
  const problems = [];
  const notes = [];
  if (!adult) problems.push(`adult email not found in troopOS users: ${adultEmail}`);
  if (!scout) problems.push(`scout email not found in troopOS users: ${scoutEmail}`);
  // userType is not a confirmed blocker for relationship creation — see issue #7 for
  // the systemic pendingTypeAssignment gap that makes this noisy today.
  if (adult && adult.userType !== 'adult') notes.push(`adult email resolves to a userType=${adult.userType} account (unconfirmed whether this blocks create): ${adultEmail}`);
  if (scout && scout.userType !== 'scout') notes.push(`scout email resolves to a userType=${scout.userType} account, pendingTypeAssignment=${!!scout.pendingTypeAssignment} (unconfirmed whether this blocks create): ${scoutEmail}`);
  if (adult && scout) {
    const relId = `${adult.id}#${scout.id}`;
    if (existingPairs.has(relId)) problems.push(`relationship already exists in troopOS (relationshipId=${relId})`);
  }
  if (problems.length) {
    fail++;
    results.push({ row: i + 1, adultEmail, scoutEmail, status: 'FAIL', problems, notes });
  } else {
    ok++;
    results.push({ row: i + 1, adultEmail, scoutEmail, status: 'OK', notes });
  }
}

console.log(`\n=== Dry-run summary: ${ok} would likely succeed, ${fail} confirmed-blocked (of ${rows.length}) ===\n`);
for (const r of results) {
  if (r.status === 'FAIL') {
    console.log(`Row ${r.row}: FAIL  ${r.adultEmail} -> ${r.scoutEmail}`);
    for (const p of r.problems) console.log(`    - ${p}`);
  }
}
const withNotes = results.filter(r => r.notes && r.notes.length);
console.log(`\n=== ${withNotes.length} rows with userType notes (not counted as failures) ===\n`);
for (const r of withNotes) {
  console.log(`Row ${r.row} [${r.status}]  ${r.adultEmail} -> ${r.scoutEmail}`);
  for (const n of r.notes) console.log(`    * ${n}`);
}
