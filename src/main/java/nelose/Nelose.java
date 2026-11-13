package nelose;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import org.lwjgl.glfw.GLFW;

public class Nelose implements ClientModInitializer {
    public static final String NAME = "Nelose";
    private static KeyBinding openKey;

    @Override
    public void onInitializeClient() {
        Config.load();
        Accounts.load();
        ClientLifecycleEvents.CLIENT_STARTED.register(Accounts::applyOnStart);
        // TEST: splash hook disabled
        // ClientLifecycleEvents.CLIENT_STARTED.register(SplashHook::tryWrap);
        KeyBinding.Category cat = KeyBinding.Category.create(Identifier.of("nelose", "main"));
        openKey = KeyBindingHelper.registerKeyBinding(
            new KeyBinding("key.nelose.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, cat));

        Features.zoomKey = KeyBindingHelper.registerKeyBinding(
            new KeyBinding("key.nelose.zoom", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_C, cat));

        Features.pearlKey = KeyBindingHelper.registerKeyBinding(
            new KeyBinding("key.nelose.pearl", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, cat));
        Features.fastFirework = KeyBindingHelper.registerKeyBinding(
            new KeyBinding("key.nelose.firework", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, cat));
        Features.fastSnowball = KeyBindingHelper.registerKeyBinding(
            new KeyBinding("key.nelose.snowball", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, cat));
        Features.fastPotion = KeyBindingHelper.registerKeyBinding(
            new KeyBinding("key.nelose.potion", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, cat));

        // replace the vanilla main menu with ours
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (screen instanceof TitleScreen && Modules.BLUR.b("Title screen")) {
                // the cinematic NELOSEVISUAL intro plays once per launch, then the normal menu
                if (NeloseIntroScreen.shouldPlay()) client.setScreen(new NeloseIntroScreen());
                else client.setScreen(new NeloseTitleScreen());
            }
        });

        // hide the vanilla crosshair while the custom one is enabled
        HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR,
            prev -> (context, tick) -> { if (!Modules.CROSSHAIR.on) prev.render(context, tick); });

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClient()) Features.onAttack(entity);
            if (FakePlayer.isFake(entity)) return ActionResult.FAIL;   // client-only, nothing is sent
            return ActionResult.PASS;
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // SplashHook.tryWrap(client);
            Features.tick(client);
            Binds.tick(client);
            while (openKey.wasPressed()) {
                if (client.currentScreen == null) client.setScreen(new NeloseScreen());
            }
        });

        HudElementRegistry.attachElementAfter(
            VanillaHudElements.MISC_OVERLAYS,
            Identifier.of("nelose", "watermark"),
            (context, tickCounter) -> {
                Watermark.render(context);
                Hud.render(context, tickCounter.getTickProgress(true));
            });
    }
}
