package me.isaiah.multiworld.command;

import java.util.HashMap;

import me.isaiah.multiworld.MultiworldMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

public class CmdCommand implements Command {

    /**
     * "/mw cmd &lt;dimension&gt; &lt;command&gt;"
     *
     * Exécute une commande Minecraft arbitraire en la ciblant sur une dimension donnée. Les sélecteurs
     * vanilla ({@code @e}, {@code @a}…) ne portent normalement que sur la dimension de l'émetteur ; ici
     * la source est retargetée sur le monde choisi via {@link ServerCommandSource#withWorld(ServerWorld)},
     * de sorte que {@code /mw cmd nether kill @e[type=cobblemon]} tue les entités dans le nether même si
     * le joueur est ailleurs.
     *
     * Réservé à {@code multiworld.admin} (voir {@link MultiworldMod#broadcast}) : la commande est exécutée
     * avec le niveau de permission op (4). Les guillemets englobants autour de la commande sont optionnels.
     */
    public static int run(MinecraftServer mc, ServerPlayerEntity plr, String[] args, String message) {
        if (args.length < 3) {
            MultiworldMod.message(plr, "&cUsage: /mw cmd <dimension> <command>");
            return 1;
        }

        // Résolution de la dimension — même pattern que WeatherCommand/TimeCommand/TpCommand.
        String a1 = args[1];

        HashMap<String, ServerWorld> worlds = new HashMap<>();
        mc.getWorldRegistryKeys().forEach(r -> {
            ServerWorld world = mc.getWorld(r);
            worlds.put(r.getValue().toString(), world);
        });

        if (a1.indexOf(':') == -1) a1 = "multiworld:" + a1;

        ServerWorld w;
        if (worlds.containsKey(a1)) {
            w = worlds.get(a1);
        } else {
            MultiworldMod.message(plr, "&cWorld not found: " + a1);
            return 1;
        }

        // Reconstruire le texte de la commande à partir de la chaîne complète (message.split(" ") dans
        // broadcast casse les espaces) : retirer les deux premiers tokens (cmd + dimension).
        String trimmed = message.trim();
        int firstSpace = trimmed.indexOf(' ');
        String afterCmd = trimmed.substring(firstSpace + 1).trim();      // "<dimension> <command>"
        int secondSpace = afterCmd.indexOf(' ');
        if (secondSpace == -1) {
            MultiworldMod.message(plr, "&cUsage: /mw cmd <dimension> <command>");
            return 1;
        }
        String command = afterCmd.substring(secondSpace + 1).trim();     // "<command>"

        // Guillemets englobants optionnels + slash initial éventuel.
        if (command.length() >= 2 && command.startsWith("\"") && command.endsWith("\"")) {
            command = command.substring(1, command.length() - 1).trim();
        }
        if (command.startsWith("/")) {
            command = command.substring(1);
        }
        if (command.isEmpty()) {
            MultiworldMod.message(plr, "&cUsage: /mw cmd <dimension> <command>");
            return 1;
        }

        String id = w.getRegistryKey().getValue().toString();

        try {
            ServerCommandSource src = plr.getCommandSource()
                    .withWorld(w)                                        // sélecteurs résolus dans w
                    .withPosition(Vec3d.ofCenter(w.getSpawnPos()))       // coords relatives cohérentes
                    .withLevel(4);                                       // autorise les commandes op
            mc.getCommandManager().executeWithPrefix(src, command);
        } catch (Exception e) {
            e.printStackTrace();
            MultiworldMod.message(plr, "&cFailed to run command in '" + id + "': " + e.getMessage());
            return 1;
        }

        MultiworldMod.message(plr, "[&cMultiworld&r]: Ran in '" + id + "': " + command);
        return 1;
    }

}
