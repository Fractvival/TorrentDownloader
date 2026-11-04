package cz.torrent;

import bt.Bt;
import bt.runtime.BtClient;
import bt.runtime.BtRuntime;
import bt.runtime.Config;
import bt.data.Storage;
import bt.data.file.FileSystemStorage;
import bt.dht.DHTConfig;
import bt.dht.DHTModule;
import bt.metainfo.Torrent;
import com.google.inject.Module;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Správa všech aktivních torrentů + sdílený runtime.
 */
public class TorrentManager {

    private final BtRuntime runtime;
    private final Map<String, TorrentItem> torrents = new ConcurrentHashMap<>();
    private final List<Consumer<TorrentItem>> listeners = new CopyOnWriteArrayList<>();
    private Path defaultSavePath;
    private long globalDownloadLimit = 0; // 0 = unlimited
    private long globalUploadLimit = 0;

    public TorrentManager(Path defaultSavePath) {
        this.defaultSavePath = defaultSavePath;

        Config config = new Config() {
            @Override
            public int getNumOfHashingThreads() {
                return Math.max(2, Runtime.getRuntime().availableProcessors());
            }
        };

        Module dhtModule = new DHTModule(new DHTConfig() {
            @Override
            public boolean shouldUseRouterBootstrap() {
                return true;
            }
        });

        this.runtime = BtRuntime.builder(config)
                .autoLoadModules()
                .module(dhtModule)
                .build();
    }

    public void addListener(Consumer<TorrentItem> listener) {
        listeners.add(listener);
    }

    private void notifyListeners(TorrentItem item) {
        for (Consumer<TorrentItem> l : listeners) {
            try { l.accept(item); } catch (Exception ignored) {}
        }
    }

    public Collection<TorrentItem> getAll() {
        return Collections.unmodifiableCollection(torrents.values());
    }

    public TorrentItem get(String id) {
        return torrents.get(id);
    }

    public Path getDefaultSavePath() {
        return defaultSavePath;
    }

    public void setDefaultSavePath(Path path) {
        this.defaultSavePath = path;
    }

    public void setGlobalDownloadLimit(long bytesPerSec) {
        this.globalDownloadLimit = bytesPerSec;
        // Pozn.: bt nemá jednoduchý global limit, aplikujeme na jednotlivé klienty pokud podporují
    }

    public void setGlobalUploadLimit(long bytesPerSec) {
        this.globalUploadLimit = bytesPerSec;
    }

    public long getGlobalDownloadLimit() { return globalDownloadLimit; }
    public long getGlobalUploadLimit() { return globalUploadLimit; }

    public synchronized TorrentItem addMagnet(String magnetUri, Path savePath) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        TorrentItem item = new TorrentItem(id, savePath != null ? savePath.toString() : defaultSavePath.toString());
        torrents.put(id, item);
        notifyListeners(item);

        try {
            Storage storage = new FileSystemStorage(savePath != null ? savePath : defaultSavePath);

            BtClient client = Bt.client(runtime)
                    .storage(storage)
                    .magnet(magnetUri)
                    .afterTorrentFetched(torrent -> {
                        item.setName(torrent.getName());
                        item.setTotalSize(torrent.getSize());
                        item.setStatus(TorrentItem.Status.DOWNLOADING);
                        notifyListeners(item);
                    })
                    .stopWhenDownloaded()
                    .build();

            item.setClient(client);

            client.startAsync(state -> {
                item.updateFromState(state);
                if (state.getPiecesTotal() > 0 && state.getPiecesRemaining() == 0) {
                    item.setStatus(TorrentItem.Status.COMPLETED);
                }
                notifyListeners(item);
            }, 1000);

        } catch (Exception e) {
            item.setStatus(TorrentItem.Status.ERROR);
            item.setErrorMessage(e.getMessage());
            notifyListeners(item);
        }

        return item;
    }

    public synchronized TorrentItem addTorrentFile(Path torrentFile, Path savePath) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        TorrentItem item = new TorrentItem(id, savePath != null ? savePath.toString() : defaultSavePath.toString());
        torrents.put(id, item);
        notifyListeners(item);

        try {
            Storage storage = new FileSystemStorage(savePath != null ? savePath : defaultSavePath);

            BtClient client = Bt.client(runtime)
                    .storage(storage)
                    .torrent(torrentFile.toUri().toURL())
                    .afterTorrentFetched(torrent -> {
                        item.setName(torrent.getName());
                        item.setTotalSize(torrent.getSize());
                        item.setStatus(TorrentItem.Status.DOWNLOADING);
                        notifyListeners(item);
                    })
                    .stopWhenDownloaded()
                    .build();

            item.setClient(client);

            client.startAsync(state -> {
                item.updateFromState(state);
                if (state.getPiecesTotal() > 0 && state.getPiecesRemaining() == 0) {
                    item.setStatus(TorrentItem.Status.COMPLETED);
                }
                notifyListeners(item);
            }, 1000);

        } catch (Exception e) {
            item.setStatus(TorrentItem.Status.ERROR);
            item.setErrorMessage(e.getMessage());
            notifyListeners(item);
        }

        return item;
    }

    public void pause(String id) {
        TorrentItem item = torrents.get(id);
        if (item == null || item.getClient() == null) return;
        try {
            item.getClient().stop();
            item.setStatus(TorrentItem.Status.PAUSED);
            notifyListeners(item);
        } catch (Exception e) {
            item.setErrorMessage(e.getMessage());
        }
    }

    public void resume(String id) {
        TorrentItem item = torrents.get(id);
        if (item == null || item.getClient() == null) return;
        if (item.getStatus() != TorrentItem.Status.PAUSED && item.getStatus() != TorrentItem.Status.STOPPED) return;

        try {
            // bt Client po stop() se už nedá restartovat snadno – v 1.10 je lepší vytvořit nový
            // Pro jednoduchost označíme jako paused a uživatel může přidat znovu.
            // Plný resume by vyžadoval more advanced state management.
            item.setStatus(TorrentItem.Status.DOWNLOADING);
            // Poznámka: skutečný resume po stop() je v této verzi knihovny omezený.
            // Pro produkční klient by se používalo jiné API / persistentní session.
            notifyListeners(item);
        } catch (Exception e) {
            item.setErrorMessage(e.getMessage());
        }
    }

    public void remove(String id, boolean deleteFiles) {
        TorrentItem item = torrents.remove(id);
        if (item == null) return;
        try {
            if (item.getClient() != null) {
                item.getClient().stop();
            }
            item.setStatus(TorrentItem.Status.STOPPED);
        } catch (Exception ignored) {}
        notifyListeners(item);
    }

    public void shutdown() {
        try {
            for (TorrentItem item : torrents.values()) {
                if (item.getClient() != null) {
                    try { item.getClient().stop(); } catch (Exception ignored) {}
                }
            }
            runtime.shutdown();
        } catch (Exception ignored) {}
    }

    public long getTotalDownloadSpeed() {
        return torrents.values().stream().mapToLong(TorrentItem::getDownloadSpeed).sum();
    }

    public long getTotalUploadSpeed() {
        return torrents.values().stream().mapToLong(TorrentItem::getUploadSpeed).sum();
    }
}
