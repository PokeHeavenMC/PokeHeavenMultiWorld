package me.isaiah.multiworld;

import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.FlatChunkGenerator;
import net.minecraft.world.gen.chunk.FlatChunkGeneratorConfig;
import net.minecraft.world.gen.chunk.FlatChunkGeneratorLayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.World;
import net.minecraft.block.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * Utility interface for cross-version development support
 * See implementation in "Multiworld-Fabric-1.XX.X/src/"
 */
public interface ICreator {

	/**
	 */
	public ServerWorld create_world(String id, Identifier dim, ChunkGenerator gen, Difficulty dif, long seed);

	/**
	 */
	public BlockPos get_pos(double x, double y, double z);

	/**
	 */
	@Deprecated
	public default Text colored_literal_(String txt, Formatting color) {
		try {
			return Text.of(txt).copy().formatted(color);
		} catch (Exception | IncompatibleClassChangeError e) {
			// MutableText interface was changed to a class in 1.19;
			// Incase for 1.18:
			return Text.of(txt);
		}
	}

	/**
	 */
	void teleleport(ServerPlayerEntity player, ServerWorld world, double x, double y, double z);

	/**
	 */
	void set_difficulty(String id, Difficulty dif);

    /**
     * Return a {@link ChunkGenerator} for the given vanilla environment,
     * or NULL if the passed argument is not NORMAL / NETHER / END.
     */
    default ChunkGenerator get_chunk_gen(MinecraftServer mc, String env) {
    	ChunkGenerator gen = null;
    	if (env.contains("NORMAL") || env.contains("DEFAULT")) {
			gen = mc.getWorld(World.OVERWORLD).getChunkManager().getChunkGenerator(); // .withSeed(seed);
		}

		if (env.contains("NETHER")) {
			gen = mc.getWorld(World.NETHER).getChunkManager().getChunkGenerator();
		}
		
		if (env.contains("END")) {
			gen = mc.getWorld(World.END).getChunkManager().getChunkGenerator(); // .withSeed(seed);
		}
		
		if (env.contains("FLAT")) {
			FlatChunkGenerator genn = (FlatChunkGenerator) this.get_flat_chunk_gen(mc);

			FlatChunkGeneratorConfig flat = genn.getConfig();
			
			FlatChunkGeneratorLayer[] layers = {
					new FlatChunkGeneratorLayer(1, Blocks.GRASS_BLOCK),
					new FlatChunkGeneratorLayer(5, Blocks.DIRT),
					new FlatChunkGeneratorLayer(2, Blocks.BEDROCK)
			};
	        
	        for (int i = layers.length - 1; i >= 0; --i) {
	            flat.getLayers().add(layers[i]);
	        }

	        flat.updateLayerBlocks();

			return genn;
		}
		
		if (env.contains("VOID")) {
			return this.get_void_chunk_gen(mc);
		}

		return gen;
    } 

	// TODO: move to icommonlib ?:
	public BlockPos get_spawn(ServerWorld world);
	public boolean is_the_end(ServerWorld world);
	public ChunkGenerator get_flat_chunk_gen(MinecraftServer mc);
	public ChunkGenerator get_void_chunk_gen(MinecraftServer mc);

	// Vanilla Permissions
	public boolean permissionLevel(ServerCommandSource source, int level);
	public boolean permissionLevel(ServerPlayerEntity plr, int level);

	void delete_world(String id);

	/**
	 * Deletes a world, then runs {@code onDeleted} once its folder is gone. Platforms where
	 * deletion is deferred override this; the default assumes {@link #delete_world(String)} is done
	 * when it returns.
	 */
	default void delete_world(String id, Runnable onDeleted) {
		delete_world(id);
		onDeleted.run();
	}

	/**
	 * True if this platform can unload a loaded world at runtime without deleting its files.
	 */
	default boolean can_unload_world() {
		return false;
	}

	/**
	 * Saves, unloads and closes a loaded world, keeping its folder, then runs {@code onUnloaded}
	 * on a later tick once nothing holds its files. Only call when {@link #can_unload_world()}.
	 */
	default void unload_world(String id, Runnable onUnloaded) {
		throw new UnsupportedOperationException("Unloading worlds is not supported on this platform");
	}

}