package io.cannonforge.retroquest.core;

import java.awt.AWTEvent;
import java.awt.EventQueue;
import java.awt.Toolkit;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Crash and diagnostic logging for shipped builds.
 *
 * <p>The installed launcher starts the game with {@code javaw.exe}, which has no
 * console: every {@code printStackTrace()} and {@code System.err} write in the
 * codebase goes nowhere, so a crash in the field leaves no trace at all. Calling
 * {@link #install()} once at startup fixes that by:
 *
 * <ol>
 *   <li>opening {@code logs/retroquest.log} next to the game data
 *       ({@code user.dir}), rotating it to {@code retroquest.log.1} once it passes
 *       {@link #MAX_BYTES};</li>
 *   <li>teeing {@link System#err} and {@link System#out} into it, so the 23
 *       existing {@code printStackTrace} calls and 26 {@code System.err} writes
 *       land in the file <em>and</em> still reach the console when there is one;</li>
 *   <li>installing a default {@link Thread.UncaughtExceptionHandler}; and</li>
 *   <li>wrapping the AWT event queue so exceptions thrown on the event-dispatch
 *       thread — where nearly all of this game's work happens — are logged with a
 *       marker instead of vanishing.</li>
 * </ol>
 *
 * <p>Everything here is best-effort: if the log file cannot be opened (read-only
 * install directory, for instance) the game runs exactly as before.
 *
 * <p><b>Wiring:</b> {@code Retroquest.main} must call {@code CrashLogger.install();}
 * as its first statement, before any Swing class is touched.
 */
public final class CrashLogger {

    private CrashLogger() {}

    /** Roll the log over at 2 MB; one previous generation is kept. */
    private static final long MAX_BYTES = 2L * 1024 * 1024;

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private static PrintStream logStream;
    private static boolean installed;

    /**
     * Installs the log file and the uncaught-exception handlers. Idempotent —
     * repeated calls do nothing.
     */
    public static synchronized void install() {
        if (installed) return;
        installed = true;

        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;

        try {
            File dir = new File(System.getProperty("user.dir"), "logs");
            dir.mkdirs();
            File file = new File(dir, "retroquest.log");
            rotateIfNeeded(file);
            logStream = new PrintStream(new FileOutputStream(file, true), true, StandardCharsets.UTF_8);
        } catch (Exception e) {
            // No log file — leave stderr alone and keep going.
            originalErr.println("[CrashLogger] could not open logs/retroquest.log: " + e);
            logStream = null;
        }

        if (logStream != null) {
            logStream.println();
            logStream.println("=== RetroQuest session " + ZonedDateTime.now().format(STAMP) + " ===");
            logStream.println("    java " + System.getProperty("java.version")
                    + " (" + System.getProperty("java.vendor") + ")"
                    + "  os " + System.getProperty("os.name") + " " + System.getProperty("os.arch")
                    + "  dir " + System.getProperty("user.dir"));

            System.setErr(new PrintStream(new Tee(originalErr, logStream), true, StandardCharsets.UTF_8));
            System.setOut(new PrintStream(new Tee(originalOut, logStream), true, StandardCharsets.UTF_8));
        }

        Thread.setDefaultUncaughtExceptionHandler(
                (t, e) -> logThrowable("uncaught on thread \"" + t.getName() + "\"", e));

        installEdtHandler();
    }

    /**
     * Wraps the system event queue so a throwable escaping an event handler is
     * logged rather than silently printed to a console that does not exist.
     * Dispatch continues afterwards, exactly as {@code EventDispatchThread} does.
     */
    private static void installEdtHandler() {
        EventQueue.invokeLater(() -> {
            try {
                Toolkit.getDefaultToolkit().getSystemEventQueue().push(new EventQueue() {
                    @Override
                    protected void dispatchEvent(AWTEvent event) {
                        try {
                            super.dispatchEvent(event);
                        } catch (Throwable t) {
                            logThrowable("uncaught on AWT event-dispatch thread", t);
                        }
                    }
                });
            } catch (Throwable t) {
                logThrowable("could not install the AWT exception handler", t);
            }
        });
    }

    /** Writes a timestamped stack trace to the log (and the console, via the tee). */
    public static void logThrowable(String context, Throwable t) {
        PrintStream target = System.err;
        synchronized (CrashLogger.class) {
            target.println("[" + ZonedDateTime.now().format(STAMP) + "] " + context + ":");
            t.printStackTrace(target);
            target.flush();
        }
    }

    /** Writes a timestamped one-line note to the log. */
    public static void log(String message) {
        System.err.println("[" + ZonedDateTime.now().format(STAMP) + "] " + message);
    }

    private static void rotateIfNeeded(File file) {
        if (!file.isFile() || file.length() < MAX_BYTES) return;
        File prev = new File(file.getParentFile(), file.getName() + ".1");
        prev.delete();
        if (!file.renameTo(prev)) file.delete(); // last resort: start fresh
    }

    /** Fans one stream's bytes out to the console and the log file. */
    private static final class Tee extends OutputStream {
        private final OutputStream a, b;

        Tee(OutputStream a, OutputStream b) { this.a = a; this.b = b; }

        @Override public void write(int x) throws IOException {
            a.write(x);
            try { b.write(x); } catch (IOException ignored) { /* log file gone; console still works */ }
        }

        @Override public void write(byte[] buf, int off, int len) throws IOException {
            a.write(buf, off, len);
            try { b.write(buf, off, len); } catch (IOException ignored) { }
        }

        @Override public void flush() throws IOException {
            a.flush();
            try { b.flush(); } catch (IOException ignored) { }
        }
    }
}
