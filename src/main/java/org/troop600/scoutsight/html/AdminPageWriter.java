package org.troop600.scoutsight.html;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Writes admin.html with the Full Troop Roster CSV embedded as {@code window.ROSTER_CSV}
 * for client-side matching against Google Workspace accounts.
 */
class AdminPageWriter {

    static void write(Path outputDir, String rosterCsvPath) throws IOException {
        String csvContent = "";
        if (rosterCsvPath != null) {
            try {
                csvContent = Files.readString(Path.of(rosterCsvPath));
            } catch (IOException e) {
                System.err.println("Warning: could not read admin roster CSV: " + e.getMessage());
            }
        }
        // Escape for embedding in a JS template literal: backslash, backtick, and ${ sequences.
        String escaped = csvContent
                .replace("\\", "\\\\")
                .replace("`", "\\`")
                .replace("${", "\\${");
        String html = ThymeleafRenderer.render("admin", Map.of("rosterCsvData", escaped));
        Files.writeString(outputDir.resolve("admin.html"), html);
    }
}
