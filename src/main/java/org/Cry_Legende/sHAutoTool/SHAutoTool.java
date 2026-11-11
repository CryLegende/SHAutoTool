package org.Cry_Legende.sHAutoTool;

import org.Cry_Legende.sHAutoTool.network.DisabledDatabase;
import org.Cry_Legende.sHAutoTool.network.NetworkDisabled;
import org.Cry_Legende.sHAutoTool.network.NetworkLink;
import org.Cry_Legende.sHAutoTool.network.ShNetworkLink;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class SHAutoTool extends JavaPlugin {

    // Ensemble concurrent : lu par la sauvegarde asynchrone pendant que le thread principal le modifie
    private final Set<UUID> disabledPlayers = ConcurrentHashMap.newKeySet();
    // Une seule écriture à la fois sur disabled_players.txt (deux sauvegardes async rapprochées se marchaient dessus)
    private final Object saveLock = new Object();
    private ToolLogic toolLogic;
    private File disabledPlayersFile;
    // Non nul seulement avec SHNetwork : la liste des joueurs désactivés vit alors dans la base partagée
    private NetworkDisabled network;
    private YamlConfiguration messages;

    // --- Champs pour la configuration ---
    private String worldMode;
    private Set<String> worldList;
    private Set<Material> blockBlacklist = new HashSet<>();
    private String placeholderEnabled;
    private String placeholderDisabled;
    private boolean disableIfHoldingGoldenAxe; // <--- NOUVEAU CHAMP

    @Override
    public void onEnable() {
        // Important: sauvegarder les valeurs par défaut AVANT de charger,
        // pour que les nouvelles clés soient ajoutées au fichier de l'utilisateur.
        saveDefaultConfig();

        loadConfigValues(getConfig());

        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        disabledPlayersFile = new File(getDataFolder(), "disabled_players.txt");

        loadMessages();
        enableNetwork();
        // Avec le mode réseau, la base fait foi : le fichier local n'est lu que par /shautotool import
        if (network == null) {
            loadDisabledPlayersFromFile();
        }

        toolLogic = new ToolLogic(this);
        getServer().getPluginManager().registerEvents(new PlayerToolListener(this, toolLogic), this);

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new AutoToolExpansion(this).register();
            getLogger().info("PlaceholderAPI hook enabled!");
        } else {
            getLogger().info("PlaceholderAPI not found, placeholder will not be available.");
        }

        getLogger().info("SHAutoTool enabled!");
    }

    @Override
    public void onDisable() {
        if (network != null) {
            network.stop();
        } else {
            saveDisabledPlayersToFile(false);
        }
        getLogger().info("SHAutoTool disabled!");
    }

    /**
     * Passe en mode réseau si SHNetwork est installé et 'reseau.actif' est vrai. Les classes qui touchent à SHNetwork
     * ne sont chargées qu'ici : sans lui, le plugin se comporte exactement comme avant.
     */
    private void enableNetwork() {
        if (!getConfig().getBoolean("reseau.actif", true)) return;
        if (!Bukkit.getPluginManager().isPluginEnabled("SHNetwork")) return;
        DisabledDatabase db = null;
        try {
            NetworkLink link = ShNetworkLink.open();
            db = new DisabledDatabase(
                getConfig().getString("reseau.base-de-donnees.url"),
                getConfig().getString("reseau.base-de-donnees.utilisateur"),
                getConfig().getString("reseau.base-de-donnees.mot-de-passe"),
                Math.max(250, getConfig().getInt("reseau.base-de-donnees.delai-ms", 3000)));
            db.createTable();
            Duration wait = Duration.ofSeconds(Math.max(1, getConfig().getInt("reseau.attente-max-secondes", 10)));
            NetworkDisabled net = new NetworkDisabled(this, disabledPlayers, db, link, wait);
            net.start();
            network = net;
            getLogger().info("SHNetwork détecté : la liste des joueurs désactivés est partagée entre les serveurs (base partagée).");
        } catch (Exception | LinkageError e) {
            if (db != null) db.close();
            getLogger().log(Level.SEVERE, "SHNetwork présent mais inutilisable, liste des joueurs désactivés locale à ce serveur : " + e);
        }
    }

    private YamlConfiguration jarMessages() {
        try (InputStreamReader reader = new InputStreamReader(Objects.requireNonNull(getResource("messages.yml")), StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        } catch (IOException | RuntimeException e) {
            return new YamlConfiguration();
        }
    }

    /**
     * messages.yml du serveur, complété par les valeurs du jar. S'il n'existe pas, il est créé à partir de la section
     * 'messages' de config.yml (textes du serveur conservés) ; cette section n'est plus lue ensuite.
     */
    private YamlConfiguration readMessages(FileConfiguration source) throws IOException, InvalidConfigurationException {
        File file = new File(getDataFolder(), "messages.yml");
        YamlConfiguration defaults = jarMessages();
        if (!file.exists()) {
            ConfigurationSection legacy = source.getConfigurationSection("messages");
            YamlConfiguration created = new YamlConfiguration();
            created.options().setHeader(List.of(
                "Messages de SHAutoTool. Couleurs avec &. Placeholders : %commands%, %file%, %error%, %ajoutes%, %deja%."));
            for (String key : defaults.getKeys(false)) {
                created.set(key, legacy != null && legacy.isString(key) ? legacy.getString(key) : defaults.getString(key));
            }
            getDataFolder().mkdirs();
            created.save(file);
        }
        YamlConfiguration loaded = new YamlConfiguration();
        loaded.load(file);
        loaded.setDefaults(defaults);
        return loaded;
    }

    // Au démarrage : un messages.yml illisible n'est jamais réécrit, les textes du jar servent en attendant
    private void loadMessages() {
        try {
            messages = readMessages(getConfig());
        } catch (IOException | InvalidConfigurationException e) {
            getLogger().severe("messages.yml unreadable (" + errorText(e) + "), using the jar texts. Fix the file then run /shautotool reload.");
            messages = new YamlConfiguration();
            messages.setDefaults(jarMessages());
        }
    }

    private static String errorText(Exception error) {
        String text = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        text = text.replaceAll("[\\s&§]+", " ").strip();
        return text.length() > 200 ? text.substring(0, 200) + "..." : text;
    }

    /** Texte de messages.yml, couleurs appliquées. */
    private String getMessage(String key) {
        return ChatColor.translateAlternateColorCodes('&', messages.getString(key, key));
    }

    private void loadConfigValues(FileConfiguration config) {

        this.worldMode = config.getString("worlds.mode", "blacklist").toLowerCase();
        this.worldList = new HashSet<>(config.getStringList("worlds.list"));

        this.blockBlacklist.clear();
        List<String> blacklistedMaterials = config.getStringList("block-blacklist");
        for (String matName : blacklistedMaterials) {
            try {
                Material material = Material.valueOf(matName.toUpperCase());
                this.blockBlacklist.add(material);
            } catch (IllegalArgumentException e) {
                getLogger().warning("Invalid material name in block-blacklist: '" + matName + "'");
            }
        }

        this.placeholderEnabled = config.getString("placeholders.status_enabled", "&aActivé");
        this.placeholderDisabled = config.getString("placeholders.status_disabled", "&cDésactivé");

        // <--- NOUVEAU : Chargement de l'option pour la hache en or ---
        this.disableIfHoldingGoldenAxe = config.getBoolean("options.disable-if-holding-golden-axe", true);
        // --- FIN NOUVEAU ---

        getLogger().info("World mode: " + this.worldMode);
        getLogger().info("Worlds in list: " + this.worldList.size());
        getLogger().info(this.blockBlacklist.size() + " block(s) loaded into the blacklist.");
        if (this.disableIfHoldingGoldenAxe) {
            getLogger().info("WorldEdit compatibility mode (Golden Axe) is ENABLED.");
        }
    }

    // <--- NOUVEAU : Getter pour l'option ---
    public boolean shouldDisableForGoldenAxe() {
        return disableIfHoldingGoldenAxe;
    }
    // --- FIN NOUVEAU ---

    public boolean isWorldAllowed(World world) {
        if (worldMode.equals("whitelist")) {
            return worldList.contains(world.getName());
        } else {
            return !worldList.contains(world.getName());
        }
    }

    public boolean isBlockBlacklisted(Block block) {
        return blockBlacklist.contains(block.getType());
    }

    public String getPlaceholderEnabled() {
        return placeholderEnabled;
    }

    public String getPlaceholderDisabled() {
        return placeholderDisabled;
    }


    private void loadDisabledPlayersFromFile() {
        if (!disabledPlayersFile.exists()) {
            return;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(disabledPlayersFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    if (!line.trim().isEmpty()) {
                        disabledPlayers.add(UUID.fromString(line.trim()));
                    }
                } catch (IllegalArgumentException e) {
                    getLogger().warning("Invalid UUID found in " + disabledPlayersFile.getName() + ": " + line);
                }
            }
        } catch (IOException e) {
            getLogger().severe("Could not load disabled players from " + disabledPlayersFile.getName() + ": " + e.getMessage());
        }
        getLogger().info(disabledPlayers.size() + " player(s) loaded with AutoTool disabled.");
    }

    private void saveDisabledPlayersToFile(boolean async) {
        Runnable saveTask = () -> {
            // L'instantané est pris sous le verrou : la dernière sauvegarde exécutée écrit toujours l'état le plus récent
            synchronized (saveLock) {
                File tmp = new File(disabledPlayersFile.getParentFile(), disabledPlayersFile.getName() + ".tmp");
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(tmp, false))) {
                    for (UUID uuid : disabledPlayers) {
                        writer.write(uuid.toString());
                        writer.newLine();
                    }
                } catch (IOException e) {
                    getLogger().severe("Could not save disabled players to " + disabledPlayersFile.getName() + ": " + e.getMessage());
                    return;
                }
                try {
                    java.nio.file.Files.move(tmp.toPath(), disabledPlayersFile.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                } catch (IOException e) {
                    getLogger().severe("Could not replace " + disabledPlayersFile.getName() + ": " + e.getMessage());
                }
            }
        };

        if (async) {
            Bukkit.getScheduler().runTaskAsynchronously(this, saveTask);
        } else {
            saveTask.run();
        }
    }

    public boolean isPlayerDisabled(Player player) {
        return disabledPlayers.contains(player.getUniqueId());
    }

    public void togglePlayer(Player player) {
        UUID playerId = player.getUniqueId();
        if (network != null) {
            boolean nowDisabled = network.toggle(playerId);
            player.sendMessage(getMessage(nowDisabled ? "toggle-disabled" : "toggle-enabled"));
            return;
        }
        if (disabledPlayers.contains(playerId)) {
            disabledPlayers.remove(playerId);
            player.sendMessage(getMessage("toggle-enabled"));
        } else {
            disabledPlayers.add(playerId);
            player.sendMessage(getMessage("toggle-disabled"));
        }
        saveDisabledPlayersToFile(true);
    }

    /** /shautotool import : reprend disabled_players.txt dans la base partagée (une fois, sans écraser ce qui y est déjà). */
    private void importDisabledPlayers(CommandSender sender) {
        if (network == null) {
            sender.sendMessage(getMessage("reseau-inactif"));
            return;
        }
        network.importFile(disabledPlayersFile.toPath(), (counts, error) -> {
            if (error != null) {
                sender.sendMessage(getMessage("reseau-import-erreur").replace("%erreur%", error));
            } else {
                sender.sendMessage(getMessage("reseau-import")
                    .replace("%ajoutes%", String.valueOf(counts[0]))
                    .replace("%deja%", String.valueOf(counts[1])));
            }
        });
    }

    private static final List<String> SUBCOMMANDS = List.of("reload", "import");

    private boolean can(CommandSender sender, String sub) {
        return sender.hasPermission("shautotool.admin." + sub);
    }

    private List<String> allowedSubcommands(CommandSender sender) {
        List<String> result = new ArrayList<>();
        for (String sub : SUBCOMMANDS) {
            if (can(sender, sub)) result.add(sub);
        }
        return result;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (command.getName().equalsIgnoreCase("autotool")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(getMessage("player-only"));
                return true;
            }
            togglePlayer(player);
            return true;
        } else if (command.getName().equalsIgnoreCase("shautotool")) {
            String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            if (!SUBCOMMANDS.contains(sub)) {
                List<String> allowed = allowedSubcommands(sender);
                sender.sendMessage(allowed.isEmpty()
                    ? getMessage("no-permission")
                    : getMessage("admin-usage").replace("%commands%", String.join("|", allowed)));
                return true;
            }
            if (!can(sender, sub)) {
                sender.sendMessage(getMessage("no-permission"));
                return true;
            }
            if (sub.equals("reload")) {
                reloadFiles(sender);
            } else {
                importDisabledPlayers(sender);
            }
            return true;
        }
        return false;
    }

    /**
     * /shautotool reload : relit config.yml et messages.yml. Les deux fichiers sont lus à part et vérifiés d'abord :
     * si l'un est illisible, rien ne change. disabled_players.txt et la base ne sont jamais touchés ; la section
     * 'reseau' demande un redémarrage.
     */
    private void reloadFiles(CommandSender sender) {
        YamlConfiguration fresh = new YamlConfiguration();
        File configPath = new File(getDataFolder(), "config.yml");
        if (!configPath.exists()) saveDefaultConfig();
        try {
            fresh.load(configPath);
        } catch (IOException | InvalidConfigurationException e) {
            reloadFailed(sender, "config.yml", e);
            return;
        }
        try (InputStreamReader reader = new InputStreamReader(Objects.requireNonNull(getResource("config.yml")), StandardCharsets.UTF_8)) {
            fresh.setDefaults(YamlConfiguration.loadConfiguration(reader));
        } catch (IOException | RuntimeException ignored) {
            // sans les valeurs du jar, celles du code s'appliquent
        }
        YamlConfiguration freshMessages;
        try {
            freshMessages = readMessages(fresh);
        } catch (IOException | InvalidConfigurationException e) {
            reloadFailed(sender, "messages.yml", e);
            return;
        }
        messages = freshMessages;
        loadConfigValues(fresh);
        sender.sendMessage(getMessage("reload-done"));
    }

    private void reloadFailed(CommandSender sender, String file, Exception error) {
        String detail = errorText(error);
        getLogger().warning("Reload cancelled, " + file + " unreadable: " + detail);
        sender.sendMessage(getMessage("reload-error").replace("%file%", file).replace("%error%", detail));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (command.getName().equalsIgnoreCase("shautotool") && args.length == 1) {
            String start = args[0].toLowerCase(Locale.ROOT);
            return allowedSubcommands(sender).stream().filter(sub -> sub.startsWith(start)).toList();
        }
        return List.of();
    }
}
