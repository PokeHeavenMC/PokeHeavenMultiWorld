package me.isaiah.multiworld.command;

import java.io.IOException;
import java.util.HashMap;

import me.isaiah.multiworld.I18n;
import me.isaiah.multiworld.MultiworldMod;
import me.isaiah.multiworld.Utils;
import me.isaiah.multiworld.config.FileConfiguration;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.Difficulty;

public class DifficultyCommand implements Command {

    /**
     * Player entry point: the current world is the one the player stands in.
     */
    public static int run(MinecraftServer mc, ServerPlayerEntity plr, String[] args) {
        return run(mc, plr.getCommandSource(), Command.getWorldFor(plr), args);
    }

    /**
     * Source entry point, usable from the console.
     *
     * <p>The console has no current world, so {@code current} is null there and the world id
     * argument becomes mandatory. Everything else is identical to the player path.
     *
     * @param current the world to fall back on when no world id is given, or null when the source
     *                has no world of its own (console)
     */
    public static int run(MinecraftServer mc, ServerCommandSource src, ServerWorld current, String[] args) {
        ServerWorld w = current;

		if (args.length < 2) {
			MultiworldMod.message(src, I18n.CMD_DIFF_USAGE);
			return 1;
		}

        String a1 = args[1];

        if (args.length >= 3) {
        	String a2 = args[2];

        	HashMap<String,ServerWorld> worlds = new HashMap<>();
            mc.getWorldRegistryKeys().forEach(r -> {
                ServerWorld world = mc.getWorld(r);
                worlds.put(r.getValue().toString(), world);
            });

            if (a2.indexOf(':') == -1) a2 = "multiworld:" + a2;

            // An unknown world id must be an error, never a silent fallback to the current world:
            // a typo would otherwise change the difficulty of the wrong world without saying so,
            // and from the console there is no current world to fall back on at all.
            if (!worlds.containsKey(a2)) {
                MultiworldMod.message(src, "[&4Multiworld&r] Unknown world: " + a2);
                return 0;
            }
            w = worlds.get(a2);
        }

        if (null == w) {
            // Console with no world id: there is nothing sensible to target.
            MultiworldMod.message(src, "[&4Multiworld&r] Usage from console: /mw difficulty <value> <world id>");
            return 0;
        }

		Difficulty d = Difficulty.NORMAL;

		// String to Difficulty
		if (a1.equalsIgnoreCase("EASY"))         { d = Difficulty.EASY; }
		else if (a1.equalsIgnoreCase("HARD"))    { d = Difficulty.HARD; }
		else if (a1.equalsIgnoreCase("NORMAL"))  { d = Difficulty.NORMAL; }
		else if (a1.equalsIgnoreCase("PEACEFUL")){ d = Difficulty.PEACEFUL; }
		else {
			MultiworldMod.message(src, "Invalid difficulty: " + a1);
			return 1;
		}

        Identifier id = w.getRegistryKey().getValue();

        // The world's own multiworld-world.yml is both what the loader reads back at server start
        // (Utils.loadSavedMultiworldWorld) and the marker that this world is ours at all: it is null
        // for the minecraft namespace and for any world we did not create. That test has to come
        // BEFORE set_difficulty, which looks the world up in a map holding only Multiworld worlds
        // and would throw on the null it finds for a vanilla one.
        FileConfiguration config;
        try {
			config = Utils.getConfigOrNull(id);
		} catch (IOException e) {
			e.printStackTrace();
			MultiworldMod.message(src, "[&4Multiworld&r] Could not read the config of world '" + id + "'");
			return 0;
		}

        if (null == config) {
            MultiworldMod.message(src, "[&4Multiworld&r] World '" + id
                    + "' is not managed by Multiworld - its difficulty is server-wide, use /difficulty.");
            return 0;
        }

        MultiworldMod.get_world_creator().set_difficulty(id.toString(), d);

        try {
			config.set("difficulty", d.getName());
			config.save();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        MultiworldMod.message(src, "[&cMultiworld&r]: Difficulty of world '" + id + "' is now set to: " + a1);
        return 1;
    }

}
