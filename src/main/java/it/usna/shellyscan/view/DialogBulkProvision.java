package it.usna.shellyscan.view;

import java.awt.BorderLayout;
import java.awt.FlowLayout;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.WindowConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import it.usna.shellyscan.Main;
import it.usna.shellyscan.prov.ProvisioningController;

public class DialogBulkProvision extends JDialog {
    private static final long serialVersionUID = 1L;

    private final JTextArea logArea;
    private final JButton discoverButton;
    private final JButton connectButton;
    private final JButton setupButton;
    private final JButton listButton;
    private final JButton stopButton;
    private final JCheckBox continuousScanCheckBox;

    private final ProvisioningController controller;
    private SwingWorker<Void, Void> backgroundWorker;

    public DialogBulkProvision(MainView owner) {
        super(owner, "Bulk Shelly Provisioner", false);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setIconImage(Main.ICON);

        // Warning Panel
        JPanel warningPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        warningPanel.add(new JLabel("WARNING: 'Discover' and 'Connect' will temporarily disconnect your computer's Wi-Fi."));

        // Log Area
        logArea = new JTextArea(25, 100);
        logArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(logArea);

        // Button Panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        discoverButton = new JButton("Discover");
        connectButton = new JButton("Connect");
        setupButton = new JButton("Setup");
        listButton = new JButton("List Devices");
        stopButton = new JButton("Stop");
        continuousScanCheckBox = new JCheckBox("Continuous Scan");

        buttonPanel.add(discoverButton);
        buttonPanel.add(connectButton);
        buttonPanel.add(setupButton);
        buttonPanel.add(listButton);
        buttonPanel.add(continuousScanCheckBox);
        buttonPanel.add(stopButton);
        
        // Main content panel
        JPanel contentPanel = new JPanel(new BorderLayout());
        contentPanel.add(warningPanel, BorderLayout.NORTH);
        contentPanel.add(scrollPane, BorderLayout.CENTER);
        contentPanel.add(buttonPanel, BorderLayout.SOUTH);

        getContentPane().add(contentPanel);

        // Controller and Listeners
        controller = new ProvisioningController(this::logToTextArea);

        discoverButton.addActionListener(e -> {
            if (continuousScanCheckBox.isSelected()) {
                runBackgroundTask(controller::startContinuousDiscovery);
            } else {
                runBackgroundTask(controller::discoverDevices);
            }
        });
        connectButton.addActionListener(e -> runBackgroundTask(controller::connectDevices));
        setupButton.addActionListener(e -> runBackgroundTask(controller::setupDevices));
        listButton.addActionListener(e -> runBackgroundTask(controller::listDevices));
        stopButton.addActionListener(e -> {
            if (backgroundWorker != null && !backgroundWorker.isDone()) {
                backgroundWorker.cancel(true);
            }
        });

        stopButton.setVisible(false); // Hide by default

        pack();
        setLocationRelativeTo(owner);
    }

    private void logToTextArea(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(message + "\n");
        });
    }

    private void runBackgroundTask(Runnable task) {
        setButtonsEnabled(false);
        stopButton.setVisible(true);
        logArea.setText(""); // Clear log on new task

        backgroundWorker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                task.run();
                return null;
            }

            @Override
            protected void done() {
                setButtonsEnabled(true);
                stopButton.setVisible(false);
                logToTextArea("\nTask finished.");
            }
        };
        backgroundWorker.execute();
    }

    private void setButtonsEnabled(boolean enabled) {
        discoverButton.setEnabled(enabled);
        connectButton.setEnabled(enabled);
        setupButton.setEnabled(enabled);
        listButton.setEnabled(enabled);
    }
}
