package me.isaiah.multiworld.fabric;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Optional;

import me.isaiah.multiworld.ICreator;
import me.isaiah.multiworld.MultiworldMod;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.path.SymlinkValidationException;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameRules;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.dimension.DimensionTypes;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.FlatChunkGenerator;
import net.minecraft.world.gen.chunk.FlatChunkGeneratorConfig;
import net.minecraft.world.level.LevelProperties;
import xyz.nucleoid.fantasy.Fantasy;
import xyz.nucleoid.fantasy.RuntimeWorldConfig;
import xyz.nucleoid.fantasy.RuntimeWorldHandle;
import xyz.nucleoid.fantasy.util.VoidChunkGenerator;

public class FabricWorldCreator implements ICreator {

    public Identifier new_id(String id) {
    	return Identifier.of(id);
    }

	public HashMap<String, RuntimeWorldConfig> worldConfigs;
	
	public FabricWorldCreator() {
		this.worldConfigs = new HashMap<>();
	}
	
    public static void init() {
        MultiworldMod.setICreator(new FabricWorldCreator());
    }

    public ServerWorld create_world(String id, Identifier dim, ChunkGenerator gen, Difficulty dif, long seed) {
    	Identifier idd = new_id(id);
    	GameRules rules = null;
		try {
			rules = readGameRules(idd);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			// e.printStackTrace();
		}
    	
    	RuntimeWorldConfig config = new RuntimeWorldConfig()
                .setDimensionType(dim_of(dim))
                .setGenerator(gen)
                .setDifficulty(dif)
				.setSeed(seed)
				.setShouldTickTime(true)
				.setWorldConstructor(MultiworldWorld::new)
				.setSunny(0)   // set clearWeatherTime to 0 for enable weather default minecraft behavior
				.setMirrorOverworldGameRules(false)   // per-dimension gamerules (don't share the overworld's)
				.setMirrorOverworldDifficulty(false)  // per-dimension difficulty (don't share the overworld's)
                ;

        // Restore time-of-day and weather from the world's own level.dat (if it already exists on
        // disk). RuntimeWorldProperties is built from this config, so it must be set BEFORE the world
        // is opened. For a brand-new world there is no level.dat yet -> read fails -> config defaults.
        try {
            LevelProperties saved = MultiworldWorld.mw$readLevelProperties(MultiworldMod.mc, idd);
            config.setTimeOfDay(saved.getTimeOfDay());
            // setSunny resets raining/thundering, setRaining/setThundering(int) set their flag from
            // time>0; re-apply the saved boolean flags last so all five fields match level.dat exactly.
            config.setSunny(saved.getClearWeatherTime());
            config.setRaining(saved.getRainTime());
            config.setThundering(saved.getThunderTime());
            config.setRaining(saved.isRaining());
            config.setThundering(saved.isThundering());
        } catch (Exception e) {
            // New world (no level.dat yet) — keep config defaults.
        }

        Fantasy fantasy = Fantasy.get(MultiworldMod.mc);
        RuntimeWorldHandle worldHandle = fantasy.getOrOpenPersistentWorld(new_id(id), config);
        this.worldConfigs.put(id, config);
        ServerWorld world = worldHandle.asWorld();
        
        if (null != rules) {
        	world.getGameRules().setAllValues(rules, null);
        }
        
        this.worldConfigs.put(id, config);
        return world;
    }
    
    /**
     * Reads the gamerules from a level.dat file in the given world folder.
     * @param savesDir Path to the root saves directory (e.g. ./saves).
     * @param worldName Name of the world folder.
     * @param dataFixer The server's DataFixer instance.
     * @return A GameRules object containing the rules from level.dat.
     * @throws IOException if the file cannot be read.
     * @throws SymlinkValidationException 
     */
    public static GameRules readGameRules(Identifier id) throws IOException {
    	try {
			return MultiworldWorld.mw$readGameRules(MultiworldMod.mc, id);
		} catch (IOException | SymlinkValidationException e) {
			// TODO Auto-generated catch block
			// e.printStackTrace();
			throw new IOException(e);
		}
    }

    /**
     * Sets the difficulty of a world we created.
     *
     * <p>{@code worldConfigs} only holds the worlds Multiworld opened, so the lookup returns null
     * for a vanilla world — callers must reject those first (see {@code DifficultyCommand}). The
     * guard here is a last resort so a missed check logs instead of throwing.
     */
    @Override
    public void set_difficulty(String id, Difficulty dif) {
    	RuntimeWorldConfig config = this.worldConfigs.get(id);
    	if (null == config) {
    		MultiworldMod.LOGGER.warn("set_difficulty: '{}' is not a world managed by Multiworld", id);
    		return;
    	}
    	config.setDifficulty(dif);
    }
    
    private static RegistryKey<DimensionType> dim_of(Identifier id) {
        return RegistryKey.of(RegistryKeys.DIMENSION_TYPE, id);
    }
    
    public void delete_world(String id) {
        Fantasy fantasy = Fantasy.get(MultiworldMod.mc);
        RuntimeWorldHandle worldHandle = fantasy.getOrOpenPersistentWorld(new_id(id), null);
        worldHandle.delete();
    }

	@Override
	public boolean is_the_end(ServerWorld world) {
		return world.getDimensionEntry() == DimensionTypes.THE_END;
	}

	@Override
	public BlockPos get_pos(double x, double y, double z) {
		return BlockPos.ofFloored(x, y, z);
	}
	
	@Override
	public BlockPos get_spawn(ServerWorld world) {
		return world.getLevelProperties().getSpawnPos();
	}
	
	@Override
	public void teleleport(ServerPlayerEntity player, ServerWorld world, double x, double y, double z) {
        TeleportTarget target = new TeleportTarget(world, new Vec3d(x, y, z), new Vec3d(0, 0, 0), 0f, 0f, TeleportTarget.NO_OP);
        
        // FabricDimensionInternals.changeDimension(player, world, target);
        
        // Per https://fabricmc.net/2024/05/31/121.html
        // for 1.21, FabricDimension API is replaced by teleportTo
        player.teleportTo(target);
	}
	
	@Override
	public ChunkGenerator get_flat_chunk_gen(MinecraftServer mc) {
		var biome = mc.getRegistryManager().get(RegistryKeys.BIOME).getEntry(mc.getRegistryManager().get(RegistryKeys.BIOME).getOrThrow(BiomeKeys.PLAINS));
        FlatChunkGeneratorConfig flat = new FlatChunkGeneratorConfig(Optional.empty(), biome, Collections.emptyList());
        FlatChunkGenerator generator = new CustomFlatChunkGenerator(flat);
        return generator;
	}
	
	// Custom Flat Gen
	class CustomFlatChunkGenerator extends FlatChunkGenerator {
		public CustomFlatChunkGenerator(FlatChunkGeneratorConfig config) {
			super(config);
		}
		
		@Override
		public int getMinimumY() {
			return 0;
		}
		
		@Override
	    public int getSeaLevel() {
	        return 0;
	    }
	}
	
	@Override
	public ChunkGenerator get_void_chunk_gen(MinecraftServer mc) {
		// var biome = mc.getRegistryManager().getOrThrow(RegistryKeys.BIOME).getOrThrow(BiomeKeys.THE_VOID);
		VoidChunkGenerator gen = new xyz.nucleoid.fantasy.util.VoidChunkGenerator(mc);
        return gen;
	}
	
	@Override
	public boolean permissionLevel(ServerCommandSource source, int level) {
		return source.hasPermissionLevel(level);
	}

	@Override
	public boolean permissionLevel(ServerPlayerEntity plr, int level) {
		return plr.hasPermissionLevel(level);
	}

}