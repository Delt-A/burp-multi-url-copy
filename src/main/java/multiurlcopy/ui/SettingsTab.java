package multiurlcopy.ui;

import burp.api.montoya.logging.Logging;
import burp.api.montoya.ui.swing.SwingUtils;
import multiurlcopy.core.CopyOutcome;
import multiurlcopy.core.CopyReporter;
import multiurlcopy.core.Hotkey;
import multiurlcopy.core.UrlFormat;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.FlowLayout;

/**
 * The "Copy URLs" suite tab: hotkey, default format, and the full text of
 * the last copy so it can be eyeballed before it is pasted into a scanner.
 */
public class SettingsTab extends JPanel implements CopyReporter {

    private final Settings settings;
    private final Logging logging;
    private final SwingUtils swingUtils;
    private final JLabel statusLabel = new JLabel("no copy yet");
    private final JTextArea lastPayload = new JTextArea();
    private final JButton hotkeyButton = new JButton();

    /** Kept so the status colour can be re-derived when the theme changes. */
    private CopyOutcome lastOutcome;

    public SettingsTab(Settings settings, Logging logging, SwingUtils swingUtils) {
        super(new BorderLayout(8, 8));
        this.settings = settings;
        this.logging = logging;
        this.swingUtils = swingUtils;

        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        add(buildControls(), BorderLayout.NORTH);
        add(buildLastCopyPane(), BorderLayout.CENTER);
    }

    private JPanel buildControls() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));

        row.add(new JLabel("Hotkey:"));
        hotkeyButton.setText(settings.hotkey().display());
        hotkeyButton.addActionListener(e -> promptForHotkey());
        row.add(hotkeyButton);

        row.add(new JLabel("   Default format:"));
        JComboBox<UrlFormat> formatBox = new JComboBox<>(UrlFormat.values());
        formatBox.setSelectedItem(settings.defaultFormat());
        formatBox.addActionListener(e -> {
            UrlFormat selected = (UrlFormat) formatBox.getSelectedItem();
            if (selected != null) {
                settings.setDefaultFormat(selected);
            }
        });
        row.add(formatBox);

        JPanel column = new JPanel();
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
        column.add(row);
        column.add(new JLabel("Select rows in any table - Burp's own or another extension's - then press the hotkey."));
        return column;
    }

    private JPanel buildLastCopyPane() {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.add(statusLabel, BorderLayout.NORTH);

        lastPayload.setEditable(false);
        lastPayload.setLineWrap(false);
        panel.add(new JScrollPane(lastPayload), BorderLayout.CENTER);

        panel.setBorder(BorderFactory.createTitledBorder("Last copy"));
        return panel;
    }

    private void promptForHotkey() {
        String current = settings.hotkey().display();
        // Parented on Burp's own frame, as the BApp Store criteria require,
        // so the dialog cannot end up behind the suite window.
        String input = JOptionPane.showInputDialog(swingUtils.suiteFrame(),
                "Hotkey, e.g. Ctrl+Alt+U\n(Ctrl+U and Ctrl+Shift+U are taken by Burp's URL encode/decode)",
                current);
        if (input == null) {
            return;
        }
        Hotkey parsed = Hotkey.parse(input);
        settings.setHotkey(parsed);
        hotkeyButton.setText(parsed.display());
    }

    @Override
    public void report(CopyOutcome outcome) {
        SwingUtilities.invokeLater(() -> {
            lastOutcome = outcome;
            statusLabel.setText(outcome.statusLine());
            applyStatusColour();
            lastPayload.setText(outcome.payload());
            lastPayload.setCaretPosition(0);
        });
        logging.logToOutput(outcome.statusLine());
    }

    /**
     * Burp swaps the look-and-feel in place when the theme changes, which
     * reaches every component as {@code updateUI}. Re-deriving the colour
     * here stops a warning shown under one theme from staying in the other
     * theme's palette.
     */
    @Override
    public void updateUI() {
        super.updateUI();
        if (statusLabel != null) {
            SwingUtilities.invokeLater(this::applyStatusColour);
        }
    }

    private void applyStatusColour() {
        boolean cleanSuccess = lastOutcome == null
                || (lastOutcome.shouldWriteClipboard() && !lastOutcome.isPartial());
        statusLabel.setForeground(cleanSuccess ? null : WarningColor.forBackground(getBackground()));
    }
}
