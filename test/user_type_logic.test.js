// Zero-dependency test for scripts/user_type_logic.js (issue #7).
// Run: node test/user_type_logic.test.js
'use strict';

var assert = require('assert');
var UT = require('../scripts/user_type_logic.js');

// ---- parseCsvLine -----------------------------------------------------------
assert.deepStrictEqual(UT.parseCsvLine('a,b,c'), ['a', 'b', 'c']);
assert.deepStrictEqual(UT.parseCsvLine('"a, with comma",b,"c ""quoted"""'), ['a, with comma', 'b', 'c "quoted"']);
assert.deepStrictEqual(UT.parseCsvLine(' " " ,ADULT MEMBERS'), ['', 'ADULT MEMBERS']);

// ---- normalizeBsaNumber -------------------------------------------------------
assert.strictEqual(UT.normalizeBsaNumber('"014042724"'), '14042724');
assert.strictEqual(UT.normalizeBsaNumber(' 14042724 '), '14042724');
assert.strictEqual(UT.normalizeBsaNumber(''), '');

// ---- parseRosterCsv -----------------------------------------------------------
var rosterCsv = [
  '" ",ADULT MEMBERS',
  '" ","First Name","Last Name","Email","BSA Number","Unit Number"',
  '"1","Douglas","Burchard","doug@example.com","12067811","Troop 600 B"',
  '" ","YOUTH MEMBERS"',
  '" ","First Name","Last Name","Rank","BSA Number","Date of Birth"',
  '"1","Arin","Patel","Second Class","14042724","06/04/2014"',
  '"2","Noah","Byrne","Scout","014550098","01/01/2013"'
].join('\n');

var roster = UT.parseRosterCsv(rosterCsv);
assert.strictEqual(roster.adultBsaNumbers.has('12067811'), true);
assert.strictEqual(roster.adultBsaNumbers.has('14042724'), false);
assert.strictEqual(roster.youthBsaNumbers.has('14042724'), true);
assert.strictEqual(roster.youthBsaNumbers.has('14550098'), true); // leading zero normalized away

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

console.log('user_type_logic.test.js: all assertions passed');
