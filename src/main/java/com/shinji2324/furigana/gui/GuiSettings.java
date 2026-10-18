package com.shinji2324.furigana.gui;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Persistent settings of the graphical interface.
 *
 * <p>Kept deliberately simple: a properties file, read and written on demand,
 * instead of {@code java.util.prefs}. The preferences API needs JNI on Windows
 * (which complicates the native image), while a plain file works the same way on
 * every platform and degrades silently when it cannot be read or written.
 */
class GuiSettings {

    /** Property that stores the last directory used in the file choosers. */
    private static final String LAST_DIRECTORY = "lastDirectory";

    private final Path file;

    /**
     * Creates the store backed by the given file.
     *
     * @param file properties file
     */
    GuiSettings(Path file) {
        this.file = file;
    }

    /**
     * Returns the store at its default location
     * ({@code ~/.furigana-parser/gui.properties}).
     *
     * @return the default store
     */
    static GuiSettings defaultLocation() {
        return new GuiSettings(
                Path.of(System.getProperty("user.home"), ".furigana-parser", "gui.properties"));
    }

    /**
     * Returns the last directory used, or {@code null} when none was stored.
     *
     * @return last directory, or {@code null}
     */
    String lastDirectory() {
        return load().getProperty(LAST_DIRECTORY);
    }

    /**
     * Stores the last directory used.
     *
     * @param directory directory to remember, or {@code null} to forget it
     */
    void lastDirectory(String directory) {
        Properties properties = load();
        if (directory == null) {
            properties.remove(LAST_DIRECTORY);
        } else {
            properties.setProperty(LAST_DIRECTORY, directory);
        }
        save(properties);
    }

    private Properties load() {
        Properties properties = new Properties();
        if (Files.isRegularFile(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                properties.load(in);
            } catch (IOException | IllegalArgumentException e) {
                // Unreadable or malformed settings: start from the defaults.
            }
        }
        return properties;
    }

    private void save(Properties properties) {
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (OutputStream out = Files.newOutputStream(file)) {
                properties.store(out, "Furigana Parser GUI");
            }
        } catch (IOException e) {
            // Settings are a convenience: failing to save them must not break a run.
        }
    }
}
