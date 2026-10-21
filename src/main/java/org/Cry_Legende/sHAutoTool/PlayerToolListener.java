package org.Cry_Legende.sHAutoTool;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public class PlayerToolListener implements Listener {

    private final SHAutoTool plugin;
    private final ToolLogic toolLogic;

    public PlayerToolListener(SHAutoTool plugin, ToolLogic toolLogic) {
        this.plugin = plugin;
        this.toolLogic = toolLogic;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        // --- NOUVEAU : Vérification de la hache en or (WorldEdit Wand) ---
        // Si l'option est activée et que le joueur tient une hache en or, on ne fait rien.
        if (plugin.shouldDisableForGoldenAxe() &&
            player.getInventory().getItemInMainHand().getType() == Material.GOLDEN_AXE) {
            return;
        }
        // --- FIN NOUVEAU ---

        // Optimisations: Sortir tôt si possible
        if (plugin.isPlayerDisabled(player) ||
            player.getGameMode() == GameMode.CREATIVE ||
            player.getGameMode() == GameMode.SPECTATOR ||
            !plugin.isWorldAllowed(player.getWorld())) {
            return;
        }

        if (event.getAction() == Action.LEFT_CLICK_BLOCK && event.getClickedBlock() != null) {
            if (plugin.isBlockBlacklisted(event.getClickedBlock())) {
                return;
            }

            toolLogic.findAndSwitchToBestToolForBlock(player, event.getClickedBlock());
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }

        // --- NOUVEAU : Vérification de la hache en or (WorldEdit Wand) ---
        // Si l'option est activée et que le joueur tient une hache en or, on ne fait rien.
        if (plugin.shouldDisableForGoldenAxe() &&
            player.getInventory().getItemInMainHand().getType() == Material.GOLDEN_AXE) {
            return;
        }
        // --- FIN NOUVEAU ---

        // Optimisations: Sortir tôt si possible
        if (plugin.isPlayerDisabled(player) ||
            player.getGameMode() == GameMode.CREATIVE ||
            player.getGameMode() == GameMode.SPECTATOR ||
            !plugin.isWorldAllowed(player.getWorld())) {
            return;
        }

        if (!(event.getEntity() instanceof LivingEntity)) {
            return;
        }

        toolLogic.findAndSwitchToBestWeapon(player, event.getEntityType());
    }
}
