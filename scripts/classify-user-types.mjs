#!/usr/bin/env node
// Dry-run classifier for issue #7 (Fix troopOS userType / pendingTypeAssignment).
//
// Read-only — never writes to troopOS. Fetches live troopOS users, classifies every
// pendingTypeAssignment account, and reports proposed userType changes plus any
// adult-only roleIds a newly-classified scout account would still be carrying.
// Nothing gets applied until this report has been reviewed and a separate apply
// step is explicitly run.
//
// Classification tries two sources, in order:
//   1. GWS Directory data (email + isYouth + scoutId, from admin.html's own
//      `?action=listUsers` — see docs/relationships-reconciliation-2026-09-16.md for
//      why this is required: troopOS's own scout.bsaMemberId is only populated on a
//      handful of pendingTypeAssignment accounts, so relying on it alone resolves
//      almost nothing). The extension can't script admin.html directly (file://
//      pages aren't accessible to it) — run this in that page's DevTools console
//      and save the result to a file:
//        copy(JSON.stringify((await fetch(localStorage.getItem('scoutsight_gws_url')
//          + '?action=listUsers').then(r=>r.json())).users.map(u=>({email:u.email,
//          isYouth:u.isYouth,scoutId:u.scoutId}))))
//      then paste the clipboard contents into a .json file.
//   2. The BSA roster CSV (ADULT MEMBERS / YOUTH MEMBERS sections), as a fallback
//      for anything GWS didn't resolve — pass it as a third argument.
//
// Usage:
//   TROOPOS_TOKEN=<session token from admin.html/troop600.com> \
//     node scripts/classify-user-types.mjs path/to/gws_users.json [path/to/roster.csv]

import { createRequire } from 'node:module';
const require = createRequire(import.meta.url);
const {
  parseRosterCsv, classifyPendingUser,
  indexGwsUsers, classifyPendingUserByGws,
  computeAdultExclusiveRoles, roleMismatchesForScout
} = require('./user_type_logic.js');

const TROOPOS_BASE = 'https://troop600.com';
const token = process.env.TROOPOS_TOKEN;
if (!token) { console.error('Set TROOPOS_TOKEN env var'); process.exit(1); }

const fs = await import('node:fs');
const gwsPath = process.argv[2];
const rosterPath = process.argv[3];
if (!gwsPath) {
  console.error('Usage: TROOPOS_TOKEN=... node scripts/classify-user-types.mjs <gws-users-json> [roster-csv-path]');
  process.exit(1);
}

const gwsIndex = indexGwsUsers(JSON.parse(fs.readFileSync(gwsPath, 'utf8')));
console.error(`GWS data loaded: ${gwsIndex.byEmail.size} by email, ${gwsIndex.byScoutId.size} by scoutId.`);

const roster = rosterPath ? parseRosterCsv(fs.readFileSync(rosterPath, 'utf8')) : null;
if (roster) console.error(`Roster loaded (fallback): ${roster.adultBsaNumbers.size} adult BSA numbers, ${roster.youthBsaNumbers.size} youth BSA numbers.`);

const headers = { Authorization: 'Bearer ' + token };
const usersResp = await fetch(TROOPOS_BASE + '/private/tables/users', { headers });
if (!usersResp.ok) {
  console.error('users fetch failed', usersResp.status, await usersResp.text());
  process.exit(1);
}
const users = await usersResp.json();

const pending = users.filter(u => u.pendingTypeAssignment);
const adultExclusiveRoles = computeAdultExclusiveRoles(users);

const toScout = [];
const toAdult = [];
const unresolved = [];

for (const u of pending) {
  let result = classifyPendingUserByGws(u, gwsIndex);
  let source = 'gws';
  if (result.userType === null && roster) {
    const rosterResult = classifyPendingUser(u, roster);
    if (rosterResult.userType !== null) { result = rosterResult; source = 'roster'; }
  }
  const { userType, reason } = result;
  if (userType === 'scout') {
    const mismatches = roleMismatchesForScout(u, adultExclusiveRoles);
    toScout.push({ user: u, reason, source, roleMismatches: mismatches });
  } else if (userType === 'adult') {
    toAdult.push({ user: u, reason, source });
  } else {
    unresolved.push({ user: u, reason });
  }
}

console.log(`\n=== Classification summary: ${pending.length} pendingTypeAssignment accounts ===`);
console.log(`  -> scout : ${toScout.length}`);
console.log(`  -> adult : ${toAdult.length}`);
console.log(`  -> unresolved (needs manual review): ${unresolved.length}`);

console.log(`\n=== Proposed -> scout (${toScout.length}) ===`);
for (const { user, source, roleMismatches } of toScout) {
  const flag = roleMismatches.length ? `  [adult-only roleIds to review: ${roleMismatches.join(', ')}]` : '';
  console.log(`  ${user.email || '(no email)'}  id=${user.id}  bsa=${(user.scout && user.scout.bsaMemberId) || '(none)'}  [${source}]${flag}`);
}

console.log(`\n=== Proposed -> adult (${toAdult.length}) ===`);
for (const { user, source } of toAdult) {
  console.log(`  ${user.email || '(no email)'}  id=${user.id}  bsa=${(user.scout && user.scout.bsaMemberId) || '(none)'}  [${source}]`);
}

console.log(`\n=== Unresolved, needs manual review (${unresolved.length}) ===`);
for (const { user, reason } of unresolved) {
  console.log(`  ${user.email || '(no email)'}  id=${user.id}  bsa=${(user.scout && user.scout.bsaMemberId) || '(none)'}  — ${reason}`);
}
