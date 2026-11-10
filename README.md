<div align="center">

<img src="assets/logo.png" alt="SkyHand" width="140">

# SHAutoTool

*Change automatiquement d'outil pour le plus adapté dans la hotbar.*

<img src="https://img.shields.io/badge/Paper-26.3-f2a900?style=for-the-badge" alt="Paper 26.3"> <img src="https://img.shields.io/badge/Java-25-e76f00?style=for-the-badge" alt="Java 25"> <img src="https://img.shields.io/badge/SkyHand-Opale-3aa655?style=for-the-badge" alt="SkyHand Opale">

</div>

---

## Présentation

Change automatiquement d'outil pour le plus adapté dans la hotbar.

Utilisé sur : **Opale**.

## Dépendances

- Optionnelles : `PlaceholderAPI`, `SHNetwork` (mode réseau)

## Mode réseau

Avec **SHNetwork** installé (et `reseau.actif: true`, valeur par défaut), plusieurs serveurs de jeu partagent la liste des joueurs qui ont désactivé l'AutoTool :

- la liste est dans MariaDB (table `shautotool_disabled`, une ligne par joueur désactivé) ; `/shautotooltoggle` ajoute ou retire cette seule ligne, sur un fil à part ;
- au départ d'un joueur, SHNetwork attend que ces écritures soient finies ;
- à son arrivée sur l'autre serveur, son état est relu en base une fois le départ confirmé ; d'ici là l'AutoTool est actif (valeur par défaut, jamais écrite en base) ;
- `disabled_players.txt` n'est plus lu ni écrit : `/shautotool import` le reprend une fois dans la base, sans écraser ce qui y est déjà.

Sans SHNetwork, ou sans les clés `reseau` dans la configuration, le plugin se comporte comme avant : liste locale dans `disabled_players.txt`. Si la base est injoignable, le joueur garde l'AutoTool actif. La table se crée toute seule au démarrage, ou à la main avec `sql/schema.sql`.

## Commandes

| Commande | Alias | Description | Permission |
|---|---|---|---|
| `/shautotooltoggle` | `/shatt`, `/autotooltoggle` | Active ou désactive l'AutoTool pour vous. | — |
| `/shautotool` | `/shat` | Commande administrative pour SHAutoTool (`reload`, `import`). | `shautotool.admin` |

## Permissions

| Permission | Description | Par défaut |
|---|---|---|
| `shautotool.admin` | Donne accès aux commandes d'administration de SHAutoTool. | `op` |
| `shautotool.reload` | Permet de recharger la configuration de SHAutoTool. | `op` |

## Configuration

Fichiers par défaut repris du serveur (secrets remplacés par `CHANGE_ME`). Cliquez sur un fichier pour l'ouvrir :

<pre>
plugins/SHAutoTool/
├── <a href="src/main/resources/config.yml">config.yml</a>
└── <a href="src/main/resources/messages.yml">messages.yml</a>
</pre>

## Compilation

Java 25 et Maven :

```bash
mvn clean package
```

Le jar est généré dans `target/`.

---

<div align="center">

<sub>Développé par <b>Cry_Legende</b> pour le serveur <b>SkyHand</b> · Tous droits réservés</sub>

</div>
