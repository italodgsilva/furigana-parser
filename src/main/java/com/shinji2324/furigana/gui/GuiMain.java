package com.shinji2324.furigana.gui;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.metal.MetalLookAndFeel;
import javax.swing.plaf.nimbus.NimbusLookAndFeel;
import java.awt.GraphicsEnvironment;

/**
 * Entry point of the graphical interface.
 *
 * <p>Unlike the CLI, it takes no arguments. The window is built on the Swing
 * event dispatch thread, preferring Nimbus and falling back to the cross-platform
 * Metal look-and-feel when it cannot be initialized.
 */
public final class GuiMain {

    private GuiMain() {
        // Entry point: must not be instantiated.
    }

    /**
     * Starts the graphical interface.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("The graphical interface needs a display. "
                    + "Use the command-line version instead.");
            return;
        }
        SwingUtilities.invokeLater(() -> {
            installLookAndFeel();
            new MainWindow().setVisible(true);
        });
    }

    /**
     * Selects the Nimbus look-and-feel, falling back to Metal when it cannot be
     * initialized.
     *
     * <p>Nimbus is instantiated directly (rather than by class name) so that the
     * native image does not need reflection for it. Some reduced runtimes — the
     * native image in particular — do not carry the font configuration files
     * Nimbus reads, so the failure is caught and Metal, which GraalVM registers
     * out of the box, is used instead.
     */
    private static void installLookAndFeel() {
        try {
            UIManager.setLookAndFeel(new NimbusLookAndFeel());
        } catch (Throwable nimbusFailure) {
            try {
                UIManager.setLookAndFeel(new MetalLookAndFeel());
            } catch (Throwable ignored) {
                // Keep the current look-and-feel when even Metal fails.
            }
        }
    }
}
