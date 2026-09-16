#!/usr/bin/env node
// Dry-run classifier for issue #7 (Fix troopOS userType / pendingTypeAssignment).
//
// Read-only — never writes to troopOS. Fetches live users, classifies every
// pendingTypeAssignment account against the roster CSV via scripts/user_type_logic.js,
// and reports proposed userType changes plus any adult-only roleIds a newly-classified
// scout account would still be carrying. Nothing gets applied until this report has
// been reviewed and a separate apply step is explicitly run.
//
// Usage:
//   TROOPOS_TOKEN=<session token from admin.html/troop600.com> \
//     node scripts/classify-user-types.mjs path/to/roster_export.csv

import { createRequire } from 'node:module';
const require = createRequire(import.meta.url);
const { parseRosterCsv, classifyPendingUser, computeAdultExclusiveRoles, roleMismatchesForScout } = require('./user_type_logic.js');

const TROOPOS_BASE = 'https://troop600.com';
const token = process.env.TROOPOS_TOKEN;
if (!token) { console.error('Set TROOPOS_TOKEN env var'); process.exit(1); }

const fs = await import('node:fs');
const rosterPath = process.argv[2];
if (!rosterPath) { console.error('Usage: TROOPOS_TOKEN=... node scripts/classify-user-types.mjs <roster-csv-path>'); process.exit(1); }

const roster = parseRosterCsv(fs.readFileSync(rosterPath, 'utf8'));
console.error(`Roster loaded: ${roster.adultBsaNumbers.size} adult BSA numbers, ${roster.youthBsaNumbers.size} youth BSA numbers.`);

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
  const { userType, reason } = classifyPendingUser(u, roster);
  if (userType === 'scout') {
    const mismatches = roleMismatchesForScout(u, adultExclusiveRoles);
    toScout.push({ user: u, reason, roleMismatches: mismatches });
  } else if (userType === 'adult') {
    toAdult.push({ user: u, reason });
  } else {
    unresolved.push({ user: u, reason });
  }
}

console.log(`\n=== Classification summary: ${pending.length} pendingTypeAssignment accounts ===`);
console.log(`  -> scout : ${toScout.length}`);
console.log(`  -> adult : ${toAdult.length}`);
console.log(`  -> unresolved (needs manual review): ${unresolved.length}`);

console.log(`\n=== Proposed -> scout (${toScout.length}) ===`);
for (const { user, roleMismatches } of toScout) {
  const flag = roleMismatches.length ? `  [adult-only roleIds to review: ${roleMismatches.join(', ')}]` : '';
  console.log(`  ${user.email || '(no email)'}  id=${user.id}  bsa=${user.scout && user.scout.bsaMemberId}${flag}`);
}

console.log(`\n=== Proposed -> adult (${toAdult.length}) ===`);
for (const { user } of toAdult) {
  console.log(`  ${user.email || '(no email)'}  id=${user.id}  bsa=${user.scout && user.scout.bsaMemberId}`);
}

console.log(`\n=== Unresolved, needs manual review (${unresolved.length}) ===`);
for (const { user, reason } of unresolved) {
  console.log(`  ${user.email || '(no email)'}  id=${user.id}  bsa=${(user.scout && user.scout.bsaMemberId) || '(none)'}  — ${reason}`);
}
