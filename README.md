# TorrentDownloader
Velmi jednoduchý stahovač torrentů

Moderní dark theme (FlatLaf)
Přidání .torrent souboru i magnet linku
Tabulka s více torrenty najednou
Grafický progress bar u každého torrentu
Rychlost stahování + ETA + počet peerů
Pozastavení / pokračování / odebrání
Nastavení výchozí složky + limity rychlosti (UI)
Celková rychlost v dolní liště
Čistě Java – žádný cizí software

Jak spustit na Windows 11

Měj nainstalovanou Javu 11+ (nejlépe Temurin z adoptium.net).
Spusť dvojklikem na JAR, nebo z příkazové řádky:

PowerShelljava -jar modern-torrent-downloader.jar


Omezení (upřímně)

Pause/Resume – knihovna bt po stop() neumožňuje plný resume jako qBittorrent. Po pozastavení je lepší torrent znovu přidat (nebo nechat běžet).
Globální limity rychlosti jsou v nastavení, ale plná enforcement není tak silná jako u nativních klientů.
Je to minimalistický klient – ne full qBittorrent. Nemá kategorie, RSS, web UI, šifrování MSE detaily atd.

Přesto je to čistý, moderní, bezpečný (žádný closed-source balast) a plně pod tvojí kontrolou.


!!! Stahuj pouze legální obsah (Linux ISO, open-source projekty, veřejně dostupné data…) !!!
