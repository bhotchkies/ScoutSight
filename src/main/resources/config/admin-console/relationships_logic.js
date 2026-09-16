/**
 * Pure, DOM-independent logic for admin.html's Assign Relationships tab, sourced
 * from troopOS's native user/relationship shape (id-keyed, not email-keyed).
 * No DOM or fetch calls — usable from both the browser (inlined into admin.html
 * by AdminPageWriter) and Node (required directly by the test script).
 */
(function (root, factory) {
  if (typeof module === 'object' && module.exports) {
    module.exports = factory();
  } else {
    root.RelationshipsLogic = factory();
  }
})(this, function () {
  'use strict';

  /** Primary label — troopOS requires first/last name at account creation, so this never falls back. */
  function displayName(person) {
    return person.lastName + ', ' + person.firstName;
  }

  /** Secondary/contact-line fallback chain: email -> personalEmail -> BSA# -> placeholder. */
  function displaySub(person) {
    if (person.email) return person.email;
    if (person.personalEmail) return person.personalEmail + ' (personal)';
    if (person.scout && person.scout.bsaMemberId) {
      return 'BSA# ' + person.scout.bsaMemberId + ' — no email on file';
    }
    return 'No contact info on file';
  }

  /** Label used on the *other* side's chip to identify a connected person. */
  function chipLabel(person) {
    var first = person.firstName;
    if (person.email) return first + ' (' + person.email.split('@')[0] + ')';
    if (person.personalEmail) return first;
    return first + ' (no email)';
  }

  function isIncomplete(person) {
    return !person.email && !person.personalEmail && !(person.scout && person.scout.bsaMemberId);
  }

  /** Splits troopOS's /private/tables/users array into { adults, scouts } by userType. */
  function splitByUserType(users) {
    var adults = [], scouts = [];
    (users || []).forEach(function (u) {
      if (u.userType === 'adult') adults.push(u);
      else if (u.userType === 'scout') scouts.push(u);
    });
    return { adults: adults, scouts: scouts };
  }

  /** role: 'adult' -> relationships where this id is adultId; 'scout' -> scoutId. */
  function relsFor(relationships, id, role) {
    return (relationships || []).filter(function (r) {
      return role === 'adult' ? r.adultId === id : r.scoutId === id;
    });
  }

  function isRelSaved(relationships, adultId, scoutId) {
    return (relationships || []).some(function (r) {
      return r.adultId === adultId && r.scoutId === scoutId;
    });
  }

  function findPendingIndex(list, adultId, scoutId) {
    for (var i = 0; i < list.length; i++) {
      if (list[i].adultId === adultId && list[i].scoutId === scoutId) return i;
    }
    return -1;
  }

  /**
   * Pure staging transition for toggling the adultId/scoutId line:
   * saved+not-pending-remove -> stage remove; saved+pending-remove -> undo;
   * unsaved+not-pending-add -> stage add; unsaved+pending-add -> cancel add.
   * Returns new {pendingAdd, pendingRemove} arrays; never mutates the inputs.
   */
  function toggleLine(relationships, pendingAdd, pendingRemove, adultId, scoutId) {
    var nextAdd = pendingAdd.slice();
    var nextRemove = pendingRemove.slice();
    var saved = isRelSaved(relationships, adultId, scoutId);

    if (saved) {
      var prIdx = findPendingIndex(nextRemove, adultId, scoutId);
      if (prIdx !== -1) nextRemove.splice(prIdx, 1);
      else nextRemove.push({ adultId: adultId, scoutId: scoutId });
    } else {
      var paIdx = findPendingIndex(nextAdd, adultId, scoutId);
      if (paIdx !== -1) nextAdd.splice(paIdx, 1);
      else nextAdd.push({ adultId: adultId, scoutId: scoutId });
    }
    return { pendingAdd: nextAdd, pendingRemove: nextRemove };
  }

  /**
   * Pure port-click reducer. `state` is { activeId, activeType, relationships, pendingAdd, pendingRemove }.
   * Returns the next state plus a `connected` flag telling the caller whether a line was just toggled
   * (so it knows to re-fetch coordinates / redraw), without touching the DOM.
   */
  function handlePortClick(state, id, type) {
    var activeId = state.activeId, activeType = state.activeType;
    if (!activeId) {
      return { activeId: id, activeType: type, pendingAdd: state.pendingAdd, pendingRemove: state.pendingRemove, connected: false };
    }
    if (activeId === id) {
      return { activeId: null, activeType: null, pendingAdd: state.pendingAdd, pendingRemove: state.pendingRemove, connected: false };
    }
    if (activeType === type) {
      return { activeId: id, activeType: type, pendingAdd: state.pendingAdd, pendingRemove: state.pendingRemove, connected: false };
    }
    var adultId = type === 'adult' ? id : activeId;
    var scoutId = type === 'scout' ? id : activeId;
    var next = toggleLine(state.relationships, state.pendingAdd, state.pendingRemove, adultId, scoutId);
    return { activeId: null, activeType: null, pendingAdd: next.pendingAdd, pendingRemove: next.pendingRemove, connected: true };
  }

  return {
    displayName: displayName,
    displaySub: displaySub,
    chipLabel: chipLabel,
    isIncomplete: isIncomplete,
    splitByUserType: splitByUserType,
    relsFor: relsFor,
    isRelSaved: isRelSaved,
    toggleLine: toggleLine,
    handlePortClick: handlePortClick
  };
});
