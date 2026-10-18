package com.shinji2324.furigana.gui;

import com.shinji2324.furigana.core.FuriganaOptions;
import com.shinji2324.furigana.core.FuriganaService;
import com.shinji2324.furigana.mpv.MpvIntegration;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Single-window graphical interface of the program.
 *
 * <p>Collects the input/output paths and the options and delegates the work to
 * {@link GuiController}; all the processing runs off the event dispatch thread
 * (through {@link SwingWorker}) so the window stays responsive during tokenization,
 * the Jisho lookups, the font download and the subsetting.
 */
public class MainWindow extends JFrame {

    private final GuiController controller;
    private final GuiSettings settings = GuiSettings.defaultLocation();

    private final JTextField inputField = new JTextField(30);
    private final JTextField outputField = new JTextField(30);
    private final JTextField videoField = new JTextField(30);
    private final JTextField embedPathField = new JTextField(22);
    private final JTextField colorField = new JTextField(10);

    private final JRadioButton assFormat = new JRadioButton("ASS", true);
    private final JRadioButton jsonFormat = new JRadioButton("JSON");

    private final JCheckBox stripCheck = new JCheckBox("Strip parentheses");
    private final JCheckBox embedCheck = new JCheckBox("Embed font");
    private final JCheckBox subsetCheck = new JCheckBox("Subset font");

    private final JButton embedBrowseButton = new JButton("Browse…");
    private final JButton colorButton = new JButton("Pick…");
    private final JLabel colorSwatch = new JLabel();
    private final JButton generateButton = new JButton("Generate");
    private final JButton playButton = new JButton("Play in MPV");
    private final JButton openFolderButton = new JButton("Open folder");

    private final JProgressBar progress = new JProgressBar();
    private final JLabel statusLabel = new JLabel("Ready.");
    private final JTextArea logArea = new JTextArea(8, 60);

    private Path lastOutput;

    /** Creates and lays out the window. */
    public MainWindow() {
        super("Furigana Parser");
        controller = new GuiController(new FuriganaService(), new MpvIntegration(),
                message -> SwingUtilities.invokeLater(() -> appendLog(message)));
        buildUi();
        wireActions();
        updateEnablement();
    }

    // ------------------------------------------------------------------
    // Layout
    // ------------------------------------------------------------------

    private void buildUi() {
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));
        ((JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        add(buildForm(), BorderLayout.NORTH);

        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createTitledBorder("Messages"));
        add(logScroll, BorderLayout.CENTER);

        add(buildActions(), BorderLayout.SOUTH);

        pack();
        setMinimumSize(new Dimension(560, 420));
        setLocationRelativeTo(null);
    }

    private JPanel buildForm() {
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;
        row = addBrowseRow(form, c, row, "Input (SRT):", inputField, this::browseInput);
        row = addBrowseRow(form, c, row, "Output:", outputField, this::browseOutput);
        row = addFormatRow(form, c, row);
        row = addBrowseRow(form, c, row, "Video (MPV):", videoField, this::browseVideo);
        c.gridx = 0;
        c.gridy = row;
        c.gridwidth = 3;
        c.weightx = 1;
        form.add(optionsPanel(), c);
        c.gridwidth = 1;
        return form;
    }

    private JPanel optionsPanel() {
        JPanel options = new JPanel(new GridBagLayout());
        options.setBorder(BorderFactory.createTitledBorder("Options"));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(2, 4, 2, 4);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;

        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 3;
        options.add(stripCheck, c);

        c.gridy = 1;
        c.gridwidth = 1;
        options.add(embedCheck, c);
        c.gridx = 1;
        c.weightx = 1;
        options.add(embedPathField, c);
        c.gridx = 2;
        c.weightx = 0;
        options.add(embedBrowseButton, c);

        c.gridx = 0;
        c.gridy = 2;
        c.gridwidth = 3;
        options.add(subsetCheck, c);

        c.gridy = 3;
        c.gridwidth = 1;
        options.add(new JLabel("Color:"), c);
        c.gridx = 1;
        c.weightx = 1;
        options.add(colorField, c);
        c.gridx = 2;
        c.weightx = 0;
        JPanel colorControls = new JPanel();
        colorSwatch.setPreferredSize(new Dimension(18, 18));
        colorSwatch.setOpaque(true);
        colorSwatch.setBackground(Color.WHITE);
        colorSwatch.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        colorControls.add(colorSwatch);
        colorControls.add(colorButton);
        options.add(colorControls, c);
        return options;
    }

    private JPanel buildActions() {
        JPanel actions = new JPanel(new BorderLayout(4, 4));

        JPanel buttons = new JPanel();
        buttons.add(generateButton);
        buttons.add(playButton);
        buttons.add(openFolderButton);
        actions.add(buttons, BorderLayout.NORTH);

        progress.setIndeterminate(true);
        progress.setVisible(false);
        actions.add(progress, BorderLayout.CENTER);
        actions.add(statusLabel, BorderLayout.SOUTH);
        return actions;
    }

    private int addBrowseRow(JPanel panel, GridBagConstraints c, int row, String label,
                             JTextField field, Runnable action) {
        JButton browse = new JButton("Browse…");
        browse.addActionListener(e -> action.run());

        c.gridx = 0;
        c.gridy = row;
        c.gridwidth = 1;
        c.weightx = 0;
        panel.add(new JLabel(label), c);
        c.gridx = 1;
        c.weightx = 1;
        panel.add(field, c);
        c.gridx = 2;
        c.weightx = 0;
        panel.add(browse, c);
        return row + 1;
    }

    private int addFormatRow(JPanel panel, GridBagConstraints c, int row) {
        ButtonGroup group = new ButtonGroup();
        group.add(assFormat);
        group.add(jsonFormat);
        JPanel formats = new JPanel();
        formats.add(assFormat);
        formats.add(jsonFormat);

        c.gridx = 0;
        c.gridy = row;
        c.gridwidth = 1;
        c.weightx = 0;
        panel.add(new JLabel("Format:"), c);
        c.gridx = 1;
        c.gridwidth = 2;
        panel.add(formats, c);
        c.gridwidth = 1;
        return row + 1;
    }

    // ------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------

    private void wireActions() {
        embedCheck.addActionListener(e -> updateEnablement());
        assFormat.addActionListener(e -> onFormatChanged());
        jsonFormat.addActionListener(e -> onFormatChanged());
        videoField.getDocument().addDocumentListener(new SimpleDocumentListener(this::updateEnablement));
        generateButton.addActionListener(e -> runJob(false));
        playButton.addActionListener(e -> runJob(true));
        openFolderButton.addActionListener(e -> openOutputFolder());
        colorButton.addActionListener(e -> pickColor());
    }

    private void browseInput() {
        Path chosen = chooseFile("Select the SRT subtitle", currentDirectory(), true,
                new FileNameExtensionFilter("SRT subtitles (*.srt)", "srt"));
        if (chosen == null) {
            return;
        }
        inputField.setText(chosen.toString());
        rememberDirectory(chosen);
        if (outputField.getText().isBlank()) {
            outputField.setText(defaultOutput(chosen).toString());
        }
    }

    private void browseOutput() {
        Path suggested = outputField.getText().isBlank()
                ? currentDirectory().resolve("subtitle.furigana.ass")
                : Path.of(outputField.getText().trim()).toAbsolutePath();
        Path chosen = chooseFile("Choose the output file", suggested, false,
                new FileNameExtensionFilter("ASS subtitles (*.ass)", "ass"),
                new FileNameExtensionFilter("JSON model (*.json)", "json"));
        if (chosen != null) {
            outputField.setText(chosen.toString());
            rememberDirectory(chosen);
        }
    }

    private void browseVideo() {
        Path chosen = chooseFile("Select the video", currentDirectory(), true);
        if (chosen != null) {
            videoField.setText(chosen.toString());
            rememberDirectory(chosen);
        }
    }

    private void pickColor() {
        Color initial = parseColor(colorField.getText());
        Color chosen = JColorChooser.showDialog(this, "Choose the subtitle color",
                initial == null ? Color.WHITE : initial);
        if (chosen != null) {
            colorField.setText(String.format("#%02X%02X%02X",
                    chosen.getRed(), chosen.getGreen(), chosen.getBlue()));
            updateSwatch(chosen);
        }
    }

    private void onFormatChanged() {
        String text = outputField.getText().trim();
        if (!text.isEmpty()) {
            outputField.setText(withExtension(Path.of(text),
                    assFormat.isSelected() ? "ass" : "json").toString());
        }
        updateEnablement();
    }

    private void runJob(boolean playing) {
        Path input = pathOrNull(inputField);
        Path output = pathOrNull(outputField);
        Path video = pathOrNull(videoField);
        FuriganaOptions options = currentOptions();

        List<String> errors = controller.validate(input, output, video, options, playing);
        if (!errors.isEmpty()) {
            JOptionPane.showMessageDialog(this, String.join("\n", errors),
                    "Cannot continue", JOptionPane.WARNING_MESSAGE);
            return;
        }

        setBusy(true, playing ? "Generating and opening MPV…" : "Generating…");
        new SwingWorker<Path, Void>() {
            @Override
            protected Path doInBackground() throws Exception {
                return playing
                        ? controller.play(input, video, options)
                        : controller.generate(input, output, options);
            }

            @Override
            protected void done() {
                try {
                    Path result = get();
                    lastOutput = result;
                    openFolderButton.setEnabled(true);
                    appendLog("Done: " + result.toAbsolutePath());
                    setBusy(false, "Done: " + result.getFileName());
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    appendLog("Error: " + cause.getMessage());
                    setBusy(false, "Failed.");
                    JOptionPane.showMessageDialog(MainWindow.this, cause.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void openOutputFolder() {
        if (lastOutput == null) {
            return;
        }
        Path folder = lastOutput.toAbsolutePath().getParent();
        if (folder == null || !Desktop.isDesktopSupported()) {
            return;
        }
        try {
            Desktop.getDesktop().open(folder.toFile());
        } catch (IOException e) {
            appendLog("Could not open the folder: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // State
    // ------------------------------------------------------------------

    private FuriganaOptions currentOptions() {
        String fontText = embedPathField.getText().trim();
        String colorText = colorField.getText().trim();
        return new FuriganaOptions(
                stripCheck.isSelected(),
                embedCheck.isSelected(),
                subsetCheck.isSelected(),
                fontText.isEmpty() ? null : Path.of(fontText),
                colorText.isEmpty() ? null : colorText);
    }

    private void updateEnablement() {
        boolean ass = assFormat.isSelected();
        boolean embed = embedCheck.isSelected();

        embedCheck.setEnabled(ass);
        embedPathField.setEnabled(ass && embed);
        embedBrowseButton.setEnabled(ass && embed);
        subsetCheck.setEnabled(ass && embed);
        colorField.setEnabled(ass);
        colorButton.setEnabled(ass);
        if (!ass) {
            embedCheck.setSelected(false);
            subsetCheck.setSelected(false);
        }
        if (!embed) {
            subsetCheck.setSelected(false);
        }

        playButton.setEnabled(!videoField.getText().isBlank());
    }

    private void setBusy(boolean busy, String status) {
        generateButton.setEnabled(!busy);
        playButton.setEnabled(!busy && !videoField.getText().isBlank());
        embedCheck.setEnabled(!busy && assFormat.isSelected());
        progress.setVisible(busy);
        setCursor(Cursor.getPredefinedCursor(busy ? Cursor.WAIT_CURSOR : Cursor.DEFAULT_CURSOR));
        if (status != null) {
            statusLabel.setText(status);
        }
    }

    private void appendLog(String message) {
        logArea.append(message + System.lineSeparator());
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private void updateSwatch(Color color) {
        colorSwatch.setBackground(color);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Path defaultOutput(Path input) {
        String name = input.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String extension = assFormat.isSelected() ? "ass" : "json";
        Path parent = input.getParent();
        String fileName = base + ".furigana." + extension;
        return parent == null ? Path.of(fileName) : parent.resolve(fileName);
    }

    private static Path withExtension(Path path, String extension) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        return path.resolveSibling(base + "." + extension);
    }

    private static Path pathOrNull(JTextField field) {
        String text = field.getText().trim();
        return text.isEmpty() ? null : Path.of(text);
    }

    private Path chooseFile(String title, Path initial, boolean open, FileNameExtensionFilter... filters) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(title);
        if (initial != null) {
            if (Files.isDirectory(initial)) {
                chooser.setCurrentDirectory(initial.toFile());
            } else {
                chooser.setSelectedFile(initial.toFile());
            }
        }
        for (FileNameExtensionFilter filter : filters) {
            chooser.setFileFilter(filter);
        }
        int result = open ? chooser.showOpenDialog(this) : chooser.showSaveDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return null;
        }
        return chooser.getSelectedFile().toPath();
    }

    private Path currentDirectory() {
        String remembered = settings.lastDirectory();
        if (remembered != null) {
            Path path = Path.of(remembered);
            if (Files.isDirectory(path)) {
                return path;
            }
        }
        return Path.of(System.getProperty("user.home"));
    }

    private void rememberDirectory(Path file) {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            settings.lastDirectory(parent.toString());
        }
    }

    private static Color parseColor(String text) {
        String value = text.trim();
        if (value.startsWith("#")) {
            value = value.substring(1);
        }
        if (value.length() == 6 && value.chars().allMatch(c -> Character.digit(c, 16) >= 0)) {
            return new Color(Integer.parseInt(value, 16));
        }
        return null;
    }

    /**
     * Document listener that runs an action on every change, without the
     * boilerplate of the three required methods.
     */
    private static final class SimpleDocumentListener implements javax.swing.event.DocumentListener {

        private final Runnable action;

        private SimpleDocumentListener(Runnable action) {
            this.action = action;
        }

        @Override
        public void insertUpdate(javax.swing.event.DocumentEvent e) {
            action.run();
        }

        @Override
        public void removeUpdate(javax.swing.event.DocumentEvent e) {
            action.run();
        }

        @Override
        public void changedUpdate(javax.swing.event.DocumentEvent e) {
            action.run();
        }
    }
}
