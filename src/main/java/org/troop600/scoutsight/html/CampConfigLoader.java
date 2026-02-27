package org.troop600.scoutsight.html;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads a camp config JSON file into a {@link CampConfig}.
 *
 * <p>Handles the specific schema:
 * <pre>
 * {
 *   "campName": "Camp Parsons",
 *   "scheduleUrl": "https://...",
 *   "scheduleParser": "CampParsonsScheduleParser",
 *   "rankCoverage": {
 *     "Scout Rank": ["1a","1b",...],
 *     ...
 *   }
 * }
 * </pre>
 */
public class CampConfigLoader {

    private static final Pattern CAMP_NAME =
            Pattern.compile("\"campName\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern SCHEDULE_URL =
            Pattern.compile("\"scheduleUrl\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern SCHEDULE_PARSER =
            Pattern.compile("\"scheduleParser\"\\s*:\\s*\"([^\"]+)\"");
    /** Matches  "key": ["val1","val2",...] */
    private static final Pattern RANK_ENTRY =
            Pattern.compile("\"([^\"]+)\"\\s*:\\s*\\[([^\\]]+)\\]");
    private static final Pattern QUOTED_STR =
            Pattern.compile("\"([^\"]+)\"");
    /** Matches the top-level "meritBadges": [...] array (may span multiple lines). */
    private static final Pattern MB_ARRAY =
            Pattern.compile("\"meritBadges\"\\s*:\\s*\\[([^\\]]+)\\]", Pattern.DOTALL);

    public static CampConfig load(Path path) throws IOException {
        String json = ResourceIO.readString(path);

        String campName = path.getFileName().toString();
        Matcher m = CAMP_NAME.matcher(json);
        if (m.find()) campName = m.group(1);

        String scheduleUrl = null;
        Matcher su = SCHEDULE_URL.matcher(json);
        if (su.find()) scheduleUrl = su.group(1);

        String scheduleParser = null;
        Matcher sp = SCHEDULE_PARSER.matcher(json);
        if (sp.find()) scheduleParser = sp.group(1);

        Map<String, Set<String>> coverage = new LinkedHashMap<>();
        int rcStart = json.indexOf("\"rankCoverage\"");
        if (rcStart >= 0) {
            String afterRankCoverage = json.substring(rcStart);
            Matcher rm = RANK_ENTRY.matcher(afterRankCoverage);
            while (rm.find()) {
                String rankName = rm.group(1);
                Set<String> reqs = new LinkedHashSet<>();
                Matcher em = QUOTED_STR.matcher(rm.group(2));
                while (em.find()) reqs.add(em.group(1).toLowerCase());
                coverage.put(rankName, reqs);
            }
        }

        List<String> meritBadges = new ArrayList<>();
        Matcher mbm = MB_ARRAY.matcher(json);
        if (mbm.find()) {
            Matcher em = QUOTED_STR.matcher(mbm.group(1));
            while (em.find()) meritBadges.add(em.group(1));
        }

        return new CampConfig(campName, coverage, List.copyOf(meritBadges),
                scheduleUrl, scheduleParser);
    }
}
