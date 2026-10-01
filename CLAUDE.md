# CLAUDE.md — Multiworld (Fabric 1.21.1)

> Ce dépôt est multi-plateforme (Fabric / NeoForge / Forge) et multi-versions Minecraft.
> **On travaille UNIQUEMENT sur la cible Fabric 1.21.1.** Ignore les autres modules
> (`fabric/Multiworld-Fabric-1.18.2` … `1.21.11`, `neoforge/*`, `Multiworld-Forge-*`)
> sauf si un changement dans `Multiworld-Common` les impacte (voir « Code partagé » plus bas).

## Qu'est-ce que c'est

Le mod **Multiworld** : crée, gère et téléporte entre plusieurs mondes à l'exécution.
Commande principale `/mw` (`tp`, `list`, `spawn`, `setspawn`, `create`, `delete`, `gamerule`,
`difficulty`, `portal`). La création de mondes runtime s'appuie sur la lib **Fantasy** de NucleoidMC.
Pour les World Border par multiworld

## Architecture

Le code est séparé en deux :

- **`Multiworld-Common/`** — TOUTE la logique du mod, agnostique de la plateforme.
  - `me.isaiah.multiworld.MultiworldMod` — point d'entrée logique : init, `register_commands`,
    `on_server_started`, dispatch de la commande `/mw` (`broadcast`).
  - `me.isaiah.multiworld.command.*` — implémentation de chaque sous-commande.
  - `me.isaiah.multiworld.ICreator` — **interface clé** : abstrait toutes les API Minecraft
    qui changent entre versions (création de monde, téléportation, générateurs, permissions…).
    Chaque cible plateforme/version en fournit une implémentation.
  - `multiworld.mixin.*` — mixins partagés (`MixinNetherPortalBlock`, `MixinGameruleCommand`,
    `MixinLevelStorageSession`, `MixinLevelInfo`).
  - `me.isaiah.multiworld.fabric.*` — helpers côté Fabric partagés (permissions LuckPerms/CyberPerms
    via `PermFabric`, `FabricEvents`, `MySaveProperties`…).

- **`fabric/Multiworld-Fabric-1.21.1/`** — la cible qu'on développe. Très mince :
  - `MultiworldModFabric` — `ModInitializer` (entrypoint `fabric.mod.json`). Branche les events
    Fabric (`ServerLifecycleEvents.SERVER_STARTED`, `CommandRegistrationCallback`) sur `MultiworldMod`.
  - `FabricWorldCreator` — implémentation de `ICreator` pour 1.21.1 (API yarn 1.21.1, Fantasy 0.6.3+1.21).
  - `MultiworldWorld` — `RuntimeWorld` Fantasy + `IMultiworldWorld` ; gère la persistance du `level.dat`.

Le module Fabric **inclut les sources de `Multiworld-Common` via `srcDir`** (voir `build.gradle.kts`
et le `build.gradle` racine) — il n'y a pas de dépendance compilée, les `.java` communs sont
compilés directement dans le module. Le dossier `dimapi` est exclu (non nécessaire en 1.21).

### Préprocesseur de version

Le `build.gradle` racine définit `createPreprocessor` : un préprocesseur maison basé sur des
commentaires `// #if mcXXX` / `// #elif` / `// #else` / `// #endif` dans le code Common.
Pour 1.21.1, `targetVersion = "mc211"` (défini dans `build.gradle.kts`). Les branches non
sélectionnées restent commentées ; la branche prise est dé-commentée à la compilation.
Quand tu touches du code commun avec ces directives, garde la cible `mc211` à l'esprit.

## Détails de version (1.21.1)

- Mappings : **Yarn `1.21.1+build.3:v2`** (noms `net.minecraft.*` à la yarn, pas Mojmap).
- `fabric-loader` `0.18.3`, `fabric-api` `0.103.0+1.21.1`.
- **Fantasy** `0.6.3+1.21` (résolu via `mavenLocal()` — voir « Pièges »).
- Java : source/target **21**, mais `options.release = 17` (Jabel permet la syntaxe 21
  tout en ciblant le bytecode 17). Les mixins sont en `JAVA_17`.
- Téléportation : utilise `player.teleportTo(TeleportTarget)` (l'API FabricDimension est
  remplacée en 1.21, cf. `FabricWorldCreator.teleleport`).

## Build & sortie

```powershell
# Construire uniquement la cible Fabric 1.21.1
./gradlew :Multiworld-Fabric-1.21.1:build
```

Le jar remappé est copié dans `output/` à la fin du build (tâche `copyReport2`).
`copy.bat` / `deleteOutput.bat` sont des utilitaires de copie/nettoyage.

## Pièges / points d'attention

- **Fantasy via `mavenLocal()`** : `build.gradle.kts` ajoute `mavenLocal()` pour résoudre
  `xyz.nucleoid:fantasy:0.6.3+1.21`. Si le build échoue sur cette dépendance, il faut la
  publier/installer dans le maven local.
- **Code partagé = impact multi-versions** : modifier `Multiworld-Common` touche TOUTES les
  cibles. Reste dans `ICreator` / les directives `// #if` pour isoler ce qui est spécifique
  à une version, et ne casse pas les autres branches.
- `fabric.mod.json` : `version` est injectée au build (`mod_version` dans `gradle.properties`,
  filtrée par `processResources`). Ne pas hardcoder.
- Permissions : appuyées sur LuckPerms **ou** CyberPerms (deps `modImplementation`), avec
  fallback sur le niveau d'op vanilla. Permission `multiworld.admin` ou op = accès total.

## Conventions

- Préfixe `mw$` / `multiworld$` pour les membres ajoutés par le mod (évite les collisions
  avec le mapping yarn, notamment dans les classes étendant des types Minecraft/Fantasy).
- Les couleurs de chat utilisent les codes `&` (traduits en `§` par `MultiworldMod`).