// Pure logic for issue #7 (Fix troopOS userType / pendingTypeAssignment for scout
// accounts). Zero dependencies so it can be unit-tested without hitting troopOS.
//
// Ground truth for classification is the BSA roster CSV export (sectioned into
// "ADULT MEMBERS" / "YOUTH MEMBERS", each with a "BSA Number" column) — NOT
// dateOfBirth/joinDate, which troopOS leaves at 0 for both adults and scouts.
'use strict';

/**
 * Splits one CSV line into fields, honoring double-quoted fields with embedded
 * commas/escaped quotes. Ported verbatim from admin.html's parseCsvLine
 * (src/main/resources/templates/admin.html:3098) — same input format, proven
 * against the real BSA roster export. Unlike that version, this one does NOT
 * trim fields, because parseRosterCsv below needs the untrimmed leading cell
 * to distinguish a quoted-space section marker (`" "` -> `' '`) from an
 * unquoted empty continuation-row cell (`''`) — trimming would make them
 * indistinguishable, which is exactly the bug admin.html's comment warns about.
 */
function parseCsvLine(line) {
  var cells = [], cell = '', inQ = false;
  for (var i = 0; i < line.length; i++) {
    var c = line[i];
    if (c === '"') {
      if (inQ && line[i + 1] === '"') { cell += '"'; i++; }
      else inQ = !inQ;
    } else if (c === ',' && !inQ) { cells.push(cell); cell = ''; }
    else cell += c;
  }
  cells.push(cell);
  return cells;
}

/** Strips whitespace/quotes/leading-zero-agnostic formatting from a BSA number for comparison. */
function normalizeBsaNumber(raw) {
  return String(raw || '').trim().replace(/^"|"$/g, '').trim().replace(/^0+(?=\d)/, '');
}

/**
 * Case-insensitive lookup of a field in a roster row object, mirroring
 * admin.html's findField (src/main/resources/templates/admin.html:3113).
 */
function findField(obj, names) {
  for (var k in obj) {
    if (k === '_section') continue;
    if (names.indexOf(k.toLowerCase().trim()) !== -1) return obj[k];
  }
  return null;
}

/**
 * Parses the multi-section BSA Full Troop Roster CSV into row objects tagged
 * with their section name. Algorithm ported from admin.html's parseRosterCsv
 * (src/main/resources/templates/admin.html:3132) — see that function's comment
 * for the section-marker-vs-continuation-row distinction this relies on.
 * Skips DEN CHIEF MEMBERS sections, same as admin.html.
 */
function parseRosterRows(text) {
  if (!text || !text.trim()) return [];
  var lines = text.split(/\r?\n/);
  var section = '';
  var headers = null;
  var rows = [];
  var skipSection = false;

  for (var i = 0; i < lines.length; i++) {
    var line = lines[i];
    if (!line.trim()) continue;
    var cells = parseCsvLine(line);
    if (!cells.length) continue;
    var first = cells[0].trim();

    if (cells[0] === ' ') {
      var isColHeader = cells.some(function (c) {
        var cl = c.trim().toLowerCase();
        return cl === 'first name' || cl === 'last name' || cl === 'email';
      });
      if (isColHeader) {
        headers = cells.slice(1).map(function (c) { return c.trim(); });
      } else {
        var sName = (cells[1] || '').trim();
        skipSection = sName.toUpperCase().indexOf('DEN CHIEF') !== -1;
        section = sName;
        headers = null;
      }
    } else if (!skipSection && headers && /^\d+$/.test(first)) {
      var dataCells = cells.slice(1);
      var obj = { _section: section };
      headers.forEach(function (h, idx) {
        obj[h] = (dataCells[idx] || '').trim();
      });
      rows.push(obj);
    }
  }

  return rows;
}

/**
 * Buckets the roster CSV's rows into adult/youth BSA-number sets by section
 * name. Any section other than ADULT MEMBERS / YOUTH MEMBERS (e.g. a skipped
 * DEN CHIEF MEMBERS section) contributes to neither set.
 */
function parseRosterCsv(csvText) {
  var rows = parseRosterRows(csvText);
  var adultBsaNumbers = new Set();
  var youthBsaNumbers = new Set();

  rows.forEach(function (row) {
    var bsa = findField(row, ['bsa number']);
    if (!bsa) return;
    var norm = normalizeBsaNumber(bsa);
    var section = (row._section || '').toUpperCase();
    if (section.indexOf('ADULT') !== -1) adultBsaNumbers.add(norm);
    else if (section.indexOf('YOUTH') !== -1) youthBsaNumbers.add(norm);
  });

  return { adultBsaNumbers: adultBsaNumbers, youthBsaNumbers: youthBsaNumbers };
}

/**
 * Classifies one pendingTypeAssignment troopOS user against the roster.
 * Returns { userType: 'scout'|'adult'|null, reason }. null means "couldn't resolve,
 * needs manual review" — never guess when the roster doesn't confirm either way.
 */
function classifyPendingUser(user, roster) {
  var bsa = user && user.scout && user.scout.bsaMemberId;
  if (!bsa) return { userType: null, reason: 'no bsaMemberId on account — needs manual review' };
  var norm = normalizeBsaNumber(bsa);
  if (roster.youthBsaNumbers.has(norm)) return { userType: 'scout', reason: 'bsaMemberId matches YOUTH MEMBERS roster' };
  if (roster.adultBsaNumbers.has(norm)) return { userType: 'adult', reason: 'bsaMemberId matches ADULT MEMBERS roster' };
  return { userType: null, reason: 'bsaMemberId ' + norm + ' not found in either roster section — needs manual review' };
}

/**
 * Indexes a GWS Directory export (admin.html's own `?action=listUsers` shape:
 * [{email, isYouth, scoutId}, ...]) by email and by scoutId, for use by
 * classifyPendingUserByGws below.
 */
function indexGwsUsers(gwsUsers) {
  var byEmail = new Map();
  var byScoutId = new Map();
  (gwsUsers || []).forEach(function (u) {
    if (u.email) byEmail.set(String(u.email).toLowerCase(), u);
    if (u.scoutId) byScoutId.set(String(u.scoutId), u);
  });
  return { byEmail: byEmail, byScoutId: byScoutId };
}

/**
 * Classifies one pendingTypeAssignment troopOS user against GWS Directory data
 * (the "raw Google user" record, distinct from troopOS's own bespoke user table
 * — see docs/relationships-reconciliation-2026-09-16.md for why troopOS's own
 * scout.bsaMemberId is too sparse to use alone). Matches by email first (the key
 * both systems share), falling back to bsaMemberId <-> GWS scoutId for troopOS
 * accounts that have no email on file. Trusts GWS's isYouth flag as ground truth;
 * returns null (needs manual review) if isYouth itself is unset on the matched
 * GWS record, or if no GWS record matches at all — never guesses.
 */
function classifyPendingUserByGws(user, gwsIndex) {
  var email = user && user.email;
  var bsa = user && user.scout && user.scout.bsaMemberId;
  var gwsUser = null, matchedBy = null;
  if (email) { gwsUser = gwsIndex.byEmail.get(String(email).toLowerCase()); if (gwsUser) matchedBy = 'email'; }
  if (!gwsUser && bsa) { gwsUser = gwsIndex.byScoutId.get(normalizeBsaNumber(bsa)) || gwsIndex.byScoutId.get(String(bsa)); if (gwsUser) matchedBy = 'bsaMemberId<->scoutId'; }
  if (!gwsUser) return { userType: null, reason: 'no matching GWS user by email or bsaMemberId — needs manual review' };
  if (gwsUser.isYouth === true) return { userType: 'scout', reason: 'GWS isYouth=true, matched by ' + matchedBy, matchedBy: matchedBy, gwsEmail: gwsUser.email };
  if (gwsUser.isYouth === false) return { userType: 'adult', reason: 'GWS isYouth=false, matched by ' + matchedBy, matchedBy: matchedBy, gwsEmail: gwsUser.email };
  return { userType: null, reason: 'matched GWS user (' + matchedBy + ') but its isYouth field is unset — needs manual review', matchedBy: matchedBy, gwsEmail: gwsUser.email };
}

/**
 * Computes roleIds that appear only on already-correctly-typed adults and never on
 * already-correctly-typed scouts, from live troopOS data (not hardcoded) — so a
 * newly-classified scout account can be checked for leftover adult-only roles.
 * `roleIds` as the field name is confirmed by both a live /private/tables/users
 * sample and docs/troopos-admin-api.md's PUT /private/tables/users body shape.
 */
function computeAdultExclusiveRoles(allUsers) {
  var scoutRoles = new Set();
  var adultRoles = new Set();
  (allUsers || []).forEach(function (u) {
    if (u.pendingTypeAssignment) return; // only trust already-resolved accounts as reference
    var roles = u.roleIds || [];
    if (u.userType === 'scout') roles.forEach(function (r) { scoutRoles.add(r); });
    if (u.userType === 'adult') roles.forEach(function (r) { adultRoles.add(r); });
  });
  var exclusive = new Set();
  adultRoles.forEach(function (r) { if (!scoutRoles.has(r)) exclusive.add(r); });
  return exclusive;
}

/** Returns the subset of a (soon-to-be-scout) user's current roleIds that are adult-exclusive. */
function roleMismatchesForScout(user, adultExclusiveRoles) {
  return (user.roleIds || []).filter(function (r) { return adultExclusiveRoles.has(r); });
}

/**
 * Computes the roleIds a newly-classified scout account should end up with:
 * current roleIds, minus any adult-exclusive ones, plus the baseline
 * ["member", "scout"] — modeled on milesg@troop600.com (id 118), whose roleIds
 * Blair set by hand via troop600.com's native UI as the reference example.
 * Preserves any other roles the account already had (e.g. "calendar-editor").
 */
function targetRoleIdsForScout(currentRoleIds, adultExclusiveRoles) {
  var kept = (currentRoleIds || []).filter(function (r) { return !adultExclusiveRoles.has(r); });
  var result = new Set(kept);
  result.add('member');
  result.add('scout');
  return Array.from(result);
}

/**
 * Computes the roleIds a newly-classified adult account should end up with:
 * current roleIds plus the baseline ["member", "parent"] — modeled on
 * vielbige@troop600.com (id 144) as the reference example. Preserves any other
 * roles the account already had (e.g. "admin", "adult-leader").
 */
function targetRoleIdsForAdult(currentRoleIds) {
  var result = new Set(currentRoleIds || []);
  result.add('member');
  result.add('parent');
  return Array.from(result);
}

module.exports = {
  parseCsvLine: parseCsvLine,
  normalizeBsaNumber: normalizeBsaNumber,
  findField: findField,
  parseRosterRows: parseRosterRows,
  parseRosterCsv: parseRosterCsv,
  classifyPendingUser: classifyPendingUser,
  indexGwsUsers: indexGwsUsers,
  classifyPendingUserByGws: classifyPendingUserByGws,
  computeAdultExclusiveRoles: computeAdultExclusiveRoles,
  roleMismatchesForScout: roleMismatchesForScout,
  targetRoleIdsForScout: targetRoleIdsForScout,
  targetRoleIdsForAdult: targetRoleIdsForAdult
};
