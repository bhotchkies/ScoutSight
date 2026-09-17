#!/usr/bin/env node
// Generates a paste-into-DevTools script that deletes troopOS user accounts
// confirmed absent from the current roster (name AND BSA-number cross-check
// against the roster CSV) — see docs/troopos-usertype-reconciliation-2026-09-16.md,
// "Second session" section, for how this list was derived.
//
// User deletion does NOT follow the generic table-client DELETE convention
// docs/troopos-admin-api.md describes (DELETE /private/tables/users with {id}
// in the body) — that 400s with {"error":"use_users_delete_endpoint"}. The
// real endpoint is DELETE /private/users/{id} (id as a path param, no body).
// Same CloudFront/WAF block as GET/PUT applies, so this must be pasted into
// troop600.com's own DevTools console and run there.
//
// Usage:
//   node scripts/generate-delete-batch.mjs --users-file <troopos-users-json> --names "Full Name 1,Full Name 2,..."
//
// Matches by exact `name` field (most of these accounts have no email on file).
// Aborts with a warning (and skips) if a name matches zero or more than one user.

const fs = await import('node:fs');
const args = process.argv.slice(2);
function argVal(flag) {
  const i = args.indexOf(flag);
  return i !== -1 ? args[i + 1] : null;
}

const usersFilePath = argVal('--users-file');
const namesArg = argVal('--names');
if (!usersFilePath || !namesArg) {
  console.error('Usage: node scripts/generate-delete-batch.mjs --users-file <path> --names "Name 1,Name 2,..."');
  process.exit(1);
}

const users = JSON.parse(fs.readFileSync(usersFilePath, 'utf8'));
const byName = new Map();
for (const u of users) {
  const key = (u.name || '').trim();
  if (!key) continue;
  if (!byName.has(key)) byName.set(key, []);
  byName.get(key).push(u);
}

const names = namesArg.split(',').map(n => n.trim()).filter(Boolean);
const toDelete = [];
for (const name of names) {
  const matches = byName.get(name) || [];
  if (matches.length === 0) { console.error(`WARNING: no user named "${name}" found, skipping`); continue; }
  if (matches.length > 1) { console.error(`WARNING: "${name}" matches ${matches.length} users, skipping (resolve manually): ${matches.map(m => m.id).join(', ')}`); continue; }
  toDelete.push({ id: matches[0].id, name });
}

console.error(`Prepared ${toDelete.length} of ${names.length} requested accounts for deletion.`);
console.error('Paste the script below into troop600.com\'s own DevTools console (logged in as an admin) and run it.\n');

const script = `
(async () => {
  const deletions = ${JSON.stringify(toDelete, null, 2)};
  const token = localStorage.getItem('jwt');
  const results = [];
  for (const d of deletions) {
    const resp = await fetch('/private/users/' + encodeURIComponent(d.id), {
      method: 'DELETE',
      headers: { Authorization: 'Bearer ' + token }
    });
    let detail = '';
    if (!resp.ok) { try { detail = (await resp.json()).error || ''; } catch (e) {} }
    results.push({ name: d.name, id: d.id, ok: resp.ok, status: resp.status, detail });
  }
  console.table(results);
})();
`.trim();

console.log(script);
