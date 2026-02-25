package org.troop600.scoutsight.html;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Parses {@code config/definitions/mb_definitions.json} into a two-level map:
 * badge name → (requirementId → requirementText).
 *
 * <p>The file format is a flat JSON object two levels deep:
 * <pre>{"First Aid MB":{"1a":"text...","1b":"text..."},...}</pre>
 *
 * <p>No external JSON library is used — parsing is done with a character-level
 * state machine that properly handles escaped characters inside strings.
 */
class MBDefinitionsParser {

    /**
     * Returns a map of badge name → (requirementId → requirementText).
     * Returns an empty map if the file does not exist.
     */
    static Map<String, Map<String, String>> parse(Path path) throws IOException {
        if (!ResourceIO.exists(path)) return Map.of();
        String json = ResourceIO.readString(path);
        Map<String, Map<String, String>> result = new LinkedHashMap<>();

        int i = skipWhitespace(json, 0);
        if (i >= json.length() || json.charAt(i) != '{') return result;
        i++; // consume '{'

        while (i < json.length()) {
            i = skipWhitespace(json, i);
            if (i >= json.length()) break;
            char c = json.charAt(i);
            if (c == '}') break;
            if (c == ',') { i++; continue; }
            if (c != '"') { i++; continue; }

            // Read badge name key
            int[] keyRange = readString(json, i);
            String badgeName = unescapeJson(json.substring(keyRange[0], keyRange[1]));
            i = keyRange[2];

            i = skipWhitespace(json, i);
            if (i >= json.length() || json.charAt(i) != ':') continue;
            i++; // consume ':'
            i = skipWhitespace(json, i);
            if (i >= json.length() || json.charAt(i) != '{') continue;

            // Read the inner object
            int[] innerRange = readObject(json, i);
            String inner = json.substring(i + 1, innerRange[0]);
            result.put(badgeName, parseInnerObject(inner));
            i = innerRange[1];
        }

        return result;
    }

    /**
     * Returns true if the given requirementId key is a "parent" grouping —
     * i.e., a pure number that has at least one child key starting with that
     * number followed by a letter (e.g., "1" when "1a" and "1b" also exist).
     */
    static boolean isParentRequirement(String reqId, Set<String> allKeys) {
        if (!reqId.matches("\\d+")) return false;
        for (String k : allKeys) {
            if (k.length() > reqId.length()
                    && k.startsWith(reqId)
                    && Character.isLetter(k.charAt(reqId.length()))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Compares two requirement IDs in natural BSA order:
     * numeric prefix ascending, then letter suffix ascending.
     * Examples: "1" < "1a" < "1b" < "2" < "10" < "10a"
     */
    static int compareReqIds(String a, String b) {
        int numA = numericPrefix(a);
        int numB = numericPrefix(b);
        if (numA != numB) return Integer.compare(numA, numB);
        String suffA = a.substring(Integer.toString(numA).length());
        String suffB = b.substring(Integer.toString(numB).length());
        return suffA.compareTo(suffB);
    }

    // -------------------------------------------------------------------------

    private static Map<String, String> parseInnerObject(String json) {
        Map<String, String> result = new LinkedHashMap<>();
        int i = 0;
        while (i < json.length()) {
            i = skipWhitespace(json, i);
            if (i >= json.length()) break;
            char c = json.charAt(i);
            if (c == '}') break;
            if (c == ',') { i++; continue; }
            if (c != '"') { i++; continue; }

            int[] keyRange = readString(json, i);
            String key = unescapeJson(json.substring(keyRange[0], keyRange[1]));
            i = keyRange[2];

            i = skipWhitespace(json, i);
            if (i >= json.length() || json.charAt(i) != ':') continue;
            i++; // consume ':'
            i = skipWhitespace(json, i);
            if (i >= json.length() || json.charAt(i) != '"') continue;

            int[] valRange = readString(json, i);
            String value = unescapeJson(json.substring(valRange[0], valRange[1]));
            result.put(key, value);
            i = valRange[2];
        }
        return result;
    }

    /**
     * Reads a JSON string starting at position {@code start} (which must point
     * at the opening {@code "}).
     *
     * @return int[3]: [contentStart, contentEnd, nextPos]
     *         where json.substring(contentStart, contentEnd) is the raw content
     *         (without enclosing quotes), and nextPos is just past the closing ".
     */
    private static int[] readString(String json, int start) {
        // start points at the opening "
        int i = start + 1;
        int contentStart = i;
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == '\\') {
                i += 2; // skip escaped character
            } else if (c == '"') {
                return new int[]{contentStart, i, i + 1};
            } else {
                i++;
            }
        }
        return new int[]{contentStart, i, i};
    }

    /**
     * Reads from the opening '{' at {@code start} to its matching '}'.
     * Returns int[2]: [innerEndExclusive, nextPos].
     */
    private static int[] readObject(String json, int start) {
        int depth = 0;
        boolean inString = false;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (inString) {
                if (c == '\\') i++; // skip escaped
                else if (c == '"') inString = false;
            } else {
                if (c == '"') inString = true;
                else if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) return new int[]{i, i + 1};
                }
            }
        }
        return new int[]{json.length(), json.length()};
    }

    private static int skipWhitespace(String json, int i) {
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
        return i;
    }

    private static String unescapeJson(String s) {
        if (s.indexOf('\\') < 0) return s; // fast path
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char next = s.charAt(i + 1);
                switch (next) {
                    case '"'  -> { sb.append('"');  i++; }
                    case '\\'  -> { sb.append('\\'); i++; }
                    case 'n'  -> { sb.append('\n'); i++; }
                    case 'r'  -> { sb.append('\r'); i++; }
                    case 't'  -> { sb.append('\t'); i++; }
                    default   -> sb.append(c);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static int numericPrefix(String s) {
        int i = 0;
        while (i < s.length() && Character.isDigit(s.charAt(i))) i++;
        if (i == 0) return 0;
        try { return Integer.parseInt(s.substring(0, i)); }
        catch (NumberFormatException e) { return 0; }
    }
}
