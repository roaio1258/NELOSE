package nelose;

import net.minecraft.client.gui.DrawContext;

/** Cheap falling snow made of 1-2 px squares. Deterministic per flake, so no state is kept. */
public final class Snow {
    public static void draw(DrawContext c, int w, int h, int count, float alpha) {
        double t = System.currentTimeMillis() / 1000.0;
        for (int i = 0; i < count; i++) {
            double depth = 0.35 + (i * 31 % 10) / 10.0 * 0.65;               // far flakes are slower and dimmer
            double x = ((i * 7919L) % Math.max(1, w)) + Math.sin(t * 0.7 + i) * 14 * depth;
            double y = (((i * 104729L) % Math.max(1, h)) + t * 22 * depth) % h;
            int s = depth > 0.75 ? 2 : 1;
            c.fill((int) x, (int) y, (int) x + s, (int) y + s, Theme.alpha(0xFFFFFFFF, alpha * (float) depth));
        }
    }

    private Snow() {}
}
