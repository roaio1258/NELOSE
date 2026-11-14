package nelose;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Every HUD widget except the watermark. */
public final class Hud {
    private static final ArrayDeque<Long> LEFT = new ArrayDeque<>(), RIGHT = new ArrayDeque<>();
    private static boolean lp, rp;
    private static final float[] KEY_ANIM = new float[7];

    public static void render(DrawContext c, float delta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options.hudHidden || mc.player == null) return;
        pollMouse(mc);
        TextRenderer tr = mc.textRenderer;
        int a1 = Theme.accent(), a2 = Theme.accent2();

        lowHealth(c, mc);
        atmosphere(c, mc);
        Esp.render(c, delta);
        World2D.render(c, delta);
        if (Modules.CROSSHAIR.on) crosshair(c, mc, a1, a2);
        if (Modules.HEALTH_INDICATOR.on) healthIndicator(c, mc, tr, a1, a2);
        if (Modules.MACE_HELPER.on) maceHud(c, mc, tr);
        if (Modules.HIT_MARKER.on) hitMarker(c, mc);
        infoPanel(c, mc, tr);
        if (Modules.MODULE_LIST.on) moduleList(c, mc, tr, a1, a2);
        if (Modules.KEYSTROKES.on) keystrokes(c, mc, tr, a1);
        if (Modules.ARMOR_HUD.on) armor(c, mc, tr);
        if (Modules.ITEM_COUNTER.on) itemCounter(c, mc, tr);
        if (Modules.TARGET_HUD.on) target(c, mc, tr, a1, a2);
    }

    // ---------- CPS ----------
    private static void pollMouse(MinecraftClient mc) {
        long win = mc.getWindow().getHandle();
        boolean l = GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean r = GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        long now = System.currentTimeMillis();
        if (mc.currentScreen == null) {
            if (l && !lp) LEFT.add(now);
            if (r && !rp) RIGHT.add(now);
        }
        lp = l; rp = r;
        while (!LEFT.isEmpty() && now - LEFT.peek() > 1000) LEFT.poll();
        while (!RIGHT.isEmpty() && now - RIGHT.peek() > 1000) RIGHT.poll();
    }

    // ---------- info panel under the watermark ----------
    private static void infoPanel(DrawContext c, MinecraftClient mc, TextRenderer tr) {
        List<String[]> rows = new ArrayList<>();
        if (Modules.COORDS.on) rows.add(new String[]{"XYZ", String.format(Locale.ROOT, Modules.COORDS.b("Decimals") ? "%.1f  %.1f  %.1f" : "%.0f  %.0f  %.0f",
            mc.player.getX(), mc.player.getY(), mc.player.getZ())});
        if (Modules.DIRECTION.on) {
            String d = mc.player.getHorizontalFacing().asString();
            rows.add(new String[]{"Facing", Character.toUpperCase(d.charAt(0)) + d.substring(1)});
        }
        if (Modules.SPEED.on) {
            boolean kmh = Modules.SPEED.idx("Unit") == 1;
            rows.add(new String[]{"Speed", String.format(Locale.ROOT, kmh ? "%.1f km/h" : "%.1f b/s", kmh ? Features.bps * 3.6 : Features.bps)});
        }
        if (Modules.CPS.on) rows.add(new String[]{"CPS", LEFT.size() + " | " + RIGHT.size()});
        if (rows.isEmpty()) return;

        int lw = 0, vw = 0;
        for (String[] r : rows) { lw = Math.max(lw, tr.getWidth(r[0])); vw = Math.max(vw, tr.getWidth(r[1])); }
        int w = lw + vw + 22, h = rows.size() * 11 + 7;
        int x = 8, y = Watermark.bottom + 4;
        HudLayout.begin(c, "info", x, y, w, h);
        Theme.card(c, x, y, w, h, 5, 0xE60B0B0E, Theme.BORDER);
        int ty = y + 4;
        for (String[] r : rows) {
            c.drawText(tr, r[0], x + 7, ty, Theme.TEXT_DIM, false);
            c.drawTextWithShadow(tr, r[1], x + 15 + lw, ty, Theme.TEXT);
            ty += 11;
        }
        HudLayout.end(c);
    }

    // ---------- module list ----------
    private static void moduleList(DrawContext c, MinecraftClient mc, TextRenderer tr, int a1, int a2) {
        List<Module> list = new ArrayList<>();
        for (Module m : Modules.ALL) if (m.on && m.listed) list.add(m);
        if (Modules.MODULE_LIST.idx("Sort") == 1) list.sort((p, q) -> p.name.compareTo(q.name));
        else list.sort((p, q) -> tr.getWidth(q.name) - tr.getWidth(p.name));
        if (list.isEmpty()) return;
        int right = mc.getWindow().getScaledWidth() - 6, y = 6;
        int mw = 0;
        for (Module m : list) mw = Math.max(mw, tr.getWidth(m.name) + 8);
        HudLayout.begin(c, "modulelist", right - mw - 2, y, mw + 2, list.size() * 11);
        for (int i = 0; i < list.size(); i++) {
            Module m = list.get(i);
            int w = tr.getWidth(m.name) + 8;
            int col = Theme.mix(a1, a2, list.size() <= 1 ? 0f : i / (float) (list.size() - 1));
            if (Modules.MODULE_LIST.b("Background")) c.fill(right - w - 2, y, right, y + 11, 0xA00B0B0E);
            c.fill(right - 2, y, right, y + 11, col);
            c.drawTextWithShadow(tr, m.name, right - w, y + 2, col);
            y += 11;
        }
        HudLayout.end(c);
    }

    // ---------- keystrokes ----------
    private static void keystrokes(DrawContext c, MinecraftClient mc, TextRenderer tr, int a1) {
        boolean[] down = {
            mc.options.forwardKey.isPressed(), mc.options.leftKey.isPressed(),
            mc.options.backKey.isPressed(), mc.options.rightKey.isPressed(),
            mc.options.jumpKey.isPressed(), lp, rp
        };
        for (int i = 0; i < 7; i++) KEY_ANIM[i] += ((down[i] ? 1f : 0f) - KEY_ANIM[i]) * 0.3f;

        int s = (int) Modules.KEYSTROKES.f("Size"), g = 3;
        int x = 8, y = mc.getWindow().getScaledHeight() / 2 - 40;
        HudLayout.begin(c, "keystrokes", x, y, 3 * s + 2 * g, 3 * (s + g) + 12);
        key(c, tr, x + s + g, y, s, s, "W", KEY_ANIM[0], a1);
        key(c, tr, x, y + s + g, s, s, "A", KEY_ANIM[1], a1);
        key(c, tr, x + s + g, y + s + g, s, s, "S", KEY_ANIM[2], a1);
        key(c, tr, x + 2 * (s + g), y + s + g, s, s, "D", KEY_ANIM[3], a1);
        int bw = (3 * s + 2 * g - g) / 2;
        key(c, tr, x, y + 2 * (s + g), bw, s, Modules.KEYSTROKES.b("Show CPS") ? LEFT.size() + " L" : "LMB", KEY_ANIM[5], a1);
        key(c, tr, x + bw + g, y + 2 * (s + g), bw + 1, s, Modules.KEYSTROKES.b("Show CPS") ? RIGHT.size() + " R" : "RMB", KEY_ANIM[6], a1);
        key(c, tr, x, y + 3 * (s + g), 3 * s + 2 * g, 12, "", KEY_ANIM[4], a1);
        HudLayout.end(c);
    }

    private static void key(DrawContext c, TextRenderer tr, int x, int y, int w, int h, String label, float p, int accent) {
        int fill = Theme.mix(0xFF0B0B0E, Theme.mix(accent, 0xFF0B0B0E, 0.55f), p);
        int border = Theme.mix(Theme.BORDER, accent, p);
        Theme.card(c, x, y, w, h, 4, Theme.alpha(fill, 0.93f), border);
        if (label.isEmpty()) {
            c.fill(x + w / 2 - 8, y + h / 2, x + w / 2 + 8, y + h / 2 + 1, Theme.mix(Theme.TEXT_DIM, 0xFFFFFFFF, p));
        } else {
            c.drawCenteredTextWithShadow(tr, label, x + w / 2, y + (h - 8) / 2 + 1, Theme.mix(Theme.TEXT_DIM, 0xFFFFFFFF, p));
        }
    }

    // ---------- armor ----------
    private static void armor(DrawContext c, MinecraftClient mc, TextRenderer tr) {
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        List<ItemStack> items = new ArrayList<>();
        for (EquipmentSlot s : slots) {
            ItemStack st = mc.player.getEquippedStack(s);
            if (!st.isEmpty()) items.add(st);
        }
        if (items.isEmpty()) {
            if (!(mc.currentScreen instanceof NeloseScreen || mc.currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen)) return;   // preview while moving widgets
            items.add(new ItemStack(Items.DIAMOND_HELMET)); items.add(new ItemStack(Items.DIAMOND_CHESTPLATE));
            items.add(new ItemStack(Items.DIAMOND_LEGGINGS)); items.add(new ItemStack(Items.DIAMOND_BOOTS));
        }
        int x = mc.getWindow().getScaledWidth() - 26;
        int h = items.size() * 18 + 6;
        int y = mc.getWindow().getScaledHeight() - h - 8;
        HudLayout.begin(c, "armor", x - 3, y - 3, 24, h);
        Theme.card(c, x - 3, y - 3, 24, h, 5, 0xE60B0B0E, Theme.BORDER);
        for (ItemStack st : items) {
            c.drawItem(st, x, y);
            if (Modules.ARMOR_HUD.b("Durability")) c.drawStackOverlay(tr, st, x, y);
            y += 18;
        }
        HudLayout.end(c);
    }

    // ---------- item counter ----------
    private static int count(MinecraftClient mc, Item item) {
        int n = 0;
        var inv = mc.player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack st = inv.getStack(i);
            if (st.isOf(item)) n += st.getCount();
        }
        return n;
    }

    private static void itemCounter(DrawContext c, MinecraftClient mc, TextRenderer tr) {
        Item[] all = {Items.TOTEM_OF_UNDYING, Items.GOLDEN_APPLE, Items.ENDER_PEARL, Items.END_CRYSTAL};
        String[] keys = {"Totem", "Gapple", "Pearl", "Crystal"};
        List<Item> sel = new ArrayList<>();
        for (int i = 0; i < all.length; i++) if (Modules.ITEM_COUNTER.b(keys[i])) sel.add(all[i]);
        if (sel.isEmpty()) return;
        Item[] items = sel.toArray(new Item[0]);
        int x = 8, y = mc.getWindow().getScaledHeight() - 30;
        HudLayout.begin(c, "items", x, y, items.length * 34 + 4, 22);
        Theme.card(c, x, y, items.length * 34 + 4, 22, 5, 0xE60B0B0E, Theme.BORDER);
        for (int i = 0; i < items.length; i++) {
            int n = count(mc, items[i]);
            int ix = x + 6 + i * 34;
            c.drawItem(new ItemStack(items[i]), ix, y + 3);
            c.drawTextWithShadow(tr, String.valueOf(n), ix + 17, y + 8, n == 0 ? Theme.TEXT_DIM : Theme.TEXT);
        }
        HudLayout.end(c);
    }

    // ---------- target hud ----------
    /** Card with the target's head on the left, name and health bar in the middle, and a health ring that is always shown. */
    private static void target(DrawContext c, MinecraftClient mc, TextRenderer tr, int a1, int a2) {
        String name; float hp, max, dist;
        LivingEntity le = null;
        if (mc.targetedEntity instanceof LivingEntity t) {
            le = t;
            name = t.getName().getString();
            hp = t.getHealth(); max = Math.max(1f, t.getMaxHealth()); dist = mc.player.distanceTo(t);
        } else if (mc.currentScreen instanceof NeloseScreen || mc.currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen) {   // preview while moving widgets
            name = "Target"; hp = 20f; max = 20f; dist = 3.5f;
        } else return;
        float frac = Math.max(0f, Math.min(1f, hp / max));
        String hpText = String.format(Locale.ROOT, "%.1f / %.0f", hp, max)
            + (Modules.TARGET_HUD.b("Distance") ? String.format(Locale.ROOT, "  %.1fm", dist) : "");
        String num = String.valueOf(Math.round(hp));
        int w = Math.max(118, Math.max(tr.getWidth(name), tr.getWidth(hpText)) + 34 + 40), h = 34;
        int x = mc.getWindow().getScaledWidth() / 2 + 16, y = mc.getWindow().getScaledHeight() / 2 + 14;
        HudLayout.begin(c, "target", x, y, w, h);
        Theme.card(c, x, y, w, h, 6, 0xE00E1220, Theme.BORDER);

        // head of the target
        int hx = x + 5, hy = y + 5, hs = 24;
        Theme.round(c, hx - 1, hy - 1, hs + 2, hs + 2, 5, Theme.alpha(0xFFFFFFFF, 0.14f));
        if (le instanceof AbstractClientPlayerEntity ap) {
            PlayerSkinDrawer.draw(c, ap.getSkin(), hx, hy, hs);
        } else if (le == null) {   // preview head
            c.fill(hx, hy, hx + hs, hy + hs, 0xFFC99A7A);
            c.fill(hx, hy, hx + hs, hy + 6, 0xFF5A3A24);
            c.fill(hx + 5, hy + 10, hx + 10, hy + 14, 0xFFFFFFFF); c.fill(hx + 14, hy + 10, hx + 19, hy + 14, 0xFFFFFFFF);
            c.fill(hx + 7, hy + 11, hx + 10, hy + 14, 0xFF3A4BA0); c.fill(hx + 16, hy + 11, hx + 19, hy + 14, 0xFF3A4BA0);
        } else {                   // a mob: accent tile with its first letter
            Theme.round(c, hx, hy, hs, hs, 5, Theme.alpha(Theme.mix(a1, a2, 0.5f), 0.9f));
            String ltr = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(Locale.ROOT);
            c.drawText(tr, ltr, hx + hs / 2 - tr.getWidth(ltr) / 2, hy + hs / 2 - 4, 0xFF0B0E16, false);
        }

        // name, optional health text, bar
        int tx = x + 34;
        c.drawTextWithShadow(tr, tr.trimToWidth(name, w - 34 - 38), tx, y + 6, Theme.TEXT);
        if (Modules.TARGET_HUD.b("Health text")) c.drawText(tr, hpText, tx, y + 17, Theme.TEXT_DIM, false);
        int bw = w - 34 - 38, by = y + h - 8;
        Theme.round(c, tx, by, bw, 3, 1, 0xFF26262D);
        int fw = Math.max(frac > 0f ? 2 : 0, (int) (bw * frac));
        if (fw > 0) Theme.gradBar(c, tx, by, fw, 3, a1, a2, bw, 1f);

        // health ring, always visible
        int rcx = x + w - 20, rcy = y + h / 2;
        int ring = Theme.mix(0xFFE5334B, 0xFF4ADE80, frac);
        Theme.round(c, rcx - 12, rcy - 12, 24, 24, 12, Theme.alpha(ring, 0.95f));
        Theme.round(c, rcx - 10, rcy - 10, 20, 20, 10, 0xFF0E1220);
        c.drawText(tr, num, rcx - tr.getWidth(num) / 2, rcy - 4, Theme.TEXT, false);
        HudLayout.end(c);
    }

    // ---------- hit marker ----------
    private static void hitMarker(DrawContext c, MinecraftClient mc) {
        long dt = System.currentTimeMillis() - Features.hitTime;
        float dur = Modules.HIT_MARKER.f("Duration");
        if (dt > dur || mc.currentScreen != null) return;
        float a = 1f - dt / dur;
        int cx = mc.getWindow().getScaledWidth() / 2, cy = mc.getWindow().getScaledHeight() / 2;
        int col = Theme.alpha(0xFFFFFFFF, a);
        int in = 3 + (int) (dt / 80);
        for (int k = in; k < in + 5; k++) {
            c.fill(cx - k, cy - k, cx - k + 1, cy - k + 1, col);
            c.fill(cx + k, cy - k, cx + k + 1, cy - k + 1, col);
            c.fill(cx - k, cy + k, cx - k + 1, cy + k + 1, col);
            c.fill(cx + k, cy + k, cx + k + 1, cy + k + 1, col);
        }
    }

    // ---------- low health ----------
    private static void lowHealth(DrawContext c, MinecraftClient mc) {
        if (!Modules.LOW_HP.on || mc.player.getHealth() > Modules.LOW_HP.f("Threshold") || mc.player.isDead()) return;
        int w = mc.getWindow().getScaledWidth(), h = mc.getWindow().getScaledHeight();
        double pulse = 0.65 + 0.35 * Math.sin(System.currentTimeMillis() / 220.0);
        float base = (float) (0.42 * pulse);
        for (int i = 0; i < 26; i++) {
            int col = Theme.alpha(0xFFE5334B, base * (1f - i / 26f));
            c.fill(0, i, w, i + 1, col);
            c.fill(0, h - i - 1, w, h - i, col);
            c.fill(i, 0, i + 1, h, col);
            c.fill(w - i - 1, 0, w - i, h, col);
        }
    }


    // ---------- atmosphere overlays (haze + light tint) ----------
    private static void atmosphere(DrawContext c, MinecraftClient mc) {
        if (!Modules.ATMOSPHERE.on) return;
        int w = mc.getWindow().getScaledWidth(), h = mc.getWindow().getScaledHeight();
        float haze = Modules.ATMOSPHERE.f("Haze"), tint = Modules.ATMOSPHERE.f("Light tint");
        if (haze > 0.005f) {
            // soft bright bands towards the horizon, strongest in the middle of the screen
            int bands = 24;
            for (int i = 0; i < bands; i++) {
                float k = 1f - Math.abs(i - bands / 2f) / (bands / 2f);
                c.fill(0, h * i / bands, w, h * (i + 1) / bands, Theme.alpha(0xFFC8D4E6, haze * 0.55f * k));
            }
        }
        if (tint > 0.005f) c.fill(0, 0, w, h, Theme.alpha(Theme.accent(), tint));
    }

    // ---------- custom crosshair ----------
    private static void crosshair(DrawContext c, MinecraftClient mc, int a1, int a2) {
        if (mc.currentScreen != null || !mc.options.getPerspective().isFirstPerson()) return;
        int cx = mc.getWindow().getScaledWidth() / 2, cy = mc.getWindow().getScaledHeight() / 2;
        int size = (int) Modules.CROSSHAIR.f("Size"), gap = (int) Modules.CROSSHAIR.f("Gap");
        int th = (int) Modules.CROSSHAIR.f("Thickness");
        int col = Modules.CROSSHAIR.b("Accent color") ? a1 : 0xFFFFFFFF;
        boolean outline = Modules.CROSSHAIR.b("Outline"), tshape = Modules.CROSSHAIR.b("T shape");
        int lo = th / 2, hi = th - lo;
        // left, right, down, (up unless T shape)
        int[][] bars = {
            {cx - gap - size, cy - lo, cx - gap, cy + hi},
            {cx + gap, cy - lo, cx + gap + size, cy + hi},
            {cx - lo, cy + gap, cx + hi, cy + gap + size},
            {cx - lo, cy - gap - size, cx + hi, cy - gap}
        };
        for (int i = 0; i < (tshape ? 3 : 4); i++) {
            int[] b = bars[i];
            if (outline) c.fill(b[0] - 1, b[1] - 1, b[2] + 1, b[3] + 1, 0xFF000000);
        }
        for (int i = 0; i < (tshape ? 3 : 4); i++) {
            int[] b = bars[i];
            c.fill(b[0], b[1], b[2], b[3], col);
        }
        if (Modules.CROSSHAIR.b("Dot")) {
            if (outline) c.fill(cx - 2, cy - 2, cx + 2, cy + 2, 0xFF000000);
            c.fill(cx - 1, cy - 1, cx + 1, cy + 1, col);
        }
    }

    // ---------- health indicator ----------
    private static void healthIndicator(DrawContext c, MinecraftClient mc, TextRenderer tr, int a1, int a2) {
        if (!(mc.targetedEntity instanceof LivingEntity le) || mc.currentScreen != null) return;
        float hp = le.getHealth(), max = Math.max(1f, le.getMaxHealth());
        int cx = mc.getWindow().getScaledWidth() / 2, cy = mc.getWindow().getScaledHeight() / 2 + 12;
        float k = Math.max(0f, Math.min(1f, hp / max));
        int col = Theme.mix(0xFFE5334B, 0xFF34D399, k);
        if (Modules.HEALTH_INDICATOR.idx("Style") == 1) {
            String t = (Modules.HEALTH_INDICATOR.b("Show name") ? le.getName().getString() + "  " : "")
                + String.format(Locale.ROOT, "%.1f", hp);
            c.drawCenteredTextWithShadow(tr, t, cx, cy, col);
            return;
        }
        int w = 44, x = cx - w / 2;
        if (Modules.HEALTH_INDICATOR.b("Show name")) c.drawCenteredTextWithShadow(tr, le.getName().getString(), cx, cy - 11, Theme.TEXT);
        c.fill(x - 1, cy - 1, x + w + 1, cy + 4, 0xC00B0B0E);
        int fw = (int) (w * k);
        Theme.gradBar(c, x, cy, fw, 3, a1, a2, w, 1f);
    }

    // ---------- mace helper ----------
    private static void maceHud(DrawContext c, MinecraftClient mc, TextRenderer tr) {
        if (mc.currentScreen != null || !mc.player.getMainHandStack().isOf(Items.MACE)) return;
        double fall = mc.player.fallDistance;
        double need = Modules.MACE_HELPER.f("Min fall");
        boolean ready = fall >= need && !mc.player.isOnGround();
        int cx = mc.getWindow().getScaledWidth() / 2, y = mc.getWindow().getScaledHeight() / 2 + 34;
        String t = ready ? "MACE READY" : String.format(Locale.ROOT, "Mace %.1f / %.1f", Math.min(fall, need), need);
        int w = tr.getWidth(t) + 14;
        Theme.card(c, cx - w / 2, y, w, 15, 5, 0xE60B0B0E, ready ? 0xFF34D399 : Theme.BORDER);
        c.drawCenteredTextWithShadow(tr, t, cx, y + 4, ready ? 0xFF34D399 : Theme.TEXT_DIM);
    }

    private Hud() {}
}
