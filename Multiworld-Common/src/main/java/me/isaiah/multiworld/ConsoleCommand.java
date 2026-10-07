package me.isaiah.multiworld;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

import me.isaiah.multiworld.command.DeleteCommand;
import me.isaiah.multiworld.command.DifficultyCommand;
import me.isaiah.multiworld.command.DuplicateCommand;
import me.isaiah.multiworld.command.ImportCommand;
import me.isaiah.multiworld.command.TpCommand;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;

public class ConsoleCommand {

	public static final Logger LOGGER = LoggerFactory.getLogger("multiworld");

	// Replies go through MultiworldMod.message(source, ...) rather than LOGGER so they also reach
	// RCON / panel sources; for the real console, source.sendMessage still ends up in the log.
	public static int broadcast_console(MinecraftServer mc, ServerCommandSource source, String message) throws CommandSyntaxException {
		if (null == message) {
			MultiworldMod.message(source, "&bMultiworld Mod for Minecraft " + mc.getVersion());
			MultiworldMod.message(source, "(Console Commands are experimental)");
			return 1;
		}

		String[] args = message.split(" ");
		if (args[0].equalsIgnoreCase("help")) {
			for (String s : MultiworldMod.COMMAND_HELP) MultiworldMod.message(source, s);

			return 1;
		}

		// Delete Command (Console Only)
		if (args[0].equalsIgnoreCase("delete")) {
			DeleteCommand.run(mc, source, args);
			return 1;
		}

		// Import Command
		if (args[0].equalsIgnoreCase("import")) {
			return ImportCommand.run(mc, source, args);
		}

		// Duplicate Command
		if (args[0].equalsIgnoreCase("duplicate")) {
			return DuplicateCommand.run(mc, source, args);
		}

		// TP Command
		if (args[0].equalsIgnoreCase("tp") ) {
			if (args.length <= 2) {
				MultiworldMod.message(source, "Usage: /mw tp <world> <player>");
				return 0;
			}
			return TpCommand.run(mc, null, args);
		}

		// List Command
        if (args[0].equalsIgnoreCase("list") ) {
            MultiworldMod.message(source, "&bAll Worlds:");
            mc.getWorlds().forEach(world -> MultiworldMod.message(source, "- " + world.getRegistryKey().getValue().toString()));
            return 1;
        }

        // Version Command
        if (args[0].equalsIgnoreCase("version") ) {
            MultiworldMod.message(source, "Multiworld Mod version " + MultiworldMod.VERSION);
            return 1;
        }

		// Difficulty Command
		// The console has no world of its own, hence the null: the world id argument is then
		// mandatory, and DifficultyCommand says so rather than falling back to some other world.
		// This is what makes a world's difficulty reachable from a scheduler or a startup script.
		if (args[0].equalsIgnoreCase("difficulty")) {
			return DifficultyCommand.run(mc, source, null, args);
		}

		// Anything else needs a player (current world, position...): say so instead of throwing
		// REQUIRES_PLAYER, which reads as if /mw itself were unusable from the console.
		MultiworldMod.message(source, "[&4Multiworld&r] '/mw " + args[0] + "' requires a player."
				+ " Console commands: help, list, version, tp, delete, import, duplicate, difficulty");
		return 0;
	}
}
