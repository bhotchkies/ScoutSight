package org.troop600.scoutsight.html;

import java.util.List;

/**
 * One Eagle-required merit badge slot. A slot is satisfied when any badge in
 * {@code badgeNames} is completed.
 */
record EagleSlot(int num, String label, List<String> badgeNames) {}
