package cz.torrent;

import bt.runtime.BtClient;
import bt.torrent.TorrentSessionState;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Model pro jeden torrent v tabulce.
 */
public class TorrentItem {

    public enum Status {
        FETCHING_METADATA("Načítám metadata..."),
        DOWNLOADING("Stahuji"),
        PAUSED("Pozastaveno"),
        SEEDING("Seeduji"),
        COMPLETED("Dokončeno"),
        ERROR("Chyba"),
        STOPPED("Zastaveno");

        private final String label;
        Status(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    private final String id;
    private String name = "Načítám...";
    private long totalSize = 0;
    private long downloaded = 0;
    private long uploaded = 0;
    private double progress = 0.0;          // 0-100
    private long downloadSpeed = 0;         // bytes/s
    private long uploadSpeed = 0;
    private int peers = 0;
    private int piecesComplete = 0;
    private int piecesTotal = 0;
    private Status status = Status.FETCHING_METADATA;
    private String errorMessage = null;
    private String savePath;

    private final AtomicReference<BtClient> client = new AtomicReference<>();
    private final AtomicReference<TorrentSessionState> lastState = new AtomicReference<>();

    // pro výpočet rychlosti
    private long lastDownloaded = 0;
    private long lastUploaded = 0;
    private long lastUpdateTime = System.currentTimeMillis();

    public TorrentItem(String id, String savePath) {
        this.id = id;
        this.savePath = savePath;
    }

    public void updateFromState(TorrentSessionState state) {
        if (state == null) return;
        this.lastState.set(state);

        long now = System.currentTimeMillis();
        long elapsed = now - lastUpdateTime;
        if (elapsed > 300) {
            long dl = state.getDownloaded();
            long ul = state.getUploaded();
            this.downloadSpeed = (dl - lastDownloaded) * 1000 / Math.max(elapsed, 1);
            this.uploadSpeed = (ul - lastUploaded) * 1000 / Math.max(elapsed, 1);
            this.lastDownloaded = dl;
            this.lastUploaded = ul;
            this.lastUpdateTime = now;
        }

        this.downloaded = state.getDownloaded();
        this.uploaded = state.getUploaded();
        this.piecesComplete = state.getPiecesComplete();
        this.piecesTotal = state.getPiecesTotal();
        this.peers = state.getConnectedPeers() != null ? state.getConnectedPeers().size() : 0;

        if (piecesTotal > 0) {
            this.progress = 100.0 * piecesComplete / piecesTotal;
            if (state.getPiecesRemaining() == 0) {
                if (status != Status.PAUSED && status != Status.STOPPED) {
                    this.status = Status.COMPLETED;
                }
            } else if (status == Status.FETCHING_METADATA) {
                this.status = Status.DOWNLOADING;
            }
        }
    }

    public String getEta() {
        if (status == Status.COMPLETED || status == Status.SEEDING) return "—";
        if (downloadSpeed <= 0 || progress >= 99.9) return "∞";
        long remaining = totalSize > 0 ? totalSize - downloaded : 0;
        if (remaining <= 0 && piecesTotal > 0 && piecesComplete < piecesTotal) {
            // hrubý odhad
            remaining = (long) ((1.0 - progress / 100.0) * Math.max(downloaded, 1) / Math.max(progress / 100.0, 0.01));
        }
        if (remaining <= 0) return "—";
        long seconds = remaining / downloadSpeed;
        return formatDuration(seconds);
    }

    private static String formatDuration(long seconds) {
        if (seconds < 60) return seconds + "s";
        if (seconds < 3600) return (seconds / 60) + "m " + (seconds % 60) + "s";
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        return h + "h " + m + "m";
    }

    // Getters & setters
    public String getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public long getTotalSize() { return totalSize; }
    public void setTotalSize(long totalSize) { this.totalSize = totalSize; }
    public long getDownloaded() { return downloaded; }
    public long getUploaded() { return uploaded; }
    public double getProgress() { return progress; }
    public long getDownloadSpeed() { return downloadSpeed; }
    public long getUploadSpeed() { return uploadSpeed; }
    public int getPeers() { return peers; }
    public int getPiecesComplete() { return piecesComplete; }
    public int getPiecesTotal() { return piecesTotal; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public String getSavePath() { return savePath; }
    public BtClient getClient() { return client.get(); }
    public void setClient(BtClient client) { this.client.set(client); }
    public TorrentSessionState getLastState() { return lastState.get(); }
}
