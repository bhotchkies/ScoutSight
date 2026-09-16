// Pure logic for issue #7 (Fix troopOS userType / pendingTypeAssignment for scout
// accounts). Zero dependencies so it can be unit-tested without hitting troopOS.
//
// Ground truth for classification is the BSA roster CSV export (sectioned into
// "ADULT MEMBERS" / "YOUTH MEMBERS", each with a "BSA Number" column) — NOT
// dateOfBirth/joinDate, which troopOS leaves at 0 for both adults and scouts.
'use strict';

/** Splits one CSV line into fields, honoring double-quoted fields with embedded commas/quotes. */
function parseCsvLine(line) {
  var fields = [];
  var cur = '';
  var inQuotes = false;
  for (var i = 0; i < line.length; i++) {
    var ch = line[i];
    if (inQuotes) {
      if (ch === '"') {
        if (line[i + 1] === '"') { cur += '"'; i++; }
        else { inQuotes = false; }
      } else {
        cur += ch;
      }
    } else if (ch === '"') {
      inQuotes = true;
    } else if (ch === ',') {
      fields.push(cur);
      cur = '';
    } else {
      cur += ch;
    }
  }
  fields.push(cur);
  return fields.map(function (f) { return f.trim(); });
}

/** Strips whitespace/quotes/leading-zero-agnostic formatting from a BSA number for comparison. */
function normalizeBsaNumber(raw) {
  return String(raw || '').trim().replace(/^"|"$/g, '').trim().replace(/^0+(?=\d)/, '');
}

/**
 * Parses the roster CSV's two sections into BSA-number sets.
 * Section markers are single-cell rows reading "ADULT MEMBERS" / "YOUTH MEMBERS";
 * each section's next row is its column header, used to locate "BSA Number".
 */
function parseRosterCsv(csvText) {
  var lines = csvText.split(/\r?\n/).filter(function (l) { return l.trim().length > 0; });
  var adultBsaNumbers = new Set();
  var youthBsaNumbers = new Set();
  var currentSet = null;
  var bsaColIndex = -1;

  for (var i = 0; i < lines.length; i++) {
    var fields = parseCsvLine(lines[i]);
    var joined = fields.join(',');
    if (/ADULT MEMBERS/i.test(joined)) { currentSet = adultBsaNumbers; bsaColIndex = -1; continue; }
    if (/YOUTH MEMBERS/i.test(joined)) { currentSet = youthBsaNumbers; bsaColIndex = -1; continue; }
    if (!currentSet) continue;
    if (bsaColIndex === -1) {
      // This is the header row for the current section.
      bsaColIndex = fields.findIndex(function (f) { return f.toLowerCase() === 'bsa number'; });
      continue;
    }
    var bsa = fields[bsaColIndex];
    if (bsa) currentSet.add(normalizeBsaNumber(bsa));
  }

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
 * Computes roleIds that appear only on already-correctly-typed adults and never on
 * already-correctly-typed scouts, from live troopOS data (not hardcoded) — so a
 * newly-classified scout account can be checked for leftover adult-only roles.
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

module.exports = {
  parseCsvLine: parseCsvLine,
  normalizeBsaNumber: normalizeBsaNumber,
  parseRosterCsv: parseRosterCsv,
  classifyPendingUser: classifyPendingUser,
  computeAdultExclusiveRoles: computeAdultExclusiveRoles,
  roleMismatchesForScout: roleMismatchesForScout
};
