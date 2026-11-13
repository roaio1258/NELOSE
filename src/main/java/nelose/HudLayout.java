package nelose;

import net.minecraft.client.gui.DrawContext;

import java.util.LinkedHashMap;
import java.util.Map;

/** Movable HUD widgets: stores per-widget offsets and the last drawn bounds. */
public final class HudLayout {
    public static final class Item {
        public final String id;
        public float dx, dy;
        public int x, y, w, h;   // default (unmoved) bounds
        long seen;
        Item(String id) { this.id = id; }
        public boolean visible() { return System.currentTimeMillis() - seen < 250; }
        public float left() { return x + dx; }
        public float top() { return y + dy; }
        public boolean hit(double mx, double my) {
            return visible() && mx >= left() && mx <= left() + w && my >= top() && my <= top() + h;
        }
    }

    public static final Map<String, Item> ITEMS = new LinkedHashMap<>();

    public static Item get(String id) { return ITEMS.computeIfAbsent(id, Item::new); }

    /** Starts drawing a widget: records its bounds and shifts everything drawn afterwards by its offset. */
    public static void begin(DrawContext c, String id, int x, int y, int w, int h) {
        Item it = get(id);
        it.x = x; it.y = y; it.w = w; it.h = h;
        it.seen = System.currentTimeMillis();
        c.getMatrices().pushMatrix();
        c.getMatrices().translate(it.dx, it.dy);
    }

    public static void end(DrawContext c) { c.getMatrices().popMatrix(); }

    /** Topmost visible widget under the cursor, or null. */
    public static Item at(double mx, double my) {
        Item found = null;
        for (Item it : ITEMS.values()) if (it.hit(mx, my)) found = it;
        return found;
    }

    /** Moves a widget so that the cursor keeps its grab point; keeps it on screen. */
    public static void drag(Item it, double mx, double my, double gx, double gy, int sw, int sh) {
        float nx = (float) (mx - gx), ny = (float) (my - gy);
        nx = Math.max(0, Math.min(sw - it.w, nx));
        ny = Math.max(0, Math.min(sh - it.h, ny));
        it.dx = nx - it.x;
        it.dy = ny - it.y;
    }

    private HudLayout() {}
}
