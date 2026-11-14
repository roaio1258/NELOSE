package nelose;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Movable list of your active potion effects: name, level, time left and a timer bar. */
public final class EffectsHud {
    private static final class E {
        String name;
        int color, ticks, amp, max;
        boolean inf;
    }

    /** Per effect: {longest duration seen, last duration}, used for the timer bar. */
    private static final Map<String, int[]> MAX = new HashMap<>();
    private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
    private static final int ROW = 13, HEAD = 15;

    private static E fake(String name, int color, int ticks, int amp, boolean inf) {
        E e = new E();
        e.name = name; e.color = 0xFF000000 | color; e.ticks = ticks; e.amp = amp; e.inf = inf; e.max = Math.max(ticks, 1200);
        return e;
    }

    public static void render(DrawContext c, MinecraftClient mc, TextRenderer tr, int a1, int a2) {
        Module M = Modules.POTION_EFFECTS;
        List<E> list = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (StatusEffectInstance inst : mc.player.getStatusEffects()) {
            StatusEffect type = inst.getEffectType().value();
            E e = new E();
            e.name = type.getName().getString();
            e.color = 0xFF000000 | type.getColor();
            e.ticks = inst.getDuration();
            e.inf = inst.isInfinite();
            e.amp = inst.getAmplifier();
            int[] mm = MAX.computeIfAbsent(e.name, k -> new int[]{e.ticks, e.ticks});
            if (e.ticks > mm[1] + 20) mm[0] = e.ticks;   // applied again with a longer time
            mm[0] = Math.max(mm[0], e.ticks);
            mm[1] = e.ticks;
            e.max = Math.max(1, mm[0]);
            names.add(e.name);
            list.add(e);
        }
        MAX.keySet().retainAll(names);

        if (list.isEmpty()) {
            // preview while moving widgets
            if (!(mc.currentScreen instanceof NeloseScreen || mc.currentScreen instanceof ChatScreen)) return;
            list.add(fake("Speed", 0x33EBFF, 2640, 1, false));
            list.add(fake("Strength", 0xFF5D5D, 960, 0, false));
            list.add(fake("Regeneration", 0xCD5CAB, 140, 1, false));
            list.add(fake("Night Vision", 0x6C7BFF, 0, 0, true));
        }

        switch (M.idx("Sort")) {
            case 1 -> list.sort((p, q) -> p.name.compareTo(q.name));
            case 2 -> list.sort((p, q) -> q.amp - p.amp);
            default -> list.sort((p, q) -> Integer.compare(q.inf ? Integer.MAX_VALUE : q.ticks, p.inf ? Integer.MAX_VALUE : p.ticks));
        }
        int total = list.size();
        int rows = Math.min(total, (int) M.f("Max rows"));

        boolean header = M.b("Header"), level = M.b("Show level"), tint = M.b("Effect colors");
        boolean bar = M.b("Timer bar"), blink = M.b("Blink when ending");
        int style = M.idx("Style");
        float scale = M.f("Scale");

        String[] nm = new String[rows], tm = new String[rows];
        int nw = 0, tw = 0;
        for (int i = 0; i < rows; i++) {
            E e = list.get(i);
            nm[i] = e.name + (level && e.amp > 0 ? " " + (e.amp + 1 < ROMAN.length ? ROMAN[e.amp + 1] : String.valueOf(e.amp + 1)) : "");
            tm[i] = e.inf ? "inf" : time(e.ticks);
            nw = Math.max(nw, tr.getWidth(nm[i]));
            tw = Math.max(tw, tr.getWidth(tm[i]));
        }
        String title = "Effects", count = String.valueOf(total);
        int pad = 7;
        int w = Math.max(96, Math.max(pad * 2 + 8 + nw + 14 + tw, pad * 2 + tr.getWidth(title) + 16 + tr.getWidth(count)));
        int h = (header ? HEAD : 0) + rows * ROW + 6;

        int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
        int bw = Math.round(w * scale), bh = Math.round(h * scale);
        int x = M.idx("Side") == 0 ? sw - 6 - bw : 6;
        int y = sh / 2 - 30;

        long now = System.currentTimeMillis();
        HudLayout.begin(c, "effects", x, y, bw, bh);
        var mat = c.getMatrices();
        mat.pushMatrix();
        mat.translate((float) x, (float) y);
        mat.scale(scale, scale);

        float op = M.f("Opacity");
        if (style == 0) {
            Theme.card(c, 0, 0, w, h, 6, Theme.alpha(0xFF0B0B0E, op), Theme.alpha(Theme.mix(Theme.BORDER, a1, 0.25f), Math.min(1f, op + 0.1f)));
        }
        if (header) {
            c.drawTextWithShadow(tr, title, pad, 4, a1);
            c.drawText(tr, count, w - pad - tr.getWidth(count), 4, Theme.TEXT_DIM, false);
            c.fill(pad, HEAD - 3, w - pad, HEAD - 2, Theme.alpha(0xFFFFFFFF, 0.08f));
        }
        for (int i = 0; i < rows; i++) {
            E e = list.get(i);
            int ry = (header ? HEAD : 0) + 3 + i * ROW;
            float a = 1f;
            if (blink && !e.inf && e.ticks < 200) a = 0.45f + 0.55f * (float) Math.abs(Math.sin(now / 160.0));
            int chip = tint ? Theme.mix(e.color, 0xFFFFFFFF, 0.25f) : a1;
            int txt = tint ? Theme.mix(e.color, 0xFFFFFFFF, 0.45f) : Theme.TEXT;
            Theme.round(c, pad, ry + 2, 3, 9, 1, Theme.alpha(chip, a));
            c.drawTextWithShadow(tr, nm[i], pad + 8, ry + 3, Theme.alpha(txt, a));
            boolean low = !e.inf && e.ticks < 200;
            c.drawTextWithShadow(tr, tm[i], w - pad - tr.getWidth(tm[i]), ry + 3, Theme.alpha(low ? 0xFFFB7185 : Theme.TEXT, a));
            if (bar && !e.inf) {
                int bx = pad + 8, full = w - pad * 2 - 8;
                int len = Math.max(1, Math.round(full * Math.min(1f, e.ticks / (float) e.max)));
                c.fill(bx, ry + 12, bx + full, ry + 13, Theme.alpha(0xFFFFFFFF, 0.07f));
                c.fill(bx, ry + 12, bx + len, ry + 13, Theme.alpha(chip, 0.85f * a));
            }
        }
        mat.popMatrix();
        HudLayout.end(c);
    }

    private static String time(int ticks) {
        int s = Math.max(0, (ticks + 19) / 20);
        if (s >= 3600) return String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60);
        return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }

    private EffectsHud() {}
}
