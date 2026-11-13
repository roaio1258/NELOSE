package nelose;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Offline nickname switcher: changes the name the game logs in with. */
public final class Accounts {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("nelose-accounts.txt");
    public static final List<String> saved = new ArrayList<>();
    /** Nickname chosen last time (empty = never switched, keep the launcher account). */
    public static String last = "";

    public static boolean valid(String n) { return n != null && n.matches("[A-Za-z0-9_]{3,16}"); }

    public static void load() {
        saved.clear();
        last = "";
        try {
            if (!Files.exists(FILE)) return;
            for (String line : Files.readAllLines(FILE, StandardCharsets.UTF_8)) {
                line = line.trim();
                if (line.startsWith("*")) { last = line.substring(1); line = last; }
                if (valid(line) && !saved.contains(line)) saved.add(line);
            }
        } catch (Exception ignored) {}
    }

    public static void save() {
        try {
            List<String> out = new ArrayList<>();
            for (String s : saved) out.add((s.equals(last) ? "*" : "") + s);
            Files.write(FILE, out, StandardCharsets.UTF_8);
        } catch (Exception ignored) {}
    }

    public static void remember(String name) {
        saved.remove(name);
        saved.add(0, name);
        while (saved.size() > 4) saved.remove(saved.size() - 1);
        last = name;
        save();
    }

    public static void forget(String name) {
        saved.remove(name);
        if (name.equals(last)) last = "";
        save();
    }

    public static String current(MinecraftClient mc) {
        try { return mc.getSession().getUsername(); } catch (Throwable t) { return "?"; }
    }

    /** Called once when the game has started: restores the nickname used last time. */
    public static void applyOnStart(MinecraftClient mc) {
        if (valid(last)) switchTo(mc, last);
    }

    /** Replaces the game session with an offline one that has the given nickname. */
    public static boolean switchTo(MinecraftClient mc, String name) {
        if (!valid(name)) return false;
        try {
            Object old = mc.getSession();
            Class<?> sc = old.getClass();
            UUID id = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));

            Constructor<?> best = null;
            for (Constructor<?> k : sc.getDeclaredConstructors()) {
                Class<?>[] t = k.getParameterTypes();
                if (t.length >= 3 && t[0] == String.class && t[1] == UUID.class && t[2] == String.class
                    && (best == null || t.length > best.getParameterCount())) best = k;
            }
            if (best == null) return false;
            Class<?>[] t = best.getParameterTypes();
            Object[] args = new Object[t.length];
            args[0] = name; args[1] = id; args[2] = "0";
            for (int i = 3; i < t.length; i++) {
                if (t[i] == Optional.class) args[i] = Optional.empty();
                else if (t[i].isEnum()) {
                    Object[] consts = t[i].getEnumConstants();
                    Object pick = consts[0];
                    for (Object e : consts) if (e.toString().equalsIgnoreCase("LEGACY")) pick = e;
                    args[i] = pick;
                } else args[i] = null;
            }
            best.setAccessible(true);
            Object fresh = best.newInstance(args);

            for (Field f : MinecraftClient.class.getDeclaredFields()) {
                if (f.getType() == sc && !Modifier.isStatic(f.getModifiers())) {
                    f.setAccessible(true);
                    f.set(mc, fresh);
                    return true;
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private Accounts() {}
}
