package org.troop600.scoutsight.gui;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.*;
import java.nio.file.*;
import org.troop600.scoutsight.html.ResourceIO;
import java.util.*;
import java.util.List;
import java.util.prefs.Preferences;
import java.util.regex.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Swing GUI for ScoutSight report generation.
 *
 * <p>Styled to match the HTML output — parchment/forest-green palette, Nimbus L&amp;F
 * with custom color overrides, black header with blue accent stripe, contextual
 * per-field help buttons, and a dark terminal-style log area.
 */
public class GuiMain extends JFrame {

    // ── Design tokens (matching HTML design system) ──────────────────────────
    private static final Color C_BG        = new Color(0xF0EBE0);
    private static final Color C_SURFACE   = new Color(0xFAF7F2);
    private static final Color C_SURFACE2  = new Color(0xF5F0E6);
    private static final Color C_BORDER    = new Color(0xD6CCB8);
    private static final Color C_TEXT      = new Color(0x28200F);
    private static final Color C_MUTED     = new Color(0x7A6E58);
    private static final Color C_GREEN     = new Color(0x1A4D2E);
    private static final Color C_GREEN_MID = new Color(0x256937);
    private static final Color C_GREEN_LT  = new Color(0x3A8A50);
    private static final Color C_AMBER     = new Color(0xC47A0E);
    private static final Color C_RED       = new Color(0xB03030);
    private static final Color C_STRIPE    = new Color(0x3A5BB8);
    private static final Color C_LOG_BG    = new Color(0x111A11);
    private static final Color C_LOG_FG    = new Color(0xC8E8C8);
    private static final Color C_LOG_CARET = new Color(0x52B056);

    // ── Fonts ────────────────────────────────────────────────────────────────
    private static final Font FONT_BODY    = ui(Font.PLAIN,  12);
    private static final Font FONT_LABEL   = ui(Font.BOLD,   12);
    private static final Font FONT_SECTION = ui(Font.BOLD,   10);
    private static final Font FONT_SMALL   = ui(Font.PLAIN,  11);
    private static final Font FONT_HINT    = ui(Font.ITALIC, 10);
    private static final Font FONT_MONO    = monoFont(12);
    private static final Font FONT_RUN     = ui(Font.BOLD,   13);

    // ── Version ──────────────────────────────────────────────────────────────
    /** Current application version — keep in sync with pom.xml / GitHub release tag. */
    static final String APP_VERSION = "1.1";
    private static final String RELEASES_API =
            "https://api.github.com/repos/bhotchkies/ScoutSight/releases/latest";

    // ── Preferences ──────────────────────────────────────────────────────────
    private static final Preferences PREFS          = Preferences.userNodeForPackage(GuiMain.class);
    private static final String      PREF_WORK_DIR  = "workDir";
    private static final String      PREF_ADV_CSV   = "advancementCsv";
    private static final String      PREF_SCOUTS    = "scoutsCsv";
    private static final String      PREF_ROSTER    = "rosterReportCsv";
    private static final String      PREF_CAMP      = "camp";

    // ── Component references ─────────────────────────────────────────────────
    private final JTextField          workDirField;
    private final JTextField          advancementField;
    private final JTextField          scoutsField;
    private final JTextField          rosterField;
    private final JComboBox<CampEntry> campCombo;
    private final JTextArea           logArea;
    private final JButton             runButton;
    private final JButton             viewButton;
    private final JLabel              statusLabel;

    // ── Entry point ──────────────────────────────────────────────────────────

    public static void main(String[] args) {
        applyTheme();
        SwingUtilities.invokeLater(() -> new GuiMain().setVisible(true));
    }

    /** Configures Nimbus L&F with design-system color overrides. */
    private static void applyTheme() {
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception ignored) { }

        UIManager.put("control",                   C_BG);
        UIManager.put("nimbusBase",                C_GREEN);
        UIManager.put("nimbusBlueGrey",            new Color(0xC4BBB0));
        UIManager.put("nimbusBorder",              C_BORDER);
        UIManager.put("nimbusSelectionBackground", C_GREEN);
        UIManager.put("nimbusLightBackground",     C_SURFACE);
        UIManager.put("text",                      C_TEXT);
        UIManager.put("nimbusFocus",               C_STRIPE);
        UIManager.put("nimbusDisabledText",        C_MUTED);
        UIManager.put("textHighlight",             C_GREEN_LT);
        UIManager.put("textHighlightText",         Color.WHITE);
        UIManager.put("info",                      new Color(0xFEF6C0));
        UIManager.put("ToolTip.font",              ui(Font.PLAIN, 12));
    }

    // ── Constructor ──────────────────────────────────────────────────────────

    public GuiMain() {
        super("ScoutSight — Report Generator");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setBackground(C_BG);

        // Text fields
        workDirField     = styledField(PREFS.get(PREF_WORK_DIR, System.getProperty("user.dir")));
        advancementField = styledField(PREFS.get(PREF_ADV_CSV,  ""));
        scoutsField      = styledField(PREFS.get(PREF_SCOUTS,   ""));
        rosterField      = styledField(PREFS.get(PREF_ROSTER,   ""));

        // Camp combo
        campCombo = new JComboBox<>();
        campCombo.setFont(FONT_BODY);
        refreshCampDropdown();
        campCombo.addActionListener(e -> {
            CampEntry sel = (CampEntry) campCombo.getSelectedItem();
            PREFS.put(PREF_CAMP, sel != null ? sel.stem() : "");
        });

        workDirField.addActionListener(e -> onWorkDirChanged());
        workDirField.addFocusListener(new FocusAdapter() {
            @Override public void focusLost(FocusEvent e) { onWorkDirChanged(); }
        });

        // Browse buttons
        JButton workDirBtn = browseButton();
        workDirBtn.addActionListener(e -> pickDirectory());
        JButton advBtn = browseButton();
        advBtn.addActionListener(e -> pickFile(advancementField, PREF_ADV_CSV));
        JButton scoutsBtn = browseButton();
        scoutsBtn.addActionListener(e -> pickFile(scoutsField, PREF_SCOUTS));
        JButton rosterBtn = browseButton();
        rosterBtn.addActionListener(e -> pickFile(rosterField, PREF_ROSTER));

        // Action buttons
        runButton = new JButton("Generate Reports");
        styleRunButton(runButton);
        runButton.addActionListener(e -> runGeneration());
        getRootPane().setDefaultButton(runButton);

        viewButton = new JButton("View Reports");
        styleSecondaryButton(viewButton);
        viewButton.addActionListener(e -> viewReports());
        updateViewButton();
        advancementField.getDocument().addDocumentListener(docListener(this::updateViewButton));

        // Status label
        statusLabel = new JLabel("Ready");
        statusLabel.setFont(FONT_SMALL);
        statusLabel.setForeground(C_MUTED);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 8));

        // Log area (dark terminal aesthetic)
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(FONT_MONO);
        logArea.setBackground(C_LOG_BG);
        logArea.setForeground(C_LOG_FG);
        logArea.setCaretColor(C_LOG_CARET);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(false);
        logArea.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
        ((DefaultCaret) logArea.getCaret()).setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
        installUrlClickHandler();

        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createMatteBorder(2, 0, 0, 0, C_GREEN));
        logScroll.getViewport().setBackground(C_LOG_BG);

        // ── Assemble layout ──────────────────────────────────────────────────
        setLayout(new BorderLayout());
        add(buildHeader(), BorderLayout.NORTH);

        JPanel body = new JPanel(new BorderLayout());
        body.setBackground(C_BG);
        body.add(buildFormCard(workDirField, workDirBtn, advBtn, scoutsBtn, rosterBtn), BorderLayout.NORTH);
        body.add(logScroll, BorderLayout.CENTER);
        add(body, BorderLayout.CENTER);

        setSize(780, 600);
        setMinimumSize(new Dimension(580, 440));
        setLocationRelativeTo(null);
        checkForUpdates();
    }

    // ── Header ───────────────────────────────────────────────────────────────

    /** Black banner with ScoutSight logo and a blue bottom stripe. */
    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                g.setColor(Color.BLACK);
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(C_STRIPE);
                g.fillRect(0, getHeight() - 3, getWidth(), 3);
            }
        };
        header.setOpaque(true);
        header.setBorder(BorderFactory.createEmptyBorder(10, 18, 13, 18));

        JPanel left = new JPanel();
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.setOpaque(false);

        ImageIcon logoIcon = loadLogoIcon(32);
        if (logoIcon != null) {
            JLabel logoLbl = new JLabel(logoIcon);
            logoLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
            left.add(logoLbl);
        } else {
            // Fallback: plain text title
            JLabel title = new JLabel("ScoutSight");
            title.setFont(new Font(resolveFont("Segoe UI", "Trebuchet MS", "SansSerif"),
                    Font.BOLD, 22));
            title.setForeground(Color.WHITE);
            title.setAlignmentX(Component.LEFT_ALIGNMENT);
            left.add(title);
        }

        left.add(Box.createVerticalStrut(3));

        JLabel tagline = new JLabel("Rank Advancement Report Generator");
        tagline.setFont(new Font(resolveFont("Segoe UI", "Trebuchet MS", "SansSerif"),
                Font.PLAIN, 11));
        tagline.setForeground(new Color(0x52B056));
        tagline.setAlignmentX(Component.LEFT_ALIGNMENT);
        left.add(tagline);

        header.add(left, BorderLayout.WEST);
        return header;
    }

    /**
     * Loads {@code ScoutSightLogo_Large.png} from the classpath and scales it
     * proportionally to {@code targetHeight} pixels. Returns {@code null} if
     * the image cannot be found or read.
     */
    private static ImageIcon loadLogoIcon(int targetHeight) {
        try (InputStream is = GuiMain.class.getResourceAsStream(
                "/templates/images/logos/ScoutSightLogo_Large.png")) {
            if (is == null) return null;
            BufferedImage img = ImageIO.read(is);
            if (img == null) return null;
            int scaledWidth = img.getWidth() * targetHeight / img.getHeight();
            Image scaled = img.getScaledInstance(scaledWidth, targetHeight, Image.SCALE_SMOOTH);
            return new ImageIcon(scaled);
        } catch (IOException e) {
            return null;
        }
    }

    // ── Form card ────────────────────────────────────────────────────────────

    private JPanel buildFormCard(
            JTextField workDirField, JButton workDirBtn,
            JButton advBtn, JButton scoutsBtn, JButton rosterBtn) {

        // Card background
        JPanel card = new JPanel(new GridBagLayout());
        card.setBackground(C_SURFACE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, C_BORDER),
                BorderFactory.createEmptyBorder(12, 18, 8, 18)));

        int r = 0;

        // Section label
        JLabel sectionLbl = new JLabel("INPUT FILES");
        sectionLbl.setFont(FONT_SECTION);
        sectionLbl.setForeground(C_MUTED);
        GridBagConstraints sc = gbc(0, r, 3, 1);
        sc.anchor = GridBagConstraints.LINE_START;
        sc.insets = new Insets(0, 0, 8, 0);
        card.add(sectionLbl, sc);
        r++;

        r = addFormRow(card, r, "Working Directory", workDirField, workDirBtn, true,
                "Required · The folder where output reports will be written. An output/ subfolder is created here automatically.",
                loadHelpHtml("_working_directory_help.html"), 100);

        r = addFormRow(card, r, "Advancement CSV", advancementField, advBtn, true,
                "Required · Download from advancements.scouting.org",
                loadHelpHtml("_advancement_csv_help.html"),250);

        r = addFormRow(card, r, "Scouts CSV", scoutsField, scoutsBtn, false,
                "Optional · Adds patrol & grade · Download from advancements.scouting.org",
                loadHelpHtml("_scouts_csv_help.html"),260);

        r = addFormRow(card, r, "Roster Report", rosterField, rosterBtn, false,
                "Optional · Adds birth year, join year & positions · Download from advancements.scouting.org",
                loadHelpHtml("_roster_report_help.html"),290);

        // Camp row
        r = addCampRow(card, r);

        // Action bar (inside card, at bottom)
        GridBagConstraints abc = gbc(0, r, 3, 1);
        abc.fill = GridBagConstraints.HORIZONTAL;
        abc.insets = new Insets(10, 0, 2, 0);
        card.add(buildActionBar(), abc);

        return card;
    }

    /**
     * Adds one labeled form row and returns the next available row index.
     * Each row is: [label + info-btn] | [field + hint sub-panel] | browse-btn
     */
    private int addFormRow(JPanel card, int row, String labelText, JTextField field,
                           JButton browseBtn, boolean required, String hint, String helpHtml, int htmlHeight) {
        // Col 0: label and ? button side-by-side
        String labelHtml = "<html><b>" + labelText + "</b>"
                + (required ? " &nbsp;<font color='#C47A0E'>*</font>" : "") + "</html>";
        JLabel label = new JLabel(labelHtml);
        label.setFont(FONT_LABEL);
        label.setForeground(C_TEXT);

        JPanel labelPanel = new JPanel();
        labelPanel.setLayout(new BoxLayout(labelPanel, BoxLayout.LINE_AXIS));
        labelPanel.setOpaque(false);
        labelPanel.add(label);
        labelPanel.add(Box.createHorizontalStrut(4));
        labelPanel.add(infoButton(helpHtml, htmlHeight));

        GridBagConstraints lc = gbc(0, row, 1, 1);
        lc.anchor = GridBagConstraints.FIRST_LINE_END;
        lc.insets = new Insets(6, 0, 2, 6);
        card.add(labelPanel, lc);

        // Col 1: field + hint stacked
        JPanel fieldStack = new JPanel(new BorderLayout(0, 2));
        fieldStack.setOpaque(false);
        fieldStack.add(field, BorderLayout.CENTER);
        if (hint != null) {
            JLabel hintLbl = new JLabel(hint);
            hintLbl.setFont(FONT_HINT);
            hintLbl.setForeground(C_MUTED);
            fieldStack.add(hintLbl, BorderLayout.SOUTH);
        }
        GridBagConstraints fc = gbc(1, row, 1, 1);
        fc.fill = GridBagConstraints.HORIZONTAL;
        fc.weightx = 1.0;
        fc.insets = new Insets(4, 0, 2, 4);
        card.add(fieldStack, fc);

        // Col 2: browse button
        GridBagConstraints bc = gbc(2, row, 1, 1);
        bc.anchor = GridBagConstraints.PAGE_START;
        bc.insets = new Insets(4, 0, 2, 0);
        card.add(browseBtn, bc);

        return row + 1;
    }

    /** Adds the Camp row (combo box, no browse btn) and returns next row index. */
    private int addCampRow(JPanel card, int row) {
        JLabel label = new JLabel("<html><b>Camp</b></html>");
        label.setFont(FONT_LABEL);
        label.setForeground(C_TEXT);

        JPanel labelPanel = new JPanel();
        labelPanel.setLayout(new BoxLayout(labelPanel, BoxLayout.LINE_AXIS));
        labelPanel.setOpaque(false);
        labelPanel.add(label);
        labelPanel.add(Box.createHorizontalStrut(4));
        labelPanel.add(infoButton(loadHelpHtml("_camp_help.html"), 60));

        GridBagConstraints lc = gbc(0, row, 1, 1);
        lc.anchor = GridBagConstraints.LINE_END;
        lc.insets = new Insets(6, 0, 2, 6);
        card.add(labelPanel, lc);

        GridBagConstraints cc = gbc(1, row, 2, 1);
        cc.fill = GridBagConstraints.HORIZONTAL;
        cc.weightx = 1.0;
        cc.insets = new Insets(4, 0, 2, 0);
        card.add(campCombo, cc);

        return row + 1;
    }

    // ── Action bar ───────────────────────────────────────────────────────────

    private JPanel buildActionBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setOpaque(false);

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.setOpaque(false);
        left.add(statusLabel);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        right.add(viewButton);
        right.add(runButton);

        bar.add(left,  BorderLayout.WEST);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    // ── Component factories ──────────────────────────────────────────────────

    private static JTextField styledField(String text) {
        JTextField tf = new JTextField(text);
        tf.setFont(FONT_BODY);
        tf.setBackground(C_SURFACE);
        tf.setForeground(C_TEXT);
        return tf;
    }

    private static JButton browseButton() {
        JButton btn = new JButton("Browse…");
        btn.setFont(FONT_SMALL);
        btn.setFocusPainted(false);
        btn.setToolTipText("Choose file");
        return btn;
    }

    private JButton infoButton(String htmlContent, int htmlHeight) {
        JButton btn = new JButton("?");
        btn.setFont(ui(Font.BOLD, 12));
        btn.setForeground(C_GREEN_MID);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setToolTipText("Click for help");
        btn.addActionListener(e -> showInfoPopup(htmlContent, htmlHeight, btn));
        return btn;
    }

    /**
     * Loads a help HTML snippet from {@code templates/<templateName>} via {@link ResourceIO}
     * and wraps it in a minimal HTML shell with CSS that Swing's HTMLEditorKit can render.
     * Falls back to a plain error message if the file cannot be read.
     */
    private static String loadHelpHtml(String templateName) {
        String snippet;
        try {
            snippet = ResourceIO.readString(Path.of("templates", templateName));
        } catch (IOException e) {
            snippet = "<p>Help text not available (" + templateName + ").</p>";
        }
        // Minimal CSS that Swing's HTML renderer understands.
        // .step uses a simple table trick so the number sits inline with the text.
        return "<html><head><style>" +
               "body{font-family:SansSerif;font-size:11pt;margin:2px 4px}" +
               ".help-section{margin:0}" +
               ".help-section-title{font-weight:bold;font-size:12pt;color:#1a4d2e}" +
               ".steps{margin:6px 0 0 0}" +
               ".step{margin:3px 0}" +
               ".step-num{font-weight:bold;color:#ffffff;background:#1a4d2e;padding:0 4px}" +
               ".step-text{margin-left:6px}" +
               "p{margin:4px 0}" +
               "code{font-family:monospace;font-size:10pt}" +
               "a{color:#256937}" +
               "strong{font-weight:bold}" +
               "</style></head><body>" +
               snippet +
               "</body></html>";
    }

    private static void styleRunButton(JButton btn) {
        btn.setFont(FONT_RUN);
        btn.setFocusPainted(false);
        // Nimbus will pick up nimbusBase (green) for the default button
    }

    private static void styleSecondaryButton(JButton btn) {
        btn.setFont(FONT_BODY);
        btn.setFocusPainted(false);
    }

    /**
     * Shows a floating help popup anchored below the invoker button.
     * Automatically dismisses when the user clicks anywhere outside it.
     */
    private void showInfoPopup(String htmlContent, int htmlHeight, Component invoker) {
        JEditorPane pane = new JEditorPane("text/html", htmlContent);
        pane.setEditable(false);
        pane.setBackground(C_SURFACE);
        pane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        pane.setFont(FONT_BODY);
        pane.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED && e.getURL() != null) {
                try { Desktop.getDesktop().browse(e.getURL().toURI()); }
                catch (Exception ignored) { }
            }
        });

        // Scroll pane: fixed viewport size; pane reflows HTML to viewport width
        JScrollPane scroll = new JScrollPane(pane,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setPreferredSize(new Dimension(380, htmlHeight));
        scroll.setBorder(null);
        scroll.getViewport().setBackground(C_SURFACE);

        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        panel.setBackground(C_SURFACE);
        panel.add(scroll, BorderLayout.CENTER);

        // JPopupMenu provides built-in "dismiss on click outside" behaviour
        JPopupMenu popup = new JPopupMenu();
        popup.setLayout(new BorderLayout());
        popup.setBorder(BorderFactory.createLineBorder(C_BORDER));
        popup.add(panel, BorderLayout.CENTER);
        popup.show(invoker, 0, invoker.getHeight());
    }

    // ── Utility / layout ─────────────────────────────────────────────────────

    private static GridBagConstraints gbc(int x, int y, int width, int height) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = x; c.gridy = y;
        c.gridwidth = width; c.gridheight = height;
        return c;
    }

    private static DocumentListener docListener(Runnable r) {
        return new DocumentListener() {
            public void insertUpdate(DocumentEvent e)  { r.run(); }
            public void removeUpdate(DocumentEvent e)  { r.run(); }
            public void changedUpdate(DocumentEvent e) { r.run(); }
        };
    }

    private static Font ui(int style, int size) {
        String family = resolveFont("Segoe UI", "Trebuchet MS", "SansSerif");
        return new Font(family, style, size);
    }

    private static Font monoFont(int size) {
        for (String name : new String[]{"Consolas", "Cascadia Code", "Courier New", Font.MONOSPACED}) {
            Font f = new Font(name, Font.PLAIN, size);
            if (!f.getFamily().equals(Font.MONOSPACED) || name.equals(Font.MONOSPACED)) return f;
        }
        return new Font(Font.MONOSPACED, Font.PLAIN, size);
    }

    private static String resolveFont(String... candidates) {
        Set<String> available = new HashSet<>(Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        for (String name : candidates) {
            if (available.contains(name)) return name;
        }
        return Font.SANS_SERIF;
    }

    // ── File / directory pickers ─────────────────────────────────────────────

    private void pickFile(JTextField field, String prefKey) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select CSV file");
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("CSV files", "csv"));
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
        chooser.setDialogTitle("Select Working Directory");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        String cur = workDirField.getText().trim();
        if (!cur.isBlank()) { File d = new File(cur); if (d.isDirectory()) chooser.setCurrentDirectory(d); }
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            String path = chooser.getSelectedFile().getAbsolutePath();
            workDirField.setText(path);
            PREFS.put(PREF_WORK_DIR, path);
            refreshCampDropdown();
        }
    }

    private File resolveInitialDir(String current, String fallbackSub) {
        if (!current.isBlank()) {
            File parent = new File(current).getParentFile();
            if (parent != null && parent.isDirectory()) return parent;
        }
        String wd = workDirField.getText().trim();
        if (!wd.isBlank()) {
            File sub = new File(wd, fallbackSub);
            if (sub.isDirectory()) return sub;
            File d = new File(wd);
            if (d.isDirectory()) return d;
        }
        return null;
    }

    private void onWorkDirChanged() {
        PREFS.put(PREF_WORK_DIR, workDirField.getText().trim());
        refreshCampDropdown();
    }

    // ── Camp dropdown ────────────────────────────────────────────────────────

    private void refreshCampDropdown() {
        String savedStem = PREFS.get(PREF_CAMP, "");
        String workDir   = workDirField.getText().trim();

        List<CampEntry> entries = new ArrayList<>();
        entries.add(new CampEntry("None", ""));

        Path campsDir = Path.of(workDir.isEmpty() ? "." : workDir, "config", "camps");
        try {
            List<Path> campFiles = Files.isDirectory(campsDir)
                    ? Files.list(campsDir).filter(p -> {
                          String n = p.getFileName().toString();
                          return n.endsWith(".json") && !n.contains("_schedule");
                      }).sorted().toList()
                    : ResourceIO.listDirectory(Path.of("config", "camps"), ".json")
                            .stream()
                            .filter(p -> !p.getFileName().toString().contains("_schedule"))
                            .toList();
            for (Path p : campFiles) {
                String stem = p.getFileName().toString().replace(".json", "");
                entries.add(new CampEntry(readCampName(p, stem), stem));
            }
        } catch (IOException ignored) { }

        SwingUtilities.invokeLater(() -> {
            CampEntry prev    = (CampEntry) campCombo.getSelectedItem();
            String    prevStr = prev != null ? prev.stem() : savedStem;
            campCombo.removeAllItems();
            CampEntry toSelect = entries.get(0);
            for (CampEntry e : entries) {
                campCombo.addItem(e);
                if (e.stem().equals(prevStr)) toSelect = e;
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
        return Arrays.stream(stemFallback.split("[_\\-]"))
                .filter(s -> !s.isBlank())
                .map(s -> Character.toUpperCase(s.charAt(0)) + s.substring(1))
                .reduce("", (a, b) -> a.isEmpty() ? b : a + " " + b);
    }

    // ── Report generation ────────────────────────────────────────────────────

    private void runGeneration() {
        String workDir = workDirField.getText().trim();
        String advCsv  = advancementField.getText().trim();

        if (workDir.isBlank()) { appendLog("ERROR: Working directory is required.\n"); return; }
        if (advCsv.isBlank())  { appendLog("ERROR: Advancement CSV is required.\n");  return; }

        CampEntry camp     = (CampEntry) campCombo.getSelectedItem();
        String    campStem = camp != null ? camp.stem() : "";

        runButton.setEnabled(false);
        logArea.setText("");
        setStatus("Generating…", C_AMBER);

        Thread worker = new Thread(() -> {
            try {
                runCli(workDir, advCsv, scoutsField.getText().trim(), campStem,
                       rosterField.getText().trim());
                setStatus("Done", C_GREEN_MID);
            } catch (Exception e) {
                setStatus("Error — see log", C_RED);
            } finally {
                SwingUtilities.invokeLater(() -> runButton.setEnabled(true));
                updateViewButton();
            }
        }, "report-generator");
        worker.setDaemon(true);
        worker.start();
    }

    private void runCli(String workDir, String advCsv, String scoutsCsv,
                        String campStem, String rosterCsv) {
        String javaExe = Path.of(System.getProperty("java.home"), "bin",
                isWindows() ? "java.exe" : "java").toString();
        String classpath = System.getProperty("java.class.path");

        List<String> cmd = List.of(
                javaExe, "-cp", classpath,
                "org.troop600.scoutsight.cli.Main",
                advCsv, scoutsCsv, campStem, rosterCsv);

        String filename = Path.of(advCsv).getFileName().toString();
        String stem     = filename.endsWith(".csv") ? filename.substring(0, filename.length() - 4) : filename;
        try {
            Files.createDirectories(Path.of(workDir, "output", stem));
        } catch (IOException e) {
            appendLog("ERROR: Could not create output directory: " + e.getMessage() + "\n");
            return;
        }

        appendLog("Running in: " + workDir + "\n\n");

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(new File(workDir));
        pb.redirectErrorStream(true);

        try {
            Process proc = pb.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(proc.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String text = line + "\n";
                    SwingUtilities.invokeLater(() -> logArea.append(text));
                }
            }
            int exit = proc.waitFor();
            if (exit != 0) appendLog("\nProcess exited with code " + exit + "\n");
            else           appendLog("\nDone.\n");
        } catch (IOException | InterruptedException e) {
            appendLog("\nERROR: " + e.getMessage() + "\n");
        }
    }

    // ── View reports ─────────────────────────────────────────────────────────

    private void updateViewButton() {
        boolean exists = resolveIndexHtml() != null;
        SwingUtilities.invokeLater(() -> viewButton.setEnabled(exists));
    }

    private Path resolveIndexHtml() {
        String workDir = workDirField.getText().trim();
        String advCsv  = advancementField.getText().trim();
        if (workDir.isBlank() || advCsv.isBlank()) return null;
        String filename = Path.of(advCsv).getFileName().toString();
        String stem     = filename.endsWith(".csv") ? filename.substring(0, filename.length() - 4) : filename;
        Path   index    = Path.of(workDir, "output", stem, "index.html");
        return Files.exists(index) ? index : null;
    }

    private void viewReports() {
        Path index = resolveIndexHtml();
        if (index == null) {
            JOptionPane.showMessageDialog(this,
                    "No report found. Run Generate Reports first.",
                    "Not Found", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try { Desktop.getDesktop().browse(index.toUri()); }
        catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                    "Could not open browser:\n" + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+");

    /**
     * Adds mouse listeners to {@link #logArea} so that {@code https://...} URLs
     * are clickable (opens the system browser) and show a hand cursor on hover.
     */
    private void installUrlClickHandler() {
        logArea.addMouseMotionListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseMoved(java.awt.event.MouseEvent e) {
                logArea.setCursor(urlAtPoint(e.getPoint()) != null
                        ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                        : Cursor.getDefaultCursor());
            }
        });
        logArea.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                String url = urlAtPoint(e.getPoint());
                if (url != null) {
                    try { Desktop.getDesktop().browse(URI.create(url)); }
                    catch (Exception ex) {
                        appendLog("Could not open browser: " + ex.getMessage() + "\n");
                    }
                }
            }
        });
    }

    /**
     * Returns the URL string in the log area that contains the character at
     * {@code point}, or {@code null} if the click was not over a URL.
     */
    private String urlAtPoint(java.awt.Point p) {
        int offset = logArea.viewToModel2D(p);
        Matcher m = URL_PATTERN.matcher(logArea.getText());
        while (m.find()) {
            if (m.start() <= offset && offset < m.end()) return m.group();
        }
        return null;
    }

    private void appendLog(String text) {
        SwingUtilities.invokeLater(() -> logArea.append(text));
    }

    private void setStatus(String text, Color color) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText(text);
            statusLabel.setForeground(color);
        });
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    // ── Version check ────────────────────────────────────────────────────────

    /**
     * Checks GitHub for a newer release in a daemon thread and logs the result
     * to the log area. Runs immediately on startup so the user sees it first.
     */
    private void checkForUpdates() {
        Thread t = new Thread(() -> {
            try {
                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(RELEASES_API))
                        .header("Accept", "application/vnd.github+json")
                        .header("User-Agent", "ScoutSight/" + APP_VERSION)
                        .build();
                HttpResponse<String> resp = client.send(request,
                        HttpResponse.BodyHandlers.ofString());

                if (resp.statusCode() == 200) {
                    String body      = resp.body();
                    String tagName   = extractJsonString(body, "tag_name");
                    String relName   = extractJsonString(body, "name");
                    String published = extractJsonString(body, "published_at");
                    String dlUrl     = extractDownloadUrl(body);

                    String latestVer  = tagName != null ? tagName.replaceFirst("^[vV]", "") : null;
                    String displayVer = tagName != null ? tagName : "unknown";
                    String pubShort   = published != null && published.length() >= 10
                                        ? published.substring(0, 10) : published;

                    if (latestVer != null && isNewerVersion(latestVer, APP_VERSION)) {
                        appendLog("╔══ Update Available ══════════════════════════════════╗\n");
                        appendLog(" version  : v" + APP_VERSION + "\n");
                        appendLog(" Latest   : " + (relName != null ? relName : displayVer)
                                + (pubShort != null ? "  (" + pubShort + ")" : "") + "\n");
                        if (dlUrl != null) {
                            appendLog(" Download : " + dlUrl + "\n");
                        }
                        appendLog("╚══════════════════════════════════════════════════════╝\n\n");
                    } else {
                        appendLog("ScoutSight v" + APP_VERSION + " — Up to date"
                                + (pubShort != null ? " (latest release: " + pubShort + ")" : "")
                                + "\n\n");
                    }
                } else if (resp.statusCode() == 404) {
                    appendLog("ScoutSight v" + APP_VERSION + " — (No releases published yet on GitHub)\n\n");
                } else {
                    appendLog("ScoutSight v" + APP_VERSION
                            + " — Version check failed (HTTP " + resp.statusCode() + ")\n\n");
                }
            } catch (Exception e) {
                appendLog("ScoutSight v" + APP_VERSION
                        + " — Version check unavailable (no network or GitHub unreachable)\n\n");
            }
        }, "version-check");
        t.setDaemon(true);
        t.start();
    }

    /** Extracts a simple string value from a JSON body by key name using regex. */
    private static String extractJsonString(String json, String key) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"\\\\]*)\"")
                           .matcher(json);
        return m.find() ? m.group(1) : null;
    }

    /** Extracts the first {@code browser_download_url} value from the release JSON. */
    private static String extractDownloadUrl(String json) {
        Matcher m = Pattern.compile("\"browser_download_url\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        return m.find() ? m.group(1) : null;
    }

    /**
     * Returns {@code true} if {@code latest} is a higher semantic version than
     * {@code current}. Strips non-numeric qualifiers (SNAPSHOT, -rc, etc.) then
     * compares dot-separated segments numerically.
     */
    private static boolean isNewerVersion(String latest, String current) {
        try {
            int[] l = parseVersionSegments(latest);
            int[] c = parseVersionSegments(current);
            int len = Math.max(l.length, c.length);
            for (int i = 0; i < len; i++) {
                int lv = i < l.length ? l[i] : 0;
                int cv = i < c.length ? c[i] : 0;
                if (lv > cv) return true;
                if (lv < cv) return false;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /** Splits a version string into numeric segments, ignoring qualifiers. */
    private static int[] parseVersionSegments(String ver) {
        String[] parts = ver.replaceAll("[^0-9.]", " ").trim().split("[\\s.]+");
        int[] result = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try { result[i] = Integer.parseInt(parts[i]); }
            catch (NumberFormatException ignored) { result[i] = 0; }
        }
        return result;
    }

    // ── Camp model ───────────────────────────────────────────────────────────

    record CampEntry(String displayName, String stem) {
        @Override public String toString() { return displayName; }
    }
}
