package org.troop600.scoutsight.html;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads {@code config/definitions/rank_definitions.json} and produces a flat
 * JSON lookup for use as a JavaScript constant in templates:
 * <pre>{"Scout Rank":{"1a":"text..."},...}</pre>
 */
class RankDefinitionsLoader {

    /** Matches the opening of a named rank block, e.g. "Scout Rank": { */
    private static final Pattern RANK_KEY = Pattern.compile(
            "\"((?:Scout|Tenderfoot|Second Class|First Class|Star Scout|Life Scout|Eagle Scout) Rank)\"\\s*:\\s*\\{");

    /** Matches a requirement id/text pair within a requirements array element. */
    private static final Pattern REQ_PAIR = Pattern.compile(
            "\"id\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"text\"\\s*:\\s*\"([^\"]*)\"");

    static String loadAsJson(Path path) throws IOException {
        if (!ResourceIO.exists(path)) return "{}";
        String raw = ResourceIO.readString(path);

        JsonBuilder jb = new JsonBuilder();
        jb.obj();

        Matcher rankMatcher = RANK_KEY.matcher(raw);
        while (rankMatcher.find()) {
            String rankName = rankMatcher.group(1);
            int blockStart = rankMatcher.end() - 1;   // position of '{'
            int blockEnd = findMatchingBrace(raw, blockStart);
            if (blockEnd < 0) continue;

            String rankBlock = raw.substring(blockStart + 1, blockEnd);
            jb.obj(rankName);
            Matcher reqMatcher = REQ_PAIR.matcher(rankBlock);
            while (reqMatcher.find()) {
                jb.field(reqMatcher.group(1), reqMatcher.group(2));
            }
            jb.endObj();
        }

        jb.endObj();
        return jb.toString();
    }

    /**
     * Returns rank name → ordered list of [id, text] pairs, preserving JSON order.
     * Used by pages that need to iterate all defined requirements in sequence.
     */
    static LinkedHashMap<String, List<String[]>> loadOrdered(Path path) throws IOException {
        if (!ResourceIO.exists(path)) return new LinkedHashMap<>();
        String raw = ResourceIO.readString(path);
        LinkedHashMap<String, List<String[]>> result = new LinkedHashMap<>();
        Matcher rankMatcher = RANK_KEY.matcher(raw);
        while (rankMatcher.find()) {
            String rankName = rankMatcher.group(1);
            int blockStart = rankMatcher.end() - 1;
            int blockEnd = findMatchingBrace(raw, blockStart);
            if (blockEnd < 0) continue;
            String rankBlock = raw.substring(blockStart + 1, blockEnd);
            List<String[]> reqs = new ArrayList<>();
            Matcher reqMatcher = REQ_PAIR.matcher(rankBlock);
            while (reqMatcher.find()) reqs.add(new String[]{ reqMatcher.group(1), reqMatcher.group(2) });
            result.put(rankName, reqs);
        }
        return result;
    }

    /** Returns the index of the '}' that closes the '{' at {@code openPos}. */
    private static int findMatchingBrace(String s, int openPos) {
        int depth = 0;
        for (int i = openPos; i < s.length(); i++) {
            if (s.charAt(i) == '{') depth++;
            else if (s.charAt(i) == '}') {
                if (--depth == 0) return i;
            }
        }
        return -1;
    }
}
