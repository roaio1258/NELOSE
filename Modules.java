package nelose;

import java.util.ArrayList;
import java.util.List;

public final class Modules {
    public static final List<Module> ALL = new ArrayList<>();

    private static Module add(String n, String d, Category c, boolean def, boolean listed) {
        Module m = new Module(n, d, c, false, listed);   // everything starts switched off
        ALL.add(m);
        return m;
    }

    // Visuals
    public static final Module FULLBRIGHT = add("Fullbright", "See clearly in the dark", Category.VISUALS, false, true);
    public static final Module ZOOM = add("Zoom", "Hold C to zoom in", Category.VISUALS, true, true)
        .slider("Zoom FOV", 15, 80, 30, 1).slider("Smoothness", 0.1, 0.6, 0.35, 0.01);
    public static final Module TARGET_HUD = add("Target HUD", "Health of the mob you look at", Category.VISUALS, true, true)
        .toggle("Health text", true).toggle("Distance", false);
    public static final Module ARMOR_HUD = add("Armor HUD", "Armor and durability on screen", Category.VISUALS, true, true)
        .toggle("Durability", true);
    public static final Module TARGET_ESP = add("Target ESP", "Animated effect around your target", Category.VISUALS, true, true)
        .mode("Mode", 0, "Ring", "Katanas", "Scan", "Lightning", "Storm", "Helix", "Shockwave", "Halo", "Orbs")
        .slider("Radius", 0.5, 2.0, 1.0, 0.05).when("Mode", 0, 1, 5, 6, 7, 8)
        .slider("Speed", 0.5, 3.0, 1.0, 0.1).when("Mode", 0, 5, 6, 7, 8)
        .slider("Trail", 1, 8, 5, 1).when("Mode", 0)
        .toggle("Orbit dots", true).when("Mode", 0)
        .slider("Katana speed", 0.5, 3.0, 1.0, 0.1).when("Mode", 1)
        .slider("Scan speed", 0.5, 3.0, 1.0, 0.1).when("Mode", 2)
        .slider("Scan trail", 1, 10, 5, 1).when("Mode", 2)
        .slider("Scan waves", 1, 4, 2, 1).when("Mode", 2)
        .toggle("Scan outline", true).when("Mode", 2)
        .slider("Bolts", 1, 5, 2, 1).when("Mode", 3, 4)
        .slider("Bolt rate", 0.5, 3.0, 1.2, 0.1).when("Mode", 3, 4)
        .toggle("Hit lightning", true)
        .mode("Quality", 1, "Low", "Medium", "High");
    public static final Module TARGET_BOX = add("Target box", "Corner box with a health bar", Category.VISUALS, false, true)
        .slider("Corner size", 4, 18, 10, 1).toggle("Health bar", true);
    public static final Module GLASS_ESP = add("Glass ESP", "Tinted glass box on your target", Category.VISUALS, false, true)
        .slider("Tint hue", 0, 1, 0.58, 0.01).slider("Tint strength", 0.05, 0.6, 0.25, 0.01)
        .slider("Glass thickness", 1, 4, 2, 1).toggle("Hit reaction", true);
    public static final Module ATMOSPHERE = add("Atmosphere", "Snow, haze, light and air particles", Category.VISUALS, false, true)
        .toggle("Snow", true).slider("Snow amount", 1, 12, 4, 1)
        .slider("Haze", 0, 0.5, 0.12, 0.01).slider("Light tint", 0, 0.3, 0.0, 0.01)
        .toggle("Air particles", true);
    public static final Module TRAJECTORY = add("Trajectory", "Predicted landing of throws and shots", Category.VISUALS, false, true)
        .toggle("Landing marker", true).slider("Steps", 20, 120, 80, 5);
    public static final Module TNT_PREVIEW = add("TNT Preview", "Names and timers of nearby TNT", Category.VISUALS, false, true)
        .slider("Range", 8, 64, 32, 2).toggle("HUD panel", true);
    public static final Module ARROW_NAMES = add("Arrow Names", "Custom labels on fired arrows", Category.VISUALS, false, true)
        .mode("Label", 0, "Owner", "Arrow", "Dart", "Needle").slider("Range", 8, 64, 32, 2);
    public static final Module CUSTOM_HITBOX = add("Custom Hitbox", "Visual hitboxes of players", Category.VISUALS, false, true)
        .mode("Color", 0, "Accent", "White", "Red").slider("Thickness", 1, 3, 1, 1).slider("Fill", 0, 0.4, 0.08, 0.01).slider("Range", 8, 64, 32, 2);
    public static final Module HEALTH_INDICATOR = add("Health Indicator", "Health of what you aim at", Category.VISUALS, false, true)
        .mode("Style", 0, "Bar", "Text").toggle("Show name", true);
    public static final Module LOGO = add("Logo", "Logo in watermark, menu and title", Category.SETTINGS, true, false)
        .mode("Model", 0, "N ring", "Haunter", "Cat")
        .toggle("Animated", true).toggle("Blizzard", true).toggle("Winter glow", true);

    // HUD
    public static final Module WATERMARK = add("Watermark", "Show the client watermark", Category.HUD, true, false)
        .toggle("Glow", true);
    public static final Module FPS = add("FPS", "Frames per second", Category.HUD, true, false);
    public static final Module PING = add("Ping", "Latency to the server", Category.HUD, true, false);
    public static final Module TIME = add("Clock", "Real time in the watermark", Category.HUD, true, false)
        .toggle("Seconds", false);
    public static final Module COORDS = add("Coordinates", "Your XYZ position", Category.HUD, true, false)
        .toggle("Decimals", false);
    public static final Module DIRECTION = add("Direction", "Where you are facing", Category.HUD, false, false);
    public static final Module SPEED = add("Speed", "Blocks per second", Category.HUD, false, false)
        .mode("Unit", 0, "b/s", "km/h");
    public static final Module CPS = add("CPS", "Clicks per second", Category.HUD, false, false);
    public static final Module CROSSHAIR = add("Custom Crosshair", "Your own crosshair, replaces vanilla", Category.HUD, false, false)
        .slider("Size", 2, 14, 5, 1).slider("Gap", 0, 8, 3, 1).slider("Thickness", 1, 4, 1, 1)
        .toggle("Dot", false).toggle("Outline", true).toggle("T shape", false).toggle("Accent color", true);
    public static final Module KEYSTROKES = add("Keystrokes", "WASD, jump and mouse buttons", Category.HUD, true, false)
        .slider("Size", 16, 30, 22, 1).toggle("Show CPS", true);
    public static final Module MODULE_LIST = add("Module list", "Enabled modules on the right", Category.HUD, true, false)
        .toggle("Background", true).mode("Sort", 0, "Length", "A-Z");

    // Player
    public static final Module CLICK_PEARL = add("Click Pearl", "Throw a pearl from the hotbar with a key", Category.PLAYER, false, true)
        .toggle("Switch back", true);
    public static final Module FAST_USE = add("Fast Use", "Keys for firework, snowball and potion", Category.PLAYER, false, true)
        .toggle("Switch back", true);
    public static final Module MACE_HELPER = add("Mace Helper", "Shows when a mace smash is ready", Category.PLAYER, false, true)
        .slider("Min fall", 1.5, 6, 1.5, 0.5).toggle("Chat message", true);
    public static final Module AUTO_SPRINT = add("Auto sprint", "Always sprint while moving forward", Category.PLAYER, true, true)
        .toggle("Hunger check", true);
    public static final Module AUTO_RESPAWN = add("Auto respawn", "Skip the death screen", Category.PLAYER, false, true)
        .slider("Delay", 0, 40, 0, 1);
    public static final Module LOW_HP = add("Low health glow", "Red edges when health is low", Category.PLAYER, true, false)
        .slider("Threshold", 2, 10, 6, 1);
    public static final Module FAKE_PLAYER = add("Fake Player", "Client-side dummy to test your effects on", Category.PLAYER, false, true)
        .mode("Name", 0, "Dummy", "Steve", "Bot", "Target")
        .slider("Count", 1, 5, 1, 1).slider("Distance", 1.5, 6, 3, 0.5)
        .toggle("Copy gear", true).toggle("Face you", true);
    public static final Module ITEM_COUNTER = add("Item counter", "Totems, gapples, pearls, crystals", Category.PLAYER, true, true)
        .toggle("Totem", true).toggle("Gapple", true).toggle("Pearl", true).toggle("Crystal", true);

    // Effects
    public static final Module HIT_PARTICLES = add("Hit particles", "Crit burst when you hit a mob", Category.EFFECTS, true, true)
        .mode("Type", 0, "Crit", "Enchant", "Hearts", "Mixed")
        .slider("Amount", 4, 40, 12, 1);
    public static final Module KILL_EFFECT = add("Kill effect", "Totem burst when your target dies", Category.EFFECTS, true, true)
        .mode("Type", 0, "Totem", "Firework", "Soul", "Hearts")
        .slider("Amount", 8, 60, 28, 1);
    public static final Module JUMP_CIRCLES = add("Jump circles", "Expanding ring when you jump", Category.EFFECTS, true, true)
        .mode("Mode", 0, "Ring", "Magnetic filings")
        .slider("Particles", 12, 60, 36, 1).slider("Spread", 0.05, 0.3, 0.12, 0.01);
    public static final Module TRAIL = add("Trail", "Colored trail behind you", Category.EFFECTS, false, true)
        .slider("Density", 1, 4, 2, 1).slider("Size", 0.5, 2.0, 1.0, 0.1);
    public static final Module AURA = add("Aura", "Spiral of particles around you", Category.EFFECTS, false, true)
        .slider("Radius", 0.4, 1.5, 0.75, 0.05).slider("Speed", 0.1, 0.8, 0.35, 0.01).slider("Density", 1, 4, 2, 1);
    public static final Module HIT_SOUNDS = add("Hit Sounds", "Sound when you hit something", Category.EFFECTS, false, true)
        .mode("Sound", 0, "Bell", "Bonk", "Bubble", "Pop", "Hit1", "Hit2", "Hit3")
        .slider("Volume", 0.1, 1.0, 0.7, 0.05).slider("Pitch", 0.5, 2.0, 1.0, 0.05);
    public static final Module HIT_MARKER = add("Hit marker", "Cross on your crosshair when you hit", Category.EFFECTS, true, true)
        .slider("Duration", 100, 600, 320, 10);

    // Theme
    public static final Module BLUR = add("Interface", "Dim, opening animation and title screen", Category.THEME, true, false)
        .slider("Dim", 0.1, 0.9, 0.55, 0.05)
        .mode("Opening animation", 0, "Default", "Cinematic", "Glass shards", "Random shards")
        .toggle("Title screen", true).slider("Title snow", 0, 200, 90, 10).toggle("GUI snow", true).toggle("Intro", true);

    public static List<Module> of(Category c) {
        List<Module> r = new ArrayList<>();
        for (Module m : ALL) if (m.cat == c) r.add(m);
        return r;
    }

    private Modules() {}
}
