// Zero-dependency test for scripts/user_type_logic.js (issue #7).
// Run: node test/user_type_logic.test.js
'use strict';

var assert = require('assert');
var UT = require('../scripts/user_type_logic.js');

// ---- parseCsvLine -----------------------------------------------------------
// Does NOT trim (unlike a naive CSV splitter) — parseRosterCsv relies on the
// untrimmed leading cell to distinguish a quoted-space section marker (`" "`)
// from an unquoted empty continuation-row cell (`''`).
assert.deepStrictEqual(UT.parseCsvLine('a,b,c'), ['a', 'b', 'c']);
assert.deepStrictEqual(UT.parseCsvLine('"a, with comma",b,"c ""quoted"""'), ['a, with comma', 'b', 'c "quoted"']);
assert.deepStrictEqual(UT.parseCsvLine('" ",ADULT MEMBERS'), [' ', 'ADULT MEMBERS']);
assert.deepStrictEqual(UT.parseCsvLine(',,,,"Parent Name"'), ['', '', '', '', 'Parent Name']);

// ---- normalizeBsaNumber -------------------------------------------------------
assert.strictEqual(UT.normalizeBsaNumber('"014042724"'), '14042724');
assert.strictEqual(UT.normalizeBsaNumber(' 14042724 '), '14042724');
assert.strictEqual(UT.normalizeBsaNumber(''), '');

// ---- parseRosterCsv -----------------------------------------------------------
var rosterCsv = [
  '" ",ADULT MEMBERS',
  '" ","First Name","Last Name","Email","BSA Number","Unit Number"',
  '"1","Douglas","Burchard","doug@example.com","12067811","Troop 600 B"',
  ',,,,"Continuation Parent Name",,', // continuation row: unquoted empty first cell, not a section marker
  '" ","YOUTH MEMBERS"',
  '" ","First Name","Last Name","Rank","BSA Number","Date of Birth"',
  '"1","Arin","Patel","Second Class","14042724","06/04/2014"',
  '"2","Noah","Byrne","Scout","014550098","01/01/2013"',
  '" ","DEN CHIEF MEMBERS"',
  '" ","First Name","Last Name","Rank","BSA Number","Date of Birth"',
  '"1","Skipped","DenChief","Star Scout","99999999","01/01/2010"'
].join('\n');

var roster = UT.parseRosterCsv(rosterCsv);
assert.strictEqual(roster.adultBsaNumbers.has('12067811'), true);
assert.strictEqual(roster.adultBsaNumbers.has('14042724'), false);
assert.strictEqual(roster.youthBsaNumbers.has('14042724'), true);
assert.strictEqual(roster.youthBsaNumbers.has('14550098'), true); // leading zero normalized away
// DEN CHIEF MEMBERS is skipped, same as admin.html's parser — not counted in either set.
assert.strictEqual(roster.youthBsaNumbers.has('99999999'), false);
assert.strictEqual(roster.adultBsaNumbers.has('99999999'), false);

// ---- classifyPendingUser -------------------------------------------------------
var scoutAccount = { email: 'arinp@troop600.com', scout: { bsaMemberId: '14042724' } };
var adultAccount = { email: 'doug@troop600.com', scout: { bsaMemberId: '12067811' } };
var noBsaAccount = { email: 'nobsa@troop600.com', scout: { bsaMemberId: '' } };
var unmatchedAccount = { email: 'ghost@troop600.com', scout: { bsaMemberId: '99999999' } };
var noScoutObjAccount = { email: 'none@troop600.com' };

assert.deepStrictEqual(UT.classifyPendingUser(scoutAccount, roster).userType, 'scout');
assert.deepStrictEqual(UT.classifyPendingUser(adultAccount, roster).userType, 'adult');
assert.strictEqual(UT.classifyPendingUser(noBsaAccount, roster).userType, null);
assert.strictEqual(UT.classifyPendingUser(unmatchedAccount, roster).userType, null);
assert.strictEqual(UT.classifyPendingUser(noScoutObjAccount, roster).userType, null);

// ---- computeAdultExclusiveRoles -------------------------------------------------
var allUsers = [
  { userType: 'adult', pendingTypeAssignment: false, roleIds: ['admin', 'adult-leader', 'member'] },
  { userType: 'adult', pendingTypeAssignment: false, roleIds: ['parent', 'member'] },
  { userType: 'scout', pendingTypeAssignment: false, roleIds: ['calendar-editor', 'member'] },
  { userType: 'scout', pendingTypeAssignment: false, roleIds: ['member'] },
  // pending accounts must not pollute the reference sets even if userType is set
  { userType: 'adult', pendingTypeAssignment: true, roleIds: ['should-be-ignored'] }
];
var exclusive = UT.computeAdultExclusiveRoles(allUsers);
assert.strictEqual(exclusive.has('admin'), true);
assert.strictEqual(exclusive.has('adult-leader'), true);
assert.strictEqual(exclusive.has('parent'), true);
assert.strictEqual(exclusive.has('member'), false); // shared by both
assert.strictEqual(exclusive.has('should-be-ignored'), false);

// ---- roleMismatchesForScout -----------------------------------------------------
var misclassifiedScout = { roleIds: ['admin', 'member', 'parent'] };
assert.deepStrictEqual(UT.roleMismatchesForScout(misclassifiedScout, exclusive).sort(), ['admin', 'parent']);
var cleanScout = { roleIds: ['member', 'calendar-editor'] };
assert.deepStrictEqual(UT.roleMismatchesForScout(cleanScout, exclusive), []);

// ---- indexGwsUsers / classifyPendingUserByGws -----------------------------------
// This is the classification path that actually matters in practice: troopOS's own
// scout.bsaMemberId is populated on only ~5 of 164 pendingTypeAssignment accounts
// (confirmed against live data), so the roster-CSV path above resolves almost
// nothing. GWS's isYouth, joined by email (or bsaMemberId<->scoutId as a fallback
// for troopOS accounts with no email on file), resolves the large majority instead.
var gwsUsers = [
  { email: 'arinp@troop600.com', isYouth: true, scoutId: '14042724' },
  { email: 'doug@troop600.com', isYouth: false, scoutId: '12067811' },
  { email: 'unset@troop600.com', isYouth: '', scoutId: '' },
  { email: 'noemailscout@troop600.com', isYouth: true, scoutId: '99999999' }
];
var gwsIndex = UT.indexGwsUsers(gwsUsers);

var byEmailScout = UT.classifyPendingUserByGws({ email: 'arinp@troop600.com', scout: {} }, gwsIndex);
assert.strictEqual(byEmailScout.userType, 'scout');
assert.strictEqual(byEmailScout.matchedBy, 'email');

var byEmailAdult = UT.classifyPendingUserByGws({ email: 'DOUG@troop600.com', scout: {} }, gwsIndex);
assert.strictEqual(byEmailAdult.userType, 'adult'); // email match is case-insensitive

var byBsaFallback = UT.classifyPendingUserByGws({ email: '', scout: { bsaMemberId: '099999999' } }, gwsIndex);
assert.strictEqual(byBsaFallback.userType, 'scout');
assert.strictEqual(byBsaFallback.matchedBy, 'bsaMemberId<->scoutId');

var unsetIsYouth = UT.classifyPendingUserByGws({ email: 'unset@troop600.com', scout: {} }, gwsIndex);
assert.strictEqual(unsetIsYouth.userType, null);

var noGwsMatch = UT.classifyPendingUserByGws({ email: 'ghost@troop600.com', scout: {} }, gwsIndex);
assert.strictEqual(noGwsMatch.userType, null);

console.log('user_type_logic.test.js: all assertions passed');
