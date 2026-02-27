package org.troop600.scoutsight.html;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads a camp schedule JSON file (e.g. {@code config/camps/camp_parsons_schedule.json})
 * into a {@link CampSchedule}.
 *
 * <p>Expected schema:
 * <pre>
 * {
 *   "campName": "Camp Parsons",
 *   "dailyClasses": [
 *     {
 *       "meritBadges": ["Canoeing MB"],
 *       "sessions": [
 *         { "start": "9:00", "end": "10:30" },
 *         { "start": "10:30", "end": "12:00" }
 *       ]
 *     }
 *   ]
 * }
 * </pre>
 *
 * <p>Uses brace-depth extraction (not a flat regex) because each dailyClass object
 * contains a nested sessions array, which would break a simple {@code [^{}]+} pattern.
 */
class CampScheduleLoader {

    private static final Pattern CAMP_NAME =
            Pattern.compile("\"campName\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern QUOTED_STR = Pattern.compile("\"([^\"]+)\"");
    private static final Pattern START_TIME =
            Pattern.compile("\"start\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern END_TIME =
            Pattern.compile("\"end\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern DAY_VAL =
            Pattern.compile("\"day\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern TIME_VAL =
            Pattern.compile("\"time\"\\s*:\\s*\"([^\"]+)\"");

    static CampSchedule load(Path path) throws IOException {
        String json = ResourceIO.readString(path);

        String campName = path.getFileName().toString();
        Matcher nm = CAMP_NAME.matcher(json);
        if (nm.find()) campName = nm.group(1);

        List<CampSchedule.DailyClass> classes = new ArrayList<>();

        int dcStart = json.indexOf("\"dailyClasses\"");
        if (dcStart >= 0) {
            int arrOpen = json.indexOf('[', dcStart);
            if (arrOpen >= 0) {
                int arrClose = findMatchingBracket(json, arrOpen);
                String arrContent = (arrClose > 0)
                        ? json.substring(arrOpen + 1, arrClose)
                        : json.substring(arrOpen + 1);

                for (String classBody : extractTopLevelObjectBodies(arrContent)) {
                    List<String> mbNames = new ArrayList<>();
                    int mbsKey = classBody.indexOf("\"meritBadges\"");
                    if (mbsKey >= 0) {
                        int mbsOpen = classBody.indexOf('[', mbsKey);
                        if (mbsOpen >= 0) {
                            int mbsClose = findMatchingBracket(classBody, mbsOpen);
                            String mbsContent = (mbsClose > 0)
                                    ? classBody.substring(mbsOpen + 1, mbsClose)
                                    : classBody.substring(mbsOpen + 1);
                            Matcher qm = QUOTED_STR.matcher(mbsContent);
                            while (qm.find()) mbNames.add(qm.group(1));
                        }
                    }
                    List<String> ranks = new ArrayList<>();
                    int ranksKey = classBody.indexOf("\"ranks\"");
                    if (ranksKey >= 0) {
                        int ranksOpen = classBody.indexOf('[', ranksKey);
                        if (ranksOpen >= 0) {
                            int ranksClose = findMatchingBracket(classBody, ranksOpen);
                            String ranksContent = (ranksClose > 0)
                                    ? classBody.substring(ranksOpen + 1, ranksClose)
                                    : classBody.substring(ranksOpen + 1);
                            Matcher qm = QUOTED_STR.matcher(ranksContent);
                            while (qm.find()) ranks.add(qm.group(1));
                        }
                    }

                    if (mbNames.isEmpty() && ranks.isEmpty()) continue;

                    List<CampSchedule.TimeSlot> sessions = new ArrayList<>();
                    int sessStart = classBody.indexOf("\"sessions\"");
                    if (sessStart >= 0) {
                        int sessArrOpen = classBody.indexOf('[', sessStart);
                        if (sessArrOpen >= 0) {
                            int sessArrClose = findMatchingBracket(classBody, sessArrOpen);
                            String sessContent = (sessArrClose > 0)
                                    ? classBody.substring(sessArrOpen + 1, sessArrClose)
                                    : classBody.substring(sessArrOpen + 1);
                            for (String sessBody : extractTopLevelObjectBodies(sessContent)) {
                                Matcher sm = START_TIME.matcher(sessBody);
                                Matcher em = END_TIME.matcher(sessBody);
                                if (sm.find() && em.find()) {
                                    sessions.add(new CampSchedule.TimeSlot(
                                            sm.group(1), em.group(1)));
                                }
                            }
                        }
                    }
                    classes.add(new CampSchedule.DailyClass(
                            List.copyOf(mbNames), List.copyOf(ranks), List.copyOf(sessions)));
                }
            }
        }

        List<CampSchedule.FreeTimeClass> freeClasses = new ArrayList<>();

        int ftStart = json.indexOf("\"freeTimeClasses\"");
        if (ftStart >= 0) {
            int ftArrOpen = json.indexOf('[', ftStart);
            if (ftArrOpen >= 0) {
                int ftArrClose = findMatchingBracket(json, ftArrOpen);
                String ftArrContent = (ftArrClose > 0)
                        ? json.substring(ftArrOpen + 1, ftArrClose)
                        : json.substring(ftArrOpen + 1);

                for (String body : extractTopLevelObjectBodies(ftArrContent)) {
                    List<String> mbNames = new ArrayList<>();
                    int mbsKey = body.indexOf("\"meritBadges\"");
                    if (mbsKey >= 0) {
                        int mbsOpen = body.indexOf('[', mbsKey);
                        if (mbsOpen >= 0) {
                            int mbsClose = findMatchingBracket(body, mbsOpen);
                            String mbsContent = (mbsClose > 0)
                                    ? body.substring(mbsOpen + 1, mbsClose)
                                    : body.substring(mbsOpen + 1);
                            Matcher qm = QUOTED_STR.matcher(mbsContent);
                            while (qm.find()) mbNames.add(qm.group(1));
                        }
                    }
                    if (mbNames.isEmpty()) continue;

                    Matcher dm = DAY_VAL.matcher(body);
                    Matcher tm = TIME_VAL.matcher(body);
                    if (!dm.find() || !tm.find()) continue;

                    freeClasses.add(new CampSchedule.FreeTimeClass(
                            List.copyOf(mbNames), dm.group(1), tm.group(1)));
                }
            }
        }

        return new CampSchedule(campName, classes, freeClasses);
    }

    /**
     * Returns the index of the {@code ']'} that matches the {@code '['} at {@code openPos},
     * respecting string literals and nested brackets.  Returns -1 if no match is found.
     */
    private static int findMatchingBracket(String s, int openPos) {
        int depth = 0;
        boolean inString = false;
        for (int i = openPos; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inString) {
                if (c == '\\') i++;          // skip escaped character
                else if (c == '"') inString = false;
            } else {
                if      (c == '"') inString = true;
                else if (c == '[') depth++;
                else if (c == ']') { if (--depth == 0) return i; }
            }
        }
        return -1;
    }

    /**
     * Extracts the bodies (content between outer braces) of all top-level {@code {...}}
     * objects within the given JSON array body string, respecting nesting and string escaping.
     */
    private static List<String> extractTopLevelObjectBodies(String s) {
        List<String> result = new ArrayList<>();
        int depth = 0;
        boolean inString = false;
        int objStart = -1;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inString) {
                if (c == '\\') i++;
                else if (c == '"') inString = false;
            } else {
                if (c == '"') {
                    inString = true;
                } else if (c == '{') {
                    if (depth == 0) objStart = i;
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0 && objStart >= 0) {
                        // Return the body (between the outer braces, exclusive)
                        result.add(s.substring(objStart + 1, i));
                        objStart = -1;
                    }
                }
            }
        }
        return result;
    }
}
