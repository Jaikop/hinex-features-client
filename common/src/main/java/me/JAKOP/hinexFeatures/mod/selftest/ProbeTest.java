package me.JAKOP.hinexFeatures.mod.selftest;

import me.JAKOP.hinexFeatures.mod.HinexFeaturesClient;
import me.JAKOP.hinexFeatures.mod.marks.PlayerMarks;
import me.JAKOP.hinexFeatures.mod.net.ClientNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import me.JAKOP.hinexFeatures.mod.plus.PlusScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

final class ProbeTest {

    private static final Logger LOGGER = LoggerFactory.getLogger("HinexSelfTest");

    private static int processed;
    private static int waitTicks;
    private static int tabRelease = -1;
    private static int useRelease = -1;
    private static int forwardRelease = -1;
    private static int jumpRelease = -1;
    private static String tabShot;
    private static String lastState = "";
    private static boolean done;

    private ProbeTest() {
    }

    static void plan(VfxShowcase.Steps steps, Minecraft mc, BooleanSupplier joined, String connect) {
        steps.add("probe-joined", 20, 1200, joined, () -> {
            mc.options.pauseOnLostFocus = false;
            if (mc.player.isDeadOrDying()) mc.player.respawn();
            LOGGER.info("[selftest] probe: {} на сервере", mc.getUser().getName());
            try {
                Files.writeString(control(mc), "", StandardCharsets.UTF_8);
            } catch (IOException e) {
                LOGGER.warn("[selftest] probe: не создать файл управления", e);
            }
            HinexFeaturesClient.addTickListener(client -> tick(client, connect));
        });
        steps.add("probe-done", 1, 0, () -> done, () -> {
        });
    }

    private static Path control(Minecraft mc) {
        return mc.gameDirectory.toPath().resolve("selftest-control.txt");
    }

    private static void tick(Minecraft mc, String connect) {
        if (done) return;
        if (tabRelease >= 0 && --tabRelease < 0) {
            SelfTest.shot(tabShot);
            mc.options.keyPlayerList.setDown(false);
        }
        if (useRelease >= 0 && --useRelease < 0) mc.options.keyUse.setDown(false);
        if (forwardRelease >= 0 && --forwardRelease < 0) mc.options.keyUp.setDown(false);
        if (jumpRelease >= 0 && --jumpRelease < 0) mc.options.keyJump.setDown(false);
        logState(mc, false);
        if (waitTicks > 0) {
            waitTicks--;
            return;
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(control(mc), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return;
        }
        if (processed >= lines.size()) return;
        String line = lines.get(processed++).strip();
        if (line.isEmpty()) return;
        LOGGER.info("[selftest] probe> {}", line);
        int space = line.indexOf(' ');
        String op = space < 0 ? line : line.substring(0, space);
        String arg = space < 0 ? "" : line.substring(space + 1);
        try {
            switch (op) {
                case "cmd" -> {
                    if (mc.player != null) mc.player.connection.sendCommand(arg);
                }
                case "chat" -> {
                    if (mc.player != null) mc.player.connection.sendChat(arg);
                }
                case "open" -> mc.gui.setScreen(new ChatScreen(arg, false));
                case "pause" -> mc.gui.setScreen(new PauseScreen(true));
                case "plusmenu" -> mc.gui.setScreen(new PlusScreen(new PauseScreen(true)));
                case "click" -> {
                    String[] xy = arg.split("\\s+");
                    if (mc.gui.screen() != null) {
                        mc.gui.screen().mouseClicked(new MouseButtonEvent(Double.parseDouble(xy[0]), Double.parseDouble(xy[1]),
                                new MouseButtonInfo(1, 0)), false);
                    }
                }
                case "drag" -> {
                    String[] p = arg.split("\\s+");
                    var screen = mc.gui.screen();
                    if (screen != null) {
                        MouseButtonInfo left = new MouseButtonInfo(1, 0);
                        double x1 = Double.parseDouble(p[0]);
                        double y1 = Double.parseDouble(p[1]);
                        double x2 = Double.parseDouble(p[2]);
                        double y2 = Double.parseDouble(p[3]);
                        screen.mouseClicked(new MouseButtonEvent(x1, y1, left), false);
                        screen.mouseDragged(new MouseButtonEvent(x2, y2, left), x2 - x1, y2 - y1);
                        screen.mouseReleased(new MouseButtonEvent(x2, y2, left));
                    }
                }
                case "type" -> {
                    if (mc.gui.screen() != null) arg.codePoints().forEach(cp -> mc.gui.screen().charTyped(new net.minecraft.client.input.CharacterEvent(cp)));
                }
                case "key" -> {
                    if (mc.gui.screen() != null) mc.gui.screen().keyPressed(new net.minecraft.client.input.KeyEvent(Integer.parseInt(arg), 0, 0));
                }
                case "health" -> {
                    if (mc.player != null) {
                        LOGGER.info("[selftest] probe health: {} effects={}", mc.player.getHealth(),
                                mc.player.getActiveEffects().stream().map(e -> e.getEffect().getRegisteredName() + ":" + e.getDuration()).toList());
                    }
                }
                case "tree" -> me.JAKOP.hinexFeatures.mod.essence.EssenceClient.open(new PauseScreen(true));
                case "useitem" -> {
                    if (mc.player != null) mc.gameMode.useItem(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND);
                }
                case "useblock" -> {
                    LOGGER.info("[selftest] probe hit: {}", mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult b
                            ? b.getBlockPos() + " " + b.getDirection() + " " + b.getLocation() : mc.hitResult == null ? "null" : mc.hitResult.getType());
                    if (mc.player != null && mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit) {
                        mc.gameMode.useItemOn(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
                    }
                }
                case "useentity" -> {
                    if (mc.player != null && mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult hit) {
                        mc.gameMode.interact(mc.player, hit.getEntity(), hit, net.minecraft.world.InteractionHand.MAIN_HAND);
                    } else {
                        LOGGER.warn("[selftest] probe: под прицелом нет сущности ({})", mc.hitResult == null ? null : mc.hitResult.getType());
                    }
                }
                case "attack" -> {
                    int times = arg.isEmpty() ? 1 : Integer.parseInt(arg);
                    for (int i = 0; i < times && mc.player != null && mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult hit; i++) {
                        mc.gameMode.attack(mc.player, hit.getEntity());
                    }
                }
                case "breakblock" -> {
                    if (mc.player != null && mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit) {
                        mc.gameMode.startDestroyBlock(hit.getBlockPos(), hit.getDirection());
                    }
                }
                case "hotbar" -> {
                    if (mc.player != null) mc.player.getInventory().setSelectedSlot(Integer.parseInt(arg));
                }
                case "lookat" -> {
                    String[] p = arg.split("\\s+");
                    mc.player.lookAt(EntityAnchorArgument.Anchor.EYES, new net.minecraft.world.phys.Vec3(
                            Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2])));
                }
                case "slotclick" -> {
                    String[] p = arg.split("\\s+");
                    net.minecraft.world.inventory.ContainerInput type = p.length > 2 && p[2].equals("quick")
                            ? net.minecraft.world.inventory.ContainerInput.QUICK_MOVE : net.minecraft.world.inventory.ContainerInput.PICKUP;
                    mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, Integer.parseInt(p[0]),
                            Integer.parseInt(p[1]), type, mc.player);
                }
                case "slots" -> {
                    String[] p = arg.split("\\s+");
                    var menu = mc.player.containerMenu;
                    StringBuilder out = new StringBuilder();
                    for (int i = Integer.parseInt(p[0]); i <= Integer.parseInt(p[1]) && i < menu.slots.size(); i++) {
                        var stack = menu.getSlot(i).getItem();
                        if (!stack.isEmpty()) out.append(i).append('=').append(stack.getCount()).append('×').append(stack.getHoverName().getString()).append("; ");
                    }
                    LOGGER.info("[selftest] probe slots: {}", out);
                }
                case "guiscale" -> {
                    if (mc.gui.screen() != null) LOGGER.info("[selftest] probe: экран {}×{}", mc.gui.screen().width, mc.gui.screen().height);
                }
                case "close" -> {
                    if (mc.player != null && mc.player.containerMenu != mc.player.inventoryMenu) mc.player.closeContainer();
                    mc.gui.setScreen(null);
                }
                case "usehold" -> {
                    mc.options.keyUse.setDown(true);
                    useRelease = Integer.parseInt(arg);
                }
                case "forward" -> {
                    mc.options.keyUp.setDown(true);
                    forwardRelease = Integer.parseInt(arg);
                }
                case "pos" -> {
                    if (mc.player != null) {
                        LOGGER.info("[selftest] probe pos: x={} y={} z={} yaw={} border={} gliding={} cooldown={}", String.format("%.1f", mc.player.getX()),
                                String.format("%.1f", mc.player.getY()), String.format("%.1f", mc.player.getZ()), String.format("%.1f", mc.player.getYRot()),
                                String.format("%.1f", mc.level.getWorldBorder().getDistanceToBorder(mc.player)),
                                mc.player.isFallFlying(), mc.player.getCooldowns().isOnCooldown(mc.player.getMainHandItem()));
                    }
                }
                case "yaw" -> {
                    if (mc.player != null) {
                        String[] p = arg.split("\\s+");
                        mc.player.setYRot(Float.parseFloat(p[0]));
                        mc.player.setXRot(p.length > 1 ? Float.parseFloat(p[1]) : 0.0F);
                    }
                }
                case "jump" -> {
                    mc.options.keyJump.setDown(true);
                    jumpRelease = Integer.parseInt(arg);
                }
                case "shot" -> SelfTest.shot(arg);
                case "tabshot" -> {
                    mc.gui.setScreen(null);
                    mc.options.keyPlayerList.setDown(true);
                    tabShot = arg;
                    tabRelease = 6;
                }
                case "look" -> {
                    if (mc.level == null) break;
                    for (Player player : mc.level.players()) {
                        if (player.getName().getString().equalsIgnoreCase(arg)) {
                            mc.player.lookAt(EntityAnchorArgument.Anchor.EYES, player.position().add(0, player.getBbHeight() + 0.3, 0));
                        }
                    }
                }
                case "state" -> logState(mc, true);
                case "wait" -> waitTicks = Integer.parseInt(arg);
                case "reconnect" -> {
                    if (mc.level != null) mc.disconnectFromWorld(net.minecraft.network.chat.Component.literal("probe reconnect"));
                    ConnectScreen.startConnecting(new TitleScreen(), mc, ServerAddress.parseString(connect),
                            new ServerData("test", connect, ServerData.Type.OTHER), false, null);
                }
                case "quit" -> done = true;
                default -> LOGGER.warn("[selftest] probe: неизвестная команда {}", op);
            }
        } catch (RuntimeException e) {
            LOGGER.error("[selftest] probe: {} FAILED", line, e);
        }
    }

    private static void logState(Minecraft mc, boolean force) {
        StringBuilder state = new StringBuilder();
        if (mc.gui.screen() instanceof DisconnectedScreen screen) {
            state.append("DISCONNECTED: ").append(screen.getNarrationMessage().getString().replace('\n', ' '));
        } else {
            ClientPacketListener connection = mc.getConnection();
            if (connection == null || mc.level == null) {
                state.append("offline");
            } else {
                List<String> listed = new ArrayList<>();
                for (PlayerInfo info : connection.getListedOnlinePlayers()) {
                    String display = info.getTabListDisplayName() == null ? "" : "=" + info.getTabListDisplayName().getString();
                    listed.add(info.getProfile().name() + display);
                }
                List<String> known = connection.getOnlinePlayers().stream().map(info -> info.getProfile().name()).sorted().toList();
                List<String> suggest = connection.getSuggestionsProvider().getOnlinePlayerNames().stream().sorted().toList();
                List<String> models = new ArrayList<>();
                List<String> marks = new ArrayList<>();
                for (Player player : mc.level.players()) {
                    if (player != mc.player) models.add(player.getName().getString());
                    PlayerMarks.Mark mark = PlayerMarks.get(player.getUUID());
                    if (mark != null) marks.add(player.getName().getString() + ":" + mark.symbol());
                }
                listed.sort(String::compareTo);
                models.sort(String::compareTo);
                state.append("tab=").append(listed).append(" known=").append(known).append(" suggest=").append(suggest)
                        .append(" models=").append(models).append(" marks=").append(marks)
                        .append(" mod=").append(ClientNetwork.state() != null);
            }
        }
        String now = state.toString();
        if (force || !now.equals(lastState)) {
            lastState = now;
            LOGGER.info("[selftest] probe state: {}", now);
        }
    }
}
