package org.Cry_Legende.sHAutoTool;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class AutoToolExpansion extends PlaceholderExpansion {

    private final SHAutoTool plugin;

    public AutoToolExpansion(SHAutoTool plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "shautotool";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Cry_Legende";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    /*
     * C'est ici que la magie opère. La méthode est appelée quand PAPI
     * rencontre un de vos placeholders.
     *
     * @param player Le joueur pour lequel le placeholder est demandé (peut être null).
     * @param identifier Le nom du placeholder après le préfixe (ex: "status").
     * @return La valeur du placeholder, ou null si l'identifier n'est pas reconnu.
     */
    @Override // <--- L'@Override fonctionnera maintenant
    public String onRequest(OfflinePlayer player, @NotNull String identifier) {
        // MODIFIÉ : La méthode a été renommée de onPlaceholderRequest à onRequest

        if (player == null) {
            return "";
        }

        // Nous avons besoin du joueur en ligne pour vérifier son statut
        Player onlinePlayer = player.getPlayer();
        if (onlinePlayer == null) {
            return ""; // Le joueur n'est pas en ligne, on ne peut pas savoir
        }

        if (identifier.equalsIgnoreCase("status")) {
            // On vérifie si l'autotool est désactivé pour ce joueur
            if (plugin.isPlayerDisabled(onlinePlayer)) {
                // On retourne le texte configuré pour l'état "désactivé"
                return ChatColor.translateAlternateColorCodes('&', plugin.getPlaceholderDisabled());
            } else {
                // Sinon, on retourne le texte pour l'état "activé"
                return ChatColor.translateAlternateColorCodes('&', plugin.getPlaceholderEnabled());
            }
        }

        // Si l'identifier n'est pas "status", on retourne null pour que PAPI sache
        // qu'on ne gère pas ce placeholder.
        return null;
    }
}