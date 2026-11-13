package nelose;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Saves modules, settings, binds and the accent color to config/nelose.properties. */
public final class Config {
    private static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("nelose.properties"); }

    public static void save() {
        Properties p = new Properties();
        p.setProperty("ver", "2");
        p.setProperty("theme", String.valueOf(Theme.preset));
        p.setProperty("season", String.valueOf(Season.current));
        for (Module m : Modules.ALL) {
            String k = "m." + m.name + ".";
            p.setProperty(k + "on", String.valueOf(m.on));
            p.setProperty(k + "key", String.valueOf(m.key));
            p.setProperty(k + "bind", String.valueOf(m.bindMode));
            for (Setting s : m.settings) {
                String v = s.isToggle ? String.valueOf(s.on) : s.isMode ? String.valueOf(s.index) : String.valueOf(s.value);
                p.setProperty(k + "s." + s.name, v);
            }
        }
        p.setProperty("win.dx", String.valueOf(NeloseScreen.winDX));
        p.setProperty("win.dy", String.valueOf(NeloseScreen.winDY));
        for (HudLayout.Item it : HudLayout.ITEMS.values()) {
            p.setProperty("hud." + it.id + ".dx", String.valueOf(it.dx));
            p.setProperty("hud." + it.id + ".dy", String.valueOf(it.dy));
        }
        try (Writer w = Files.newBufferedWriter(file())) { p.store(w, "Nelose"); } catch (IOException ignored) {}
    }

    public static void load() {
        Path f = file();
        if (!Files.exists(f)) return;
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(f)) { p.load(r); } catch (IOException e) { return; }
        boolean fresh = !"2".equals(p.getProperty("ver"));
        try {
            Theme.preset = Integer.parseInt(p.getProperty("theme", String.valueOf(Theme.preset)));
            Season.current = Math.max(0, Math.min(Season.NAMES.length - 1,
                Integer.parseInt(p.getProperty("season", String.valueOf(Season.current)))));
            NeloseScreen.winDX = Float.parseFloat(p.getProperty("win.dx", "0"));
            NeloseScreen.winDY = Float.parseFloat(p.getProperty("win.dy", "0"));
            for (String id : new String[]{"watermark", "info", "modulelist", "keystrokes", "armor", "items", "target"}) {
                HudLayout.Item it = HudLayout.get(id);
                it.dx = Float.parseFloat(p.getProperty("hud." + id + ".dx", "0"));
                it.dy = Float.parseFloat(p.getProperty("hud." + id + ".dy", "0"));
            }
            for (Module m : Modules.ALL) {
                String k = "m." + m.name + ".";
                // configs from before version 2 had modules switched on: start from all off
                m.on = fresh ? false : Boolean.parseBoolean(p.getProperty(k + "on", String.valueOf(m.on)));
                m.anim = m.on ? 1f : 0f;
                m.key = Integer.parseInt(p.getProperty(k + "key", String.valueOf(m.key)));
                m.bindMode = Integer.parseInt(p.getProperty(k + "bind", String.valueOf(m.bindMode)));
                for (Setting s : m.settings) {
                    String v = p.getProperty(k + "s." + s.name);
                    if (v == null) continue;
                    if (s.isToggle) { s.on = Boolean.parseBoolean(v); s.anim = s.on ? 1f : 0f; }
                    else if (s.isMode) s.index = Math.max(0, Math.min(s.options.length - 1, Integer.parseInt(v)));
                    else s.value = Math.max(s.min, Math.min(s.max, Double.parseDouble(v)));
                }
            }
        } catch (NumberFormatException ignored) {}
    }

    private Config() {}
}
