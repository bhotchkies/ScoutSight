package org.troop600.scoutsight.cli;

import org.troop600.scoutsight.html.HtmlGenerator;
import org.troop600.scoutsight.model.Scout;
import org.troop600.scoutsight.parser.AdvancementParser;
import org.troop600.scoutsight.parser.AdminRosterEntry;
import org.troop600.scoutsight.parser.AdminRosterParser;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Main {

    public static void main(String[] args) throws IOException {
        run(args, Path.of(System.getProperty("user.dir")), System.out);
    }

    public static void run(String[] args, Path workDir, PrintStream log) throws IOException {
        Path csv;
        if (args.length > 0) {
            csv = Path.of(args[0]);
        } else {
            csv = findLatestCsv(Path.of("inputdata"));
        }

        Path adminRosterCsv = (args.length > 1 && !args[1].isEmpty()) ? Path.of(args[1]) : null;
        String campName     = (args.length > 2 && !args[2].isEmpty()) ? args[2] : null;

        log.println("Parsing: " + csv);
        List<Scout> scouts = new AdvancementParser().parse(csv);
        log.printf("Loaded %d scouts%n%n", scouts.size());

        if (adminRosterCsv != null) {
            log.println("Parsing admin roster: " + adminRosterCsv);
            Map<String, AdminRosterEntry> roster = new AdminRosterParser().parse(adminRosterCsv);
            Set<String> matchedIds = new HashSet<>();
            int joined = 0;
            for (Scout s : scouts) {
                AdminRosterEntry entry = roster.get(s.bsaMemberId);
                if (entry != null) {
                    s.patrol      = entry.patrol();
                    s.schoolGrade = entry.schoolGrade();
                    s.joinYear    = entry.joinYear();
                    s.dateJoined  = entry.dateJoined();
                    s.birthYear   = entry.birthYear();
                    s.gender      = entry.gender();
                    s.schoolInfo  = entry.schoolInfo();
                    s.positions   = entry.positions();
                    matchedIds.add(s.bsaMemberId);
                    joined++;
                }
            }
            log.printf("Joined admin roster data for %d/%d scouts%n%n", joined, scouts.size());

            // Roster-only scouts: on the roster but with zero rows in the advancement export
            // (e.g. brand-new Scouts who haven't completed any requirements yet). Without this,
            // AdvancementParser's output is the sole source of the scout list and such scouts
            // never appear anywhere in the generated reports.
            int added = 0;
            for (Map.Entry<String, AdminRosterEntry> e : roster.entrySet()) {
                String bsaNum = e.getKey();
                if (matchedIds.contains(bsaNum)) continue;
                AdminRosterEntry entry = e.getValue();
                Scout s = new Scout(bsaNum, entry.firstName(), "", entry.lastName(),
                    new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
                s.patrol      = entry.patrol();
                s.schoolGrade = entry.schoolGrade();
                s.joinYear    = entry.joinYear();
                s.dateJoined  = entry.dateJoined();
                s.birthYear   = entry.birthYear();
                s.gender      = entry.gender();
                s.schoolInfo  = entry.schoolInfo();
                s.positions   = entry.positions();
                scouts.add(s);
                added++;
            }
            if (added > 0) {
                log.printf("Added %d roster-only scout(s) with no advancement data yet%n%n", added);
            }
        }

        for (Scout s : scouts) {
            log.printf("  %-30s  ranks: %2d  merit badges: %3d  awards: %3d%n",
                s.displayName(),
                s.ranks.size(),
                s.meritBadges.size(),
                s.awards.size());
        }

        HtmlGenerator.generate(scouts, csv, campName, workDir);
    }

    private static Path findLatestCsv(Path dir) throws IOException {
        try (var stream = Files.list(dir)) {
            return stream
                .filter(p -> p.getFileName().toString().contains("_Advancement_"))
                .max(Comparator.comparingLong(p -> p.toFile().lastModified()))
                .orElseThrow(() -> new IOException("No Advancement CSV found in " + dir));
        }
    }
}
