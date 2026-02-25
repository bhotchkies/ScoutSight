package org.troop600.scoutsight.html;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads a requirement-category JSON file into a {@link RequirementCategory}.
 *
 * <p>Handles the specific schema:
 * <pre>
 * {
 *   "categoryName": "Planning Required",
 *   "rankRequirements": {
 *     "Tenderfoot Rank": ["6a","6b",...],
 *     ...
 *   }
 * }
 * </pre>
 */
class RequirementCategoryLoader {

    private static final Pattern CATEGORY_NAME =
            Pattern.compile("\"categoryName\"\\s*:\\s*\"([^\"]+)\"");
    /** Matches  "key": ["val1","val2",...] */
    private static final Pattern RANK_ENTRY =
            Pattern.compile("\"([^\"]+)\"\\s*:\\s*\\[([^\\]]+)\\]");
    private static final Pattern QUOTED_STR =
            Pattern.compile("\"([^\"]+)\"");

    static RequirementCategory load(Path path) throws IOException {
        String json = ResourceIO.readString(path);

        String categoryName = path.getFileName().toString();
        Matcher m = CATEGORY_NAME.matcher(json);
        if (m.find()) categoryName = m.group(1);

        Map<String, Set<String>> rankRequirements = new LinkedHashMap<>();
        int rcStart = json.indexOf("\"rankRequirements\"");
        if (rcStart >= 0) {
            String afterRankReqs = json.substring(rcStart);
            Matcher rm = RANK_ENTRY.matcher(afterRankReqs);
            while (rm.find()) {
                String rankName = rm.group(1);
                Set<String> reqs = new LinkedHashSet<>();
                Matcher em = QUOTED_STR.matcher(rm.group(2));
                while (em.find()) reqs.add(em.group(1).toLowerCase());
                rankRequirements.put(rankName, reqs);
            }
        }

        return new RequirementCategory(categoryName, rankRequirements);
    }
}
