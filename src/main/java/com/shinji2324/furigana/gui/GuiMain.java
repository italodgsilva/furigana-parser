package com.shinji2324.furigana.gui;

import javax.swing.*;

public final class GuiMain {

    /** System property used to control the look-and-feel. */
    public static final String LOOK_AND_FEEL_PROPERTY = "furigana.gui.lookAndFeel";

    private GuiMain() {
    }

    public static void main(String[] args) {
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("The graphical interface needs a display. "
                    + "Use the command-line version instead.");
            return;
        }
        installLookAndFeel();
        SwingUtilities.invokeLater(() -> new MainWindow().setVisible(true));
    }

    /**
     * Selects the Nimbus look-and-feel, falling back to Metal when it cannot be
     * initialized.
     */
    private static void installLookAndFeel() {
        String requested = System.getProperty(LOOK_AND_FEEL_PROPERTY, "");
        if ("metal".equalsIgnoreCase(requested)) {
            setLookAndFeel(new javax.swing.plaf.metal.MetalLookAndFeel());
            return;
        }
        try {
            UIManager.setLookAndFeel(new javax.swing.plaf.nimbus.NimbusLookAndFeel());
        } catch (Throwable nimbusFailure) {
            if ("nimbus".equalsIgnoreCase(requested)) {
                throw new IllegalStateException("Nimbus was requested with -D"
                        + LOOK_AND_FEEL_PROPERTY + "=nimbus but could not be initialized",
                        nimbusFailure);
            }
            System.err.println("Nimbus is unavailable (" + nimbusFailure
                    + "); falling back to Metal.");
            setLookAndFeel(new javax.swing.plaf.metal.MetalLookAndFeel());
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
