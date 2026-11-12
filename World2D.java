package nelose;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Overlays that are anchored to things in the world: hitboxes, TNT, arrows, trajectory. */
public final class World2D {

    public static void render(DrawContext c, float delta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || !mc.options.getPerspective().isFirstPerson()) return;
        boolean any = Modules.CUSTOM_HITBOX.on || Modules.TNT_PREVIEW.on || Modules.ARROW_NAMES.on || Modules.TRAJECTORY.on;
        if (!any) return;
        Esp.setup(delta);
        TextRenderer tr = mc.textRenderer;

        if (Modules.CUSTOM_HITBOX.on) hitboxes(c, mc, delta);
        if (Modules.TNT_PREVIEW.on) tnt(c, mc, tr, delta);
        if (Modules.ARROW_NAMES.on) arrows(c, mc, tr, delta);
        if (Modules.TRAJECTORY.on) trajectory(c, mc, delta);
    }

    // ---------- Custom Hitbox ----------
    private static void hitboxes(DrawContext c, MinecraftClient mc, float delta) {
        double range = Modules.CUSTOM_HITBOX.f("Range");
        int th = (int) Modules.CUSTOM_HITBOX.f("Thickness");
        float fill = Modules.CUSTOM_HITBOX.f("Fill");
        int a1 = Theme.accent(), a2 = Theme.accent2();
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player || p.isInvisible() || mc.player.distanceTo(p) > range) continue;
            Vec3d pos = p.getLerpedPos(delta);
            double w = p.getWidth(), h = p.getHeight();
            int col = switch (Modules.CUSTOM_HITBOX.idx("Color")) {
                case 1 -> 0xFFFFFFFF;
                case 2 -> 0xFFE5334B;
                default -> a1;
            };
            Esp.wireBox(c, pos.x, pos.y, pos.z, w, h, th, Theme.alpha(col, 0.9f));
            if (fill > 0.01f) {
                double[] top = Esp.project(pos.x, pos.y + h, pos.z), bot = Esp.project(pos.x, pos.y, pos.z);
                if (top != null && bot != null) {
                    int hh = (int) Math.abs(bot[1] - top[1]);
                    int ww = (int) (hh * (w / h));
                    int x = (int) top[0] - ww / 2, y = (int) top[1];
                    if (ww > 1 && hh > 1 && hh < 2000) c.fill(x, y, x + ww, y + hh, Theme.alpha(col, fill));
                }
            }
        }
    }

    // ---------- TNT Preview ----------
    private static int tntColor(int fuse) {
        if (fuse <= 20) return 0xFFE5334B;      // about to explode
        if (fuse <= 50) return 0xFFFBBF24;
        return 0xFF34D399;                        // fresh
    }

    private static void tnt(DrawContext c, MinecraftClient mc, TextRenderer tr, float delta) {
        double range = Modules.TNT_PREVIEW.f("Range");
        List<TntEntity> list = new ArrayList<>();
        for (Entity e : mc.world.getEntities()) {
            if (e instanceof TntEntity t && mc.player.distanceTo(t) <= range) list.add(t);
        }
        list.sort((a, b) -> Float.compare(mc.player.distanceTo(a), mc.player.distanceTo(b)));
        for (int i = 0; i < list.size() && i < 12; i++) {
            TntEntity t = list.get(i);
            Vec3d p = t.getLerpedPos(delta);
            double[] s = Esp.project(p.x, p.y + t.getHeight() + 0.5, p.z);
            if (s == null) continue;
            int fuse = t.getFuse();
            String txt = "TNT " + String.format(Locale.ROOT, "%.1f", Math.max(0, fuse) / 20.0) + "s";
            int w = tr.getWidth(txt) + 8, x = (int) s[0] - w / 2, y = (int) s[1] - 6;
            Theme.card(c, x, y, w, 13, 4, 0xE60B0B0E, tntColor(fuse));
            c.drawTextWithShadow(tr, txt, x + 4, y + 3, tntColor(fuse));
        }
        if (!Modules.TNT_PREVIEW.b("HUD panel") || list.isEmpty()) return;
        int rows = Math.min(list.size(), 4);
        int w = 104, h = rows * 11 + 17;
        int x = mc.getWindow().getScaledWidth() - w - 8, y = mc.getWindow().getScaledHeight() / 2 - h / 2;
        Theme.card(c, x, y, w, h, 5, 0xE60B0B0E, Theme.BORDER);
        c.drawTextWithShadow(tr, "TNT nearby: " + list.size(), x + 7, y + 5, Theme.TEXT);
        for (int i = 0; i < rows; i++) {
            TntEntity t = list.get(i);
            String r = String.format(Locale.ROOT, "%.0fm  %.1fs", mc.player.distanceTo(t), Math.max(0, t.getFuse()) / 20.0);
            c.drawText(tr, r, x + 7, y + 17 + i * 11, tntColor(t.getFuse()), false);
        }
    }

    // ---------- Arrow Names ----------
    private static void arrows(DrawContext c, MinecraftClient mc, TextRenderer tr, float delta) {
        double range = Modules.ARROW_NAMES.f("Range");
        String kind = new String[]{"", "Arrow", "Dart", "Needle"}[Modules.ARROW_NAMES.idx("Label")];
        int a1 = Theme.accent();
        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof PersistentProjectileEntity a) || mc.player.distanceTo(a) > range) continue;
            Vec3d p = a.getLerpedPos(delta);
            double[] s = Esp.project(p.x, p.y + 0.3, p.z);
            if (s == null) continue;
            String txt;
            if (kind.isEmpty()) {
                Entity o = a.getOwner();
                txt = (o == null ? "Arrow" : o.getName().getString()) + "'s arrow";
            } else txt = kind;
            int w = tr.getWidth(txt) + 6, x = (int) s[0] - w / 2, y = (int) s[1] - 5;
            c.fill(x, y, x + w, y + 11, 0xA00B0B0E);
            c.fill(x, y + 10, x + w, y + 11, a1);
            c.drawTextWithShadow(tr, txt, x + 3, y + 2, Theme.TEXT);
        }
    }

    // ---------- Trajectory ----------
    /** {speed, gravity, drag}; null when the held item is not thrown or shot. */
    private static double[] ballistics(MinecraftClient mc, ItemStack st) {
        Item it = st.getItem();
        if (it == Items.BOW) {
            if (!mc.player.isUsingItem()) return null;
            float f = mc.player.getItemUseTime() / 20f;
            f = (f * f + f * 2f) / 3f;
            f = Math.min(1f, f);
            if (f < 0.1f) return null;
            return new double[]{f * 3.0, 0.05, 0.99};
        }
        if (it == Items.CROSSBOW) return new double[]{3.15, 0.05, 0.99};
        if (it == Items.TRIDENT) return new double[]{2.5, 0.05, 0.99};
        if (it == Items.ENDER_PEARL || it == Items.SNOWBALL || it == Items.EGG) return new double[]{1.5, 0.03, 0.99};
        if (it == Items.SPLASH_POTION || it == Items.LINGERING_POTION) return new double[]{0.5, 0.05, 0.99};
        if (it == Items.EXPERIENCE_BOTTLE) return new double[]{0.7, 0.07, 0.99};
        return null;
    }

    private static void trajectory(DrawContext c, MinecraftClient mc, float delta) {
        double[] b = ballistics(mc, mc.player.getMainHandStack());
        if (b == null) b = ballistics(mc, mc.player.getOffHandStack());
        if (b == null) return;

        double yaw = Math.toRadians(mc.player.getYaw(delta)), pit = Math.toRadians(mc.player.getPitch(delta));
        Vec3d dir = new Vec3d(-Math.sin(yaw) * Math.cos(pit), -Math.sin(pit), Math.cos(yaw) * Math.cos(pit));
        Vec3d pos = mc.player.getCameraPosVec(delta).add(0, -0.1, 0);
        Vec3d vel = dir.multiply(b[0]);
        int steps = (int) Modules.TRAJECTORY.f("Steps");
        int a1 = Theme.accent(), a2 = Theme.accent2();
        double[] prev = Esp.project(pos.x, pos.y, pos.z);
        Vec3d hit = null;
        for (int i = 0; i < steps; i++) {
            Vec3d next = pos.add(vel);
            var res = mc.world.raycast(new RaycastContext(pos, next, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, mc.player));
            boolean stop = res.getType() != HitResult.Type.MISS;
            if (stop) next = res.getPos();
            double[] cur = Esp.project(next.x, next.y, next.z);
            if (prev != null && cur != null) {
                Esp.line(c, prev[0], prev[1], cur[0], cur[1], 1, Theme.alpha(Theme.mix(a1, a2, i / (float) steps), 0.85f));
            }
            prev = cur;
            if (stop) { hit = next; break; }
            pos = next;
            vel = vel.multiply(b[2]).add(0, -b[1], 0);
            if (pos.y < mc.world.getBottomY() - 8) break;
        }
        if (hit != null && Modules.TRAJECTORY.b("Landing marker")) {
            double[] s = Esp.project(hit.x, hit.y, hit.z);
            if (s != null) {
                int x = (int) s[0], y = (int) s[1];
                c.fill(x - 3, y - 3, x + 4, y + 4, Theme.alpha(a1, 0.35f));
                c.fill(x - 1, y - 1, x + 2, y + 2, 0xFFFFFFFF);
                Esp.line(c, x - 6, y, x - 3, y, 1, 0xFFFFFFFF);
                Esp.line(c, x + 4, y, x + 7, y, 1, 0xFFFFFFFF);
                Esp.line(c, x, y - 6, x, y - 3, 1, 0xFFFFFFFF);
                Esp.line(c, x, y + 4, x, y + 7, 1, 0xFFFFFFFF);
            }
        }
    }

    private World2D() {}
}
