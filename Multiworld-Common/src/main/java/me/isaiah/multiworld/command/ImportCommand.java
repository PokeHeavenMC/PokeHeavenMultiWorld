package me.isaiah.multiworld.command;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Optional;

import me.isaiah.multiworld.MultiworldMod;
import me.isaiah.multiworld.Utils;
import me.isaiah.multiworld.config.FileConfiguration;
import multiworld.api.WorldFolderMode;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.gen.chunk.ChunkGenerator;

/**
 * The "/mw import &lt;folder&gt; &lt;id&gt; [env] [-s=seed] [-g=gen]" Command.
 *
 * <p>Takes a world folder dropped by hand into {@code world/dimensions/multiworld/}, renames it to
 * the folder of {@code id} and loads it right away. An existing world with that id is replaced,
 * after the same double confirmation as {@code /mw delete}. The folder may also be a loaded world
 * (e.g. made by {@code /mw create}): it is then unloaded first, keeping its files, and renamed.
 */
public class ImportCommand implements Command {

	private static final long TIMEOUT = 20_000;
	private static HashMap<String, Long> confirm = new HashMap<>();

	public static int run(MinecraftServer mc, ServerCommandSource source, String[] args) {
		if (args.length < 3) {
			MultiworldMod.message(source, "Usage: /mw import <folder in dimensions/multiworld> <id> [NORMAL|NETHER|END] [-s=seed] [-g=generator]");
			return 0;
		}

		String folder = args[1];
		if (folder.contains("/") || folder.contains("\\") || folder.contains("..") || folder.contains(":")) {
			MultiworldMod.message(source, "[&4Multiworld&r] <folder> must be a plain folder name inside dimensions/multiworld.");
			return 0;
		}
		Path src = Utils.getWorldStoragePath().resolve("multiworld").resolve(folder);
		if (!Files.isDirectory(src)) {
			MultiworldMod.message(source, "[&4Multiworld&r] Folder not found: " + src);
			return 0;
		}
		// The folder can be a world created by /mw create: it is unloaded (files kept) before the move.
		final String srcId = "multiworld:" + folder;
		final boolean sourceLoaded = isLoaded(mc, srcId);
		if (sourceLoaded && !MultiworldMod.get_world_creator().can_unload_world()) {
			MultiworldMod.message(source, "[&4Multiworld&r] Folder '" + folder + "' is the loaded world " + srcId + ", it cannot be imported.");
			return 0;
		}

		String id = args[2];
		if (id.indexOf(':') == -1) id = "multiworld:" + id;
		Identifier idd = MultiworldMod.new_id(id);
		if (null == idd) {
			MultiworldMod.message(source, "[&4Multiworld&r] Invalid world id: " + id);
			return 0;
		}
		if (idd.getNamespace().equalsIgnoreCase("minecraft")) {
			MultiworldMod.message(source, "[&4Multiworld&r] Cannot import over vanilla world '" + id + "'.");
			return 0;
		}

		Path target = Utils.getWorldPath(idd, WorldFolderMode.VANILLA);
		if (target.toAbsolutePath().normalize().equals(src.toAbsolutePath().normalize())) {
			MultiworldMod.message(source, "[&4Multiworld&r] Folder '" + folder + "' already is the folder of " + id + ".");
			return 0;
		}

		final String fid = id;
		final boolean replacing = isLoaded(mc, id) || Files.exists(target);
		if (replacing || sourceLoaded) {
			// Same confirmation as /mw delete, keyed on the whole import.
			String key = folder + "->" + id;
			Long start = confirm.remove(key);
			if (null == start || System.currentTimeMillis() - start > TIMEOUT) {
				confirm.put(key, System.currentTimeMillis());
				if (sourceLoaded) {
					MultiworldMod.message(source, "[&cMultiworld&r] " + srcId + " is loaded: importing will unload it (players in it are sent to the overworld) and rename it to " + id + ".");
				}
				if (replacing) {
					MultiworldMod.message(source, "[&cMultiworld&r] World " + id + " already exists: importing will DELETE it and replace it with '" + folder + "'.");
				}
				MultiworldMod.message(source, "Run the command again within 20s to confirm.");
				return 1;
			}

			// Unloading and deleting a loaded world both finish on a later tick, hence the chain.
			// The source is unloaded first: if that goes wrong, the target has not been deleted yet.
			Runnable importStep = () -> doImport(mc, source, src, target, idd, fid, args, sourceLoaded ? srcId : null);
			Runnable deleteStep = replacing ? () -> DeleteCommand.deleteWorld(source, fid, importStep) : importStep;
			if (sourceLoaded) {
				MultiworldMod.message(source, "Unloading " + srcId + "...");
				MultiworldMod.get_world_creator().unload_world(srcId, deleteStep);
			} else {
				deleteStep.run();
			}
			return 1;
		}

		return doImport(mc, source, src, target, idd, fid, args, null);
	}

	private static boolean isLoaded(MinecraftServer mc, String id) {
		// Iterates instead of mc.getWorld(RegistryKey): the registry package moved across the
		// versions this common code is compiled for.
		Identifier i = MultiworldMod.new_id(id);
		if (null == i) return false;
		for (ServerWorld w : mc.getWorlds()) {
			if (w.getRegistryKey().getValue().equals(i)) return true;
		}
		return false;
	}

	/**
	 * @param unloadedSrcId id the source folder was loaded under before this import unloaded it, or null
	 */
	private static int doImport(MinecraftServer mc, ServerCommandSource source, Path src, Path target, Identifier idd, String id, String[] args, String unloadedSrcId) {
		// The unloaded source keeps its folder and yml: it simply comes back at the next server start.
		String keptNote = null == unloadedSrcId ? "" : " " + unloadedSrcId + " is unloaded but its folder is untouched, it will load again at the next restart.";

		if (Files.exists(target)) {
			MultiworldMod.message(source, "[&4Multiworld&r] Could not import: " + target + " still exists." + keptNote);
			return 0;
		}

		try {
			Files.createDirectories(target.getParent());
			Files.move(src, target);
		} catch (Exception e) {
			e.printStackTrace();
			MultiworldMod.message(source, "[&4Multiworld&r] Could not move " + src + " to " + target + ": " + e.getMessage() + keptNote);
			return 0;
		}

		if (null != unloadedSrcId) {
			// A legacy config (config/multiworld/worlds) would load the old id again at startup.
			try {
				Util.get_config_file(MultiworldMod.new_id(unloadedSrcId)).delete();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		File yml = target.resolve(Utils.WORLD_YML_NAME).toFile();
		try {
			if (yml.exists()) {
				// The yml carries the id the world is loaded under at server start: point it at the new one.
				FileConfiguration config = new FileConfiguration(yml);
				config.set("namespace", idd.getNamespace());
				config.set("path", idd.getPath());
				config.save();
			} else {
				writeNewConfig(mc, source, idd, args);
			}
		} catch (Exception e) {
			e.printStackTrace();
			MultiworldMod.message(source, "[&4Multiworld&r] Could not write " + yml + ": " + e.getMessage());
			return 0;
		}

		Utils.loadSavedMultiworldWorld(mc, target, Optional.of(id));

		if (isLoaded(mc, id)) {
			MultiworldMod.message(source, "[&aMultiworld&r] Imported '" + src.getFileName() + "' as " + id + ".");
			return 1;
		}
		MultiworldMod.message(source, "[&4Multiworld&r] Folder moved to " + target + " but the world failed to load, see the server log.");
		return 0;
	}

	/**
	 * No multiworld-world.yml: build one from the optional arguments, like /mw create does.
	 */
	private static void writeNewConfig(MinecraftServer mc, ServerCommandSource source, Identifier idd, String[] args) {
		String env = "NORMAL";
		long seed = 0;
		String customGen = "";

		for (int i = 3; i < args.length; i++) {
			String arg = args[i];

			CreateCommand.Tuple<ChunkGenerator, String> gen = CreateCommand.checkArgForGen(mc, arg);
			if (null != gen) {
				if (null != gen.first) {
					customGen = gen.second;
				} else {
					MultiworldMod.message(source, "&4Invalid ChunkGenerator: \"" + gen.second + "\"");
				}
				continue;
			}

			Optional<Long> s = CreateCommand.checkArgForSeed(mc, arg);
			if (s.isPresent()) {
				seed = s.get();
				continue;
			}

			if (!arg.startsWith("-")) {
				env = arg.toUpperCase(Util.AMERICAN_STANDARD);
			}
		}

		MultiworldMod.message(source, "No " + Utils.WORLD_YML_NAME + " in the folder, creating one (environment " + env + ", seed " + seed + ").");
		CreateCommand.makeConfigFile(idd, env, seed, customGen, WorldFolderMode.VANILLA);
	}

}
