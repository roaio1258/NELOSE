package nelose;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Sends your own chat message when you kill a player.
 * Set the text by typing  .killmsg your text  in chat (it is not sent to the server). {name} = the victim.
 */
public final class KillMessage {
    public static String message = "gg";
    private static Entity done;
    private static int pending;
    private static String pendingName = "";
    private static long lastSent;

    private static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("nelose-killmsg.txt"); }

    public static void load() {
        try {
            Path f = file();
            if (Files.exists(f)) {
                String s = Files.readString(f, StandardCharsets.UTF_8).trim();
                if (!s.isEmpty()) message = s;
            }
        } catch (IOException ignored) {}
    }

    private static void save() {
        try { Files.writeString(file(), message, StandardCharsets.UTF_8); } catch (IOException ignored) {}
    }

    /** Handles the .killmsg chat command. Returns true when the chat line was a command (do not send it). */
    public static boolean command(String raw) {
        if (!raw.startsWith(".killmsg")) return false;
        MinecraftClient mc = MinecraftClient.getInstance();
        String arg = raw.substring(8).trim();
        if (arg.isEmpty()) {
            say(mc, "[Nelose] Сообщение при убийстве: " + message + "   (изменить: .killmsg текст)");
        } else {
            message = arg.length() > 200 ? arg.substring(0, 200) : arg;
            save();
            say(mc, "[Nelose] Сообщение при убийстве сохранено: " + message);
        }
        return true;
    }

    private static void say(MinecraftClient mc, String s) {
        mc.inGameHud.getChatHud().addMessage(Text.literal(s));
    }

    public static void tick(MinecraftClient mc) {
        if (!Modules.KILL_MESSAGE.on || mc.player == null || mc.world == null) { pending = 0; return; }
        if (pending > 0) {
            if (--pending == 0) send(mc);
            return;
        }
        Entity t = Features.target;
        if (t instanceof PlayerEntity p && t != done && !FakePlayer.isFake(t)
            && System.currentTimeMillis() - Features.targetTime < 4000 && (!p.isAlive() || p.getHealth() <= 0f)) {
            done = t;
            if (System.currentTimeMillis() - lastSent >= (long) (Modules.KILL_MESSAGE.f("Cooldown") * 1000)) {
                pendingName = p.getName().getString();
                pending = Math.max(1, (int) Modules.KILL_MESSAGE.f("Delay"));
            }
        }
    }

    private static void send(MinecraftClient mc) {
        if (mc.player == null || mc.player.networkHandler == null || message.isEmpty()) return;
        String text = message.replace("{name}", pendingName);
        lastSent = System.currentTimeMillis();
        if (text.startsWith("/")) mc.player.networkHandler.sendChatCommand(text.substring(1));
        else mc.player.networkHandler.sendChatMessage(text);
    }

    private KillMessage() {}
}
