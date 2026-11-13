package nelose;

import java.util.ArrayList;
import java.util.List;

public final class Module {
    public final String name, desc;
    public final Category cat;
    public final boolean listed;   // shown in the module list on screen
    public boolean on;
    public int key = -1;           // GLFW key, -1 = unbound
    public int bindMode;           // 0 = toggle, 1 = hold
    public boolean keyWas;
    public float anim;             // 0..1 switch animation
    public final List<Setting> settings = new ArrayList<>();

    Module(String name, String desc, Category cat, boolean def, boolean listed) {
        this.name = name; this.desc = desc; this.cat = cat;
        this.on = def; this.anim = def ? 1f : 0f; this.listed = listed;
    }

    public Module slider(String n, double min, double max, double def, double step) {
        settings.add(Setting.slider(n, min, max, def, step));
        return this;
    }

    public Module toggle(String n, boolean def) {
        settings.add(Setting.toggle(n, def));
        return this;
    }

    public Module mode(String n, int def, String... opts) {
        settings.add(Setting.mode(n, def, opts));
        return this;
    }

    /** The last added setting is only shown while the mode setting `mode` is one of `vals`. */
    public Module when(String mode, int... vals) {
        Setting s = settings.get(settings.size() - 1);
        s.depName = mode; s.depVals = vals;
        return this;
    }

    public boolean visible(Setting s) {
        if (s.depName == null) return true;
        int i = idx(s.depName);
        for (int v : s.depVals) if (v == i) return true;
        return false;
    }

    public int idx(String n) { return find(n).index; }

    private Setting find(String n) {
        for (Setting s : settings) if (s.name.equals(n)) return s;
        throw new IllegalArgumentException("No setting " + n + " in " + name);
    }

    public void setIdx(String n, int v) { Setting s = find(n); s.index = Math.max(0, Math.min(s.options.length - 1, v)); }

    public float f(String n) { return (float) find(n).value; }
    public boolean b(String n) { return find(n).on; }
}
