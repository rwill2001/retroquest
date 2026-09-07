package io.cannonforge.retroquest.dialog;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.Timer;

import io.cannonforge.retroquest.core.DisplayScale;
import io.cannonforge.retroquest.core.Fonts;
import io.cannonforge.retroquest.core.Retroquest;
import io.cannonforge.retroquest.core.SoundManager;
import io.cannonforge.retroquest.model.Player;

/**
 * Opening crawl shown once when a brand-new character is created.
 *
 * <p>Skippable from the first frame: the first key press (or click) dumps the rest
 * of the crawl instantly, the second begins the game; Escape leaves at once. A
 * restart inside the same JVM session skips the crawl entirely — dying should not
 * cost the player the intro a second time.
 */
@SuppressWarnings("serial")
public class IntroCinematicDialog extends JDialog {

    /** True once the crawl has played in this session — a restart goes straight to the game. */
    private static boolean seenThisSession = false;

    private static final int LINE_MS = 900;

    private final Retroquest game;
    private final Player player;
    private final JTextArea storyArea;
    private final JLabel hint;
    private final List<String> lines = new ArrayList<>();
    private int currentLine = 0;
    private boolean allShown = false;
    private boolean finished = false;
    private Timer typewriterTimer;
    private KeyEventDispatcher anyKeyDispatcher;

    public IntroCinematicDialog(Retroquest game, Player player) { this(game, player, true); }

    /**
     * @param show false builds the dialog laid out but never displayed — the offscreen entry
     *        point {@code RetroRecorder} uses to film it. This dialog is modal, so the public
     *        constructor blocks its caller until the player dismisses it.
     */
    public IntroCinematicDialog(Retroquest game, Player player, boolean show) {
        super(game, true); // modal, no title bar
        this.game = game;
        this.player = player;

        setUndecorated(true);
        setSize(DisplayScale.scaled(820), DisplayScale.scaled(620));
        setLocationRelativeTo(game);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setBackground(Color.BLACK);

        JPanel main = new JPanel(new BorderLayout());
        main.setBackground(Color.BLACK);
        main.setBorder(BorderFactory.createLineBorder(new Color(0, 255, 180), 8));

        // Title
        JLabel title = new JLabel("AQUALONIA", SwingConstants.CENTER);
        title.setFont(Fonts.monoBold(52));
        title.setForeground(new Color(0, 255, 180));
        title.setBorder(BorderFactory.createEmptyBorder(30, 0, 20, 0));
        main.add(title, BorderLayout.NORTH);

        // Story area - retro green CRT style
        storyArea = new JTextArea();
        storyArea.setEditable(false);
        storyArea.setBackground(new Color(0, 10, 0));
        storyArea.setForeground(new Color(0, 255, 140));
        storyArea.setFont(Fonts.mono(18));
        storyArea.setLineWrap(true);
        storyArea.setWrapStyleWord(true);
        storyArea.setMargin(new Insets(30, 40, 30, 40));
        JScrollPane scroll = new JScrollPane(storyArea);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setPreferredSize(new Dimension(0, 0)); // hide scrollbar
        main.add(scroll, BorderLayout.CENTER);

        // Subtitle + skip hint at the bottom. The hint is present from the very first
        // frame so the player never has to guess whether the crawl can be cut short.
        JPanel south = new JPanel();
        south.setBackground(Color.BLACK);
        south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));
        south.setBorder(BorderFactory.createEmptyBorder(10, 0, 22, 0));

        JLabel subtitle = new JLabel("THE UNBOUND", SwingConstants.CENTER);
        subtitle.setFont(Fonts.monoBold(28));
        subtitle.setForeground(new Color(255, 220, 80));
        subtitle.setAlignmentX(CENTER_ALIGNMENT);

        hint = new JLabel("ANY KEY — SKIP AHEAD      ESC — BEGIN NOW", SwingConstants.CENTER);
        hint.setFont(Fonts.mono(14));
        hint.setForeground(new Color(120, 180, 140));
        hint.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        hint.setAlignmentX(CENTER_ALIGNMENT);

        south.add(subtitle);
        south.add(hint);
        main.add(south, BorderLayout.SOUTH);

        setContentPane(main);

        // === THE STORY LINES (dramatic & personal) ===
        lines.add("Long ago the World-Serpent Aqualon dreamed the islands of Aqualonia into existence...");
        lines.add("");
        lines.add("But seven of its own dreams betrayed it.");
        lines.add("They shattered their father into a thousand glowing shards");
        lines.add("and waged endless war across the rising seas.");
        lines.add("");
        lines.add("Mortals became their pawns.");
        lines.add("Cities drown. Monsters walk the land. The tide climbs higher every year.");
        lines.add("");
        lines.add("And then... you were born.");
        lines.add("");
        lines.add("Under the rare Null Alignment — when every god's power canceled out.");
        lines.add("The one soul none of them can touch.");
        lines.add("");
        lines.add("You are the Unbound.");
        lines.add("");
        lines.add("The only mortal they all desperately need.");
        lines.add("The only one who can tip the balance... or break it forever.");
        lines.add("");
        lines.add("The scar on your chest burns as the tide surges around your feet.");
        lines.add("The gods are already whispering your name.");
        lines.add("");
        lines.add("Welcome to Aqualonia, " + player.getName().toUpperCase() + ".");
        lines.add("");
        lines.add("Your story begins now...");

        // Seen it already this session (i.e. the player died and rerolled) — don't
        // charge them for it twice. Straight into the game.
        if (seenThisSession) {
            dispose();
            game.startGame(player, true);
            return;
        }
        seenThisSession = true;

        installSkipHandlers();
        startCinematic();
        setVisible(show);
    }

    // ── Skip handling ─────────────────────────────────────────────────────────

    /**
     * Installs the any-key and click handlers <em>before</em> the crawl starts, so the
     * very first frame is already skippable.
     */
    private void installSkipHandlers() {
        anyKeyDispatcher = e -> {
            if (e.getID() != KeyEvent.KEY_PRESSED) return false;
            if (e.getKeyCode() == KeyEvent.VK_ESCAPE) finishIntro();
            else                                     advance();
            return true;   // consume so the keystroke doesn't leak into the game
        };
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addKeyEventDispatcher(anyKeyDispatcher);

        addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { advance(); }
        });

        setFocusable(true);
        requestFocusInWindow();
    }

    /** First press reveals the rest of the crawl; the next one starts the game. */
    private void advance() {
        if (allShown) { finishIntro(); return; }
        if (typewriterTimer != null) typewriterTimer.stop();
        while (currentLine < lines.size()) appendLine(lines.get(currentLine++));
        showAllShown();
    }

    private void startCinematic() {
        SoundManager.getInstance().fadeOutTitleTheme(400,
                () -> SoundManager.getInstance().playIntroTheme());

        storyArea.setText("");

        typewriterTimer = new Timer(LINE_MS, e -> {
            // Blank lines are paragraph spacing, not beats — emit them with the next
            // real line instead of burning a full tick on each. That alone cut about
            // a third of the original 31-second crawl.
            while (currentLine < lines.size() && lines.get(currentLine).isEmpty()) {
                appendLine(lines.get(currentLine++));
            }
            if (currentLine < lines.size()) {
                appendLine(lines.get(currentLine++));
            } else {
                typewriterTimer.stop();
                showAllShown();
            }
        });
        typewriterTimer.setInitialDelay(500);
        typewriterTimer.start();
    }

    private void appendLine(String line) {
        if (!line.isEmpty()) {
            storyArea.append(line + "\n\n");
            storyArea.setCaretPosition(storyArea.getDocument().getLength());
        }
    }

    private void showAllShown() {
        if (allShown) return;
        allShown = true;
        hint.setText("PRESS ANY KEY TO BEGIN YOUR JOURNEY");
        hint.setFont(Fonts.monoBold(18));
        hint.setForeground(new Color(255, 255, 100));
        revalidate();
        repaint();
    }

    private void finishIntro() {
        if (finished) return;    // the dispatcher and the mouse listener can both fire
        finished = true;

        if (anyKeyDispatcher != null) {
            KeyboardFocusManager.getCurrentKeyboardFocusManager()
                    .removeKeyEventDispatcher(anyKeyDispatcher);
            anyKeyDispatcher = null;
        }

        if (typewriterTimer != null) typewriterTimer.stop();
        SoundManager.getInstance().stopIntroTheme();
        dispose();
        game.startGame(player, true);
    }
}
