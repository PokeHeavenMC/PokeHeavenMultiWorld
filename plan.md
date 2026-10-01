# Plan : corriger l'absence de météo (pluie/orage) dans les dimensions Multiworld

## Contexte / cause racine

Le mod **Multiworld** (IsaiahMC) crée ses mondes via la librairie **Fantasy** (NucleoidMC),
embarquée dans le build. L'état météo d'un monde runtime est délégué à un
`RuntimeWorldConfig`, dont le champ par défaut est :

```java
// xyz.nucleoid.fantasy.RuntimeWorldConfig
private int sunnyTime = Integer.MAX_VALUE;
```

Ce `sunnyTime` est exposé comme le **clearWeatherTime** vanilla :

```java
// xyz.nucleoid.fantasy.RuntimeWorldProperties
public int getClearWeatherTime() { return this.config.getSunnyTime(); }
```

Or, dans `ServerWorld.tickWeather()`, tant que `clearWeatherTime > 0`, le jeu **force le
beau temps** : il décrémente ce compteur de 1 par tick et remet `raining`/`thundering` à
`false`, sans jamais lancer la pluie. Partant de ~2,1 milliards, il faudrait ~3 400 ans de
jeu pour atteindre 0. Résultat : **il ne pleut jamais**. (Équivaut à `/weather clear` permanent.)

Référence : issue GitHub #168 « Weather isn't working in multiworld dimensions ».

## Décision de conception : le correctif doit être rétroactif

**Le correctif minimal est automatiquement rétroactif — aucune recréation de monde ni
migration de `level.dat` n'est nécessaire.** Raisons vérifiées dans le code :

1. Au démarrage (`MultiworldMod.on_server_started`), chaque monde sauvegardé est rouvert
   en repassant par le **même** `FabricWorldCreator.create_world(...)`.
2. `create_world` reconstruit un `new RuntimeWorldConfig()` neuf à chaque appel
   (donc `sunnyTime = Integer.MAX_VALUE` par défaut).
3. `RuntimeWorldProperties.getClearWeatherTime()` lit **toujours** `config.getSunnyTime()`,
   jamais le `clearWeatherTime` sauvegardé dans le `level.dat`.
4. Fantasy ne restaure pas l'état météo depuis le disque
   (`getOrOpenPersistentWorld` utilise le config fourni tel quel).

Conclusion : corriger la valeur du config dans `create_world` répare aussi bien les nouveaux
mondes que les mondes existants, dès le prochain redémarrage du serveur.

## Correctif principal (cible : Fabric 1.21.1)

Fichier : `fabric/Multiworld-Fabric-1.21.1/src/main/java/me/isaiah/multiworld/fabric/FabricWorldCreator.java`

Dans la méthode `create_world(...)`, ajouter `.setSunny(0)` au builder du `RuntimeWorldConfig` :

```java
RuntimeWorldConfig config = new RuntimeWorldConfig()
        .setDimensionType(dim_of(dim))
        .setGenerator(gen)
        .setDifficulty(dif)
        .setSeed(seed)
        .setShouldTickTime(true)
        .setWorldConstructor(MultiworldWorld::new)
        .setSunny(0)   // <-- AJOUT : remet clearWeatherTime à 0 pour que le cycle météo vanilla fonctionne
        ;
```

Notes pour l'implémentation :
- `setSunny(int)` est public sur `RuntimeWorldConfig` et renvoie le config (builder chaînable).
- `setSunny(0)` met `sunnyTime = 0`, `raining = false`, `thundering = false`. Le cycle vanilla
  prend ensuite le relais : `tickWeather()` appelle `setRaining(...)`/`setThundering(...)` en
  **dernier** à chaque tick, donc l'état final fait autorité et n'est plus écrasé.
- **Ne PAS** sous-classer `RuntimeWorldProperties` : elle est `public final` et `RuntimeWorld`
  fait un cast direct `(RuntimeWorldProperties) this.properties`. Inutile aussi d'éditer Fantasy.

Note : un admin qui veut figer le beau temps sur une dimension précise peut le faire sans code,
via la gamerule `doWeatherCycle false` sur ce monde (le cycle s'arrête et le temps reste clair).
Pas besoin d'un paramètre dédié.

## Pré-requis côté jeu (à documenter, pas à coder)

Le cycle météo vanilla n'avance que si la gamerule `doWeatherCycle` du monde est à `true`.
Le bug empêchait la pluie **même** avec cette gamerule activée ; après correctif, vérifier que
la dimension a bien `doWeatherCycle=true` (`/mw gamerule doWeatherCycle true` ou via le
`level.dat`/config du monde). Les gamerules sont déjà rechargées depuis le `level.dat` par
`readGameRules` / `reinitWorldGamerules`, donc rien à changer ici.

## Périmètre

**Uniquement Fabric 1.21.1.** Ne pas toucher aux autres dossiers de version (`Multiworld-Fabric-1.18.2`,
`1.19.x`, `1.20.x`, `1.21.4`, `1.21.8`, `1.21.10`, `1.21.11`, ni les modules Forge/NeoForge),
même si le bug y existe aussi.

## Alternative technique au correctif

Le correctif builder `.setSunny(0)` est l'approche recommandée. Une autre option, plus lourde et
à réserver si jamais elle ne convient pas :

- **Mixin sur Fantasy embarqué** : comme Multiworld embarque Fantasy et utilise déjà des mixins
  (cf. `xyz.nucleoid.fantasy.mixin.*`), on peut cibler `RuntimeWorldConfig` pour changer la valeur
  par défaut de `sunnyTime` (0 au lieu de `Integer.MAX_VALUE`) via `@ModifyConstant`/redirection
  d'initialiseur. Ne nécessite pas de forker Fantasy.

Privilégier le correctif principal `.setSunny(0)` : minimal, lisible, rétroactif.

## Vérification

1. `./gradlew build` (ou la tâche de build du module Fabric 1.21.1) doit compiler sans erreur.
2. Test runtime :
   - Démarrer un serveur avec un monde Multiworld **déjà existant** (créé avant le patch).
   - S'y téléporter, `/mw gamerule doWeatherCycle true` si besoin.
   - `/weather rain` doit fonctionner, et le cycle naturel doit finir par déclencher la pluie
     sans rester bloqué au beau fixe. Aucune recréation de monde nécessaire.
3. Optionnel : test sur un **nouveau** monde (`/mw create ...`) pour valider le cas création.

## Résumé en une phrase

Ajouter `.setSunny(0)` au builder `RuntimeWorldConfig` dans `FabricWorldCreator.create_world`
(Fabric 1.21.1 uniquement) corrige la météo pour les mondes neufs **et** existants (rétroactif au
prochain redémarrage), sans toucher au repo Fantasy ni recréer/migrer aucun monde.
