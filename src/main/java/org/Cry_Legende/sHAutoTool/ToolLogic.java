package org.Cry_Legende.sHAutoTool;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.EnumSet;

public class ToolLogic {

    private final SHAutoTool plugin;

    // Ensembles pour identifier les types d'outils
    private static final EnumSet<Material> SWORDS = EnumSet.of(
        Material.WOODEN_SWORD, Material.STONE_SWORD, Material.IRON_SWORD,
        Material.GOLDEN_SWORD, Material.DIAMOND_SWORD, Material.NETHERITE_SWORD
    );
    private static final EnumSet<Material> AXES = EnumSet.of(
        Material.WOODEN_AXE, Material.STONE_AXE, Material.IRON_AXE,
        Material.GOLDEN_AXE, Material.DIAMOND_AXE, Material.NETHERITE_AXE
    );
    private static final EnumSet<Material> PICKAXES = EnumSet.of(
        Material.WOODEN_PICKAXE, Material.STONE_PICKAXE, Material.IRON_PICKAXE,
        Material.GOLDEN_PICKAXE, Material.DIAMOND_PICKAXE, Material.NETHERITE_PICKAXE
    );
    private static final EnumSet<Material> SHOVELS = EnumSet.of(
        Material.WOODEN_SHOVEL, Material.STONE_SHOVEL, Material.IRON_SHOVEL,
        Material.GOLDEN_SHOVEL, Material.DIAMOND_SHOVEL, Material.NETHERITE_SHOVEL
    );
    private static final EnumSet<Material> HOES = EnumSet.of(
        Material.WOODEN_HOE, Material.STONE_HOE, Material.IRON_HOE,
        Material.GOLDEN_HOE, Material.DIAMOND_HOE, Material.NETHERITE_HOE
    );
    private static final EnumSet<Material> SHEARS = EnumSet.of(Material.SHEARS);

    private static final EnumSet<Material> ALL_TOOLS = EnumSet.noneOf(Material.class);

    static {
        ALL_TOOLS.addAll(SWORDS);
        ALL_TOOLS.addAll(AXES);
        ALL_TOOLS.addAll(PICKAXES);
        ALL_TOOLS.addAll(SHOVELS);
        ALL_TOOLS.addAll(HOES);
        ALL_TOOLS.addAll(SHEARS);
    }

    public ToolLogic(SHAutoTool plugin) {
        this.plugin = plugin;
    }

    public boolean isTool(ItemStack item) {
        return item != null && ALL_TOOLS.contains(item.getType());
    }

    public boolean isWeapon(ItemStack item) {
        return item != null && (SWORDS.contains(item.getType()) || AXES.contains(item.getType()));
    }

    public boolean isCorrectToolTypeForBlock(ItemStack tool, Block block) {
        if (tool == null || tool.getType() == Material.AIR) return false;
        Material toolMaterial = tool.getType();
        Material blockMaterial = block.getType();

        if (Tag.MINEABLE_PICKAXE.isTagged(blockMaterial)) return PICKAXES.contains(toolMaterial);
        if (Tag.MINEABLE_AXE.isTagged(blockMaterial)) return AXES.contains(toolMaterial);
        if (Tag.MINEABLE_SHOVEL.isTagged(blockMaterial)) return SHOVELS.contains(toolMaterial);
        if (Tag.MINEABLE_HOE.isTagged(blockMaterial)) return HOES.contains(toolMaterial);

        if (Tag.WOOL.isTagged(blockMaterial) ||
            Tag.LEAVES.isTagged(blockMaterial) ||
            blockMaterial == Material.COBWEB ||
            blockMaterial == Material.VINE ||
            blockMaterial == Material.TRIPWIRE ||
            blockMaterial == Material.GLOW_LICHEN ||
            blockMaterial == Material.SCULK_VEIN) {
            return SHEARS.contains(toolMaterial);
        }
        return false;
    }

    public void findAndSwitchToBestToolForBlock(Player player, Block block) {
        PlayerInventory inventory = player.getInventory();
        ItemStack currentHeldItem = inventory.getItemInMainHand();
        int currentHeldSlot = inventory.getHeldItemSlot();

        if (isCorrectToolTypeForBlock(currentHeldItem, block)) {
            return;
        }

        int bestSlot = -1;
        float bestSpeed = 0.0f;

        for (int i = 0; i < 9; i++) {
            ItemStack itemInSlot = inventory.getItem(i);
            if (itemInSlot == null || itemInSlot.getType() == Material.AIR) continue;

            // --- MODIFICATION ---
            // On ignore la hache en or pour éviter les conflits avec WorldEdit.
            if (itemInSlot.getType() == Material.GOLDEN_AXE) {
                continue;
            }
            // --- FIN DE LA MODIFICATION ---

            float speed = block.getDestroySpeed(itemInSlot, true);

            if (isCorrectToolTypeForBlock(itemInSlot, block) && speed > bestSpeed) {
                bestSpeed = speed;
                bestSlot = i;
            }
        }

        if (bestSlot != -1 && bestSlot != currentHeldSlot) {
            inventory.setHeldItemSlot(bestSlot);
        }
    }

    public void findAndSwitchToBestWeapon(Player player, EntityType targetType) {
        PlayerInventory inventory = player.getInventory();
        ItemStack currentHeldItem = inventory.getItemInMainHand();
        int currentHeldSlot = inventory.getHeldItemSlot();

        if (isWeapon(currentHeldItem)) {
            // Petite exception : si on tient la hache en or, on cherche quand même une meilleure arme.
            if (currentHeldItem.getType() != Material.GOLDEN_AXE) {
                return;
            }
        }

        int bestSlot = -1;
        float bestDamageScore = -1.0f;

        for (int i = 0; i < 9; i++) {
            ItemStack itemInSlot = inventory.getItem(i);
            if (itemInSlot == null || itemInSlot.getType() == Material.AIR) continue;

            // --- MODIFICATION ---
            // On ignore aussi la hache en or lors de la recherche d'une arme.
            if (itemInSlot.getType() == Material.GOLDEN_AXE) {
                continue;
            }
            // --- FIN DE LA MODIFICATION ---

            float currentDamageScore = 0f;
            boolean isCurrentSlotWeapon = false;

            if (SWORDS.contains(itemInSlot.getType())) {
                isCurrentSlotWeapon = true;
                currentDamageScore = 1000f;
                currentDamageScore += itemInSlot.getEnchantmentLevel(Enchantment.DAMAGE_ALL) * 100;
                currentDamageScore += itemInSlot.getEnchantmentLevel(Enchantment.DAMAGE_UNDEAD) * getUndeadMultiplier(targetType);
                currentDamageScore += itemInSlot.getEnchantmentLevel(Enchantment.DAMAGE_ARTHROPODS) * getArthropodMultiplier(targetType);
            } else if (AXES.contains(itemInSlot.getType())) {
                isCurrentSlotWeapon = true;
                currentDamageScore = 500f;
                currentDamageScore += itemInSlot.getEnchantmentLevel(Enchantment.DAMAGE_ALL) * 100;
            }

            if (isCurrentSlotWeapon && currentDamageScore > bestDamageScore) {
                bestDamageScore = currentDamageScore;
                bestSlot = i;
            }
        }

        if (bestSlot != -1 && bestSlot != currentHeldSlot) {
            inventory.setHeldItemSlot(bestSlot);
        }
    }

    private float getUndeadMultiplier(EntityType type) {
        return switch (type) {
            case SKELETON, STRAY, WITHER_SKELETON, ZOMBIE, HUSK, DROWNED,
                 ZOMBIE_VILLAGER, ZOMBIFIED_PIGLIN, PHANTOM, WITHER, ZOGLIN -> 50f;
            default -> 0f;
        };
    }

    private float getArthropodMultiplier(EntityType type) {
        return switch (type) {
            case SPIDER, CAVE_SPIDER, BEE, SILVERFISH, ENDERMITE -> 50f;
            default -> 0f;
        };
    }
}
