package io.cannonforge.installer;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.List;
import java.util.jar.JarFile;

/**
 * RetroQuest Windows Installer.
 * Self-contained — no external dependencies.
 * Bundles: game fat JAR, data/, bundled JRE (jlink minimal).
 */
@SuppressWarnings("serial")
public class Installer extends JFrame {

    // -------------------------------------------------------------------------
    // Theme
    // -------------------------------------------------------------------------
    static final Color BG        = new Color(10, 10, 14);
    static final Color AMBER     = new Color(255, 176, 0);
    static final Color AMBER_DIM = new Color(160, 100, 0);
    static final Color PANEL_BG  = new Color(18, 16, 10);
    static final Color BORDER    = new Color(100, 60, 0);
    static final Color SEL_BG    = new Color(60, 40, 0);
    static final Color TEXT_WHITE = new Color(220, 210, 180);

    static final Font F_TITLE  = new Font("Courier New", Font.BOLD, 22);
    static final Font F_BODY   = new Font("Courier New", Font.PLAIN, 13);
    static final Font F_LOG    = new Font("Courier New", Font.PLAIN, 11);
    static final Font F_BUTTON = new Font("Courier New", Font.BOLD, 13);

    static final String ASCII_ART =
            " ____  _____ _____ ____   ___  ____  _   _ _____ ____ _____\n" +
            "|  _ \\| ____|_   _|  _ \\ / _ \\/ ___|| | | | ____/ ___|_   _|\n" +
            "| |_) |  _|   | | | |_) | | | \\___ \\| | | |  _| \\___ \\ | |\n" +
            "|  _ <| |___  | | |  _ <| |_| |___) | |_| | |___ ___) || |\n" +
            "|_| \\_\\_____|_|_| |_| \\_\\\\___/|____/ \\___/|_____|____/ |_|\n";

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------
    private final CardLayout cards = new CardLayout();
    private final JPanel     deck  = new JPanel(cards);

    private JTextField dirField;
    private RetroProgressBar progressBar;
    private JTextArea logArea;
    private JCheckBox launchCheckbox;

    private File installDir;

    // -------------------------------------------------------------------------
    // Entry point
    // -------------------------------------------------------------------------
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new Installer().setVisible(true));
    }

    public Installer() {
        setTitle("RetroQuest Installer");
        setSize(560, 400);
        setResizable(false);
        setUndecorated(true);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        loadWindowIcon();

        JPanel chrome = new ChromePanel();
        chrome.setLayout(new BorderLayout());
        chrome.setBorder(BorderFactory.createEmptyBorder(28, 8, 8, 8));
        chrome.add(deck, BorderLayout.CENTER);
        setContentPane(chrome);

        deck.setOpaque(false);
        deck.add(buildWelcomePage(),  "welcome");
        deck.add(buildDirPage(),      "dir");
        deck.add(buildProgressPage(), "progress");
        deck.add(buildFinishPage(),   "finish");

        cards.show(deck, "welcome");
    }

    // -------------------------------------------------------------------------
    // Window icon
    // -------------------------------------------------------------------------

    private void loadWindowIcon() {
        try (InputStream in = getInstallerResourceStream("installer/retroquest_icon.png")) {
            if (in != null) setIconImage(ImageIO.read(in));
        } catch (Exception ignored) {}
    }

    /** Reads a resource from inside the installer JAR. Returns null if not found. */
    private InputStream getInstallerResourceStream(String entryName) {
        try {
            URL loc = Installer.class.getProtectionDomain().getCodeSource().getLocation();
            JarFile jar = new JarFile(new File(loc.toURI()));
            var entry = jar.getEntry(entryName);
            if (entry == null) { jar.close(); return null; }
            // Wrap so that closing the stream also closes the jar
            return new FilterInputStream(jar.getInputStream(entry)) {
                @Override public void close() throws IOException { super.close(); jar.close(); }
            };
        } catch (Exception e) { return null; }
    }

    // -------------------------------------------------------------------------
    // Pages
    // -------------------------------------------------------------------------

    private JPanel buildWelcomePage() {
        RetroPanel p = new RetroPanel();
        p.setLayout(new BorderLayout(0, 12));
        p.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));

        // ASCII art
        JTextArea art = new JTextArea(ASCII_ART);
        art.setFont(new Font("Courier New", Font.BOLD, 10));
        art.setForeground(AMBER);
        art.setBackground(PANEL_BG);
        art.setEditable(false);
        art.setFocusable(false);
        p.add(art, BorderLayout.NORTH);

        // Sub-title
        JLabel sub = new JLabel("v1.1.0  \u2014  WINDOWS INSTALLER", SwingConstants.CENTER);
        sub.setFont(F_BODY);
        sub.setForeground(AMBER_DIM);
        p.add(sub, BorderLayout.CENTER);

        // Description
        JTextArea desc = new JTextArea(
                "This wizard will install RetroQuest and RetroForge on your computer.\n\n" +
                "A bundled Java runtime is included — no separate Java installation needed.\n\n" +
                "Click Next to choose the installation directory.");
        desc.setFont(F_BODY);
        desc.setForeground(TEXT_WHITE);
        desc.setBackground(PANEL_BG);
        desc.setEditable(false);
        desc.setFocusable(false);
        desc.setLineWrap(true);
        desc.setWrapStyleWord(true);
        p.add(desc, BorderLayout.SOUTH);

        JPanel buttons = buildButtonRow(
                makeButton("Cancel", e -> System.exit(0)),
                makeButton("Next >", e -> cards.show(deck, "dir")));
        return wrapWithButtons(p, buttons);
    }

    private JPanel buildDirPage() {
        RetroPanel p = new RetroPanel();
        p.setLayout(new BorderLayout(0, 10));
        p.setBorder(BorderFactory.createEmptyBorder(16, 14, 10, 14));

        JLabel title = new JLabel("[ CHOOSE INSTALL DIRECTORY ]");
        title.setFont(F_TITLE);
        title.setForeground(AMBER);
        p.add(title, BorderLayout.NORTH);

        String defPath = System.getenv("LOCALAPPDATA");
        if (defPath == null) defPath = System.getProperty("user.home");
        defPath += File.separator + "RetroQuest";

        dirField = new JTextField(defPath);
        dirField.setFont(F_BODY);
        dirField.setForeground(AMBER);
        dirField.setBackground(new Color(20, 18, 12));
        dirField.setCaretColor(AMBER);
        dirField.setBorder(BorderFactory.createLineBorder(BORDER, 1));

        RetroButton browse = makeButton("Browse...", e -> {
            JFileChooser fc = new JFileChooser(dirField.getText());
            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                dirField.setText(fc.getSelectedFile().getAbsolutePath());
            }
        });

        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setOpaque(false);
        row.add(dirField, BorderLayout.CENTER);
        row.add(browse,   BorderLayout.EAST);

        JLabel note = new JLabel(
                "<html><font color='#A06400'>Installation requires approximately 120 MB of disk space.<br>" +
                "Your saves in saves/ will never be deleted.</font></html>");
        note.setFont(F_BODY);

        JPanel center = new JPanel(new BorderLayout(0, 10));
        center.setOpaque(false);
        center.add(row,  BorderLayout.NORTH);
        center.add(note, BorderLayout.CENTER);
        p.add(center, BorderLayout.CENTER);

        RetroButton install = makeButton("Install", e -> startInstall());
        JPanel buttons = buildButtonRow(
                makeButton("< Back", ev -> cards.show(deck, "welcome")),
                makeButton("Cancel", ev -> System.exit(0)),
                install);
        return wrapWithButtons(p, buttons);
    }

    private JPanel buildProgressPage() {
        RetroPanel p = new RetroPanel();
        p.setLayout(new BorderLayout(0, 8));
        p.setBorder(BorderFactory.createEmptyBorder(16, 14, 10, 14));

        JLabel title = new JLabel("[ INSTALLING ]");
        title.setFont(F_TITLE);
        title.setForeground(AMBER);
        p.add(title, BorderLayout.NORTH);

        progressBar = new RetroProgressBar();
        progressBar.setPreferredSize(new Dimension(0, 28));

        logArea = new JTextArea();
        logArea.setFont(F_LOG);
        logArea.setForeground(AMBER_DIM);
        logArea.setBackground(BG);
        logArea.setEditable(false);
        logArea.setFocusable(false);
        JScrollPane scroll = new JScrollPane(logArea);
        scroll.setBorder(BorderFactory.createLineBorder(BORDER, 1));
        scroll.setBackground(BG);

        JPanel center = new JPanel(new BorderLayout(0, 6));
        center.setOpaque(false);
        center.add(progressBar, BorderLayout.NORTH);
        center.add(scroll,      BorderLayout.CENTER);
        p.add(center, BorderLayout.CENTER);

        return p; // no button row — buttons added dynamically on finish
    }

    private JPanel buildFinishPage() {
        RetroPanel p = new RetroPanel();
        p.setLayout(new BorderLayout(0, 12));
        p.setBorder(BorderFactory.createEmptyBorder(16, 14, 10, 14));

        JLabel title = new JLabel("[ INSTALLATION COMPLETE ]");
        title.setFont(F_TITLE);
        title.setForeground(AMBER);
        p.add(title, BorderLayout.NORTH);

        JTextArea msg = new JTextArea(
                "RetroQuest has been installed successfully.\n\n" +
                "Shortcuts have been created on your Desktop.\n" +
                "You can also launch the game from the install directory\n" +
                "using retroquest.bat or retroforge.bat.");
        msg.setFont(F_BODY);
        msg.setForeground(TEXT_WHITE);
        msg.setBackground(PANEL_BG);
        msg.setEditable(false);
        msg.setFocusable(false);
        msg.setLineWrap(true);
        msg.setWrapStyleWord(true);
        p.add(msg, BorderLayout.CENTER);

        launchCheckbox = new JCheckBox("Launch RetroQuest now");
        launchCheckbox.setFont(F_BODY);
        launchCheckbox.setForeground(AMBER);
        launchCheckbox.setBackground(PANEL_BG);
        launchCheckbox.setSelected(true);
        p.add(launchCheckbox, BorderLayout.SOUTH);

        JPanel buttons = buildButtonRow(
                makeButton("Finish", e -> onFinish()));
        return wrapWithButtons(p, buttons);
    }

    // -------------------------------------------------------------------------
    // Install logic
    // -------------------------------------------------------------------------

    private void startInstall() {
        String path = dirField.getText().trim();
        if (path.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please choose a directory.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        installDir = new File(path);
        cards.show(deck, "progress");

        new InstallWorker().execute();
    }

    private class InstallWorker extends SwingWorker<Void, String> {

        @Override
        protected Void doInBackground() throws Exception {
            installDir.mkdirs();
            publish("Starting installation to: " + installDir.getAbsolutePath());

            setProgress(0);
            extractGameJar();
            setProgress(5);
            extractIcon();
            setProgress(6);
            extractDataDir();
            setProgress(20);
            extractJre();
            setProgress(90);
            createSavesDir();
            setProgress(91);
            writeLaunchers();
            setProgress(95);
            writeUninstaller();
            setProgress(96);
            createShortcuts();
            setProgress(100);

            publish("Installation complete.");
            return null;
        }

        @Override
        protected void process(List<String> chunks) {
            for (String line : chunks) {
                logArea.append(line + "\n");
                logArea.setCaretPosition(logArea.getDocument().getLength());
            }
        }

        @Override
        protected void done() {
            try {
                get(); // rethrow any exception
                progressBar.setValue(100);
                cards.show(deck, "finish");
            } catch (Exception ex) {
                String msg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                JOptionPane.showMessageDialog(Installer.this,
                        "Installation failed:\n" + msg, "Error", JOptionPane.ERROR_MESSAGE);
            }
        }

        private void extractGameJar() throws Exception {
            publish("Extracting game JAR...");
            File dest = new File(installDir, "retroquest.jar");
            extractResource("installer/retroquest-1.1.0-fat.jar", dest);
        }

        private void extractIcon() throws Exception {
            publish("Extracting icon...");
            extractResource("installer/retroquest.ico", new File(installDir, "retroquest.ico"));
        }

        private void extractDataDir() throws Exception {
            publish("Extracting data files...");
            List<String> entries = listInstallerResources("installer/data/");
            for (String entry : entries) {
                String rel = entry.substring("installer/data/".length());
                File dest = new File(installDir, "data" + File.separator + rel.replace('/', File.separatorChar));
                dest.getParentFile().mkdirs();
                extractResource(entry, dest);
                publish("  data/" + rel);
            }
        }

        private void extractJre() throws Exception {
            publish("Extracting bundled JRE (this may take a moment)...");
            List<String> entries = listInstallerResources("installer/jre/");
            int total = entries.size();
            int i = 0;
            for (String entry : entries) {
                String rel = entry.substring("installer/jre/".length());
                File dest = new File(installDir, "jre" + File.separator + rel.replace('/', File.separatorChar));
                dest.getParentFile().mkdirs();
                extractResource(entry, dest);
                i++;
                // progress from 20→90 spread across JRE files
                setProgress(20 + (70 * i / total));
            }
        }

        private void createSavesDir() {
            File saves = new File(installDir, "saves");
            saves.mkdirs();
            publish("Created saves/ directory.");
        }

        private void writeLaunchers() throws Exception {
            publish("Writing launchers...");
            writeTextFile(new File(installDir, "retroquest.bat"),
                    "@echo off\r\n" +
                    "cd /d \"%~dp0\"\r\n" +
                    "start \"\" \"%~dp0jre\\bin\\javaw.exe\" -jar \"%~dp0retroquest.jar\"\r\n");
            writeTextFile(new File(installDir, "retroforge.bat"),
                    "@echo off\r\n" +
                    "cd /d \"%~dp0\"\r\n" +
                    "start \"\" \"%~dp0jre\\bin\\javaw.exe\" -cp \"%~dp0retroquest.jar\" io.cannonforge.retroquest.editor.RetroForge\r\n");
            publish("  retroquest.bat");
            publish("  retroforge.bat");
        }

        private void writeUninstaller() throws Exception {
            publish("Writing uninstaller...");
            writeTextFile(new File(installDir, "uninstall.bat"),
                    "@echo off\r\n" +
                    "rmdir /s /q \"%~dp0data\"\r\n" +
                    "rmdir /s /q \"%~dp0jre\"\r\n" +
                    "del /f /q \"%~dp0retroquest.jar\"\r\n" +
                    "del /f /q \"%~dp0retroquest.bat\"\r\n" +
                    "del /f /q \"%~dp0retroforge.bat\"\r\n" +
                    "del /f /q \"%~dp0uninstall.bat\"\r\n" +
                    "del /f /q \"%USERPROFILE%\\Desktop\\RetroQuest.lnk\"\r\n" +
                    "del /f /q \"%USERPROFILE%\\Desktop\\RetroForge.lnk\"\r\n" +
                    "echo Your saves in %~dp0saves were preserved.\r\n" +
                    "pause\r\n");
        }

        private void createShortcuts() {
            publish("Creating Desktop shortcuts...");
            String desktop = System.getProperty("user.home") + File.separator + "Desktop";
            String javaW = installDir.getAbsolutePath() + "\\jre\\bin\\javaw.exe";
            String jar   = installDir.getAbsolutePath() + "\\retroquest.jar";
            String ico   = installDir.getAbsolutePath() + "\\retroquest.ico";

            createShortcut(desktop, "RetroQuest",
                    javaW, "-jar " + jar,
                    installDir.getAbsolutePath(),
                    "RetroQuest RPG", ico);
            createShortcut(desktop, "RetroForge",
                    javaW, "-cp " + jar + " io.cannonforge.retroquest.editor.RetroForge",
                    installDir.getAbsolutePath(),
                    "RetroForge Map Editor", ico);
        }

        /** Creates a Windows .lnk shortcut via PowerShell script file. Warns on failure, never aborts. */
        private void createShortcut(String desktop, String name, String target,
                                    String args, String workDir, String desc, String iconPath) {
            String lnk = desktop + "\\" + name + ".lnk";
            File psFile = null;
            try {
                // Write a temporary .ps1 script to avoid command-line quoting issues
                psFile = File.createTempFile("shortcut_", ".ps1");
                psFile.deleteOnExit();
                String script =
                    "$ws = New-Object -ComObject WScript.Shell\r\n" +
                    "$s = $ws.CreateShortcut('" + lnk.replace("'", "''") + "')\r\n" +
                    "$s.TargetPath = '" + target.replace("'", "''") + "'\r\n" +
                    "$s.Arguments = '" + args.replace("'", "''") + "'\r\n" +
                    "$s.WorkingDirectory = '" + workDir.replace("'", "''") + "'\r\n" +
                    "$s.Description = '" + desc.replace("'", "''") + "'\r\n" +
                    "$s.IconLocation = '" + iconPath.replace("'", "''") + "'\r\n" +
                    "$s.Save()\r\n";
                writeTextFile(psFile, script);
                Process p = new ProcessBuilder("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass",
                        "-File", psFile.getAbsolutePath())
                        .redirectErrorStream(true)
                        .start();
                String output = new String(p.getInputStream().readAllBytes());
                int exitCode = p.waitFor();
                if (exitCode != 0) {
                    publish("  WARNING: Shortcut " + name + " exit code " + exitCode + ": " + output.trim());
                } else {
                    publish("  Shortcut: " + name + ".lnk");
                }
            } catch (Exception e) {
                publish("  WARNING: Could not create shortcut for " + name + ": " + e.getMessage());
            } finally {
                if (psFile != null) psFile.delete();
            }
        }

        // ------------------------------------------------------------------
        // Utilities
        // ------------------------------------------------------------------

        private void extractResource(String entryName, File dest) throws Exception {
            URL loc = Installer.class.getProtectionDomain().getCodeSource().getLocation();
            try (JarFile jar = new JarFile(new File(loc.toURI()));
                 InputStream in = jar.getInputStream(jar.getEntry(entryName));
                 OutputStream out = new FileOutputStream(dest)) {
                in.transferTo(out);
            }
        }

        private List<String> listInstallerResources(String prefix) throws Exception {
            URL loc = Installer.class.getProtectionDomain().getCodeSource().getLocation();
            List<String> result = new ArrayList<>();
            try (JarFile jar = new JarFile(new File(loc.toURI()))) {
                jar.entries().asIterator().forEachRemaining(e -> {
                    if (!e.isDirectory() && e.getName().startsWith(prefix)) {
                        result.add(e.getName());
                    }
                });
            }
            Collections.sort(result);
            return result;
        }

        private void writeTextFile(File f, String content) throws Exception {
            f.getParentFile().mkdirs();
            try (FileWriter w = new FileWriter(f)) {
                w.write(content);
            }
            // Make .bat files executable on non-Windows just in case
            f.setExecutable(true);
        }
    }

    // -------------------------------------------------------------------------
    // Finish action
    // -------------------------------------------------------------------------

    private void onFinish() {
        if (launchCheckbox != null && launchCheckbox.isSelected() && installDir != null) {
            try {
                File javaw = new File(installDir, "jre/bin/javaw.exe");
                File jar   = new File(installDir, "retroquest.jar");
                if (javaw.exists() && jar.exists()) {
                    new ProcessBuilder(javaw.getAbsolutePath(), "-jar", jar.getAbsolutePath())
                            .directory(installDir)
                            .start();
                }
            } catch (Exception e) {
                // best-effort
            }
        }
        System.exit(0);
    }

    // -------------------------------------------------------------------------
    // UI helpers
    // -------------------------------------------------------------------------

    private RetroButton makeButton(String text, ActionListener action) {
        RetroButton b = new RetroButton(text);
        b.addActionListener(action);
        return b;
    }

    private JPanel buildButtonRow(JButton... buttons) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        row.setOpaque(false);
        for (JButton b : buttons) row.add(b);
        return row;
    }

    private JPanel wrapWithButtons(JPanel content, JPanel buttons) {
        JPanel wrap = new JPanel(new BorderLayout(0, 6));
        wrap.setOpaque(false);
        wrap.add(content, BorderLayout.CENTER);
        wrap.add(buttons, BorderLayout.SOUTH);
        return wrap;
    }

    // -------------------------------------------------------------------------
    // Custom components
    // -------------------------------------------------------------------------

    /** Outer chrome — draws BG, amber frame border, scanlines, and title bar. */
    class ChromePanel extends JPanel {
        ChromePanel() { setBackground(BG); }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth(), h = getHeight();

            // Scanlines
            g2.setColor(new Color(0, 0, 0, 25));
            for (int y = 0; y < h; y += 4) {
                g2.drawLine(0, y, w, y);
            }

            // Amber border 3px
            g2.setColor(AMBER);
            g2.setStroke(new BasicStroke(3f));
            g2.drawRect(1, 1, w - 2, h - 2);

            // Title bar background
            g2.setColor(new Color(14, 12, 6));
            g2.fillRect(3, 3, w - 6, 22);

            // Title text
            g2.setFont(new Font("Courier New", Font.BOLD, 12));
            g2.setColor(AMBER);
            String title = "[ RETROQUEST INSTALLER ]";
            FontMetrics fm = g2.getFontMetrics();
            int tx = (w - fm.stringWidth(title)) / 2;
            g2.drawString(title, tx, 18);

            // Close button
            g2.setColor(AMBER_DIM);
            g2.drawString("[X]", w - 36, 18);
        }

        @Override
        protected void paintChildren(Graphics g) {
            super.paintChildren(g);
        }
    }

    /** A panel with PANEL_BG fill and inset BORDER rectangle. */
    static class RetroPanel extends JPanel {
        RetroPanel() { setBackground(PANEL_BG); }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            g.setColor(BORDER);
            g.drawRect(8, 8, getWidth() - 17, getHeight() - 17);
        }
    }

    /** Amber-themed button with hover effect. */
    static class RetroButton extends JButton {
        private boolean hovered = false;

        RetroButton(String text) {
            super(text);
            setFont(F_BUTTON);
            setForeground(AMBER);
            setBackground(PANEL_BG);
            setBorder(BorderFactory.createLineBorder(AMBER, 1));
            setFocusPainted(false);
            setContentAreaFilled(false);
            setOpaque(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hovered = true;  repaint(); }
                @Override public void mouseExited(MouseEvent e)  { hovered = false; repaint(); }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            g.setColor(hovered ? SEL_BG : PANEL_BG);
            g.fillRect(0, 0, getWidth(), getHeight());
            super.paintComponent(g);
        }
    }

    /** Custom retro progress bar — amber on dark, leading edge highlight. */
    static class RetroProgressBar extends JComponent {
        private int value = 0;

        void setValue(int v) {
            this.value = Math.max(0, Math.min(100, v));
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            int w = getWidth(), h = getHeight();

            // Background
            g2.setColor(PANEL_BG);
            g2.fillRect(0, 0, w, h);

            // Fill
            int fillW = (int) ((w - 2) * value / 100.0);
            if (fillW > 0) {
                g2.setColor(SEL_BG);
                g2.fillRect(1, 1, fillW, h - 2);
                // Leading edge
                g2.setColor(AMBER);
                g2.drawLine(fillW, 1, fillW, h - 2);
            }

            // Border
            g2.setColor(AMBER);
            g2.setStroke(new BasicStroke(1f));
            g2.drawRect(0, 0, w - 1, h - 1);

            // Percent label
            String pct = value + "%";
            g2.setFont(F_LOG);
            FontMetrics fm = g2.getFontMetrics();
            g2.setColor(AMBER);
            g2.drawString(pct, (w - fm.stringWidth(pct)) / 2, h / 2 + fm.getAscent() / 2 - 1);
        }
    }

    // -------------------------------------------------------------------------
    // Close button handling (click [X] label area)
    // -------------------------------------------------------------------------
    {
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                // [X] is drawn at roughly x=(W-36) to W, y=3 to 25
                int x = e.getX(), y = e.getY();
                int w = getWidth();
                if (x >= w - 40 && x <= w - 4 && y >= 3 && y <= 24) {
                    System.exit(0);
                }
            }
        });

        // Drag to move undecorated window
        final int[] drag = {0, 0};
        addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                drag[0] = e.getX(); drag[1] = e.getY();
            }
        });
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseDragged(MouseEvent e) {
                Point loc = getLocation();
                setLocation(loc.x + e.getX() - drag[0], loc.y + e.getY() - drag[1]);
            }
        });
    }
}
