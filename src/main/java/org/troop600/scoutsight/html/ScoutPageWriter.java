package org.troop600.scoutsight.html;

import org.troop600.scoutsight.model.AdvancementItem;
import org.troop600.scoutsight.model.Requirement;
import org.troop600.scoutsight.model.Scout;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds and writes {@code output/<stem>/scout_<memberId>.html} — one page per scout.
 */
class ScoutPageWriter {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MM/dd/yyyy");

    private record CampMBEntry(String name, boolean eagleRequired, int pct) {}

    /** Ranks covered by summer camp configs (Scout through First Class). */
    private static final String[] CAMP_RANK_NAMES = {
        "Scout Rank", "Tenderfoot Rank", "Second Class Rank", "First Class Rank"
    };

    static void write(List<Scout> scouts, Path outputDir, String stem,
                      List<CampConfig> camps, List<EagleSlot> eagleSlots,
                      String rankDefsJson) throws IOException {
        for (Scout scout : scouts) {
            writeScout(scout, outputDir, stem, camps, eagleSlots, rankDefsJson);
        }
    }

    private static void writeScout(Scout scout, Path outputDir, String stem,
                                   List<CampConfig> camps, List<EagleSlot> eagleSlots,
                                   String rankDefsJson) throws IOException {
        String json = buildJson(scout, stem, camps, eagleSlots);
        String html = ThymeleafRenderer.render("scout", Map.of("scoutJson", json, "rankDefs", rankDefsJson));
        Files.writeString(outputDir.resolve("scout_" + scout.bsaMemberId + ".html"), html);
    }

    // -------------------------------------------------------------------------
    // JSON building

    private static String buildJson(Scout scout, String stem,
                                    List<CampConfig> camps, List<EagleSlot> eagleSlots) {
        Set<String> eagleRequiredMBs = eagleSlots.stream()
                .flatMap(s -> s.badgeNames().stream())
                .collect(java.util.stream.Collectors.toSet());
        JsonBuilder jb = new JsonBuilder();
        jb.obj()
          .field("title", scout.displayName())
          .field("memberId", scout.bsaMemberId)
          .field("stem", stem);

        // Roster info (null fields if no roster report was loaded)
        boolean hasRosterInfo = scout.patrol != null || scout.birthYear != null;
        if (hasRosterInfo) {
            jb.obj("rosterInfo")
              .field("patrol",    scout.patrol    != null ? scout.patrol    : "")
              .field("grade",     scout.schoolGrade)
              .field("schoolInfo", scout.schoolInfo != null ? scout.schoolInfo : "")
              .field("birthYear", scout.birthYear != null ? scout.birthYear : "")
              .field("joinYear",  scout.joinYear  != null ? scout.joinYear  : "")
              .field("gender",    scout.gender    != null ? scout.gender    : "")
              .field("positions", scout.positions != null ? scout.positions : "")
              .endObj();
        }

        // Ranks: canonical order, only started
        jb.arr("ranks");
        for (String rankName : IndexPageWriter.RANK_NAMES) {
            AdvancementItem item = findByName(scout.ranks, rankName);
            if (item != null && isStarted(item)) {
                appendItem(jb, item);
            }
        }
        jb.endArr();

        // Eagle-required MBs: by slot order
        jb.arr("eagleSlots");
        for (EagleSlot slot : eagleSlots) {
            List<AdvancementItem> startedBadges = scout.meritBadges.stream()
                    .filter(item -> slot.badgeNames().contains(item.name))
                    .filter(ScoutPageWriter::isStarted)
                    .toList();
            boolean slotComplete = startedBadges.stream().anyMatch(AdvancementItem::isComplete);
            jb.obj()
              .field("slotNum", slot.num())
              .field("slotLabel", slot.label())
              .field("singleBadge", slot.badgeNames().size() == 1)
              .field("complete", slotComplete);
            jb.arr("badges");
            startedBadges.forEach(item -> appendItem(jb, item));
            jb.endArr()
              .endObj();
        }
        jb.endArr();

        // Elective MBs: alphabetical, started
        jb.arr("electiveMBs");
        scout.meritBadges.stream()
                .filter(item -> !eagleRequiredMBs.contains(item.name))
                .filter(ScoutPageWriter::isStarted)
                .sorted(Comparator.comparing(item -> item.name))
                .forEach(item -> appendItem(jb, item));
        jb.endArr();

        // Awards: alphabetical, started
        jb.arr("awards");
        scout.awards.stream()
                .filter(ScoutPageWriter::isStarted)
                .sorted(Comparator.comparing(item -> item.name))
                .forEach(item -> appendItem(jb, item));
        jb.endArr();

        // Camp data: one entry per camp config
        jb.arr("camps");
        for (CampConfig camp : camps) {
            appendCampData(jb, scout, camp, eagleRequiredMBs);
        }
        jb.endArr();

        jb.endObj();
        return jb.toString();
    }

    private static void appendCampData(JsonBuilder jb, Scout scout, CampConfig camp,
                                        Set<String> eagleRequiredMBs) {
        jb.obj().field("campName", camp.campName);
        jb.arr("ranks");

        for (String rankName : CAMP_RANK_NAMES) {
            AdvancementItem item = findByName(scout.ranks, rankName);
            if (item == null) continue;

            if (item.isComplete()) continue; // rank earned → no outstanding requirements

            Set<String> campReqs = camp.rankCoverage.getOrDefault(rankName, Set.of());

            List<Requirement> incomplete = item.requirements.stream()
                    .filter(r -> r.requirementId != null && !r.requirementId.isBlank())
                    .filter(r -> r.dateCompleted == null)
                    .sorted(new ReqIdComparator())
                    .toList();

            List<Requirement> atCamp = incomplete.stream()
                    .filter(r -> campReqs.contains(r.requirementId.toLowerCase()))
                    .toList();
            List<Requirement> atMeetings = incomplete.stream()
                    .filter(r -> !campReqs.contains(r.requirementId.toLowerCase()))
                    .toList();

            if (atCamp.isEmpty() && atMeetings.isEmpty()) continue;

            jb.obj().field("rankName", rankName);
            jb.arr("atCamp");
            for (Requirement r : atCamp) jb.obj().field("id", r.requirementId).endObj();
            jb.endArr();
            jb.arr("atMeetings");
            for (Requirement r : atMeetings) jb.obj().field("id", r.requirementId).endObj();
            jb.endArr();
            jb.endObj();
        }

        jb.endArr();

        // Camp merit badges: offered by camp, not yet earned by scout
        Set<String> scoutMBNames = scout.meritBadges.stream()
                .map(item -> item.name)
                .collect(java.util.stream.Collectors.toSet());

        List<CampMBEntry> campMBs = new ArrayList<>();
        for (String mbName : camp.meritBadges) {
            if (!scoutMBNames.contains(mbName)) {
                campMBs.add(new CampMBEntry(mbName, eagleRequiredMBs.contains(mbName), 0));
            }
        }
        for (AdvancementItem item : scout.meritBadges) {
            if (camp.meritBadges.contains(item.name) && !item.isComplete()) {
                long done = item.requirements.stream().filter(Requirement::isComplete).count();
                int total = item.requirements.size();
                int pct = total == 0 ? 0 : (int) Math.round(done * 100.0 / total);
                campMBs.add(new CampMBEntry(item.name, eagleRequiredMBs.contains(item.name), pct));
            }
        }
        campMBs.sort(Comparator
                .comparingInt((CampMBEntry e) -> e.pct() > 0 ? 1 : 0)
                .thenComparingInt(e -> e.eagleRequired() ? 0 : 1)
                .thenComparing(CampMBEntry::name));

        jb.arr("meritBadges");
        for (CampMBEntry mb : campMBs) {
            jb.obj()
              .field("name", mb.name().replace(" MB", ""))
              .field("eagleRequired", mb.eagleRequired())
              .field("pct", mb.pct())
              .endObj();
        }
        jb.endArr();

        jb.endObj();
    }

    private static boolean isStarted(AdvancementItem item) {
        return item.isComplete() || item.requirements.stream().anyMatch(r -> r.dateCompleted != null);
    }

    private static AdvancementItem findByName(List<AdvancementItem> items, String name) {
        for (AdvancementItem item : items) {
            if (name.equals(item.name)) return item;
        }
        return null;
    }

    private static void appendItem(JsonBuilder jb, AdvancementItem item) {
        jb.obj()
          .field("name", item.name)
          .field("completed", item.isComplete());
        if (item.isComplete()) {
            jb.field("dateCompleted", item.dateCompleted.format(DATE_FMT));
        }

        List<Requirement> sortedReqs = item.requirements.stream()
                .filter(r -> r.requirementId != null && !r.requirementId.isBlank())
                .sorted(new ReqIdComparator())
                .toList();

        boolean parentComplete = item.isComplete();
        jb.arr("requirements");
        for (Requirement req : sortedReqs) {
            boolean reqComplete = parentComplete || req.isComplete();
            java.time.LocalDate reqDate = req.isComplete() ? req.dateCompleted
                                        : parentComplete   ? item.dateCompleted
                                        : null;
            jb.obj()
              .field("id", req.requirementId)
              .field("completed", reqComplete);
            if (reqDate != null) {
                jb.field("dateCompleted", reqDate.format(DATE_FMT));
            }
            jb.endObj();
        }
        jb.endArr()
          .endObj();
    }

    // -------------------------------------------------------------------------
    // Requirement sorting

    private static class ReqIdComparator implements Comparator<Requirement> {
        @Override
        public int compare(Requirement a, Requirement b) {
            int[] la = extractLeadingInt(a.requirementId);
            int[] lb = extractLeadingInt(b.requirementId);
            if (la == null && lb == null) return a.requirementId.compareTo(b.requirementId);
            if (la == null) return 1;   // prose-only sorts after all numeric
            if (lb == null) return -1;
            if (la[0] != lb[0]) return Integer.compare(la[0], lb[0]);
            return a.requirementId.substring(la[1]).compareTo(b.requirementId.substring(lb[1]));
        }
    }

    /** Returns [value, endIndex] for the leading integer in {@code id}, or null if no leading digit. */
    private static int[] extractLeadingInt(String id) {
        int i = 0;
        while (i < id.length() && Character.isDigit(id.charAt(i))) i++;
        if (i == 0) return null;
        return new int[]{ Integer.parseInt(id.substring(0, i)), i };
    }
}
