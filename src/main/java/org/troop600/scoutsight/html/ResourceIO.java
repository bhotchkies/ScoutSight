package org.troop600.scoutsight.html;

import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;

/**
 * Reads project resources (config files, templates) from the filesystem when
 * available, or directly from the classpath JAR when not.
 *
 * <p>All methods accept relative {@link Path} objects whose string form matches
 * the classpath resource path — e.g. {@code Path.of("config","camps","camp_parsons.json")}
 * maps to classpath resource {@code /config/camps/camp_parsons.json}.
 */
public class ResourceIO {

    private ResourceIO() {}

    /**
     * Returns the content of the resource at {@code path}.
     * Tries the filesystem first; falls back to the classpath.
     *
     * @throws IOException if the resource is found in neither location.
     */
    public static String readString(Path path) throws IOException {
        if (Files.isRegularFile(path)) return Files.readString(path);
        try (InputStream in = stream(path)) {
            if (in != null) return new String(in.readAllBytes());
        }
        throw new IOException("Resource not found on filesystem or classpath: " + path);
    }

    /** Returns true if the resource exists on the filesystem or classpath. */
    public static boolean exists(Path path) {
        if (Files.isRegularFile(path)) return true;
        try (InputStream in = stream(path)) { return in != null; }
        catch (IOException e) { return false; }
    }

    /**
     * Lists files within a directory whose names end with {@code suffix},
     * sorted by filename. Checks the filesystem first; falls back to the JAR.
     * Returns an empty list if the directory is not found in either place.
     */
    public static List<Path> listDirectory(Path dir, String suffix) throws IOException {
        if (Files.isDirectory(dir)) {
            try (var s = Files.list(dir)) {
                return s.filter(p -> p.getFileName().toString().endsWith(suffix))
                        .sorted().toList();
            }
        }
        // JAR fallback: entry names have no leading slash
        String jarPrefix = dir.toString().replace('\\', '/') + "/";
        return listJarNames(jarPrefix, suffix).stream()
                .map(dir::resolve)
                .sorted()
                .toList();
    }

    /**
     * Copies a directory tree to {@code dst}, overwriting existing files.
     * Reads from the filesystem if {@code src} exists there; otherwise from the JAR.
     * Silently returns if the source is found in neither place.
     */
    public static void copyDirectory(Path src, Path dst) throws IOException {
        if (Files.isDirectory(src)) {
            try (var walk = Files.walk(src)) {
                for (Path source : walk.toList()) {
                    Path target = dst.resolve(src.relativize(source));
                    if (Files.isDirectory(source)) Files.createDirectories(target);
                    else {
                        Files.createDirectories(target.getParent());
                        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
            return;
        }
        // JAR fallback
        String prefix = src.toString().replace('\\', '/') + "/";
        URL loc = jarLocation();
        if (loc == null) return;
        try (JarFile jar = new JarFile(Path.of(loc.toURI()).toFile())) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (!entry.getName().startsWith(prefix)) continue;
                String relative = entry.getName().substring(prefix.length());
                if (relative.isEmpty()) continue;
                Path target = dst.resolve(relative);
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    try (InputStream in = jar.getInputStream(entry)) {
                        Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        } catch (URISyntaxException e) {
            throw new IOException("Cannot locate JAR: " + e.getMessage(), e);
        }
    }

    // ── Internal helpers ─────────────────────────────────────────────────────

    private static InputStream stream(Path path) {
        return ResourceIO.class.getResourceAsStream("/" + path.toString().replace('\\', '/'));
    }

    private static List<String> listJarNames(String jarPrefix, String suffix) {
        URL loc = jarLocation();
        if (loc == null) return listClasspathDirNames(jarPrefix, suffix);
        List<String> names = new ArrayList<>();
        try (JarFile jar = new JarFile(Path.of(loc.toURI()).toFile())) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry e = entries.nextElement();
                if (e.isDirectory()) continue;
                String name = e.getName();
                if (name.startsWith(jarPrefix) && name.endsWith(suffix))
                    names.add(name.substring(jarPrefix.length()));
            }
        } catch (Exception ignored) {}
        return names;
    }

    /**
     * Enumerates files in a classpath directory when running from expanded class files
     * (e.g. {@code target/classes/}) rather than a JAR.
     * {@code prefix} is the slash-separated resource path with trailing slash,
     * e.g. {@code "config/requirement-categories/"}.
     */
    private static List<String> listClasspathDirNames(String prefix, String suffix) {
        String resourcePath = "/" + (prefix.endsWith("/") ? prefix.substring(0, prefix.length() - 1) : prefix);
        URL url = ResourceIO.class.getResource(resourcePath);
        if (url == null || !"file".equals(url.getProtocol())) return List.of();
        try {
            Path dir = Path.of(url.toURI());
            if (!Files.isDirectory(dir)) return List.of();
            try (var s = Files.list(dir)) {
                return s.map(p -> p.getFileName().toString())
                        .filter(n -> n.endsWith(suffix))
                        .sorted()
                        .toList();
            }
        } catch (Exception ignored) {
            return List.of();
        }
    }

    /** Returns the location of the running JAR, or null if running from class files. */
    private static URL jarLocation() {
        URL loc = ResourceIO.class.getProtectionDomain().getCodeSource().getLocation();
        return (loc != null && loc.getPath().endsWith(".jar")) ? loc : null;
    }
}
