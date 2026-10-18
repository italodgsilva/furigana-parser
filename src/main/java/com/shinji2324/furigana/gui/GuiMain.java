package com.shinji2324.furigana.gui;

import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.metal.MetalLookAndFeel;
import javax.swing.plaf.nimbus.NimbusLookAndFeel;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Entry point of the graphical interface.
 *
 * <p>Unlike the CLI, it takes no arguments. The window is built on the Swing
 * event dispatch thread, preferring Nimbus and falling back to the cross-platform
 * Metal look-and-feel when it cannot be initialized.
 */
public final class GuiMain {

    /**
     * System property that pins the look-and-feel: {@code nimbus} (fail instead
     * of falling back), {@code metal}, or anything else for the default
     * Nimbus-then-Metal behaviour. It is mainly useful for troubleshooting and
     * for regenerating the native-image metadata, which must cover both
     * look-and-feels.
     */
    private static final String LOOK_AND_FEEL_PROPERTY = "furigana.gui.lookAndFeel";

    private GuiMain() {
        // Entry point: must not be instantiated.
    }

    /**
     * Starts the graphical interface.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        prepareNativeImageRuntime();
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("The graphical interface needs a display. "
                    + "Use the command-line version instead.");
            return;
        }
        installLookAndFeel();
        SwingUtilities.invokeLater(() -> new MainWindow().setVisible(true));
    }

    /**
     * Makes AWT usable inside a GraalVM native image.
     *
     * <p>Native images do not set the {@code java.home} system property, and
     * AWT's {@code FontConfiguration} refuses to start without it. It also
     * probes {@code ${java.home}/lib} for a fontconfig configuration file and
     * only enables its system-fontconfig fallback when that directory exists,
     * so the property is pointed at a throw-away directory that contains an
     * empty {@code lib} directory.
     *
     * <p>On a regular JVM {@code java.home} is always set and this method does
     * nothing.
     */
    private static void prepareNativeImageRuntime() {
        if (System.getProperty("java.home") != null) {
            return;
        }
        try {
            Path home = Files.createTempDirectory("furigana-parser-java-home");
            // Registered before the nested directory so that it is removed
            // last (delete-on-exit runs in reverse registration order).
            home.toFile().deleteOnExit();
            Path lib = Files.createDirectories(home.resolve("lib"));
            lib.toFile().deleteOnExit();
            System.setProperty("java.home", home.toString());
        } catch (IOException unavailable) {
            throw new IllegalStateException(
                    "Could not create a java.home directory for the native image",
                    unavailable);
        }
    }

    /**
     * Selects the Nimbus look-and-feel, falling back to Metal when it cannot be
     * initialized.
     *
     * <p>Nimbus is instantiated directly (rather than by class name) so that the
     * native image does not need reflection for it. Both look-and-feels need the
     * metadata collected with the tracing agent in the native image; the failure
     * is reported instead of silently changing the window's appearance.
     */
    private static void installLookAndFeel() {
        String requested = System.getProperty(LOOK_AND_FEEL_PROPERTY, "");
        if ("metal".equalsIgnoreCase(requested)) {
            setLookAndFeel(new MetalLookAndFeel());
            return;
        }
        try {
            UIManager.setLookAndFeel(new NimbusLookAndFeel());
        } catch (Throwable nimbusFailure) {
            if ("nimbus".equalsIgnoreCase(requested)) {
                throw new IllegalStateException("Nimbus was requested with -D"
                        + LOOK_AND_FEEL_PROPERTY + "=nimbus but could not be initialized",
                        nimbusFailure);
            }
            System.err.println("Nimbus is unavailable (" + nimbusFailure
                    + "); falling back to Metal.");
            setLookAndFeel(new MetalLookAndFeel());
        }
    }

    private static void setLookAndFeel(LookAndFeel lookAndFeel) {
        try {
            UIManager.setLookAndFeel(lookAndFeel);
        } catch (Throwable ignored) {
            // Keep the current look-and-feel when even this one fails.
        }
    }
}
