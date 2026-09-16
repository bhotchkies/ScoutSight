// Zero-dependency test for admin.html's Relationships tab pure logic.
// Run: node test/relationships_logic.test.js
'use strict';

var assert = require('assert');
var RL = require('../src/main/resources/config/admin-console/relationships_logic.js');

var fullyProvisioned = { id: '1001', userType: 'adult', firstName: 'Dana', lastName: 'Alvarez',
  email: 'dana.alvarez@troop600.com', personalEmail: 'dana.alvarez@gmail.com' };
var noEmailHasPersonal = { id: '1002', userType: 'adult', firstName: 'Marcus', lastName: 'Byrne',
  email: '', personalEmail: 'mbyrne77@yahoo.com' };
var worstCaseAdult = { id: '1003', userType: 'adult', firstName: 'Priya', lastName: 'Chandra',
  email: '', personalEmail: '' };
var scoutWithBsaId = { id: '2002', userType: 'scout', firstName: 'Noah', lastName: 'Byrne',
  email: '', personalEmail: '', scout: { bsaMemberId: '14550098' } };
var worstCaseScout = { id: '2003', userType: 'scout', firstName: 'Ava', lastName: 'Chandra',
  email: '', personalEmail: '' };

// ---- displayName ----------------------------------------------------------
assert.strictEqual(RL.displayName(fullyProvisioned), 'Alvarez, Dana');

// ---- displaySub fallback chain ---------------------------------------------
assert.strictEqual(RL.displaySub(fullyProvisioned), 'dana.alvarez@troop600.com');
assert.strictEqual(RL.displaySub(noEmailHasPersonal), 'mbyrne77@yahoo.com (personal)');
assert.strictEqual(RL.displaySub(scoutWithBsaId), 'BSA# 14550098 — no email on file');
assert.strictEqual(RL.displaySub(worstCaseAdult), 'No contact info on file');
assert.strictEqual(RL.displaySub(worstCaseScout), 'No contact info on file');

// ---- chipLabel --------------------------------------------------------------
assert.strictEqual(RL.chipLabel(fullyProvisioned), 'Dana (dana.alvarez)');
assert.strictEqual(RL.chipLabel(noEmailHasPersonal), 'Marcus');
assert.strictEqual(RL.chipLabel(worstCaseAdult), 'Priya (no email)');

// ---- isIncomplete -------------------------------------------------------------
assert.strictEqual(RL.isIncomplete(fullyProvisioned), false);
assert.strictEqual(RL.isIncomplete(scoutWithBsaId), false);
assert.strictEqual(RL.isIncomplete(worstCaseAdult), true);

// ---- splitByUserType ----------------------------------------------------------
var split = RL.splitByUserType([fullyProvisioned, scoutWithBsaId, worstCaseAdult, worstCaseScout]);
assert.deepStrictEqual(split.adults.map(function (u) { return u.id; }), ['1001', '1003']);
assert.deepStrictEqual(split.scouts.map(function (u) { return u.id; }), ['2002', '2003']);
assert.deepStrictEqual(RL.splitByUserType(undefined), { adults: [], scouts: [] });

// ---- relsFor / isRelSaved -------------------------------------------------------
var rels = [
  { relationshipId: '1001#2001', adultId: '1001', scoutId: '2001', kind: 'parent', verified: true },
  { relationshipId: '1001#2002', adultId: '1001', scoutId: '2002', kind: 'parent', verified: true }
];
assert.strictEqual(RL.relsFor(rels, '1001', 'adult').length, 2);
assert.strictEqual(RL.relsFor(rels, '2002', 'scout').length, 1);
assert.strictEqual(RL.relsFor(rels, '9999', 'adult').length, 0);
assert.strictEqual(RL.isRelSaved(rels, '1001', '2001'), true);
assert.strictEqual(RL.isRelSaved(rels, '1001', '2003'), false);

// ---- toggleLine: unsaved pair stages an add, clicking again cancels it ----------
var t1 = RL.toggleLine(rels, [], [], '1003', '2003');
assert.deepStrictEqual(t1.pendingAdd, [{ adultId: '1003', scoutId: '2003' }]);
assert.deepStrictEqual(t1.pendingRemove, []);
var t2 = RL.toggleLine(rels, t1.pendingAdd, t1.pendingRemove, '1003', '2003');
assert.deepStrictEqual(t2.pendingAdd, []);

// ---- toggleLine: saved pair stages a remove, clicking again undoes it ----------
var t3 = RL.toggleLine(rels, [], [], '1001', '2001');
assert.deepStrictEqual(t3.pendingRemove, [{ adultId: '1001', scoutId: '2001' }]);
assert.deepStrictEqual(t3.pendingAdd, []);
var t4 = RL.toggleLine(rels, t3.pendingAdd, t3.pendingRemove, '1001', '2001');
assert.deepStrictEqual(t4.pendingRemove, []);

// ---- toggleLine does not mutate its input arrays --------------------------------
var origAdd = [];
var origRemove = [];
RL.toggleLine(rels, origAdd, origRemove, '1003', '2003');
assert.deepStrictEqual(origAdd, []);
assert.deepStrictEqual(origRemove, []);

// ---- handlePortClick: select adult, then select scout stages a pending add -----
var s0 = { activeId: null, activeType: null, relationships: rels, pendingAdd: [], pendingRemove: [] };
var s1 = RL.handlePortClick(s0, '1003', 'adult');
assert.strictEqual(s1.activeId, '1003');
assert.strictEqual(s1.activeType, 'adult');
assert.strictEqual(s1.connected, false);

var s2 = RL.handlePortClick(
  { activeId: s1.activeId, activeType: s1.activeType, relationships: rels, pendingAdd: s1.pendingAdd, pendingRemove: s1.pendingRemove },
  '2003', 'scout'
);
assert.strictEqual(s2.activeId, null);
assert.strictEqual(s2.connected, true);
assert.deepStrictEqual(s2.pendingAdd, [{ adultId: '1003', scoutId: '2003' }]);

// ---- handlePortClick: clicking the same active port again clears selection -----
var s3 = RL.handlePortClick(s0, '1003', 'adult');
var s4 = RL.handlePortClick({ activeId: s3.activeId, activeType: s3.activeType, relationships: rels, pendingAdd: [], pendingRemove: [] }, '1003', 'adult');
assert.strictEqual(s4.activeId, null);
assert.strictEqual(s4.connected, false);

// ---- handlePortClick: clicking another port on the same side switches selection -
var s5 = RL.handlePortClick(s0, '1001', 'adult');
var s6 = RL.handlePortClick({ activeId: s5.activeId, activeType: s5.activeType, relationships: rels, pendingAdd: [], pendingRemove: [] }, '1003', 'adult');
assert.strictEqual(s6.activeId, '1003');
assert.strictEqual(s6.activeType, 'adult');
assert.strictEqual(s6.connected, false);

console.log('relationships_logic.test.js: all assertions passed');
