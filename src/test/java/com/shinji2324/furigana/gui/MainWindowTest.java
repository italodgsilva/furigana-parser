package com.shinji2324.furigana.gui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * Smoke test for {@link MainWindow}. It builds and shows the window on the event
 * dispatch thread and is skipped when there is no display (as in CI).
 */
class MainWindowTest {

    @Test
    @DisplayName("The window can be built and shown")
    void buildsAndShows() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display");

        boolean[] visible = new boolean[1];
        SwingUtilities.invokeAndWait(() -> {
            MainWindow window = new MainWindow();
            window.setVisible(true);
            visible[0] = window.isVisible();
            window.dispose();
        });

        assertTrue(visible[0]);
    }
}
