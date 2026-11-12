package nelose;

import java.util.Locale;

/** A module option: either an on/off switch or a numeric slider. */
public final class Setting {
    public final String name;
    public final boolean isToggle;
    public boolean isMode;
    public String[] options;
    public int index;
    public boolean on;
    public double value, min, max, step;
    public float anim;   // switch animation

    private Setting(String n, boolean toggle) { name = n; isToggle = toggle; }

    public static Setting toggle(String n, boolean def) {
        Setting s = new Setting(n, true);
        s.on = def; s.anim = def ? 1f : 0f;
        return s;
    }

    public static Setting mode(String n, int def, String... opts) {
        Setting s = new Setting(n, false);
        s.isMode = true; s.options = opts; s.index = def;
        return s;
    }

    public void cycle(int d) { index = (index + d + options.length) % options.length; }

    public static Setting slider(String n, double min, double max, double def, double step) {
        Setting s = new Setting(n, false);
        s.min = min; s.max = max; s.value = def; s.step = step;
        return s;
    }

    public void setFromFraction(double k) {
        k = Math.max(0, Math.min(1, k));
        double v = min + (max - min) * k;
        if (step > 0) v = Math.round(v / step) * step;
        value = Math.max(min, Math.min(max, v));
    }

    public double fraction() { return (value - min) / (max - min); }

    public String display() {
        if (isMode) return options[index];
        return step >= 1 ? String.valueOf((int) Math.round(value)) : String.format(Locale.ROOT, "%.2f", value);
    }
}
