package me.isaiah.multiworld.command;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import me.isaiah.multiworld.MultiworldMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameRules;
import net.minecraft.world.level.ServerWorldProperties;

public class InfoCommand implements Command {

    /**
     * "/mw info [world id]"
     *
     * Read-only diagnostics for a dimension: players, time of day, weather (+ change timers),
     * gamerules differing from the overworld, and spawn position. Targets the player's current
     * dimension by default, or the named world if an id is supplied. Mirrors {@link DifficultyCommand}.
     */
    public static int run(MinecraftServer mc, ServerPlayerEntity plr, String[] args) {
        ServerWorld w = Command.getWorldFor(plr);

        // Optional world id (args[1]) — same resolution as DifficultyCommand.
        if (args.length >= 2) {
            String a1 = args[1];

            HashMap<String, ServerWorld> worlds = new HashMap<>();
            mc.getWorldRegistryKeys().forEach(r -> {
                ServerWorld world = mc.getWorld(r);
                worlds.put(r.getValue().toString(), world);
            });

            if (a1.indexOf(':') == -1) a1 = "multiworld:" + a1;

            if (worlds.containsKey(a1)) {
                w = worlds.get(a1);
            } else {
                MultiworldMod.message(plr, "&cWorld not found: " + a1);
                return 1;
            }
        }

        String id = w.getRegistryKey().getValue().toString();

        MultiworldMod.message(plr, "&b=== Multiworld Info: &r" + id + " &b===");

        // Players
        List<ServerPlayerEntity> players = w.getPlayers();
        if (players.isEmpty()) {
            MultiworldMod.message(plr, "&aPlayers&r (0): (none)");
        } else {
            StringBuilder names = new StringBuilder();
            for (ServerPlayerEntity p : players) {
                if (names.length() > 0) names.append(", ");
                names.append(p.getName().getString());
            }
            MultiworldMod.message(plr, "&aPlayers&r (" + players.size() + "): " + names);
        }

        // Time
        long timeOfDay = w.getTimeOfDay();
        long day = timeOfDay / 24000L;
        long dayTime = timeOfDay % 24000L;
        MultiworldMod.message(plr, "&aTime&r: " + timeOfDay + " (day " + day + ", daytime " + dayTime + ")");

        // Difficulty (per-dimension: Fantasy's RuntimeWorldProperties, not the server's)
        MultiworldMod.message(plr, "&aDifficulty&r: " + w.getDifficulty().getName()
                + (w.getLevelProperties().isDifficultyLocked() ? " (locked)" : ""));

        // Weather
        boolean raining = w.isRaining();
        boolean thundering = w.isThundering();
        String state = thundering ? "thunder" : (raining ? "rain" : "clear");
        StringBuilder weather = new StringBuilder("&aWeather&r: " + state);
        if (w.getLevelProperties() instanceof ServerWorldProperties props) {
            int clear = props.getClearWeatherTime();
            int rain = props.getRainTime();
            int thunder = props.getThunderTime();
            weather.append("(clear=").append(clear)
                   .append(", rain=").append(rain)
                   .append(", thunder=").append(thunder).append(")");
        }
        MultiworldMod.message(plr, weather.toString());

        // Gamerules differing from the overworld
        GameRules worldRules = w.getGameRules();
        GameRules overworldRules = mc.getOverworld().getGameRules();
        List<String> diffs = new ArrayList<>();
        worldRules.accept(new GameRules.Visitor() {
            @Override
            public <T extends GameRules.Rule<T>> void visit(GameRules.Key<T> key, GameRules.Type<T> type) {
                String here = worldRules.get(key).serialize();
                String over = overworldRules.get(key).serialize();
                if (!here.equals(over)) {
                    diffs.add(key.getName() + ": " + here + " (overworld: " + over + ")");
                }
            }
        });
        if (diffs.isEmpty()) {
            MultiworldMod.message(plr, "&aGamerules&r: (same as overworld)");
        } else {
            MultiworldMod.message(plr, "&aGamerules differing from overworld&r (" + diffs.size() + "):");
            for (String d : diffs) {
                MultiworldMod.message(plr, "  &7- &r" + d);
            }
        }

        // Spawn (same source as /mw spawn: Multiworld config "spawnpos" set by /mw setspawn, with fallback)
        BlockPos spawn = SpawnCommand.getSpawn(w);
        MultiworldMod.message(plr, "&aSpawn&r: " + spawn.getX() + " / " + spawn.getY() + " / " + spawn.getZ());

        return 1;
    }

}
