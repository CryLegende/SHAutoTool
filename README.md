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

## Commandes

| Commande | Alias | Description | Permission |
|---|---|---|---|
| `/shautotooltoggle` | `/shatt`, `/autotooltoggle` | Active ou désactive l'AutoTool pour vous. | — |
| `/shautotool` | `/shat` | Commande administrative pour SHAutoTool. | `shautotool.admin` |

## Permissions

| Permission | Description | Par défaut |
|---|---|---|
| `shautotool.admin` | Donne accès aux commandes d'administration de SHAutoTool. | `op` |
| `shautotool.reload` | Permet de recharger la configuration de SHAutoTool. | `op` |

## Configuration

Fichiers par défaut repris du serveur (secrets remplacés par `CHANGE_ME`) :

- `config.yml`

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
