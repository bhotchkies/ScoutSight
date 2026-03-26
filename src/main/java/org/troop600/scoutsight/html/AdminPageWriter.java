package org.troop600.scoutsight.html;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Writes admin.html with the Full Troop Roster CSV embedded as {@code window.ROSTER_CSV}
 * and the admin-console blocked-email list embedded as {@code window.BLOCKED_EMAILS}
 * for client-side filtering of Google Workspace accounts.
 */
class AdminPageWriter {

    private static final Path BLOCKED_EMAILS_FILE =
            Path.of("config", "admin-console", "blocked_emails.json");

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

        // Read the blocked-emails list; default to empty array if file is absent.
        String blockedEmailsJson = "[]";
        try {
            blockedEmailsJson = ResourceIO.readString(BLOCKED_EMAILS_FILE).trim();
        } catch (IOException e) {
            System.err.println("Warning: could not read blocked_emails.json: " + e.getMessage());
        }

        String html = ThymeleafRenderer.render("admin",
                Map.of("rosterCsvData", escaped, "blockedEmailsJson", blockedEmailsJson));
        Files.writeString(outputDir.resolve("admin.html"), html);
    }
}
