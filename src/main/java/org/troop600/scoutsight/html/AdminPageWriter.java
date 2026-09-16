package org.troop600.scoutsight.html;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Writes admin.html with the Full Troop Roster CSV embedded as {@code window.ROSTER_CSV},
 * the admin-console blocked-email list embedded as {@code window.BLOCKED_EMAILS}, the
 * Apps Script Code.gs source injected into the setup UI {@code <pre>} block, and the
 * Assign Relationships tab's pure logic module inlined verbatim into a {@code <script>}
 * block (the same file Node requires directly for its zero-dependency test script).
 */
class AdminPageWriter {

    private static final Path BLOCKED_EMAILS_FILE =
            Path.of("config", "admin-console", "blocked_emails.json");
    private static final Path CODE_GS_FILE =
            Path.of("config", "admin-console", "code.gs");
    private static final Path RELATIONSHIPS_LOGIC_FILE =
            Path.of("config", "admin-console", "relationships_logic.js");

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

        // Read Code.gs and email templates; HTML-escape for embedding in <pre> elements.
        String codeGs = "";
        try {
            codeGs = htmlEscape(ResourceIO.readString(CODE_GS_FILE));
        } catch (IOException e) {
            System.err.println("Warning: could not read code.gs: " + e.getMessage());
        }
        // Read the Relationships tab's pure-logic module; embedded verbatim as JS source
        // (the same file Node requires directly for its zero-dependency test script).
        String relationshipsLogicJs = "";
        try {
            relationshipsLogicJs = ResourceIO.readString(RELATIONSHIPS_LOGIC_FILE);
        } catch (IOException e) {
            System.err.println("Warning: could not read relationships_logic.js: " + e.getMessage());
        }

        String html = ThymeleafRenderer.render("admin",
                Map.of("rosterCsvData", escaped, "blockedEmailsJson", blockedEmailsJson,
                       "codeGsContent", codeGs, "relationshipsLogicJs", relationshipsLogicJs));
        Files.writeString(outputDir.resolve("admin.html"), html);
    }

    private static String htmlEscape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
