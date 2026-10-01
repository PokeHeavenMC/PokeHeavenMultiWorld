# Bug : Multiworld écrase le `level.dat` principal (world border perdue après un crash)

> Note destinée à Claude (ou à un dev) travaillant dans ce dépôt. Cible : **Fabric 1.21.1 uniquement**
> (`fabric/Multiworld-Fabric-1.21.1` + `Multiworld-Common`).

## Symptôme observé

Quand le serveur **crashe** (kill, watchdog, crash JVM, sans passage par `/stop`), la world border
disparaît de **tous** les mondes — overworld compris — au redémarrage. Avec un arrêt propre, tout
va bien.

Le mod compagnon **Multi World Borders** (`D:\Dev\Minecraft\worldborderfixer`) gérait mal une
partie du problème et a été corrigé de son côté (voir « Ce qui est déjà corrigé »). La cause
côté overworld est **dans Multiworld**, et elle touche tout le `level.dat`, pas seulement la
border.

## Cause racine

### 1. Toutes les `MultiworldWorld` utilisent la session du serveur principal

`Multiworld-Common/src/main/java/me/isaiah/multiworld/Utils.java` — `shouldUseNewWorldFormat(...)` :
toute la logique est commentée (`/* ... */`) et la méthode **renvoie toujours `false`**.

Donc dans `fabric/Multiworld-Fabric-1.21.1/src/main/java/me/isaiah/multiworld/fabric/MultiworldWorld.java` :

```java
private static Session mw$session(MinecraftServer server, Identifier id) {
    boolean useUs = Utils.shouldUseNewWorldFormat(server, id);   // toujours false
    if (!useUs) { return ((MinecraftServerAccess) server).getSession(); }  // ← session PRINCIPALE
    ...
}
```

`mw$levelStorageAccess` est donc **la session du monde principal**.

### 2. Chaque sauvegarde d'un monde Multiworld réécrit le `level.dat` principal

```java
@Override
public void save(@Nullable ProgressListener progressListener, boolean flush, boolean savingDisabled) {
    super.save(progressListener, flush, savingDisabled);
    this.multiworld$saveLevelDatFile();
}

@Override
public void multiworld$saveLevelDatFile() {
    this.mw$levelStorageAccess.backupLevelDataFile(..., getSaveProperties(), ...);
}
```

`getSaveProperties()` construit un `MySaveProperties` (`Multiworld-Common/.../fabric/MySaveProperties.java`)
via le constructeur public de `LevelProperties` → **world border = `WorldBorder.DEFAULT_BORDER`**.
La ligne `props.setWorldBorder(swProps.getWorldBorder())` n'y change rien : `swProps` est la
`RuntimeWorldProperties` de Fantasy, qui délègue à un autre `MySaveProperties`, lui aussi avec la
border par défaut.

Résultat : `world/level.dat` est réécrit avec la border par défaut, et aussi le `LevelName` du
monde Multiworld, son spawn, son heure, sa météo et ses gamerules, **à la place de ceux de
l'overworld**.

Cela arrive :
- dans le **constructeur** de `MultiworldWorld` (`this.save(null, true, false);`), donc à chaque
  démarrage et à chaque `/mw create` ;
- à chaque autosave / `save-all` (boucle sur les mondes dans `MinecraftServer.saveAll`) ;
- à chaque appel direct à `multiworld$saveLevelDatFile()`.

### 3. Pourquoi ça ne se voit qu'après un crash

Dans `MinecraftServer.saveAll` (1.21.1), vanilla sauvegarde d'abord tous les mondes, **puis**
réécrit `level.dat` avec les bonnes valeurs de l'overworld (`setWorldBorder(overworld.getWorldBorder().write())`
puis `backupLevelDataFile`). Vanilla écrit donc en dernier lors d'une autosave ou d'un arrêt propre.

Si le serveur crashe **après une écriture de Multiworld et avant la prochaine autosave** (toutes
les 5 min, ou jamais s'il crashe peu après le démarrage), `level.dat` garde la version
Multiworld : l'overworld perd sa border (et potentiellement son spawn, son heure, ses gamerules…).

## Correction à faire (dans Multiworld)

Objectif : **une `MultiworldWorld` ne doit jamais écrire le `level.dat` de la session principale.**
Vanilla s'en charge déjà pour l'overworld.

Piste minimale et sûre, dans `MultiworldWorld.multiworld$saveLevelDatFile()` :

```java
@Override
public void multiworld$saveLevelDatFile() {
    // La session principale appartient à vanilla : ne pas écraser le level.dat de l'overworld.
    if (this.mw$levelStorageAccess == ((MinecraftServerAccess) this.getServer()).getSession()) {
        return;
    }
    this.mw$levelStorageAccess.backupLevelDataFile(...);
}
```

Points à vérifier avant ou pendant le correctif :

1. **Persistance par monde (heure, météo, gamerules).** `mw$readLevelProperties` /
   `mw$readGameRules` lisent le `level.dat` depuis `Utils.getWorldStoragePath()` +
   `Utils.getWorldName(id)`, donc **pas** depuis la session principale où l'écriture a lieu
   aujourd'hui. Il faut vérifier si la restauration par monde fonctionne vraiment. Tous les
   mondes écrivent aujourd'hui le même fichier principal, donc le dernier monde sauvegardé
   écrase les autres. Si on veut une persistance par monde, il faut écrire dans un `level.dat`
   **propre à chaque monde** : le même chemin que celui lu par `mw$readLevelProperties`.
2. **`mw$getSession` renvoie une session fermée.** Elle est créée dans un `try-with-resources` et
   renvoyée : le verrou est libéré à la sortie. Un `backupLevelDataFile` dessus échouera
   probablement (`checkValid`). Ne pas se contenter de remplacer la session principale par
   `mw$getSession(...)` sans garder la session ouverte (et la fermer à l'unload/delete du monde).
3. **Border dans `getSaveProperties()`.** Si un `level.dat` par monde est conservé, y écrire la
   vraie border du monde (`props.setWorldBorder(this.getWorldBorder().write())`) plutôt que
   `swProps.getWorldBorder()` (toujours la border par défaut).
4. Ne pas modifier `shouldUseNewWorldFormat` à la légère : ça change le dossier de stockage des
   mondes existants (chunks, entités, données). Il faudrait une migration.

## Ce qui est déjà corrigé côté Multi World Borders (`worldborderfixer`)

Pas besoin de refaire ces points ici ; ils servent de contexte :

- Le mod ne réécrit plus `data/worldBorder.dat` tant que la border n'a pas été restaurée. Avant,
  la sauvegarde faite dans le constructeur de `MultiworldWorld` (avant `ServerWorldEvents.LOAD`)
  remplaçait la border sauvegardée par la border par défaut.
- La border de l'overworld est restaurée depuis son propre `data/worldBorder.dat` s'il existe,
  plutôt que depuis `level.dat`. Cela contourne ce bug pour la border uniquement.
- Chaque changement de border est écrit tout de suite sur le disque.

Ce correctif ne protège **que la border**. Le spawn, l'heure, la météo, les gamerules et le nom du
monde principal restent exposés à ce bug tant que Multiworld écrit dans le `level.dat` principal.

## Test de validation

1. Serveur Fabric 1.21.1 avec Multiworld + Multi World Borders.
2. Dans l'overworld : `/worldborder set 200`, `/gamerule doDaylightCycle false`, noter le spawn.
3. `/mw create test NORMAL`, puis s'assurer qu'**aucune autosave** n'a eu lieu depuis.
4. Tuer le processus Java (pas de `/stop`).
5. Redémarrer : border, gamerule, spawn et `LevelName` de l'overworld doivent être intacts.
   Avant correctif : `level.dat` contient les valeurs du monde `test` / les valeurs par défaut.
6. Refaire le test avec un arrêt propre pour vérifier qu'il n'y a pas de régression.
