package cz.torrent;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;

import javax.swing.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Vstupní bod – moderní minimalistický pure-Java torrent klient.
 */
public class Main {

    public static void main(String[] args) {
        // Moderní dark theme
        try {
            FlatDarkLaf.setup();
            FlatLaf.setPreferredFontFamily("Segoe UI");
        } catch (Exception e) {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}
        }

        // Výchozí složka pro stahování
        Path downloads = Paths.get(System.getProperty("user.home"), "Downloads", "Torrents");
        try {
            Files.createDirectories(downloads);
        } catch (Exception e) {
            downloads = Paths.get("downloads");
            try { Files.createDirectories(downloads); } catch (Exception ignored) {}
        }

        TorrentManager manager = new TorrentManager(downloads);

        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame(manager);
            frame.setVisible(true);
        });
    }
}
