package nelose;

import java.util.HashMap;
import java.util.Map;

/** Russian descriptions shown under every module card. */
public final class Ru {
    private static final Map<String, String> M = new HashMap<>();

    private static void a(String name, String ru) { M.put(name, ru); }

    static {
        // Visuals
        a("Fullbright", "Яркость: всё видно в темноте");
        a("Zoom", "Приближение по зажатой клавише C");
        a("Target HUD", "Здоровье моба, на которого смотришь");
        a("Armor HUD", "Броня и прочность на экране");
        a("Free Look", "Свободный обзор: камера крутится, игрок смотрит как раньше (зажми Alt)");
        a("Target ESP", "Анимация вокруг цели");
        a("Target box", "Рамка с полоской здоровья");
        a("Glass ESP", "Стеклянный бокс на цели");
        a("Atmosphere", "Снег, дымка и частицы в воздухе");
        a("Trajectory", "Куда упадёт бросок или выстрел");
        a("TNT Preview", "Названия и таймеры ближайшего TNT");
        a("Arrow Names", "Подписи на выпущенных стрелах");
        a("Custom Hitbox", "Хитбоксы игроков");
        a("Health Indicator", "Здоровье того, на кого целишься");
        a("Logo", "Логотип в ватермарке и меню");
        // HUD
        a("Watermark", "Ватермарк: стиль, цвет, размер, сервер (.wm текст), пресеты Glass/Neon/Classic");
        a("Keybinds", "Список модулей с назначенными клавишами");
        a("Inventory HUD", "Твой инвентарь на экране");
        a("Notifications", "Уведомление при включении и выключении модулей");
        a("Session", "Сколько времени ты играешь");
        a("Combo Counter", "Удары подряд, дальность и криты");
        a("Totem Warning", "Предупреждение, если нет тотема");
        a("Crit Indicator", "Показывает, когда следующий удар будет критом");
        a("Damage Popup", "Урон, который ты наносишь, всплывает у прицела");
        a("Potion Effects", "Список активных эффектов с таймерами");
        a("PvP Timer", "Красный таймер боя сверху экрана");
        a("FPS", "Кадры в секунду");
        a("Ping", "Задержка до сервера");
        a("Clock", "Реальное время");
        a("Coordinates", "Твои координаты XYZ");
        a("Direction", "Куда ты смотришь");
        a("Speed", "Скорость в блоках в секунду");
        a("CPS", "Клики в секунду");
        a("Custom Crosshair", "Свой прицел вместо обычного");
        a("Keystrokes", "WASD, прыжок и кнопки мыши");
        a("Module list", "Список включённых модулей");
        // Player
        a("Click Pearl", "Бросок жемчуга по клавише");
        a("Fast Use", "Клавиши для фейерверка, снежка и зелья");
        a("Mace Helper", "Показывает, когда готов удар булавой");
        a("Auto Clicker", "Зажми кнопку мыши, и он кликает сам");
        a("Kill Message", "Пишет в чат твой текст при убийстве (.killmsg текст)");
        a("Auto sprint", "Всегда бежишь вперёд");
        a("Auto respawn", "Пропуск экрана смерти");
        a("Low health glow", "Красные края при низком здоровье");
        a("Fake Player", "Манекен для проверки эффектов");
        a("Item counter", "Тотемы, яблоки, жемчуг, кристаллы");
        // BedWars
        a("Bed Finder", "Подписи с расстоянием на кроватях рядом");
        a("Resource ESP", "Подписи на выпавших ресурсах");
        a("Resource Counter", "Железо, золото, алмазы и изумруды в инвентаре");
        a("Block Counter", "Сколько блоков осталось для моста");
        a("Void Alert", "Предупреждение о падении в бездну");
        a("Player Radar", "Игроки рядом: расстояние и здоровье");
        a("Eagle", "Приседает на краю блока, отпускает при отходе");
        a("Auto Bridge", "Ставит блоки под ногами и строит мост");
        a("BW Quick Use", "Клавиши: огненный шар, яйцо, вода, блоки, меч, кирка");
        // Effects
        a("Hit particles", "Частицы при ударе по мобу");
        a("Kill effect", "Эффект, когда цель умирает");
        a("Jump circles", "Кольцо при прыжке");
        a("Trail", "Цветной след за тобой");
        a("Aura", "Спираль частиц вокруг тебя");
        a("Hit Sounds", "Звук при ударе");
        a("Hit marker", "Крестик на прицеле при попадании");
        // Theme
        a("Interface", "Затемнение, анимация открытия, главное меню");
    }

    /** Russian text for a module name, or an empty string. */
    public static String of(String name) { return M.getOrDefault(name, ""); }

    private Ru() {}
}
