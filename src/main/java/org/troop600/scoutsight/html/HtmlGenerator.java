package org.troop600.scoutsight.html;

import org.troop600.scoutsight.model.Scout;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Entry point for HTML report generation.
 *
 * <p>Call {@link #generate(List, Path, String)} after parsing to produce a self-contained
 * static HTML site under {@code output/<csv-stem>/}.
 * Pass {@code null} for {@code campName} to omit camp-specific HTML.
 */
public class HtmlGenerator {

    public static void generate(List<Scout> scouts, Path csvPath, String campName) throws IOException {
        String filename = csvPath.getFileName().toString();
        String stem = filename.endsWith(".csv")
                ? filename.substring(0, filename.length() - 4)
                : filename;

        Path outputDir = Path.of("output", stem);
        Files.createDirectories(outputDir);
        ResourceIO.copyDirectory(Path.of("templates", "images"), outputDir.resolve("images"));

        boolean hasPatrolData = scouts.stream()
                .anyMatch(s -> s.patrol != null && !s.patrol.isBlank());
        ThymeleafRenderer.setHasPatrolPage(hasPatrolData);

        List<CampConfig> camps = loadCampConfigs(campName);
        List<RequirementCategory> categories = loadRequirementCategories();
        List<EagleSlot> eagleSlots = loadEagleSlots();
        String rankDefsJson = loadRankDefinitions();
        java.util.Map<String, java.util.Map<String, String>> mbDefs = loadMBDefinitions();
        IndexPageWriter.write(scouts, outputDir, stem);
        ScoutPageWriter.write(scouts, outputDir, stem, camps, eagleSlots, rankDefsJson);
        LinkedHashMap<String, List<String[]>> rankDefsOrdered = loadRankDefinitionsOrdered();
        TrailToFirstClassPageWriter.write(scouts, camps, categories, rankDefsOrdered, outputDir, stem);
        java.util.Map<String, String> badgeLinks = EagleMBDetailPageWriter.write(scouts, eagleSlots, mbDefs, outputDir);
        EagleMBSummaryPageWriter.write(scouts, eagleSlots, camps, badgeLinks, outputDir, stem);
        HelpPageWriter.write(outputDir);
        if (hasPatrolData) PatrolBalancingPageWriter.write(scouts, outputDir, stem);
        System.out.println("HTML output written to: output/" + stem + "/");
    }

    private static List<CampConfig> loadCampConfigs(String campName) throws IOException {
        if (campName == null) return List.of();
        String needle = campName.toLowerCase();
        List<Path> jsonFiles = ResourceIO.listDirectory(Path.of("config", "camps"), ".json")
                .stream()
                .filter(p -> p.getFileName().toString().toLowerCase().contains(needle))
                .toList();
        if (jsonFiles.isEmpty()) {
            System.err.println("Warning: no camp config file found matching '" + campName + "' in config/camps/");
        }
        List<CampConfig> camps = new ArrayList<>();
        for (Path p : jsonFiles) {
            camps.add(CampConfigLoader.load(p));
        }
        return camps;
    }

    private static List<EagleSlot> loadEagleSlots() throws IOException {
        Path file = Path.of("config", "mb-categories", "eagle-slots.json");
        if (!ResourceIO.exists(file)) return List.of();
        return EagleSlotsLoader.load(file);
    }

    private static java.util.Map<String, java.util.Map<String, String>> loadMBDefinitions() throws IOException {
        Path mbFile = Path.of("config", "definitions", "mb_definitions.json");
        return MBDefinitionsParser.parse(mbFile);
    }

    private static LinkedHashMap<String, List<String[]>> loadRankDefinitionsOrdered() throws IOException {
        return RankDefinitionsLoader.loadOrdered(Path.of("config", "definitions", "rank_definitions.json"));
    }

    private static String loadRankDefinitions() throws IOException {
        Path rankFile = Path.of("config", "definitions", "rank_definitions.json");
        Path mbFile   = Path.of("config", "definitions", "mb_definitions.json");
        String rankJson = RankDefinitionsLoader.loadAsJson(rankFile);
        String mbJson   = MBDefinitionsLoader.loadAsJson(mbFile);
        return mergeJsonObjects(rankJson, mbJson);
    }

    /** Merges two flat JSON objects ({...}) by concatenating their entries. */
    private static String mergeJsonObjects(String a, String b) {
        String at = a.trim();
        String bt = b.trim();
        if (bt.equals("{}")) return at;
        if (at.equals("{}")) return bt;
        String aInner = at.substring(1, at.length() - 1).trim();
        String bInner = bt.substring(1, bt.length() - 1).trim();
        if (aInner.isEmpty()) return bt;
        if (bInner.isEmpty()) return at;
        return "{" + aInner + "," + bInner + "}";
    }

    private static List<RequirementCategory> loadRequirementCategories() throws IOException {
        List<RequirementCategory> categories = new ArrayList<>();
        for (Path p : ResourceIO.listDirectory(Path.of("config", "requirement-categories"), ".json")) {
            categories.add(RequirementCategoryLoader.load(p));
        }
        return categories;
    }
}
