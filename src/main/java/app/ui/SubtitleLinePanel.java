package app.ui;

import app.model.SubtitleLine;
import app.model.SubtitleToken;

import javax.swing.BorderFactory;
import javax.swing.JTextPane;
import javax.swing.SwingConstants;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.Color;
import java.awt.Font;
import java.util.List;

public final class SubtitleLinePanel extends JTextPane {
    private static final Color BASE_TEXT = new Color(245, 245, 245);
    private static final Color TIMED_TEXT = new Color(190, 190, 190);
    private static final Color ACTIVE_BG = new Color(255, 215, 64);
    private static final Color ACTIVE_TEXT = new Color(32, 32, 32);
    private static final Color PANEL_BG = new Color(26, 26, 32);

    public SubtitleLinePanel() {
        setEditable(false);
        setOpaque(true);
        setBackground(PANEL_BG);
        setForeground(BASE_TEXT);
        setBorder(BorderFactory.createEmptyBorder(14, 18, 14, 18));
        setFont(new Font("SansSerif", Font.PLAIN, 26));
    }

    public void clearLine() {
        setText("");
        setToolTipText(null);
    }

    public void render(SubtitleLine line, double activeTimeSeconds) {
        setToolTipText(line.displayName());
        StyledDocument document = getStyledDocument();

        try {
            document.remove(0, document.getLength());
            if (line.tokens() == null || line.tokens().isEmpty()) {
                document.insertString(0, line.text() == null ? "" : line.text(), regularStyle(false));
            } else {
                insertTokens(document, line.tokens(), activeTimeSeconds);
            }
            setParagraphAttributes(regularStyle(false), true);
        } catch (BadLocationException e) {
            setText(line.text() == null ? "" : line.text());
        }
    }

    private void insertTokens(StyledDocument document, List<SubtitleToken> tokens, double activeTimeSeconds)
            throws BadLocationException {
        boolean first = true;
        for (SubtitleToken token : tokens) {
            if (!first) {
                document.insertString(document.getLength(), " ", regularStyle(false));
            }
            first = false;

            boolean active = token != null && token.isActive(activeTimeSeconds);
            boolean timed = token != null && token.hasTiming();
            String tokenText = token == null || token.text() == null ? "" : token.text();
            document.insertString(document.getLength(), tokenText, regularStyle(timed, active));
        }
    }

    private SimpleAttributeSet regularStyle(boolean timed) {
        return regularStyle(timed, false);
    }

    private SimpleAttributeSet regularStyle(boolean timed, boolean active) {
        SimpleAttributeSet style = new SimpleAttributeSet();
        StyleConstants.setAlignment(style, SwingConstants.CENTER);
        StyleConstants.setFontFamily(style, "SansSerif");
        StyleConstants.setFontSize(style, 26);
        StyleConstants.setBold(style, active);
        StyleConstants.setForeground(style, active ? ACTIVE_TEXT : timed ? TIMED_TEXT : BASE_TEXT);
        StyleConstants.setBackground(style, active ? ACTIVE_BG : PANEL_BG);
        return style;
    }
}
