package nelose;

import net.minecraft.client.MinecraftClient;

/** Smooth zoom: the field of view is scaled every frame by a smoothed factor (see GameRendererMixin). */
public final class Zoom {
    /** True once the render hook has run, then the tick based fallback in Features is not used. */
    public static boolean hooked;
    private static double factor = 1.0;
    private static long last;

    /** Current FOV multiplier, advanced by real time so it is smooth at any frame rate. */
    public static double factor() {
        hooked = true;
        long now = System.nanoTime();
        double dt = last == 0 ? 0.016 : (now - last) / 1e9;
        if (dt >= 0.001) {   // the hand pass in the same frame must not advance it twice
            last = now;
            dt = Math.min(dt, 0.1);
            MinecraftClient mc = MinecraftClient.getInstance();
            boolean want = Modules.ZOOM.on && Features.zoomKey != null && Features.zoomKey.isPressed() && mc.currentScreen == null;
            double base = Math.max(1, mc.options.getFov().getValue());
            double goal = want ? Math.min(1.0, Modules.ZOOM.f("Zoom FOV") / base) : 1.0;
            double k = 1.0 - Math.exp(-dt * (6.0 + Modules.ZOOM.f("Smoothness") * 50.0));
            factor += (goal - factor) * k;
            if (Math.abs(goal - factor) < 0.0004) factor = goal;
        }
        return factor;
    }

    private Zoom() {}
}
