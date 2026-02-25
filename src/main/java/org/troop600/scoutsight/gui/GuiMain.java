package org.troop600.scoutsight.gui;

import javax.swing.*;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.io.*;
import java.nio.file.*;
import org.troop600.scoutsight.html.ResourceIO;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.prefs.Preferences;
import java.util.regex.*;

/**
 * Swing GUI for Scout Rank Planning report generation.
 *
 * <p>No extra JARs needed — uses only classes built into the JDK.
 * Report generation is delegated to {@code troop600.advancementplan.cli.Main}
 * via a subprocess so the working directory is set correctly.
 */
public class GuiMain extends JFrame {

    private static final Preferences PREFS = Preferences.userNodeForPackage(GuiMain.class);
    private static final String PREF_WORK_DIR    = "workDir";
    private static final String PREF_ADV_CSV     = "advancementCsv";
    private static final String PREF_SCOUTS_CSV  = "scoutsCsv";
    private static final String PREF_ROSTER_CSV  = "rosterReportCsv";
    private static final String PREF_CAMP        = "camp";

    private final JTextField workDirField;
    private final JTextField advancementField;
    private final JTextField scoutsField;
    private final JTextField rosterField;
    private final JComboBox<CampEntry> campCombo;
    private final JTextArea outputArea;
    private final JButton runButton;
    private final JButton viewButton;

    public static void main(String[] args) {
        // Apply the platform's native look and feel (Aqua on macOS, Windows on Windows)
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
        catch (Exception ignored) { }
        SwingUtilities.invokeLater(() -> new GuiMain().setVisible(true));
    }

    public GuiMain() {
        super("Scout Rank Planning - Report Generator");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // ── Fields ─────────────────────────────────────────────────────────
        workDirField    = new JTextField(PREFS.get(PREF_WORK_DIR,   System.getProperty("user.dir")));
        advancementField = new JTextField(PREFS.get(PREF_ADV_CSV,    ""));
        scoutsField     = new JTextField(PREFS.get(PREF_SCOUTS_CSV, ""));
        rosterField     = new JTextField(PREFS.get(PREF_ROSTER_CSV, ""));

        campCombo = new JComboBox<>();
        refreshCampDropdown();
        campCombo.addActionListener(e -> {
            CampEntry sel = (CampEntry) campCombo.getSelectedItem();
            PREFS.put(PREF_CAMP, sel != null ? sel.stem() : "");
        });

        // Reload camps when the working directory changes
        workDirField.addActionListener(e -> {
            PREFS.put(PREF_WORK_DIR, workDirField.getText().trim());
            refreshCampDropdown();
        });
        workDirField.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusLost(java.awt.event.FocusEvent e) {
                PREFS.put(PREF_WORK_DIR, workDirField.getText().trim());
                refreshCampDropdown();
            }
        });

        // ── Buttons ─────────────────────────────────────────────────────────
        JButton workDirBtn = new JButton("Pick...");
        workDirBtn.addActionListener(e -> pickDirectory());

        JButton advBtn = new JButton("Pick...");
        advBtn.addActionListener(e -> pickFile(advancementField, PREF_ADV_CSV));

        JButton scoutsBtn = new JButton("Pick...");
        scoutsBtn.addActionListener(e -> pickFile(scoutsField, PREF_SCOUTS_CSV));

        JButton rosterBtn = new JButton("Pick...");
        rosterBtn.addActionListener(e -> pickFile(rosterField, PREF_ROSTER_CSV));

        runButton = new JButton("Generate Reports");
        runButton.addActionListener(e -> runGeneration());
        getRootPane().setDefaultButton(runButton);

        viewButton = new JButton("View Reports");
        viewButton.addActionListener(e -> viewReports());
        updateViewButton();
        // Re-evaluate whenever the advancement CSV path changes
        advancementField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e)  { updateViewButton(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e)  { updateViewButton(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { updateViewButton(); }
        });

        // ── Output area ──────────────────────────────────────────────────────
        outputArea = new JTextArea();
        outputArea.setEditable(false);
        outputArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        outputArea.setLineWrap(true);
        outputArea.setWrapStyleWord(false);
        // Auto-scroll to bottom as text is appended
        ((DefaultCaret) outputArea.getCaret()).setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
        JScrollPane scrollPane = new JScrollPane(outputArea);

        // ── Form panel ───────────────────────────────────────────────────────
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(10, 10, 6, 10));

        addFormRow(form, 0, "Working Directory:", workDirField, workDirBtn);
        addFormRow(form, 1, "Advancement CSV:",   advancementField, advBtn);
        addFormRow(form, 2, "Scouts CSV (optional):", scoutsField, scoutsBtn);
        addFormRow(form, 3, "Roster Report (optional):", rosterField, rosterBtn);
        addFormRow(form, 4, "Camp:", campCombo, null);

        JButton helpButton = new JButton("Help");
        helpButton.addActionListener(e -> showHelpDialog());

        JPanel leftButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 4));
        leftButtons.add(helpButton);

        JPanel rightButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
        rightButtons.add(viewButton);
        rightButtons.add(runButton);

        JPanel runRow = new JPanel(new BorderLayout());
        runRow.setBorder(BorderFactory.createEmptyBorder(0, 10, 4, 10));
        runRow.add(leftButtons, BorderLayout.WEST);
        runRow.add(rightButtons, BorderLayout.EAST);

        // ── Root layout ──────────────────────────────────────────────────────
        JPanel top = new JPanel(new BorderLayout());
        top.add(form, BorderLayout.CENTER);
        top.add(runRow, BorderLayout.SOUTH);

        setLayout(new BorderLayout(0, 0));
        add(top, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);

        setSize(720, 540);
        setMinimumSize(new Dimension(500, 380));
        setLocationRelativeTo(null); // center on screen
    }

    // ── Layout helper ────────────────────────────────────────────────────────

    /** Adds a label + component + optional button as one form row. */
    private void addFormRow(JPanel form, int row, String label,
                            JComponent field, JButton btn) {
        GridBagConstraints lc = new GridBagConstraints();
        lc.gridx = 0; lc.gridy = row;
        lc.anchor = GridBagConstraints.LINE_END;
        lc.insets = new Insets(3, 4, 3, 6);
        form.add(new JLabel(label), lc);

        GridBagConstraints fc = new GridBagConstraints();
        fc.gridx = 1; fc.gridy = row;
        fc.fill = GridBagConstraints.HORIZONTAL;
        fc.weightx = 1.0;
        fc.insets = new Insets(3, 0, 3, btn != null ? 4 : 0);
        fc.gridwidth = btn != null ? 1 : 2; // span if no button
        form.add(field, fc);

        if (btn != null) {
            GridBagConstraints bc = new GridBagConstraints();
            bc.gridx = 2; bc.gridy = row;
            bc.insets = new Insets(3, 0, 3, 4);
            form.add(btn, bc);
        }
    }

    // ── File / directory pickers ─────────────────────────────────────────────

    private void pickFile(JTextField field, String prefKey) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select CSV file");
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "CSV files", "csv"));

        // Start in the directory of the current value, or inputdata/ under workDir
        File initialDir = resolveInitialDir(field.getText().trim(), "inputdata");
        if (initialDir != null) chooser.setCurrentDirectory(initialDir);

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            String path = chooser.getSelectedFile().getAbsolutePath();
            field.setText(path);
            PREFS.put(prefKey, path);
        }
    }

    private void pickDirectory() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select Project Root (Working Directory)");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

        String current = workDirField.getText().trim();
        if (!current.isBlank()) {
            File d = new File(current);
            if (d.isDirectory()) chooser.setCurrentDirectory(d);
        }

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            String path = chooser.getSelectedFile().getAbsolutePath();
            workDirField.setText(path);
            PREFS.put(PREF_WORK_DIR, path);
            refreshCampDropdown();
        }
    }

    private File resolveInitialDir(String currentValue, String fallbackSubdir) {
        if (!currentValue.isBlank()) {
            File parent = new File(currentValue).getParentFile();
            if (parent != null && parent.isDirectory()) return parent;
        }
        String workDir = workDirField.getText().trim();
        if (!workDir.isBlank()) {
            File sub = new File(workDir, fallbackSubdir);
            if (sub.isDirectory()) return sub;
            File wd = new File(workDir);
            if (wd.isDirectory()) return wd;
        }
        return null;
    }

    // ── Camp dropdown ────────────────────────────────────────────────────────

    private void refreshCampDropdown() {
        String savedStem = PREFS.get(PREF_CAMP, "");
        String workDir = workDirField.getText().trim();

        List<CampEntry> entries = new ArrayList<>();
        entries.add(new CampEntry("None", ""));

        Path campsDir = Path.of(workDir.isEmpty() ? "." : workDir, "config", "camps");
        try {
            // Use absolute filesystem paths when the directory exists in workDir;
            // otherwise fall back to relative paths resolved from the JAR classpath.
            List<Path> campFiles = Files.isDirectory(campsDir)
                    ? Files.list(campsDir).filter(p -> p.getFileName().toString().endsWith(".json"))
                           .sorted().toList()
                    : ResourceIO.listDirectory(Path.of("config", "camps"), ".json");
            for (Path p : campFiles) {
                String stem = p.getFileName().toString().replace(".json", "");
                entries.add(new CampEntry(readCampName(p, stem), stem));
            }
        } catch (IOException ignored) { }

        SwingUtilities.invokeLater(() -> {
            CampEntry prev = (CampEntry) campCombo.getSelectedItem();
            String prevStem = prev != null ? prev.stem() : savedStem;

            campCombo.removeAllItems();
            CampEntry toSelect = entries.get(0);
            for (CampEntry e : entries) {
                campCombo.addItem(e);
                if (e.stem().equals(prevStem)) toSelect = e;
            }
            campCombo.setSelectedItem(toSelect);
        });
    }

    private String readCampName(Path jsonFile, String stemFallback) {
        try {
            String content = Files.isRegularFile(jsonFile)
                    ? Files.readString(jsonFile)
                    : ResourceIO.readString(jsonFile);
            Matcher m = Pattern.compile("\"campName\"\\s*:\\s*\"([^\"]+)\"").matcher(content);
            if (m.find()) return m.group(1);
        } catch (IOException ignored) { }
        // Fallback: "camp_parsons" → "Camp Parsons"
        return Arrays.stream(stemFallback.split("[_\\-]"))
                     .filter(s -> !s.isBlank())
                     .map(s -> Character.toUpperCase(s.charAt(0)) + s.substring(1))
                     .reduce("", (a, b) -> a.isEmpty() ? b : a + " " + b);
    }

    // ── Report generation ────────────────────────────────────────────────────

    private void runGeneration() {
        String workDir = workDirField.getText().trim();
        String advCsv  = advancementField.getText().trim();

        if (workDir.isBlank()) { appendOutput("ERROR: Working directory is required.\n"); return; }
        if (advCsv.isBlank())  { appendOutput("ERROR: Advancement CSV is required.\n");  return; }

        CampEntry camp = (CampEntry) campCombo.getSelectedItem();
        String campStem = camp != null ? camp.stem() : "";

        runButton.setEnabled(false);
        outputArea.setText("");

        Thread worker = new Thread(() -> {
            try {
                runCli(workDir, advCsv, scoutsField.getText().trim(), campStem,
                       rosterField.getText().trim());
            } finally {
                SwingUtilities.invokeLater(() -> runButton.setEnabled(true));
                updateViewButton();
            }
        }, "report-generator");
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * Launches {@code troop600.advancementplan.cli.Main} in a subprocess with
     * {@code workDir} as its working directory and streams output to the log area.
     */
    private void runCli(String workDir, String advCsv, String scoutsCsv, String campStem,
                        String rosterCsv) {
        String javaExe = Path.of(System.getProperty("java.home"), "bin",
                isWindows() ? "java.exe" : "java").toString();
        String classpath = System.getProperty("java.class.path");

        // Always pass exactly 4 args; empty string → treated as null by Main
        List<String> cmd = List.of(
                javaExe,
                "-cp", classpath,
                "org.troop600.scoutsight.cli.Main",
                advCsv,
                scoutsCsv,
                campStem,
                rosterCsv
        );

        // Pre-create the output directory so a bad path or permissions error surfaces
        // immediately with a clear message rather than buried in subprocess output.
        String filename = Path.of(advCsv).getFileName().toString();
        String stem = filename.endsWith(".csv") ? filename.substring(0, filename.length() - 4) : filename;
        try {
            Files.createDirectories(Path.of(workDir, "output", stem));
        } catch (IOException e) {
            appendOutput("ERROR: Could not create output directory: " + e.getMessage() + "\n");
            return;
        }

        appendOutput("Running in: " + workDir + "\n\n");

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(new File(workDir));
        pb.redirectErrorStream(true);

        try {
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String text = line + "\n";
                    SwingUtilities.invokeLater(() -> outputArea.append(text));
                }
            }
            int exitCode = process.waitFor();
            if (exitCode != 0) appendOutput("\nProcess exited with code " + exitCode + "\n");
        } catch (IOException | InterruptedException e) {
            appendOutput("\nERROR: " + e.getMessage() + "\n");
        }
    }

    // ── Help dialog ──────────────────────────────────────────────────────────

    private void showHelpDialog() {
        JEditorPane editorPane = new JEditorPane();
        editorPane.setContentType("text/html");
        editorPane.setEditable(false);
        editorPane.setText(loadHelpHtml());
        editorPane.setCaretPosition(0); // scroll to top

        // Open hyperlinks in the default browser
        editorPane.addHyperlinkListener(e -> {
            if (e.getEventType() == javax.swing.event.HyperlinkEvent.EventType.ACTIVATED
                    && e.getURL() != null) {
                try { Desktop.getDesktop().browse(e.getURL().toURI()); }
                catch (Exception ignored) { }
            }
        });

        JScrollPane scroll = new JScrollPane(editorPane);

        JButton closeBtn = new JButton("Close");
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnPanel.add(closeBtn);

        JDialog dialog = new JDialog(this, "Help \u2014 ScoutSight", true);
        dialog.setLayout(new BorderLayout());
        dialog.add(scroll, BorderLayout.CENTER);
        dialog.add(btnPanel, BorderLayout.SOUTH);
        dialog.setSize(580, 420);
        dialog.setLocationRelativeTo(this);

        closeBtn.addActionListener(e -> dialog.dispose());
        dialog.getRootPane().setDefaultButton(closeBtn);

        dialog.setVisible(true);
    }

    /**
     * Loads help.html from the templates directory (filesystem first, JAR resource fallback).
     * Strips the Thymeleaf inline expression so the raw HTML renders cleanly.
     */
    private String loadHelpHtml() {
        // 1. Try filesystem (works in IntelliJ and after first-run extraction from JAR)
        String workDir = workDirField.getText().trim();
        if (!workDir.isBlank()) {
            Path helpFile = Path.of(workDir, "templates", "help.html");
            if (Files.isRegularFile(helpFile)) {
                try { return stripThymeleaf(Files.readString(helpFile)); }
                catch (IOException ignored) { }
            }
        }
        // 2. Try JAR resource (before any extraction has happened)
        try (InputStream in = GuiMain.class.getResourceAsStream("/templates/help.html")) {
            if (in != null) return stripThymeleaf(new String(in.readAllBytes()));
        } catch (IOException ignored) { }
        // 3. Fallback
        return "<html><body><p>Help content not available.</p></body></html>";
    }

    /** Removes Thymeleaf unescaped inline expressions such as [(${siteHeader})]. */
    private static String stripThymeleaf(String html) {
        return html.replaceAll("\\[\\(\\$\\{[^}]+\\}\\)\\]", "");
    }

    /** Enables the View Reports button only when the output index.html already exists. */
    private void updateViewButton() {
        boolean exists = resolveIndexHtml() != null;
        SwingUtilities.invokeLater(() -> viewButton.setEnabled(exists));
    }

    /** Returns the output index.html Path if it exists, otherwise null. */
    private Path resolveIndexHtml() {
        String workDir = workDirField.getText().trim();
        String advCsv  = advancementField.getText().trim();
        if (workDir.isBlank() || advCsv.isBlank()) return null;
        String filename = Path.of(advCsv).getFileName().toString();
        String stem = filename.endsWith(".csv") ? filename.substring(0, filename.length() - 4) : filename;
        Path index = Path.of(workDir, "output", stem, "index.html");
        return Files.exists(index) ? index : null;
    }

    private void viewReports() {
        Path index = resolveIndexHtml();
        if (index == null) {
            JOptionPane.showMessageDialog(this,
                    "No report found. Generate reports first.",
                    "Not Found", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            Desktop.getDesktop().browse(index.toUri());
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                    "Could not open browser:\n" + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void appendOutput(String text) {
        SwingUtilities.invokeLater(() -> outputArea.append(text));
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    // ── Camp model ───────────────────────────────────────────────────────────

    record CampEntry(String displayName, String stem) {
        @Override public String toString() { return displayName; }
    }
}
