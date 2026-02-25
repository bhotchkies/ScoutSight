package org.troop600.scoutsight.html;

import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Map;

/**
 * Renders Thymeleaf TEXT-mode templates loaded from the {@code templates/} directory.
 *
 * <p>Templates use {@code [(${varName})]} (unescaped inline) to embed values.
 * TEXT mode leaves all non-expression content (HTML, JSX, JS) completely untouched.
 */
class ThymeleafRenderer {

    private static final TemplateEngine ENGINE;

    static {
        StringTemplateResolver resolver = new StringTemplateResolver();
        resolver.setTemplateMode(TemplateMode.TEXT);
        resolver.setCacheable(false);
        ENGINE = new TemplateEngine();
        ENGINE.setTemplateResolver(resolver);
    }

    static String render(String templateName, Map<String, String> variables) {
        String content;
        try {
            content = ResourceIO.readString(Path.of("templates", templateName + ".html"));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot load template: " + templateName, e);
        }
        Context ctx = new Context();
        variables.forEach(ctx::setVariable);
        ctx.setVariable("siteHeader", loadHeader());
        return ENGINE.process(content, ctx);
    }

    private static String loadHeader() {
        try {
            return ResourceIO.readString(Path.of("templates", "_header.html")).trim();
        } catch (IOException e) {
            return "";
        }
    }
}
