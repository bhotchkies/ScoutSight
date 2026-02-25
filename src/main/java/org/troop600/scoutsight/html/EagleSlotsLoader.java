package org.troop600.scoutsight.html;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads eagle-required merit badge slot definitions from a JSON file.
 *
 * <p>Expected schema:
 * <pre>
 * {
 *   "slots": [
 *     { "num": 1, "label": "First Aid", "badges": ["First Aid MB"] },
 *     { "num": 8, "label": "Emergency Preparedness or Lifesaving",
 *                "badges": ["Emergency Preparedness MB", "Lifesaving MB"] },
 *     ...
 *   ]
 * }
 * </pre>
 */
class EagleSlotsLoader {

    /** Matches a single slot object: { ... } where content has no nested braces. */
    private static final Pattern SLOT_OBJECT = Pattern.compile("\\{([^{}]+)\\}");
    private static final Pattern NUM          = Pattern.compile("\"num\"\\s*:\\s*(\\d+)");
    private static final Pattern LABEL        = Pattern.compile("\"label\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern BADGES_ARRAY = Pattern.compile("\"badges\"\\s*:\\s*\\[([^\\]]+)\\]");
    private static final Pattern QUOTED_STR   = Pattern.compile("\"([^\"]+)\"");

    static List<EagleSlot> load(Path path) throws IOException {
        String json = ResourceIO.readString(path);
        List<EagleSlot> slots = new ArrayList<>();

        int slotsStart = json.indexOf("\"slots\"");
        if (slotsStart < 0) return slots;

        Matcher slotMatcher = SLOT_OBJECT.matcher(json.substring(slotsStart));
        while (slotMatcher.find()) {
            String obj = slotMatcher.group(1);

            Matcher numM = NUM.matcher(obj);
            if (!numM.find()) continue;
            int num = Integer.parseInt(numM.group(1));

            Matcher labelM = LABEL.matcher(obj);
            if (!labelM.find()) continue;
            String label = labelM.group(1);

            List<String> badges = new ArrayList<>();
            Matcher badgesArrayM = BADGES_ARRAY.matcher(obj);
            if (badgesArrayM.find()) {
                Matcher badgeM = QUOTED_STR.matcher(badgesArrayM.group(1));
                while (badgeM.find()) badges.add(badgeM.group(1));
            }

            slots.add(new EagleSlot(num, label, List.copyOf(badges)));
        }

        slots.sort(Comparator.comparingInt(EagleSlot::num));
        return slots;
    }
}
