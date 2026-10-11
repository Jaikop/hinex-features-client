package me.JAKOP.hinexFeatures.mod.selftest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import me.JAKOP.hinexFeatures.mod.HinexFeaturesClient;
import me.JAKOP.hinexFeatures.mod.dialog.DialogAnswerScreen;
import me.JAKOP.hinexFeatures.mod.dialog.DialogClient;
import me.JAKOP.hinexFeatures.mod.dialog.DialogHistory;
import me.JAKOP.hinexFeatures.mod.dialog.DialogSpec;
import me.JAKOP.hinexFeatures.mod.dialog.HistoryButton;
import me.JAKOP.hinexFeatures.mod.dialog.HistoryScreen;
import me.JAKOP.hinexFeatures.mod.dialog.editor.DialogEditorScreen;
import me.JAKOP.hinexFeatures.mod.vfx.VfxClock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;

final class DialogTest {

    private static final Logger LOGGER = LoggerFactory.getLogger("HinexSelfTest");

    private static final String[] STYLES = {
            """
            {"title":"Хроники","text":"<gradient:#ffd27a:#ff9a3c>Глава I.</gradient> <i>Пепел падал третий день, и город молчал.</i>",
             "borderStyle":"ornate","position":"top","titleStyle":"inline","icon":"minecraft:textures/item/writable_book.png",
             "iconSize":32,"window":"fade","replace":true}""",
            """
            {"title":"Голос из бездны","text":"<glitch>ТЫ НЕ ДОЛЖЕН БЫЛ</glitch> <shake:1.5>этого видеть</shake>…",
             "borderStyle":"glow","border":"#b46cff","background":"#0b0714","titleColor":"#d8b4ff","textColor":"#e8dcff",
             "window":"shake","appear":"words","position":"center","replace":true}""",
            """
            {"title":"Система","text":"<pulse>ЗАГРУЗКА ПРОТОКОЛА…</pulse> <blink>▮</blink><br>Доступ: <#55ff55>разрешён</#55ff55>",
             "borderStyle":"pixel","border":"#55ff55","background":"#001a05","titleColor":"#55ff55","textColor":"#aaffaa",
             "voice":"robot","window":"pop","replace":true}""",
            """
            {"title":"Письмо","text":"Дорогой друг, <glow>приходи к маяку</glow> на закате. <br><i>— твой М.</i>",
             "borderStyle":"double","background":"#f2e3c2","background2":"#d8c49a","opacity":0.96,"border":"#5e432a",
             "titleColor":"#5e432a","textColor":"#3a2a1a","width":230,"replace":true}"""};

    private static boolean recording;
    private static String prefix = "";
    private static int frame;
    private static int frames;
    private static int nextFrame;

    private DialogTest() {
    }

    static void plan(VfxShowcase.Steps steps, Minecraft mc, BooleanSupplier joined) {
        steps.add("dl-joined", 20, 1200, joined, () -> {
            mc.options.pauseOnLostFocus = false;
            if (mc.player.isDeadOrDying()) mc.player.respawn();
            VfxClock.setFixed(true);
            HinexFeaturesClient.addTickListener(DialogTest::tick);
        });
        steps.add("dl-setup", 100, 0, () -> true, () -> {
            command(mc, "time set 13200");
            command(mc, "weather clear");
            mc.player.setXRot(-6.0F);
            mc.gui.setScreen(null);
        });

        steps.add("dl-choice", 20, 0, () -> true, () -> {
            command(mc, "dialog say @s title=Старейшина icon=minecraft:textures/item/totem_of_undying.png "
                    + "answers=Помогу|Некогда|Подробнее Путник! <shake>Тьма</shake> у ворот, и только "
                    + "<#ffd27a><wave>древний свет</wave></#ffd27a> спасёт нас.");
            record("dl-choice", 44);
        });
        steps.add("dl-choice-screen", 10, 600, () -> !recording && mc.gui.screen() instanceof DialogAnswerScreen, () -> {
            LOGGER.info("[selftest] dialog: окно ответа открыто");
            SelfTest.shot("dl-answer-choice");
        });
        steps.add("dl-choice-key", 10, 0, () -> true, () -> {
            if (mc.gui.screen() instanceof DialogAnswerScreen screen) screen.keyPressed(new KeyEvent(InputConstants.KEY_2, 0, 0));
        });

        steps.add("dl-text", 60, 0, () -> mc.gui.screen() == null, () -> {
            JsonObject action = new JsonObject();
            action.addProperty("action", "send");
            action.addProperty("targets", "@s");
            action.add("dialog", JsonParser.parseString("""
                    {"title":"Страж","text":"<b>Стой!</b> Кто идёт? Назови <rainbow>пароль</rainbow>.",
                     "answers":{"type":"text","placeholder":"Назови пароль","inputStyle":"line","inputWidth":70,"inputHeight":18,
                     "inputBackground":"#1a1208","inputOpacity":0.6,"inputBorder":"#ffd27a","inputColor":"#ffe9b0",
                     "maxLength":40,"hintText":"↵ — сказать пароль"}}""").getAsJsonObject());
            DialogClient.editorAction(action);
        });
        steps.add("dl-text-screen", 10, 600, () -> mc.gui.screen() instanceof DialogAnswerScreen, () -> {
            for (GuiEventListener child : mc.gui.screen().children()) {
                if (child instanceof EditBox box) box.setValue("Свет во тьме");
            }
        });
        steps.add("dl-text-shot", 4, 0, () -> true, () -> SelfTest.shot("dl-answer-text"));
        steps.add("dl-text-send", 10, 0, () -> true, () -> {
            if (mc.gui.screen() instanceof DialogAnswerScreen screen) screen.keyPressed(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
        });

        for (int i = 0; i < STYLES.length; i++) {
            String json = STYLES[i];
            int index = i;
            steps.add("dl-style-" + i, 50, 0, () -> mc.gui.screen() == null, () -> {
                JsonObject action = new JsonObject();
                action.addProperty("action", "send");
                action.addProperty("targets", "@s");
                action.add("dialog", JsonParser.parseString(json).getAsJsonObject());
                DialogClient.editorAction(action);
            });
            steps.add("dl-style-" + i + "-shot", 70, 0, () -> true, () -> SelfTest.shot("dl-style-" + index));
        }

        steps.add("dl-template", 40, 0, () -> true, () -> {
            JsonObject action = new JsonObject();
            action.addProperty("action", "save");
            action.addProperty("name", "Пролог: старейшина");
            action.add("dialog", DialogSpec.example());
            DialogClient.editorAction(action);
        });
        steps.add("dl-editor", 20, 0, () -> true, () -> command(mc, "dialog"));
        steps.add("dl-editor-open", 10, 400, () -> mc.gui.screen() instanceof DialogEditorScreen, () -> LOGGER.info("[selftest] dialog: редактор открыт"));
        steps.add("dl-editor-shot", 40, 0, () -> true, () -> SelfTest.shot("dl-editor"));
        steps.add("dl-editor-scroll", 5, 0, () -> true, () -> {
            if (mc.gui.screen() instanceof DialogEditorScreen editor) editor.mouseScrolled(60, editor.height - 20, 0, -30);
        });
        String[][] toggles = {{"встать в очередь за текущим", "заменить текущее окно"}, {"без ответа", "варианты 1–5", "свой ответ"}};
        for (String[] labels : toggles) {
            steps.add("dl-editor-click " + labels[0], 5, 0, () -> true, () -> clickButton(mc, labels));
        }
        steps.add("dl-editor-clicked-shot", 5, 0, () -> true, () -> SelfTest.shot("dl-editor-toggles"));
        String[] tabs = {"templates", "sent", "answers"};
        for (int i = 0; i < tabs.length; i++) {
            int index = i + 1;
            steps.add("dl-editor-" + tabs[i], 10, 0, () -> true, () -> {
                if (mc.gui.screen() instanceof DialogEditorScreen editor) editor.showTab(index);
            });
            steps.add("dl-editor-" + tabs[i] + "-shot", 10, 0, () -> true, () -> SelfTest.shot("dl-editor-" + tabs[index - 1]));
        }
        steps.add("dl-editor-back", 5, 0, () -> true, () -> {
            if (mc.gui.screen() instanceof DialogEditorScreen editor) editor.showTab(0);
        });

        steps.add("dl-pause", 10, 0, () -> true, () -> mc.gui.setScreen(new PauseScreen(true)));
        steps.add("dl-pause-shot", 20, 0, () -> true, () -> SelfTest.shot("dl-pause"));
        steps.add("dl-history-open", 5, 0, () -> true, () -> {
            for (GuiEventListener child : mc.gui.screen().children()) {
                if (child instanceof HistoryButton button) {
                    mc.gui.screen().mouseClicked(new MouseButtonEvent(button.getX() + 10, button.getY() + 10, new MouseButtonInfo(1, 0)), false);
                    return;
                }
            }
            LOGGER.error("[selftest] dialog: кнопки истории в меню паузы нет");
        });
        steps.add("dl-history-shot", 20, 0, () -> true, () -> {
            LOGGER.info("[selftest] dialog: экран {}, в истории {} записей", mc.gui.screen() == null ? "-" : mc.gui.screen().getClass().getSimpleName(),
                    DialogHistory.entries().size());
            SelfTest.shot("dl-history");
        });
        steps.add("dl-history-search", 5, 0, () -> true, () -> {
            if (mc.gui.screen() instanceof HistoryScreen history) history.search("свет");
        });
        steps.add("dl-history-search-shot", 10, 0, () -> true, () -> SelfTest.shot("dl-history-search"));
        steps.add("dl-history-reset", 5, 0, () -> true, () -> {
            if (mc.gui.screen() instanceof HistoryScreen history) history.search("");
        });
        for (String label : new String[]{"Кто говорит", "Сегодня", "С ответом", "Когда"}) {
            steps.add("dl-history-click " + label, 6, 0, () -> true, () -> {
                if (!(mc.gui.screen() instanceof HistoryScreen history)) return;
                int[] point = history.find(label);
                if (point == null) {
                    LOGGER.error("[selftest] dialog history: «{}» не видно в боковой панели", label);
                    return;
                }
                boolean handled = history.mouseClicked(new MouseButtonEvent(point[0], point[1], new MouseButtonInfo(1, 0)), false);
                LOGGER.info("[selftest] dialog history: клик «{}» {}, в ленте {}", label, handled ? "принят" : "НЕ принят", history.found());
            });
        }
        steps.add("dl-history-filters-shot", 6, 0, () -> true, () -> SelfTest.shot("dl-history-filters"));
        steps.add("dl-end", 10, 0, () -> true, () -> {
            for (DialogHistory.Entry entry : DialogHistory.entries()) {
                LOGGER.info("[selftest] dialog history: «{}» ответ={}", entry.view().title.getString(), entry.answer);
            }
            mc.gui.setScreen(null);
            VfxClock.setFixed(false);
        });
    }

    private static void clickButton(Minecraft mc, String... labels) {
        List<String> names = List.of(labels);
        for (GuiEventListener child : mc.gui.screen().children()) {
            if (child instanceof net.minecraft.client.gui.components.AbstractWidget widget && names.contains(widget.getMessage().getString())) {
                String label = widget.getMessage().getString();
                double x = widget.getX() + widget.getWidth() / 2.0;
                double y = widget.getY() + widget.getHeight() / 2.0;
                boolean handled = mc.gui.screen().mouseClicked(new MouseButtonEvent(x, y, new MouseButtonInfo(1, 0)), false);
                LOGGER.info("[selftest] dialog editor: «{}» (visible={}, active={}) → клик {}, теперь «{}»", label, widget.visible,
                        widget.active, handled ? "принят" : "НЕ принят", widget.getMessage().getString());
                return;
            }
        }
        LOGGER.error("[selftest] dialog editor: кнопки «{}» нет среди виджетов экрана", labels[0]);
    }

    private static void record(String name, int count) {
        prefix = name;
        frame = 0;
        frames = count;
        recording = true;
        nextFrame = VfxClock.frames() + 2;
    }

    private static void tick(Minecraft mc) {
        if (!recording) {
            VfxClock.advance(0.05);
            return;
        }
        if (VfxClock.frames() < nextFrame) return;
        SelfTest.shot(String.format(Locale.ROOT, "%s-%03d", prefix, frame++));
        VfxClock.advance(1.0 / 12.0);
        nextFrame = VfxClock.frames() + 2;
        if (frame >= frames) recording = false;
    }

    private static void command(Minecraft mc, String command) {
        if (mc.player != null) mc.player.connection.sendCommand(command);
    }
}
