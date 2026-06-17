package me.isaiah.multiworld.command;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

import me.isaiah.multiworld.MultiworldMod;
import me.isaiah.multiworld.Utils;
import me.isaiah.multiworld.config.FileConfiguration;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.GameRules;

public interface IGameruleCommand {

	public Set<String> getKeys();

	public void initRulesMapIfNeeded(MinecraftServer server);

	public void set_gamerule_from_cfg(ServerWorld world, String key, String val);

	public int run(MinecraftServer mc, ServerPlayerEntity plr, String[] args);

	/**
	 * Apply a gamerule to every loaded dimension/world.
	 *
	 * <p>Usage: {@code /mw gameruleAll <gamerule> <value>}. The change is applied
	 * live to all worlds and persisted to each Multiworld world's config (vanilla
	 * dimensions are changed but not persisted, since they have no Multiworld config).
	 *
	 * <p>Implemented as a {@code default} method so it stays version-agnostic and
	 * available to every {@link IGameruleCommand} implementation without per-version code.
	 */
	default int runAll(MinecraftServer mc, ServerPlayerEntity plr, String[] args) {
		if (args.length < 3) {
			MultiworldMod.message(plr, "[&4Multiworld&r] Usage: /mw gameruleAll <gamerule> <value>");
			return 1;
		}

		final String name = args[1];
		final String value = args[2];

		// Resolve gamerule name -> key from the server's gamerules.
		final HashMap<String, GameRules.Key<?>> keys = new HashMap<>();
		mc.getGameRules().accept(new GameRules.Visitor() {
			@Override
			public <T extends GameRules.Rule<T>> void visit(GameRules.Key<T> key, GameRules.Type<T> type) {
				keys.put(key.getName(), key);
			}
		});

		if (!keys.containsKey(name)) {
			MultiworldMod.message(plr, "[&4Multiworld&r] Unknown gamerule: " + name);
			return 1;
		}

		boolean is_bol = value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false");

		int count = 0;
		for (ServerWorld w : mc.getWorlds()) {
			try {
				if (is_bol) {
					((GameRules.BooleanRule) w.getGameRules().get(keys.get(name))).set(Boolean.valueOf(value), mc);
				} else {
					((GameRules.IntRule) w.getGameRules().get(keys.get(name))).set(Integer.valueOf(value), mc);
				}
			} catch (NumberFormatException e) {
				MultiworldMod.message(plr, "[&4Multiworld&r] Invalid value for gamerule " + name + ": " + value);
				return 1;
			}

			// Persist to per-world config (Multiworld worlds only; vanilla worlds return null).
			try {
				Identifier id = w.getRegistryKey().getValue();
				FileConfiguration config = Utils.getConfigOrNull(id);
				if (null != config) {
					if (!config.is_set("gamerules")) {
						config.set("gamerules", new ArrayList<String>());
					}
					config.set("gamerule_" + name, value);
					config.save();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}

			count++;
		}

		MultiworldMod.message(plr, "[&cMultiworld&r]: Gamerule " + name + " is now set to: " + value + " in " + count + " world(s)");
		return 1;
	}

}