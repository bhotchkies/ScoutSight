package org.troop600.scoutsight.html;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

class HelpPageWriter {

    static void write(Path outputDir) throws IOException {
        String html = ThymeleafRenderer.render("help", Map.of());
        Files.writeString(outputDir.resolve("help.html"), html);
    }
}
