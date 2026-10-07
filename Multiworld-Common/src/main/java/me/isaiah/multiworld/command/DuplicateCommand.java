package me.isaiah.multiworld.command;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

import me.isaiah.multiworld.MultiworldMod;
import me.isaiah.multiworld.Utils;
import me.isaiah.multiworld.config.FileConfiguration;
import multiworld.api.WorldFolderMode;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

/**
 * The "/mw duplicate &lt;id&gt; &lt;newid&gt;" Command.
 *
 * <p>Copies the folder of a loaded Multiworld world (blocks, entities, level.dat, multiworld-world.yml)
 * to the folder of {@code newid} and loads the copy right away. The source stays loaded: it is
 * flushed to disk first, and the copy runs on the server thread so no autosave can interleave.
 */
public class DuplicateCommand implements Command {

	public static int run(MinecraftServer mc, ServerCommandSource source, String[] args) {
		if (args.length < 3) {
			MultiworldMod.message(source, "Usage: /mw duplicate <id> <newid>");
			return 0;
		}

		String id = args[1];
		if (id.indexOf(':') == -1) id = "multiworld:" + id;
		String newId = args[2];
		if (newId.indexOf(':') == -1) newId = "multiworld:" + newId;

		Identifier srcIdd = MultiworldMod.new_id(id);
		Identifier newIdd = MultiworldMod.new_id(newId);
		if (null == srcIdd) {
			MultiworldMod.message(source, "[&4Multiworld&r] Invalid world id: " + id);
			return 0;
		}
		if (null == newIdd) {
			MultiworldMod.message(source, "[&4Multiworld&r] Invalid world id: " + newId);
			return 0;
		}
		if (srcIdd.getNamespace().equalsIgnoreCase("minecraft")) {
			MultiworldMod.message(source, "[&4Multiworld&r] Cannot duplicate vanilla world '" + id + "', only Multiworld worlds.");
			return 0;
		}
		if (newIdd.getNamespace().equalsIgnoreCase("minecraft")) {
			MultiworldMod.message(source, "[&4Multiworld&r] Cannot duplicate into vanilla world '" + newId + "'.");
			return 0;
		}

		ServerWorld world = getLoaded(mc, srcIdd);
		if (null == world) {
			MultiworldMod.message(source, "[&4Multiworld&r] World " + id + " is not loaded.");
			return 0;
		}
		Path src = Utils.getWorldPath(srcIdd, WorldFolderMode.VANILLA);
		if (!Files.exists(src.resolve(Utils.WORLD_YML_NAME))) {
			MultiworldMod.message(source, "[&4Multiworld&r] " + id + " is not a Multiworld world (no " + Utils.WORLD_YML_NAME + " in " + src + ").");
			return 0;
		}

		Path target = Utils.getWorldPath(newIdd, WorldFolderMode.VANILLA);
		if (null != getLoaded(mc, newIdd) || Files.exists(target)) {
			MultiworldMod.message(source, "[&4Multiworld&r] World " + newId + " already exists. Delete it first or pick another id.");
			return 0;
		}

		MultiworldMod.message(source, "Duplicating " + id + " to " + newId + "...");
		long start = System.currentTimeMillis();

		// flush = wait for pending chunk/entity I/O, so the files on disk match the live world.
		world.save(null, true, false);

		try {
			copyDirectory(src, target);
		} catch (IOException e) {
			e.printStackTrace();
			try {
				deleteDirectory(target);
			} catch (IOException e2) {
				e2.printStackTrace();
			}
			MultiworldMod.message(source, "[&4Multiworld&r] Could not copy " + src + " to " + target + ": " + e.getMessage());
			return 0;
		}

		File yml = target.resolve(Utils.WORLD_YML_NAME).toFile();
		try {
			// The yml carries the id the world is loaded under at server start: point it at the copy's.
			FileConfiguration config = new FileConfiguration(yml);
			config.set("namespace", newIdd.getNamespace());
			config.set("path", newIdd.getPath());
			config.save();
		} catch (Exception e) {
			e.printStackTrace();
			MultiworldMod.message(source, "[&4Multiworld&r] Could not write " + yml + ": " + e.getMessage());
			return 0;
		}

		Utils.loadSavedMultiworldWorld(mc, target, Optional.of(newId));

		if (null != getLoaded(mc, newIdd)) {
			MultiworldMod.LOGGER.info("Duplicated {} as {} in {} ms", id, newId, System.currentTimeMillis() - start);
			MultiworldMod.message(source, "[&aMultiworld&r] Duplicated " + id + " as " + newId + ".");
			return 1;
		}
		MultiworldMod.message(source, "[&4Multiworld&r] Folder copied to " + target + " but the world failed to load, see the server log.");
		return 0;
	}

	private static ServerWorld getLoaded(MinecraftServer mc, Identifier id) {
		// Iterates instead of mc.getWorld(RegistryKey): the registry package moved across the
		// versions this common code is compiled for.
		for (ServerWorld w : mc.getWorlds()) {
			if (w.getRegistryKey().getValue().equals(id)) return w;
		}
		return null;
	}

	private static void copyDirectory(Path src, Path target) throws IOException {
		try (Stream<Path> walk = Files.walk(src)) {
			for (Path p : (Iterable<Path>) walk::iterator) {
				if (p.getFileName().toString().equals("session.lock")) continue;
				Path dest = target.resolve(src.relativize(p).toString());
				if (Files.isDirectory(p)) {
					Files.createDirectories(dest);
				} else {
					Files.copy(p, dest, StandardCopyOption.COPY_ATTRIBUTES);
				}
			}
		}
	}

	private static void deleteDirectory(Path dir) throws IOException {
		if (!Files.exists(dir)) return;
		try (Stream<Path> walk = Files.walk(dir)) {
			for (Path p : (Iterable<Path>) walk.sorted(Comparator.reverseOrder())::iterator) {
				Files.delete(p);
			}
		}
	}

}
