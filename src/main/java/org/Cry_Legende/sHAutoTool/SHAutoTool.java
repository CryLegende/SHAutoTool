package org.Cry_Legende.sHAutoTool;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.*;

public class SHAutoTool extends JavaPlugin {

    private final Set<UUID> disabledPlayers = new HashSet<>();
    private ToolLogic toolLogic;
    private File disabledPlayersFile;

    // --- Champs pour la configuration ---
    private String worldMode;
    private Set<String> worldList;
    private Set<Material> blockBlacklist = new HashSet<>();
    private String placeholderEnabled;
    private String placeholderDisabled;
    private String messageToggleEnabled;
    private String messageToggleDisabled;
    private boolean disableIfHoldingGoldenAxe; // <--- NOUVEAU CHAMP

    @Override
    public void onEnable() {
        // Important: sauvegarder les valeurs par défaut AVANT de charger,
        // pour que les nouvelles clés soient ajoutées au fichier de l'utilisateur.
        saveDefaultConfig();

        loadConfigValues();

        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        disabledPlayersFile = new File(getDataFolder(), "disabled_players.txt");

        loadDisabledPlayersFromFile();

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
        saveDisabledPlayersToFile(false);
        getLogger().info("SHAutoTool disabled!");
    }

    private void loadConfigValues() {
        FileConfiguration config = getConfig();

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

        this.messageToggleEnabled = config.getString("messages.toggle-enabled", "&aSHAutoTool a été activé pour vous !");
        this.messageToggleDisabled = config.getString("messages.toggle-disabled", "&cSHAutoTool a été désactivé pour vous !");

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
        Set<UUID> playersToSave = new HashSet<>(disabledPlayers);

        Runnable saveTask = () -> {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(disabledPlayersFile, false))) {
                for (UUID uuid : playersToSave) {
                    writer.write(uuid.toString());
                    writer.newLine();
                }
            } catch (IOException e) {
                getLogger().severe("Could not save disabled players to " + disabledPlayersFile.getName() + ": " + e.getMessage());
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
        if (disabledPlayers.contains(playerId)) {
            disabledPlayers.remove(playerId);
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', this.messageToggleEnabled));
        } else {
            disabledPlayers.add(playerId);
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', this.messageToggleDisabled));
        }
        saveDisabledPlayersToFile(true);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (command.getName().equalsIgnoreCase("shautotooltoggle")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("This command can only be run by a player.");
                return true;
            }
            togglePlayer(player);
            return true;
        } else if (command.getName().equalsIgnoreCase("shautotool")) {
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                if (!sender.hasPermission("shautotool.reload")) {
                    sender.sendMessage("§cYou do not have permission to execute this command.");
                    return true;
                }
                reloadConfig();
                loadConfigValues();
                sender.sendMessage("§aSHAutoTool configuration reloaded.");
                return true;
            }
            sender.sendMessage("§eUsage: /shautotool reload");
            return true;
        }
        return false;
    }
}
