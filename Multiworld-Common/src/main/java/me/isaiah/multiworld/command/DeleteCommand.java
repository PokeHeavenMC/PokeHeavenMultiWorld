package me.isaiah.multiworld.command;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

import org.slf4j.Logger;

import me.isaiah.multiworld.ConsoleCommand;
import me.isaiah.multiworld.MultiworldMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;

public class DeleteCommand implements Command {

	public static Logger LOGGER = ConsoleCommand.LOGGER;

	private static HashMap<String, Long> map = new HashMap<>();

	/**
	 * Run Command
	 */
    public static int run(MinecraftServer mc, ServerCommandSource source, String[] args) {
        if (args.length == 1) {
        	MultiworldMod.message(source, "Usage: /mw delete <id>");
            return 0;
        }

        String id = args[1];

        // Same default namespace as create/tp/difficulty: without it "test" parses as
        // minecraft:test, so neither the config nor the world folder of multiworld:test is found.
        if (id.indexOf(':') == -1) id = "multiworld:" + id;

        if (id.startsWith("minecraft:")) {
        	MultiworldMod.message(source, "[&4Multiworld&r] Cannot delete vanilla world '" + id + "'.");
        	return 0;
        }

        if (!map.containsKey(id)) {
        	map.put(id, System.currentTimeMillis() );

        	MultiworldMod.message(source, "NOTE: This command will destroy the life, universe and everything associated with the world.");
        	MultiworldMod.message(source, "For this reason, Please run command again to confirm to delete \"" + id + "\".");
        	return 1;
        }

        long start = map.remove(id);
        long now = System.currentTimeMillis();
        long TIMEOUT = 20_000;

        if (now - start > TIMEOUT) {
        	MultiworldMod.message(source, "Delete request timed-out (>20s). Please try again.");
        	return 0;
        }

        deleteWorld(source, id, () -> {});
        return 1;
    }

    /**
     * Deletes the legacy config and the world itself, without confirmation.
     *
     * @param id    normalized world id (namespace included)
     * @param after run once the world folder is gone (deletion of a loaded world is deferred)
     */
    public static void deleteWorld(ServerCommandSource source, String id, Runnable after) {
        MultiworldMod.message(source, "Deleting multiworld config for \"" + id + "\"...");
        try {
			File config = Util.get_config_file(MultiworldMod.new_id(id));
			config.delete();
		} catch (IOException e) {
			e.printStackTrace();
		}

        MultiworldMod.message(source, "Deleting world folder \"" + id + "\"...");
        MultiworldMod.get_world_creator().delete_world(id, after);
    }

}
