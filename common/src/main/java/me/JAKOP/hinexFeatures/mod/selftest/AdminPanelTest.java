package me.JAKOP.hinexFeatures.mod.selftest;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import me.JAKOP.hinexFeatures.mod.admin.AdminButton;
import me.JAKOP.hinexFeatures.mod.admin.Macro;
import me.JAKOP.hinexFeatures.mod.admin.MacroRunner;
import me.JAKOP.hinexFeatures.mod.admin.MacroScreen;
import me.JAKOP.hinexFeatures.mod.admin.MacroStore;
import me.JAKOP.hinexFeatures.mod.admin.QuickMenuScreen;
import me.JAKOP.hinexFeatures.mod.admin.WorkspaceClient;
import me.JAKOP.hinexFeatures.mod.admin.WorkspaceScreen;
import me.JAKOP.hinexFeatures.mod.net.ClientNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

final class AdminPanelTest {

    private static final Logger LOGGER = LoggerFactory.getLogger("HinexSelfTest");
    private static final int LEFT = 1;

    private static BlockPos base = BlockPos.ZERO;
    private static Macro macro;
    private static int guiScale;

    private AdminPanelTest() {
    }

    static void plan(VfxShowcase.Steps steps, Minecraft mc, BooleanSupplier joined) {
        steps.add("ap-joined", 20, 1200, () -> joined.getAsBoolean() && ClientNetwork.state().admin(), () -> {
            mc.options.pauseOnLostFocus = false;
            if (mc.player.isDeadOrDying()) mc.player.respawn();
            LOGGER.info("[selftest] adminpanel: admin={} (сервер считает игрока админом)", ClientNetwork.state().admin());
        });
        steps.add("ap-setup", 40, 0, () -> true, () -> {
            command(mc, "time set 1000");
            command(mc, "weather clear");
            command(mc, "gamemode creative");
            mc.gui.setScreen(null);
        });

        steps.add("ap-macro", 20, 0, () -> true, () -> {
            MacroStore.macros().removeIf(m -> m.id.equals("selftest"));
            macro = new Macro("selftest", "Объявление ивента");
            macro.steps.add(new Macro.Step("command", "say [макрос] шаг 1 — {me} стоит на {x} {y} {z}", 1.0));
            macro.steps.add(new Macro.Step("wait", "", 1.0));
            macro.steps.add(new Macro.Step("chat", "Ивент начинается через минуту!", 0.5));
            macro.steps.add(new Macro.Step("command", "time set 6000", 0.0));
            macro.quick = true;
            MacroStore.macros().addFirst(macro);
            MacroStore.save();
            MacroRunner.start(macro);
            LOGGER.info("[selftest] adminpanel: макрос запущен, шагов {}", macro.steps.size());
        });
        steps.add("ap-macro-hud", 12, 0, () -> true, () -> SelfTest.shot("ap-macro-running"));
        steps.add("ap-macro-done", 10, 400, () -> !MacroRunner.running(macro), () ->
                LOGGER.info("[selftest] adminpanel: макрос выполнен"));

        steps.add("ap-f7", 10, 0, () -> true, () -> {
            MacroScreen.preselect("selftest");
            mc.keyboardHandler.keyPress(mc.getWindow().handle(), 1, new KeyEvent(InputConstants.KEY_F7, 0x40000040, 0));
            mc.keyboardHandler.keyPress(mc.getWindow().handle(), 0, new KeyEvent(InputConstants.KEY_F7, 0x40000040, 0));
            LOGGER.info("[selftest] adminpanel: F7 → {}", screen(mc));
        });
        steps.add("ap-macros-shot", 15, 0, () -> true, () -> SelfTest.shot("ap-macros"));

        steps.add("ap-quick", 5, 0, () -> true, () -> {
            mc.gui.setScreen(null);
            mc.keyboardHandler.keyPress(mc.getWindow().handle(), 1, new KeyEvent(InputConstants.KEY_G, 'g', 0));
            LOGGER.info("[selftest] adminpanel: G → {}", screen(mc));
        });
        steps.add("ap-quick-shot", 10, 0, () -> true, () -> SelfTest.shot("ap-quick"));
        steps.add("ap-quick-close", 5, 0, () -> true, () -> {
            if (mc.gui.screen() instanceof QuickMenuScreen quick) quick.keyReleased(new KeyEvent(InputConstants.KEY_G, 'g', 0));
            LOGGER.info("[selftest] adminpanel: G отпущена → {}, макросов идёт: {}", screen(mc), MacroRunner.running(macro));
        });

        steps.add("ap-pause", 5, 0, () -> true, () -> mc.gui.setScreen(new PauseScreen(true)));
        steps.add("ap-pause-shot", 15, 0, () -> true, () -> {
            boolean found = false;
            for (GuiEventListener child : mc.gui.screen().children()) found |= child instanceof AdminButton;
            LOGGER.info("[selftest] adminpanel: кнопка панели в меню паузы {}", found ? "есть" : "НЕТ");
            SelfTest.shot("ap-pause");
        });

        steps.add("ap-ws-open", 5, 0, () -> true, () -> {
            base = mc.player.blockPosition().offset(3, 0, 0);
            mc.gui.setScreen(new WorkspaceScreen());
        });
        steps.add("ap-ws-loaded", 5, 400, WorkspaceClient::loaded, () -> {
            LOGGER.info("[selftest] adminpanel: схема загружена, блоков {}", WorkspaceClient.nodes().size());
            List<JsonObject> ops = new ArrayList<>();
            for (String id : List.copyOf(WorkspaceClient.nodes().keySet())) ops.add(op("remove", "id", id));
            if (!ops.isEmpty()) WorkspaceClient.edit(ops.toArray(JsonObject[]::new));
            JsonObject run = new JsonObject();
            run.addProperty("running", true);
            WorkspaceClient.send("run", run);
        });
        steps.add("ap-ws-build", 10, 0, () -> true, () -> {
            String bx = String.valueOf(base.getX());
            String by = String.valueOf(base.getY());
            String bz = String.valueOf(base.getZ());
            WorkspaceClient.edit(
                    node("btn", "button", 0, 0, "label", "Старт ивента"),
                    node("c1", "command", 150, -7, "mode", "impulse", "command", "say Кнопка на схеме нажата!"),
                    node("c2", "command", 320, -7, "mode", "chain", "command", "say ...и цепной блок после него"),
                    node("lever", "lever", 0, 90, "label", "Режим ивента"),
                    node("clk", "clock", 150, 90, "period", "20"),
                    node("cnt", "counter", 290, 90, "count", "3"),
                    node("c3", "command", 430, 83, "mode", "impulse", "command", "say Каждый третий тик таймера"),
                    node("wout", "world_out", 150, 170, "bx", bx, "by", by, "bz", bz, "block", "minecraft:redstone_block"),
                    node("win", "world_in", 0, 250, "bx", String.valueOf(base.getX() + 1), "by", by, "bz", bz),
                    node("and", "and", 150, 250),
                    node("c4", "command", 260, 243, "mode", "repeat", "interval", "40", "command", "say Редстоун в мире запитан"),
                    node("ev", "event", 0, 330, "event", "chat", "filter", "!свет"),
                    node("c5", "command", 150, 323, "mode", "impulse", "command", "effect give {player} minecraft:glowing 10 0 true"),
                    node("c6", "command", 320, 323, "mode", "chain", "command", "say {player} написал: {message}"),
                    node("c7", "command", 490, -7, "mode", "chain", "command", "tp НетТакого 0 100 0"),
                    node("note", "note", 450, 243, "text", "Тест: кнопка, рычаг, редстоун в мире, событие чата"));
            WorkspaceClient.edit(
                    wire("w1", "c1", 0, "c2", 0), wire("w11", "c2", 0, "c7", 0),
                    wire("w2", "lever", 0, "clk", 0), wire("w3", "clk", 0, "cnt", 0), wire("w4", "cnt", 0, "c3", 0),
                    wire("w5", "lever", 0, "wout", 0),
                    wire("w6", "win", 0, "and", 0), wire("w7", "lever", 0, "and", 1), wire("w8", "and", 0, "c4", 0),
                    wire("w9", "ev", 0, "c5", 0), wire("w10", "c5", 1, "c6", 0));
        });
        steps.add("ap-ws-fit", 10, 200, () -> WorkspaceClient.nodes().size() == 16, () -> {
            if (mc.gui.screen() instanceof WorkspaceScreen screen) screen.fit();
            LOGGER.info("[selftest] adminpanel: блоков {}, проводов {}", WorkspaceClient.nodes().size(), WorkspaceClient.wires().size());
        });
        steps.add("ap-ws-drag", 5, 0, () -> true, () -> {
            if (!(mc.gui.screen() instanceof WorkspaceScreen screen)) return;
            int[] from = screen.portOnScreen("btn", true, 0);
            int[] to = screen.portOnScreen("c1", false, 0);
            MouseButtonInfo left = new MouseButtonInfo(LEFT, 0);
            screen.mouseClicked(new MouseButtonEvent(from[0], from[1], left), false);
            screen.mouseDragged(new MouseButtonEvent((from[0] + to[0]) / 2.0, (from[1] + to[1]) / 2.0, left), 1, 1);
            screen.mouseDragged(new MouseButtonEvent(to[0], to[1], left), 1, 1);
            screen.mouseReleased(new MouseButtonEvent(to[0], to[1], left));
        });
        steps.add("ap-ws-wired", 10, 200, () -> hasWire("btn", "c1"), () ->
                LOGGER.info("[selftest] adminpanel: провод мышью создан, проводов {}", WorkspaceClient.wires().size()));
        steps.add("ap-ws-press", 5, 0, () -> true, () -> {
            if (!(mc.gui.screen() instanceof WorkspaceScreen screen)) return;
            WorkspaceClient.Node btn = WorkspaceClient.nodes().get("btn");
            int[] p = screen.toScreen(btn.x + 100 - 8.5F, btn.y + 36 - 8.5F);
            screen.mouseClicked(new MouseButtonEvent(p[0], p[1], new MouseButtonInfo(LEFT, 0)), false);
        });
        steps.add("ap-ws-lever", 10, 0, () -> true, () -> {
            JsonObject extra = new JsonObject();
            extra.addProperty("id", "lever");
            WorkspaceClient.send("toggle", extra);
        });
        steps.add("ap-ws-chat", 30, 0, () -> true, () -> mc.player.connection.sendChat("!свет в конце тоннеля"));
        steps.add("ap-ws-select", 60, 0, () -> true, () -> {
            if (mc.gui.screen() instanceof WorkspaceScreen screen) {
                screen.selectNode("c5");
                screen.setLogVisible(true);
            }
        });
        steps.add("ap-ws-shot", 10, 0, () -> true, () -> SelfTest.shot("ap-workspace"));
        steps.add("ap-ws-big", 5, 0, () -> true, () -> {
            guiScale = mc.options.guiScale().get();
            mc.options.guiScale().set(guiScale >= 3 ? guiScale - 1 : 1);
            mc.resizeGui();
            if (mc.gui.screen() instanceof WorkspaceScreen screen) screen.fit();
        });
        steps.add("ap-ws-big-shot", 10, 0, () -> true, () -> SelfTest.shot("ap-workspace-big"));
        steps.add("ap-ws-big-end", 5, 0, () -> true, () -> {
            mc.options.guiScale().set(guiScale);
            mc.resizeGui();
        });
        steps.add("ap-ws-report", 5, 0, () -> true, () -> {
            for (JsonObject entry : WorkspaceClient.log()) {
                LOGGER.info("[selftest] adminpanel log: {} {} /{} {}", entry.get("ok").getAsBoolean() ? "✓" : "✗",
                        entry.get("label").getAsString(), entry.get("command").getAsString(), entry.has("note") ? entry.get("note").getAsString() : "");
            }
            boolean block = mc.level.getBlockState(base).is(Blocks.REDSTONE_BLOCK);
            LOGGER.info("[selftest] adminpanel: блок редстоуна в мире {}, светится {}, датчик {}, and {}", block ? "стоит" : "НЕТ",
                    mc.player.hasEffect(net.minecraft.world.effect.MobEffects.GLOWING), WorkspaceClient.level("win", 0),
                    WorkspaceClient.level("and", 0));
        });
        steps.add("ap-world", 5, 0, () -> true, () -> {
            mc.gui.setScreen(null);
            mc.player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, net.minecraft.world.phys.Vec3.atCenterOf(base));
        });
        steps.add("ap-world-shot", 20, 0, () -> true, () -> SelfTest.shot("ap-world"));
        steps.add("ap-lever-off", 5, 0, () -> true, () -> {
            JsonObject extra = new JsonObject();
            extra.addProperty("id", "lever");
            WorkspaceClient.send("toggle", extra);
        });
        steps.add("ap-end", 30, 0, () -> true, () -> LOGGER.info("[selftest] adminpanel: рычаг выключен, блок в мире {}",
                mc.level.getBlockState(base).is(Blocks.REDSTONE_BLOCK) ? "ОСТАЛСЯ" : "убран"));
    }

    private static boolean hasWire(String from, String to) {
        for (WorkspaceClient.Wire wire : WorkspaceClient.wires().values()) {
            if (wire.from().equals(from) && wire.to().equals(to)) return true;
        }
        return false;
    }

    private static JsonObject op(String kind, String key, String value) {
        JsonObject op = new JsonObject();
        op.addProperty("op", kind);
        op.addProperty(key, value);
        return op;
    }

    private static JsonObject node(String id, String type, int x, int y, String... props) {
        JsonObject node = new JsonObject();
        node.addProperty("id", id);
        node.addProperty("type", type);
        node.addProperty("x", x);
        node.addProperty("y", y);
        JsonObject values = new JsonObject();
        for (int i = 0; i + 1 < props.length; i += 2) values.addProperty(props[i], props[i + 1]);
        node.add("props", values);
        JsonObject op = new JsonObject();
        op.addProperty("op", "add");
        op.add("node", node);
        return op;
    }

    private static JsonObject wire(String id, String from, int fromPort, String to, int toPort) {
        JsonObject op = new JsonObject();
        op.addProperty("op", "connect");
        op.addProperty("id", id);
        op.addProperty("from", from);
        op.addProperty("fromPort", fromPort);
        op.addProperty("to", to);
        op.addProperty("toPort", toPort);
        return op;
    }

    private static String screen(Minecraft mc) {
        return mc.gui.screen() == null ? "нет экрана" : mc.gui.screen().getClass().getSimpleName();
    }

    private static void command(Minecraft mc, String command) {
        if (mc.player != null) mc.player.connection.sendCommand(command);
    }
}
