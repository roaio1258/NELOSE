package nelose;

import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

/** Per-module keybinds: toggle or hold. */
public final class Binds {
    public static void tick(MinecraftClient mc) {
        long win = mc.getWindow().getHandle();
        boolean free = mc.currentScreen == null;
        for (Module m : Modules.ALL) {
            if (m.key < 0) continue;
            boolean down = GLFW.glfwGetKey(win, m.key) == GLFW.GLFW_PRESS;
            if (free) {
                if (m.bindMode == 0) { if (down && !m.keyWas) m.on = !m.on; }
                else m.on = down;
            }
            m.keyWas = down;
        }
    }

    public static String keyName(int key) {
        if (key < 0) return "None";
        if (key >= GLFW.GLFW_KEY_F1 && key <= GLFW.GLFW_KEY_F12) return "F" + (key - GLFW.GLFW_KEY_F1 + 1);
        return switch (key) {
            case GLFW.GLFW_KEY_SPACE -> "Space";
            case GLFW.GLFW_KEY_ENTER -> "Enter";
            case GLFW.GLFW_KEY_TAB -> "Tab";
            case GLFW.GLFW_KEY_LEFT_SHIFT -> "L-Shift";
            case GLFW.GLFW_KEY_RIGHT_SHIFT -> "R-Shift";
            case GLFW.GLFW_KEY_LEFT_CONTROL -> "L-Ctrl";
            case GLFW.GLFW_KEY_RIGHT_CONTROL -> "R-Ctrl";
            case GLFW.GLFW_KEY_LEFT_ALT -> "L-Alt";
            case GLFW.GLFW_KEY_RIGHT_ALT -> "R-Alt";
            case GLFW.GLFW_KEY_UP -> "Up";
            case GLFW.GLFW_KEY_DOWN -> "Down";
            case GLFW.GLFW_KEY_LEFT -> "Left";
            case GLFW.GLFW_KEY_RIGHT -> "Right";
            default -> {
                String n = GLFW.glfwGetKeyName(key, 0);
                yield n == null ? "Key " + key : n.toUpperCase();
            }
        };
    }

    private Binds() {}
}
