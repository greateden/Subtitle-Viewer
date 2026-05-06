package app;

import app.io.WordLinksLoader;
import app.model.Sentence;
import app.model.SubtitleLine;
import app.model.WordLinksProject;
import app.ui.SubtitleLinePanel;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.event.ChangeEvent;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class SubtitleViewerApp {
    private static final DecimalFormat SECONDS_FORMAT = new DecimalFormat("0.00");
    private static final int TIMER_DELAY_MS = 40;
    private static final Color WINDOW_BG = new Color(18, 18, 24);
    private static final Color CONTROL_BG = new Color(34, 34, 42);
    private static final int MAX_VISIBLE_LINES = 3;

    private final JFrame frame = new JFrame("Wordlinks Subtitle Viewer");
    private final JLabel fileLabel = new JLabel("No JSON loaded");
    private final JLabel currentTimeLabel = new JLabel("00:00.00");
    private final JLabel activeSentenceLabel = new JLabel("No active subtitle");
    private final JLabel offsetLabel = new JLabel("Offset: 0 ms");
    private final JLabel speedLabel = new JLabel("Speed: 1.00x");
    private final JButton playPauseButton = new JButton("Play");
    private final JSlider timelineSlider = new JSlider();
    private final JSlider offsetSlider = new JSlider(-5000, 5000, 0);
    private final JTextField customSpeedField = new JTextField("1.0", 5);
    private final List<LineSlot> lineSlots = new ArrayList<>();
    private final Timer playbackTimer;

    private WordLinksProject project;
    private Path currentFile;
    private double playbackSeconds;
    private double totalSeconds;
    private boolean playing;
    private boolean scrubbing;
    private long lastTickMillis;
    private double playbackSpeed = 1.0;

    public SubtitleViewerApp() {
        playbackTimer = new Timer(TIMER_DELAY_MS, event -> advancePlayback());
        buildUi();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            applySystemLookAndFeel();
            SubtitleViewerApp app = new SubtitleViewerApp();
            app.show();

            if (args.length > 0) {
                app.loadProject(Path.of(args[0]));
            }
        });
    }

    private static void applySystemLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }
    }

    private void buildUi() {
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setMinimumSize(new Dimension(1100, 700));
        frame.getContentPane().setBackground(WINDOW_BG);
        frame.setLayout(new BorderLayout(18, 18));
        frame.add(buildTopBar(), BorderLayout.NORTH);
        frame.add(buildSubtitleArea(), BorderLayout.CENTER);
        frame.add(buildControls(), BorderLayout.SOUTH);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                playbackTimer.stop();
            }
        });
    }

    private JPanel buildTopBar() {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBackground(WINDOW_BG);
        panel.setBorder(BorderFactory.createEmptyBorder(16, 16, 0, 16));

        JButton loadButton = new JButton("Load JSON");
        loadButton.addActionListener(event -> chooseFile());

        fileLabel.setForeground(Color.WHITE);
        fileLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));

        panel.add(loadButton, BorderLayout.WEST);
        panel.add(fileLabel, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildSubtitleArea() {
        JPanel container = new JPanel();
        container.setBackground(WINDOW_BG);
        container.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));

        for (int i = 0; i < MAX_VISIBLE_LINES; i++) {
            SubtitleLinePanel textPane = new SubtitleLinePanel();
            JLabel label = new JLabel("Language", SwingConstants.CENTER);
            label.setForeground(new Color(190, 190, 200));
            label.setFont(new Font("SansSerif", Font.BOLD, 15));

            JPanel slotPanel = new JPanel(new BorderLayout(0, 6));
            slotPanel.setBackground(CONTROL_BG);
            slotPanel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(58, 58, 68), 1, true),
                    BorderFactory.createEmptyBorder(10, 10, 10, 10)
            ));
            slotPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 180));
            slotPanel.add(label, BorderLayout.NORTH);
            slotPanel.add(textPane, BorderLayout.CENTER);

            lineSlots.add(new LineSlot(slotPanel, label, textPane));
            container.add(slotPanel);
            if (i < MAX_VISIBLE_LINES - 1) {
                container.add(Box.createVerticalStrut(12));
            }
        }

        return container;
    }

    private JPanel buildControls() {
        JPanel outer = new JPanel();
        outer.setBackground(WINDOW_BG);
        outer.setBorder(BorderFactory.createEmptyBorder(0, 16, 16, 16));
        outer.setLayout(new BoxLayout(outer, BoxLayout.Y_AXIS));

        JPanel infoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 0));
        infoPanel.setBackground(WINDOW_BG);
        currentTimeLabel.setForeground(Color.WHITE);
        activeSentenceLabel.setForeground(new Color(210, 210, 220));
        infoPanel.add(currentTimeLabel);
        infoPanel.add(activeSentenceLabel);

        timelineSlider.setMinimum(0);
        timelineSlider.setMaximum(1);
        timelineSlider.addChangeListener(this::handleTimelineChange);

        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        buttonRow.setBackground(WINDOW_BG);

        playPauseButton.setEnabled(false);
        playPauseButton.addActionListener(event -> togglePlayback());

        JButton restartButton = new JButton("Restart");
        restartButton.setEnabled(true);
        restartButton.addActionListener(event -> {
            pausePlayback();
            setPlaybackSeconds(0.0, true);
        });

        JButton stepBackButton = new JButton("-1s");
        stepBackButton.addActionListener(event -> setPlaybackSeconds(playbackSeconds - 1.0, true));

        JButton stepForwardButton = new JButton("+1s");
        stepForwardButton.addActionListener(event -> setPlaybackSeconds(playbackSeconds + 1.0, true));

        buttonRow.add(playPauseButton);
        buttonRow.add(restartButton);
        buttonRow.add(stepBackButton);
        buttonRow.add(stepForwardButton);
        buttonRow.add(Box.createHorizontalStrut(12));

        JLabel offsetText = new JLabel("Subtitle offset");
        offsetText.setForeground(Color.WHITE);
        buttonRow.add(offsetText);

        offsetSlider.setPreferredSize(new Dimension(220, 40));
        offsetSlider.setMajorTickSpacing(1000);
        offsetSlider.setMinorTickSpacing(250);
        offsetSlider.addChangeListener(event -> {
            offsetLabel.setText("Offset: " + offsetSlider.getValue() + " ms");
            renderCurrentState();
        });
        buttonRow.add(offsetSlider);

        offsetLabel.setForeground(new Color(210, 210, 220));
        buttonRow.add(offsetLabel);

        buttonRow.add(Box.createHorizontalStrut(12));

        JLabel speedText = new JLabel("Playback speed");
        speedText.setForeground(Color.WHITE);
        buttonRow.add(speedText);

        JButton normalSpeedButton = new JButton("1.0x");
        normalSpeedButton.addActionListener(event -> setPlaybackSpeed(1.0));
        buttonRow.add(normalSpeedButton);

        JButton mediumSpeedButton = new JButton("0.8x");
        mediumSpeedButton.addActionListener(event -> setPlaybackSpeed(0.8));
        buttonRow.add(mediumSpeedButton);

        JButton slowSpeedButton = new JButton("0.5x");
        slowSpeedButton.addActionListener(event -> setPlaybackSpeed(0.5));
        buttonRow.add(slowSpeedButton);

        customSpeedField.addActionListener(event -> applyCustomSpeed());
        buttonRow.add(customSpeedField);

        JButton setSpeedButton = new JButton("Set");
        setSpeedButton.addActionListener(event -> applyCustomSpeed());
        buttonRow.add(setSpeedButton);

        speedLabel.setForeground(new Color(210, 210, 220));
        buttonRow.add(speedLabel);

        outer.add(infoPanel);
        outer.add(Box.createVerticalStrut(8));
        outer.add(timelineSlider);
        outer.add(Box.createVerticalStrut(8));
        outer.add(buttonRow);
        return outer;
    }

    private void chooseFile() {
        JFileChooser chooser = new JFileChooser(currentFile == null ? Path.of(".").toFile() : currentFile.getParent().toFile());
        chooser.setFileFilter(new FileNameExtensionFilter("JSON files", "json"));
        int result = chooser.showOpenDialog(frame);

        if (result == JFileChooser.APPROVE_OPTION) {
            loadProject(chooser.getSelectedFile().toPath());
        }
    }

    private void loadProject(Path path) {
        try {
            pausePlayback();
            project = WordLinksLoader.load(path);
            currentFile = path;
            totalSeconds = findTotalSeconds(project);
            playbackSeconds = 0.0;
            timelineSlider.setMaximum(Math.max(1, (int) Math.ceil(totalSeconds * 1000)));
            timelineSlider.setValue(0);
            playPauseButton.setEnabled(totalSeconds > 0.0);
            fileLabel.setText(path.getFileName() + " | format: " + safe(project.format()) + " | mode: " + safe(project.mode()));
            frame.setTitle("Wordlinks Subtitle Viewer - " + path.getFileName());
            renderCurrentState();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(frame,
                    "Could not load JSON file:\n" + ex.getMessage(),
                    "Load error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private double findTotalSeconds(WordLinksProject loadedProject) {
        if (loadedProject == null || loadedProject.sentences() == null || loadedProject.sentences().isEmpty()) {
            return 0.0;
        }

        return loadedProject.sentences().stream()
                .map(Sentence::end)
                .max(Comparator.naturalOrder())
                .orElse(0.0);
    }

    private void togglePlayback() {
        if (playing) {
            pausePlayback();
        } else {
            startPlayback();
        }
    }

    private void startPlayback() {
        if (project == null) {
            return;
        }

        if (playbackSeconds >= totalSeconds) {
            setPlaybackSeconds(0.0, true);
        }

        playing = true;
        lastTickMillis = System.currentTimeMillis();
        playbackTimer.start();
        playPauseButton.setText("Pause");
    }

    private void pausePlayback() {
        playing = false;
        playbackTimer.stop();
        playPauseButton.setText("Play");
    }

    private void advancePlayback() {
        if (!playing || scrubbing) {
            return;
        }

        long now = System.currentTimeMillis();
        double delta = (now - lastTickMillis) / 1000.0;
        lastTickMillis = now;
        setPlaybackSeconds(playbackSeconds + (delta * playbackSpeed), false);

        if (playbackSeconds >= totalSeconds) {
            pausePlayback();
        }
    }

    private void handleTimelineChange(ChangeEvent event) {
        if (project == null) {
            return;
        }

        scrubbing = timelineSlider.getValueIsAdjusting();
        if (scrubbing || !playing) {
            setPlaybackSeconds(timelineSlider.getValue() / 1000.0, !scrubbing);
        }

        if (!scrubbing && playing) {
            lastTickMillis = System.currentTimeMillis();
        }
    }

    private void setPlaybackSeconds(double newValue, boolean syncSlider) {
        playbackSeconds = clamp(newValue, 0.0, totalSeconds);

        if (syncSlider && !timelineSlider.getValueIsAdjusting()) {
            timelineSlider.setValue((int) Math.round(playbackSeconds * 1000));
        }

        if (!syncSlider && !timelineSlider.getValueIsAdjusting()) {
            timelineSlider.setValue((int) Math.round(playbackSeconds * 1000));
        }

        renderCurrentState();
    }

    private void setPlaybackSpeed(double newSpeed) {
        playbackSpeed = Math.max(0.05, newSpeed);
        customSpeedField.setText(trimSpeed(playbackSpeed));
        speedLabel.setText("Speed: " + String.format("%.2fx", playbackSpeed));
    }

    private void applyCustomSpeed() {
        try {
            double parsed = Double.parseDouble(customSpeedField.getText().trim());
            if (parsed <= 0.0) {
                throw new NumberFormatException("Playback speed must be positive.");
            }
            setPlaybackSpeed(parsed);
        } catch (NumberFormatException exception) {
            JOptionPane.showMessageDialog(frame,
                    "Playback speed must be a positive number like 0.8, 0.5, or 1.25.",
                    "Invalid speed",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void renderCurrentState() {
        currentTimeLabel.setText(formatTime(playbackSeconds));

        if (project == null) {
            activeSentenceLabel.setText("No active subtitle");
            clearLines();
            return;
        }

        double subtitleTime = playbackSeconds - (offsetSlider.getValue() / 1000.0);
        Sentence activeSentence = findSentenceAt(subtitleTime);

        if (activeSentence == null) {
            activeSentenceLabel.setText("No active subtitle");
            clearLines();
            return;
        }

        activeSentenceLabel.setText("Sentence " + activeSentence.index()
                + " | subtitle time " + SECONDS_FORMAT.format(subtitleTime) + " s");

        List<SubtitleLine> lines = activeSentence.lines() == null ? List.of() : activeSentence.lines();
        for (int i = 0; i < lineSlots.size(); i++) {
            LineSlot slot = lineSlots.get(i);
            if (i < lines.size()) {
                SubtitleLine line = lines.get(i);
                slot.panel.setVisible(true);
                slot.label.setText(line.displayName());
                slot.textPane.render(line, subtitleTime);
            } else {
                slot.panel.setVisible(false);
                slot.textPane.clearLine();
            }
        }

        frame.revalidate();
        frame.repaint();
    }

    private void clearLines() {
        for (LineSlot slot : lineSlots) {
            slot.panel.setVisible(false);
            slot.label.setText("Language");
            slot.textPane.clearLine();
        }
    }

    private Sentence findSentenceAt(double subtitleTime) {
        if (project == null || project.sentences() == null) {
            return null;
        }

        for (Sentence sentence : project.sentences()) {
            if (sentence != null && sentence.contains(subtitleTime)) {
                return sentence;
            }
        }
        return null;
    }

    private void show() {
        frame.setLocationByPlatform(true);
        frame.setVisible(true);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String formatTime(double seconds) {
        int wholeMinutes = (int) seconds / 60;
        double remainingSeconds = seconds - (wholeMinutes * 60);
        return String.format("%02d:%05.2f", wholeMinutes, remainingSeconds);
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private static String trimSpeed(double value) {
        String formatted = String.format("%.2f", value);
        if (formatted.endsWith("00")) {
            return formatted.substring(0, formatted.length() - 1);
        }
        if (formatted.endsWith("0")) {
            return formatted.substring(0, formatted.length() - 1);
        }
        return formatted;
    }

    private record LineSlot(JPanel panel, JLabel label, SubtitleLinePanel textPane) {
    }
}
