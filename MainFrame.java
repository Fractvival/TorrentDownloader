package cz.torrent;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.extras.FlatSVGIcon;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Hlavní okno aplikace – minimalistický moderní torrent klient.
 */
public class MainFrame extends JFrame {

    private final TorrentManager manager;
    private final TorrentTableModel tableModel;
    private final JTable table;
    private final JLabel statusLabel;
    private final JLabel speedLabel;

    public MainFrame(TorrentManager manager) {
        this.manager = manager;
        this.tableModel = new TorrentTableModel();

        setTitle("Modern Torrent Downloader");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(980, 560);
        setLocationRelativeTo(null);
        setMinimumSize(new Dimension(800, 400));

        // === Toolbar ===
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.setBorder(new EmptyBorder(6, 8, 6, 8));
        toolbar.setLayout(new FlowLayout(FlowLayout.LEFT, 6, 0));

        JButton btnAddTorrent = createButton("➕  Přidat .torrent", e -> addTorrentFile());
        JButton btnAddMagnet = createButton("🧲  Magnet link", e -> addMagnet());
        JButton btnPause = createButton("⏸  Pozastavit", e -> pauseSelected());
        JButton btnResume = createButton("▶  Pokračovat", e -> resumeSelected());
        JButton btnRemove = createButton("🗑  Odebrat", e -> removeSelected());
        JButton btnSettings = createButton("⚙  Nastavení", e -> showSettings());

        toolbar.add(btnAddTorrent);
        toolbar.add(btnAddMagnet);
        toolbar.addSeparator(new Dimension(12, 0));
        toolbar.add(btnPause);
        toolbar.add(btnResume);
        toolbar.add(btnRemove);
        toolbar.addSeparator(new Dimension(12, 0));
        toolbar.add(btnSettings);

        // === Table ===
        table = new JTable(tableModel);
        table.setRowHeight(32);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 1));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);
        table.getTableHeader().setReorderingAllowed(false);

        // Progress column renderer
        table.getColumnModel().getColumn(2).setCellRenderer(new ProgressBarRenderer());
        // Right align numbers
        DefaultTableCellRenderer rightRenderer = new DefaultTableCellRenderer();
        rightRenderer.setHorizontalAlignment(SwingConstants.RIGHT);
        table.getColumnModel().getColumn(3).setCellRenderer(rightRenderer);
        table.getColumnModel().getColumn(4).setCellRenderer(rightRenderer);
        table.getColumnModel().getColumn(5).setCellRenderer(rightRenderer);
        table.getColumnModel().getColumn(6).setCellRenderer(rightRenderer);

        // Column widths
        table.getColumnModel().getColumn(0).setPreferredWidth(260); // Name
        table.getColumnModel().getColumn(1).setPreferredWidth(90);  // Status
        table.getColumnModel().getColumn(2).setPreferredWidth(160); // Progress
        table.getColumnModel().getColumn(3).setPreferredWidth(90);  // Size
        table.getColumnModel().getColumn(4).setPreferredWidth(90);  // Downloaded
        table.getColumnModel().getColumn(5).setPreferredWidth(80);  // Speed
        table.getColumnModel().getColumn(6).setPreferredWidth(70);  // ETA
        table.getColumnModel().getColumn(7).setPreferredWidth(50);  // Peers

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createEmptyBorder());

        // Double-click opens folder (placeholder)
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    // could open folder
                }
            }
        });

        // === Status bar ===
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBorder(new EmptyBorder(4, 10, 4, 10));
        statusLabel = new JLabel("Připraven");
        speedLabel = new JLabel("↓ 0 B/s   ↑ 0 B/s");
        speedLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        statusBar.add(statusLabel, BorderLayout.WEST);
        statusBar.add(speedLabel, BorderLayout.EAST);

        // Layout
        setLayout(new BorderLayout());
        add(toolbar, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        add(statusBar, BorderLayout.SOUTH);

        // Listen for updates
        manager.addListener(item -> SwingUtilities.invokeLater(() -> {
            tableModel.fireTableDataChanged();
            updateStatusBar();
        }));

        // Timer for smooth speed updates
        Timer timer = new Timer(800, e -> {
            tableModel.fireTableDataChanged();
            updateStatusBar();
        });
        timer.start();

        // Window close
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                manager.shutdown();
            }
        });
    }

    private JButton createButton(String text, java.awt.event.ActionListener action) {
        JButton b = new JButton(text);
        b.addActionListener(action);
        b.setFocusable(false);
        return b;
    }

    private void updateStatusBar() {
        long dl = manager.getTotalDownloadSpeed();
        long ul = manager.getTotalUploadSpeed();
        speedLabel.setText(String.format("↓ %s/s    ↑ %s/s", formatSize(dl), formatSize(ul)));
        int count = manager.getAll().size();
        statusLabel.setText(count == 0 ? "Připraven" : "Aktivních torrentů: " + count);
    }

    private void addTorrentFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Vyber .torrent soubor");
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Torrent soubory (*.torrent)", "torrent"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            Path file = chooser.getSelectedFile().toPath();
            manager.addTorrentFile(file, manager.getDefaultSavePath());
            statusLabel.setText("Přidávám torrent...");
        }
    }

    private void addMagnet() {
        String magnet = JOptionPane.showInputDialog(this,
                "Vlož magnet link:",
                "Přidat magnet",
                JOptionPane.PLAIN_MESSAGE);
        if (magnet != null && magnet.trim().toLowerCase().startsWith("magnet:")) {
            manager.addMagnet(magnet.trim(), manager.getDefaultSavePath());
            statusLabel.setText("Přidávám magnet...");
        } else if (magnet != null && !magnet.isBlank()) {
            JOptionPane.showMessageDialog(this, "Neplatný magnet link.", "Chyba", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void pauseSelected() {
        int row = table.getSelectedRow();
        if (row < 0) return;
        TorrentItem item = tableModel.getItemAt(row);
        manager.pause(item.getId());
    }

    private void resumeSelected() {
        int row = table.getSelectedRow();
        if (row < 0) return;
        TorrentItem item = tableModel.getItemAt(row);
        manager.resume(item.getId());
    }

    private void removeSelected() {
        int row = table.getSelectedRow();
        if (row < 0) return;
        TorrentItem item = tableModel.getItemAt(row);
        int confirm = JOptionPane.showConfirmDialog(this,
                "Opravdu odebrat \"" + item.getName() + "\"?",
                "Odebrat torrent",
                JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            manager.remove(item.getId(), false);
            tableModel.fireTableDataChanged();
        }
    }

    private void showSettings() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;

        JTextField pathField = new JTextField(manager.getDefaultSavePath().toString(), 30);
        JButton browse = new JButton("...");
        browse.addActionListener(e -> {
            JFileChooser fc = new JFileChooser(manager.getDefaultSavePath().toFile());
            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                pathField.setText(fc.getSelectedFile().getAbsolutePath());
            }
        });

        JSpinner dlLimit = new JSpinner(new SpinnerNumberModel(
                (int)(manager.getGlobalDownloadLimit() / 1024), 0, 100000, 50));
        JSpinner ulLimit = new JSpinner(new SpinnerNumberModel(
                (int)(manager.getGlobalUploadLimit() / 1024), 0, 100000, 50));

        c.gridx = 0; c.gridy = 0;
        panel.add(new JLabel("Výchozí složka:"), c);
        c.gridx = 1;
        panel.add(pathField, c);
        c.gridx = 2;
        panel.add(browse, c);

        c.gridx = 0; c.gridy = 1;
        panel.add(new JLabel("Limit stahování (KB/s, 0=∞):"), c);
        c.gridx = 1;
        panel.add(dlLimit, c);

        c.gridx = 0; c.gridy = 2;
        panel.add(new JLabel("Limit odesílání (KB/s, 0=∞):"), c);
        c.gridx = 1;
        panel.add(ulLimit, c);

        int result = JOptionPane.showConfirmDialog(this, panel, "Nastavení",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION) {
            manager.setDefaultSavePath(Paths.get(pathField.getText()));
            manager.setGlobalDownloadLimit(((Number) dlLimit.getValue()).longValue() * 1024);
            manager.setGlobalUploadLimit(((Number) ulLimit.getValue()).longValue() * 1024);
        }
    }

    // ========== Table Model ==========
    private class TorrentTableModel extends AbstractTableModel {
        private final String[] columns = {
                "Název", "Stav", "Průběh", "Velikost", "Staženo", "Rychlost", "ETA", "Peers"
        };

        @Override
        public int getRowCount() {
            return new ArrayList<>(manager.getAll()).size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int col) {
            return columns[col];
        }

        public TorrentItem getItemAt(int row) {
            List<TorrentItem> list = new ArrayList<>(manager.getAll());
            if (row >= 0 && row < list.size()) return list.get(row);
            return null;
        }

        @Override
        public Object getValueAt(int row, int col) {
            TorrentItem item = getItemAt(row);
            if (item == null) return "";
            switch (col) {
                case 0: return item.getName();
                case 1: return item.getStatus().getLabel();
                case 2: return item.getProgress(); // handled by renderer
                case 3: return formatSize(item.getTotalSize());
                case 4: return formatSize(item.getDownloaded());
                case 5: return formatSize(item.getDownloadSpeed()) + "/s";
                case 6: return item.getEta();
                case 7: return item.getPeers();
                default: return "";
            }
        }
    }

    // ========== Progress bar renderer ==========
    private static class ProgressBarRenderer extends JProgressBar implements TableCellRenderer {
        public ProgressBarRenderer() {
            setStringPainted(true);
            setBorderPainted(false);
            setMaximum(1000); // pro přesnost (0.1 %)
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus,
                                                       int row, int column) {
            double progress = value instanceof Number ? ((Number) value).doubleValue() : 0.0;
            setValue((int) Math.round(progress * 10));
            setString(String.format("%.1f %%", progress));
            if (isSelected) {
                setBackground(table.getSelectionBackground());
            } else {
                setBackground(table.getBackground());
            }
            return this;
        }
    }

    private static String formatSize(long bytes) {
        if (bytes < 0) return "—";
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
}
