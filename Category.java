package nelose;

public enum Category {
    VISUALS("Visuals", "Camera and world visuals"),
    HUD("HUD", "Information on your screen"),
    PLAYER("Player", "Quality of life helpers"),
    EFFECTS("Effects", "Particles and hit effects"),
    THEME("Theme", "Colors and interface"),
    SETTINGS("Settings", "Logo model, animation and blizzard");

    public final String title, sub;
    Category(String t, String s) { title = t; sub = s; }
}
