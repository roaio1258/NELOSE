package nelose;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.DeathScreen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Personal waypoints: stored in config/nelose-marks.txt, drawn on screen, visible only to you. */
public final class Marks {
    public static final class Mark {
        public String name, dim;
        public int x, y, z, color;
        public boolean auto;   // made by Autometki (shown in the "События" list)
    }

    public static final List<Mark> LIST = new ArrayList<>();
    public static boolean autoDeath = true;
    public static final int[] PALETTE = {0xFFFFFFFF, 0xFFE5334B, 0xFFF59E0B, 0xFFFBBF24, 0xFF34D399, 0xFF4EE2EC, 0xFF6F8CFF, 0xFFA78BFA, 0xFFF472B6};

    private static double lx, ly, lz;
    private static boolean hasLast, deadDone;

    private static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("nelose-marks.txt"); }

    public static String dim(MinecraftClient mc) {
        return mc.world == null ? "" : mc.world.getRegistryKey().getValue().toString();
    }

    public static Mark add(String name, int x, int y, int z, int color, String dim, boolean auto) {
        Mark m = new Mark();
        m.name = name.replace('\t', ' ').replace('\n', ' ');
        m.x = x; m.y = y; m.z = z; m.color = color; m.dim = dim; m.auto = auto;
        LIST.add(m);
        save();
        return m;
    }

    public static void save() {
        List<String> out = new ArrayList<>();
        out.add("auto\t" + (autoDeath ? 1 : 0));
        for (Mark m : LIST)
            out.add(m.name + "\t" + m.x + "\t" + m.y + "\t" + m.z + "\t" + Integer.toHexString(m.color) + "\t" + m.dim + "\t" + (m.auto ? 1 : 0));
        try { Files.write(file(), out); } catch (IOException ignored) {}
    }

    public static void load() {
        Path f = file();
        if (!Files.exists(f)) return;
        try {
            LIST.clear();
            for (String line : Files.readAllLines(f)) {
                String[] p = line.split("\t");
                if (p.length == 2 && p[0].equals("auto")) { autoDeath = p[1].equals("1"); continue; }
                if (p.length < 7) continue;
                Mark m = new Mark();
                m.name = p[0]; m.x = Integer.parseInt(p[1]); m.y = Integer.parseInt(p[2]); m.z = Integer.parseInt(p[3]);
                m.color = (int) Long.parseLong(p[4], 16); m.dim = p[5]; m.auto = p[6].equals("1");
                LIST.add(m);
            }
        } catch (IOException | NumberFormatException ignored) {}
    }

    // ---------- autometki: mark the place where you die ----------
    public static void tick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) { hasLast = false; return; }
        if (mc.currentScreen instanceof DeathScreen) {
            if (!deadDone && autoDeath && hasLast) {
                deadDone = true;
                String d = dim(mc);
                LIST.removeIf(m -> m.auto && m.dim.equals(d));
                add("Смерть", (int) Math.floor(lx), (int) Math.floor(ly), (int) Math.floor(lz), 0xFFE5334B, d, true);
            }
        } else {
            deadDone = false;
            if (mc.player.isAlive()) { lx = mc.player.getX(); ly = mc.player.getY(); lz = mc.player.getZ(); hasLast = true; }
        }
    }

    // ---------- drawing in the world ----------
    public static void render(DrawContext c, MinecraftClient mc, TextRenderer tr, float delta) {
        if (LIST.isEmpty() || mc.player == null || mc.world == null || !mc.options.getPerspective().isFirstPerson()) return;
        String d = dim(mc);
        Esp.setup(delta);
        int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
        for (Mark m : LIST) {
            if (!m.dim.equals(d)) continue;
            double[] s = Esp.project(m.x + 0.5, m.y + 0.5, m.z + 0.5);
            if (s == null) continue;
            double dist = Math.sqrt(mc.player.squaredDistanceTo(m.x + 0.5, m.y + 0.5, m.z + 0.5));
            String txt = m.name + " " + String.format(Locale.ROOT, "%.0fm", dist);
            int w = tr.getWidth(txt) + 8;
            int x = (int) Math.max(4, Math.min(sw - w - 4, s[0] - w / 2));
            int y = (int) Math.max(4, Math.min(sh - 18, s[1] - 14));
            Theme.card(c, x, y, w, 13, 4, 0xE60B0B0E, m.color);
            c.drawTextWithShadow(tr, txt, x + 4, y + 3, m.color);
            if (s[0] > 4 && s[0] < sw - 4 && s[1] > 18 && s[1] < sh - 4) Theme.round(c, (int) s[0] - 2, (int) s[1], 4, 4, 2, m.color);
        }
    }

    private Marks() {}
}
