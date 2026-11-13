package nelose;

import net.minecraft.client.gui.DrawContext;

public final class Theme {
    public static final int BG        = 0xF00A0A0C;
    public static final int PANEL     = 0xF2111114;
    public static final int CARD      = 0xFF18181C;
    public static final int CARD_HOV  = 0xFF1F1F25;
    public static final int BORDER    = 0xFF26262D;
    public static final int TEXT      = 0xFFEDEDF2;
    public static final int TEXT_DIM  = 0xFF8A8A96;

    public static final String[] PRESET_NAMES = {"Winter", "Violet", "Ocean", "Rose", "Mint", "Amber", "Rainbow"};
    private static final int[][] P = {
        {0xBFE6FF, 0x5BB8F5}, {0xA78BFA, 0x60A5FA}, {0x60A5FA, 0x22D3EE}, {0xF472B6, 0xFB7185},
        {0x34D399, 0xA3E635}, {0xFBBF24, 0xFB923C}
    };
    public static int preset = 0;

    public static int grad(int p, int which) {
        if (p >= P.length) {
            long off = which == 0 ? 0 : 900L;
            float h = ((System.currentTimeMillis() + off) % 6000L) / 6000f;
            return 0xFF000000 | java.awt.Color.HSBtoRGB(h, 0.55f, 1f);
        }
        return 0xFF000000 | P[p][which];
    }

    public static int accent()  { return grad(preset, 0); }
    public static int accent2() { return grad(preset, 1); }

    public static int alpha(int color, float a) {
        int al = (int) (((color >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, a)));
        return (al << 24) | (color & 0xFFFFFF);
    }

    public static int mix(int a, int b, float t) {
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return 0xFF000000 | ((int) (ar + (br - ar) * t) << 16) | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    /** Rounded rectangle built from 1px rows. */
    public static void round(DrawContext c, int x, int y, int w, int h, int r, int col) {
        r = Math.min(r, Math.min(w, h) / 2);
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = r - (int) Math.round(Math.sqrt(Math.max(0, r * r - dy * dy)));
            c.fill(x + inset, y + i, x + w - inset, y + i + 1, col);
            c.fill(x + inset, y + h - i - 1, x + w - inset, y + h - i, col);
        }
        c.fill(x, y + r, x + w, y + h - r, col);
    }

    /** Rounded rectangle with a vertical gradient from `top` to `bot`, both opaque RGB. */
    public static void roundGrad(DrawContext c, int x, int y, int w, int h, int r, int top, int bot, float a) {
        r = Math.min(r, Math.min(w, h) / 2);
        for (int row = 0; row < h; row++) {
            int inset = 0;
            if (row < r) {
                double dy = r - row - 0.5;
                inset = r - (int) Math.round(Math.sqrt(Math.max(0, r * r - dy * dy)));
            } else if (row >= h - r) {
                double dy = row - (h - r) + 0.5;
                inset = r - (int) Math.round(Math.sqrt(Math.max(0, r * r - dy * dy)));
            }
            c.fill(x + inset, y + row, x + w - inset, y + row + 1, alpha(mix(top, bot, row / (float) Math.max(1, h - 1)), a));
        }
    }

    /** Rounded card with 1px border. */
    public static void card(DrawContext c, int x, int y, int w, int h, int r, int fill, int border) {
        round(c, x, y, w, h, r, border);
        round(c, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), fill);
    }

    /** Horizontal gradient drawn in a few wide segments instead of one fill per pixel. */
    public static void gradBar(DrawContext c, int x, int y, int w, int h, int c1, int c2, int total, float a) {
        if (w <= 0) return;
        int seg = Math.max(1, Math.min(w, 20));
        float tot = Math.max(1, total);
        for (int i = 0; i < seg; i++) {
            int x1 = x + w * i / seg, x2 = x + w * (i + 1) / seg;
            if (x2 <= x1) continue;
            c.fill(x1, y, x2, y + h, alpha(mix(c1, c2, Math.min(1f, (x1 + x2 - 2 * x) / 2f / tot)), a));
        }
    }

    /** Soft glow around a box. */
    public static void glow(DrawContext c, int x, int y, int w, int h, int r, int col, int layers) {
        for (int i = layers; i >= 1; i--) {
            round(c, x - i, y - i, w + i * 2, h + i * 2, r + i, alpha(col, 0.035f * (layers - i + 1) / layers));
        }
    }
}
