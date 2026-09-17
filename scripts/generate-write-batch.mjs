#!/usr/bin/env node
// Generates a paste-into-DevTools script that writes userType/roleIds/
// pendingTypeAssignment for a reviewed batch of accounts from issue #7's
// classify-user-types.mjs dry run. Never calls troopOS itself — troop600.com's
// CloudFront/WAF blocks scripted PUTs the same way it blocks scripted GETs
// (see docs/troopos-usertype-reconciliation-2026-09-16.md), so the output of
// this script must be pasted into troop600.com's own DevTools console and run
// there, same as the issue #7 batch write.
//
// Usage:
//   node scripts/generate-write-batch.mjs --users-file <troopos-users-json> \
//     --scouts email1,email2,... --adults email3,email4,...

import { createRequire } from 'node:module';
const require = createRequire(import.meta.url);
const { computeAdultExclusiveRoles, targetRoleIdsForScout, targetRoleIdsForAdult } = require('./user_type_logic.js');

const fs = await import('node:fs');
const args = process.argv.slice(2);
function argVal(flag) {
  const i = args.indexOf(flag);
  return i !== -1 ? args[i + 1] : null;
}

const usersFilePath = argVal('--users-file');
const scoutsArg = argVal('--scouts');
const adultsArg = argVal('--adults');
if (!usersFilePath || (!scoutsArg && !adultsArg)) {
  console.error('Usage: node scripts/generate-write-batch.mjs --users-file <path> [--scouts email1,email2] [--adults email3,email4]');
  process.exit(1);
}

const users = JSON.parse(fs.readFileSync(usersFilePath, 'utf8'));
const byEmail = new Map(users.map(u => [String(u.email || '').toLowerCase(), u]));
const adultExclusiveRoles = computeAdultExclusiveRoles(users);

function resolve(emailsArg, kind) {
  if (!emailsArg) return [];
  return emailsArg.split(',').map(e => e.trim().toLowerCase()).filter(Boolean).map(email => {
    const u = byEmail.get(email);
    if (!u) { console.error(`WARNING: ${email} not found in users file, skipping`); return null; }
    const roleIds = kind === 'scout'
      ? targetRoleIdsForScout(u.roleIds, adultExclusiveRoles)
      : targetRoleIdsForAdult(u.roleIds);
    return { ...u, userType: kind, roleIds, pendingTypeAssignment: false };
  }).filter(Boolean);
}

const payload = [...resolve(scoutsArg, 'scout'), ...resolve(adultsArg, 'adult')];

console.error(`Prepared ${payload.length} accounts for write (${resolve(scoutsArg, 'scout').length} scout, ${resolve(adultsArg, 'adult').length} adult).`);
console.error('Paste the script below into troop600.com\'s own DevTools console (logged in as an admin) and run it.\n');

const script = `
(async () => {
  const updates = ${JSON.stringify(payload, null, 2)};
  const token = localStorage.getItem('jwt');
  const results = [];
  for (const u of updates) {
    const resp = await fetch('/private/tables/users', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + token },
      body: JSON.stringify(u)
    });
    results.push({ email: u.email, id: u.id, userType: u.userType, ok: resp.ok, status: resp.status });
  }
  console.table(results);
  copy(JSON.stringify(results));
  console.log('Results copied to clipboard.');
})();
`.trim();

console.log(script);
