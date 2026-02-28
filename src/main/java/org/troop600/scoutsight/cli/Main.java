package org.troop600.scoutsight.cli;

import org.troop600.scoutsight.html.HtmlGenerator;
import org.troop600.scoutsight.model.Scout;
import org.troop600.scoutsight.parser.AdvancementParser;
import org.troop600.scoutsight.parser.RosterReportEntry;
import org.troop600.scoutsight.parser.RosterReportParser;
import org.troop600.scoutsight.parser.ScoutRosterEntry;
import org.troop600.scoutsight.parser.ScoutRosterParser;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

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

        Path rosterCsv        = (args.length > 1 && !args[1].isEmpty()) ? Path.of(args[1]) : null;
        String campName       = (args.length > 2 && !args[2].isEmpty()) ? args[2] : null;
        Path rosterReportCsv  = (args.length > 3 && !args[3].isEmpty()) ? Path.of(args[3]) : null;

        log.println("Parsing: " + csv);
        List<Scout> scouts = new AdvancementParser().parse(csv);
        log.printf("Loaded %d scouts%n%n", scouts.size());

        if (rosterCsv != null) {
            log.println("Parsing roster: " + rosterCsv);
            Map<String, ScoutRosterEntry> roster = new ScoutRosterParser().parse(rosterCsv);
            int joined = 0;
            for (Scout s : scouts) {
                ScoutRosterEntry entry = roster.get(s.bsaMemberId);
                if (entry != null) {
                    s.patrol      = entry.patrol();
                    s.schoolGrade = entry.schoolGrade();
                    s.dateJoined  = entry.dateJoined();
                    joined++;
                }
            }
            log.printf("Joined roster data for %d/%d scouts%n%n", joined, scouts.size());
        }

        if (rosterReportCsv != null) {
            log.println("Parsing roster report: " + rosterReportCsv);
            Map<String, RosterReportEntry> report = new RosterReportParser().parse(rosterReportCsv);
            int joined = 0;
            for (Scout s : scouts) {
                RosterReportEntry entry = report.get(s.bsaMemberId);
                if (entry != null) {
                    s.patrol     = entry.patrol();
                    s.schoolGrade = entry.schoolGrade();
                    s.joinYear   = entry.joinYear();
                    s.birthYear  = entry.birthYear();
                    s.schoolInfo = entry.schoolInfo();
                    s.gender     = entry.gender();
                    s.positions  = entry.positions();
                    joined++;
                }
            }
            log.printf("Joined roster report data for %d/%d scouts%n%n", joined, scouts.size());
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
