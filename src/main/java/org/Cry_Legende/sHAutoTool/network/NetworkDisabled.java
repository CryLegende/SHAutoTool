package org.Cry_Legende.sHAutoTool.network;

import org.Cry_Legende.sHAutoTool.SHAutoTool;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.logging.Level;

/**
 * Mode réseau : la liste des joueurs qui ont désactivé l'AutoTool vit dans la base partagée, une ligne par joueur.
 *
 * <ul>
 *   <li>à l'arrivée d'un joueur : une fois le départ de l'autre serveur confirmé, son état est lu en base ;
 *       d'ici là il est traité comme actif (valeur par défaut), et cette valeur par défaut n'est jamais écrite ;</li>
 *   <li>à chaque bascule : une seule ligne est ajoutée ou retirée, sur un fil à part (jamais le fil principal) ;</li>
 *   <li>au départ : la barrière de SHNetwork attend que ces écritures soient finies ;</li>
 *   <li>au départ aussi, le joueur sort de la mémoire : la base fait foi à son prochain passage.</li>
 * </ul>
 *
 * Le fichier disabled_players.txt n'est plus lu ni écrit dans ce mode (sauf par la commande d'import).
 */
public final class NetworkDisabled implements Listener {

    private static final int WRITE_ATTEMPTS = 3;

    private final SHAutoTool plugin;
    /** Ensemble du plugin : les joueurs en ligne qui ont désactivé l'AutoTool. */
    private final Set<UUID> memory;
    private final DisabledDatabase db;
    private final NetworkLink link;
    private final Duration waitMax;
    private final ExecutorService io = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "SHAutoTool-reseau");
        thread.setDaemon(true);
        return thread;
    });
    private final Object lock = new Object();
    /** Lecture en cours de chaque joueur qui vient d'arriver (jeton de la lecture). */
    private final Map<UUID, Object> loading = new ConcurrentHashMap<>();
    /** Joueurs qui ont basculé pendant leur lecture : leur choix l'emporte sur ce que la base répond. */
    private final Set<UUID> touched = ConcurrentHashMap.newKeySet();

    public NetworkDisabled(SHAutoTool plugin, Set<UUID> memory, DisabledDatabase db, NetworkLink link, Duration waitMax) {
        this.plugin = plugin;
        this.memory = memory;
        this.db = db;
        this.link = link;
        this.waitMax = waitMax;
    }

    /** Branche le mode réseau : écoute des connexions et sauvegarde au départ. */
    public void start() {
        link.registerSaveTask(player -> flush());
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        // les joueurs déjà là (rechargement à chaud) sont lus comme à une arrivée
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            beginLoad(online.getUniqueId());
        }
    }

    /** Termine les écritures en cours puis ferme la base. */
    public void stop() {
        io.shutdown();
        try {
            if (!io.awaitTermination(5, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("Des écritures de l'AutoTool n'ont pas pu être terminées à l'arrêt.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        db.close();
    }

    /** Bascule l'état du joueur (activé / désactivé) ; rend vrai s'il est désormais désactivé. */
    public boolean toggle(UUID uuid) {
        boolean nowDisabled;
        synchronized (lock) {
            nowDisabled = memory.add(uuid);
            if (!nowDisabled) {
                memory.remove(uuid);
            }
            if (loading.containsKey(uuid)) {
                touched.add(uuid);
            }
        }
        submit(() -> write(uuid, nowDisabled));
        return nowDisabled;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        beginLoad(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        synchronized (lock) {
            loading.remove(uuid);
            touched.remove(uuid);
            memory.remove(uuid);
        }
    }

    private void beginLoad(UUID uuid) {
        Object token = new Object();
        synchronized (lock) {
            loading.put(uuid, token);
            touched.remove(uuid);
            memory.remove(uuid);
        }
        link.awaitSaved(uuid, waitMax).whenComplete((saved, error) -> {
            if (error != null || !Boolean.TRUE.equals(saved)) {
                plugin.getLogger().warning("Départ de " + uuid + " non confirmé par l'autre serveur"
                    + (error != null ? " (" + error + ")" : "") + " : état de l'AutoTool relu quand même.");
            }
            submit(() -> load(uuid, token));
        });
    }

    private void load(UUID uuid, Object token) {
        boolean disabled;
        try {
            disabled = db.isDisabled(uuid);
        } catch (SQLException e) {
            // le joueur garde l'état par défaut (actif) plutôt que de rester bloqué
            plugin.getLogger().log(Level.WARNING, "État de l'AutoTool de " + uuid + " non relu en base : " + e.getMessage());
            synchronized (lock) {
                loading.remove(uuid, token);
                touched.remove(uuid);
            }
            return;
        }
        synchronized (lock) {
            if (loading.get(uuid) != token) {
                return; // parti ou revenu entre-temps
            }
            loading.remove(uuid);
            if (!touched.remove(uuid)) {
                if (disabled) {
                    memory.add(uuid);
                } else {
                    memory.remove(uuid);
                }
            }
        }
    }

    private void write(UUID uuid, boolean disabled) {
        for (int attempt = 1; attempt <= WRITE_ATTEMPTS; attempt++) {
            try {
                db.setDisabled(uuid, disabled);
                return;
            } catch (SQLException e) {
                if (attempt == WRITE_ATTEMPTS) {
                    plugin.getLogger().log(Level.WARNING, "État de l'AutoTool de " + uuid + " non écrit en base : " + e.getMessage());
                    return;
                }
                try {
                    Thread.sleep(200L);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    /**
     * Reprend les joueurs de disabled_players.txt dans la base, sans toucher à ceux qui y sont déjà.
     * Tourne hors du fil principal ; {@code done} reçoit (nombre ajouté, nombre déjà présent) ou l'erreur.
     */
    public void importFile(Path file, BiConsumer<int[], String> done) {
        submit(() -> {
            Set<UUID> players = new HashSet<>();
            try {
                if (Files.exists(file)) {
                    for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                        String text = line.trim();
                        if (text.isEmpty()) {
                            continue;
                        }
                        try {
                            players.add(UUID.fromString(text));
                        } catch (IllegalArgumentException invalid) {
                            plugin.getLogger().warning("UUID invalide ignoré dans " + file.getFileName() + " : " + text);
                        }
                    }
                }
                int added = db.importAll(players);
                done.accept(new int[]{added, players.size() - added}, null);
            } catch (IOException | SQLException e) {
                done.accept(null, e.getMessage());
            }
        });
    }

    /** Futur terminé quand toutes les écritures déjà demandées sont faites (le fil d'écriture les traite dans l'ordre). */
    private CompletableFuture<Void> flush() {
        CompletableFuture<Void> done = new CompletableFuture<>();
        try {
            io.execute(() -> done.complete(null));
        } catch (RejectedExecutionException stopped) {
            done.complete(null);
        }
        return done;
    }

    private void submit(Runnable task) {
        try {
            io.execute(task);
        } catch (RejectedExecutionException stopped) {
            // plugin arrêté : plus rien à écrire
        }
    }
}
