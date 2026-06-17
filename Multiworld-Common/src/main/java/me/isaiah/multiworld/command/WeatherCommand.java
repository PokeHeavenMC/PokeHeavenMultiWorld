package me.isaiah.multiworld.command;

import java.util.HashMap;

import me.isaiah.multiworld.MultiworldMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

public class WeatherCommand implements Command {

    /**
     * "/mw weather <clear|rain|thunder> [world id] [duration]"
     *
     * Sets the weather of the world the player is currently in, or of the named world if a world id
     * is supplied. Mirrors {@link TimeCommand} / {@link DifficultyCommand}.
     *
     * Unlike vanilla {@code /weather} (which only affects the dimension the sender is in), this can
     * target an arbitrary runtime world. The optional duration is expressed in seconds, exactly like
     * vanilla {@code /weather} (which multiplies it by 20 to get ticks). When omitted, the same
     * random duration providers vanilla uses are applied.
     */
    public static int run(MinecraftServer mc, ServerPlayerEntity plr, String[] args) {
        ServerWorld w = Command.getWorldFor(plr);

        if (args.length < 2) {
            MultiworldMod.message(plr, "&cUsage: /mw weather <clear|rain|thunder> [world id] [duration seconds]");
            return 1;
        }

        String type = args[1].toLowerCase();

        // Optional world id (args[2]) — same resolution as TimeCommand/DifficultyCommand.
        if (args.length >= 3) {
            String a2 = args[2];

            HashMap<String, ServerWorld> worlds = new HashMap<>();
            mc.getWorldRegistryKeys().forEach(r -> {
                ServerWorld world = mc.getWorld(r);
                worlds.put(r.getValue().toString(), world);
            });

            if (a2.indexOf(':') == -1) a2 = "multiworld:" + a2;

            if (worlds.containsKey(a2)) {
                w = worlds.get(a2);
            } else {
                MultiworldMod.message(plr, "&cWorld not found: " + a2);
                return 1;
            }
        }

        // Optional duration in seconds (args[3]) -> ticks (*20). -1 = "not provided".
        int durationTicks = -1;
        if (args.length >= 4) {
            try {
                int seconds = Integer.parseInt(args[3]);
                if (seconds < 0) throw new NumberFormatException();
                durationTicks = seconds * 20;
            } catch (NumberFormatException e) {
                MultiworldMod.message(plr, "&cInvalid duration: " + args[3] + " (seconds, a non-negative number)");
                return 1;
            }
        }

        // Apply, matching vanilla WeatherCommand semantics:
        //   clear   -> setWeather(clearTime, 0, false, false)
        //   rain    -> setWeather(0, rainTime, true,  false)
        //   thunder -> setWeather(0, thunderTime, true, true)
        // When no duration is given, use the same random providers vanilla uses.
        switch (type) {
            case "clear" -> {
                int t = durationTicks >= 0 ? durationTicks
                        : ServerWorld.CLEAR_WEATHER_DURATION_PROVIDER.get(w.getRandom());
                w.setWeather(t, 0, false, false);
            }
            case "rain" -> {
                int t = durationTicks >= 0 ? durationTicks
                        : ServerWorld.RAIN_WEATHER_DURATION_PROVIDER.get(w.getRandom());
                w.setWeather(0, t, true, false);
            }
            case "thunder" -> {
                int t = durationTicks >= 0 ? durationTicks
                        : ServerWorld.THUNDER_WEATHER_DURATION_PROVIDER.get(w.getRandom());
                w.setWeather(0, t, true, true);
            }
            default -> {
                MultiworldMod.message(plr, "&cInvalid weather: " + type + " (clear, rain, thunder)");
                return 1;
            }
        }

        String id = w.getRegistryKey().getValue().toString();
        MultiworldMod.message(plr, "[&cMultiworld&r]: Weather of world '" + id + "' set to: " + type);
        return 1;
    }

}
