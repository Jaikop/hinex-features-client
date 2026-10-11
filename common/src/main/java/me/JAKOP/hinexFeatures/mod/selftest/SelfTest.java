package me.JAKOP.hinexFeatures.mod.selftest;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import me.JAKOP.hinexFeatures.mod.HinexFeaturesClient;
import me.JAKOP.hinexFeatures.mod.dossier.ClientDossier;
import me.JAKOP.hinexFeatures.mod.gate.Gate;
import me.JAKOP.hinexFeatures.mod.gate.GateScreen;
import me.JAKOP.hinexFeatures.mod.update.BlockedMods;
import me.JAKOP.hinexFeatures.mod.update.Updater;
import me.JAKOP.hinexFeatures.mod.mixin.chat.ChatScreenAccessor;
import me.JAKOP.hinexFeatures.mod.net.ClientNetwork;
import me.JAKOP.hinexFeatures.mod.net.Protocol;
import me.JAKOP.hinexFeatures.mod.net.ServerState;
import me.JAKOP.hinexFeatures.mod.platform.Platform;
import me.JAKOP.hinexFeatures.mod.typing.TypingIndicators;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.MixinEnvironment;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;

public final class SelfTest {

    private static final Logger LOGGER = LoggerFactory.getLogger("HinexSelfTest");

    private record Step(String name, int delay, int timeout, BooleanSupplier waitFor, Runnable action) {
    }

    private static long typeUntil;
    private static int typeTicks;

    private final Deque<Step> steps = new ArrayDeque<>();
    private int wait;
    private int waiting;
    private boolean audited;
    private boolean started;

    public static void start() {
        SelfTest test = new SelfTest();
        HinexFeaturesClient.addTickListener(test::bootstrap);
    }

    private void bootstrap(Minecraft mc) {
        if (started) return;
        started = true;
        HinexFeaturesClient.addTickListener(client -> client.gui.toastManager().clear());
        Integer scale = Integer.getInteger("hinex.selftest.guiscale");
        if (scale != null) {
            mc.options.guiScale().set(scale);
            mc.resizeGui();
            HinexFeaturesClient.state().acceptTos(null);
        }
        init(mc);
    }

    private void init(Minecraft mc) {
        String connect = System.getProperty("hinex.selftest.connect");
        String pack = System.getProperty("hinex.selftest.pack");

        String vfx = System.getProperty("hinex.selftest.vfx");
        if (vfx != null) {
            step("accept-tos", 20, () -> true, () -> HinexFeaturesClient.state().acceptTos(Gate.currentTosVersion()));
            step("create-world", 20, () -> Gate.stage() == Gate.Stage.DONE, () -> mc.createWorldOpenFlows().createFreshLevel(
                    "hinex-vfx-" + System.currentTimeMillis(),
                    new LevelSettings("Hinex VFX", GameType.CREATIVE, LevelSettings.DifficultySettings.DEFAULT, true, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(12345L, false, false),
                    WorldPresets::createTestWorldDimensions,
                    new TitleScreen()));
            VfxShowcase.plan(this::step, mc, vfx);
            step("quit", 20, () -> true, () -> {
                LOGGER.info("[selftest] DONE");
                mc.stop();
            });
            HinexFeaturesClient.addTickListener(this::tick);
            return;
        }

        if (Boolean.getBoolean("hinex.selftest.blocked")) {
            step("blocked-gate", 40, 1200, () -> mc.gui.screen() instanceof GateScreen && Gate.stage() == Gate.Stage.BLOCKED_MODS,
                    () -> {
                        LOGGER.info("[selftest] blocked: {}", BlockedMods.found());
                        shot("blocked-1");
                    });
            step("blocked-disable", 10, () -> true, BlockedMods::disable);
            step("blocked-done", 2, 200, () -> BlockedMods.status() != BlockedMods.Status.IDLE, () -> {
                LOGGER.info("[selftest] blocked status: {} {}", BlockedMods.status(), BlockedMods.error());
                shot("blocked-2");
            });
            HinexFeaturesClient.addTickListener(this::tick);
            return;
        }

        if (System.getProperty("hinex.update.jar") != null) {
            step("update-gate", 40, 1200, () -> mc.gui.screen() instanceof GateScreen && Gate.stage() == Gate.Stage.UPDATE,
                    () -> shot("update-1"));
            step("update-start", 10, () -> true, () -> {
                Updater.Release release = Updater.available();
                LOGGER.info("[selftest] update: {}", release);
                if (release != null) Updater.start(release);
            });
            step("update-progress", 1, () -> true, () -> shot("update-2"));
            step("update-done", 2, 1200, () -> Updater.status() == Updater.Status.RESTARTING || Updater.status() == Updater.Status.FAILED,
                    () -> {
                        LOGGER.info("[selftest] update status: {} {}", Updater.status(), Updater.error());
                        shot("update-3");
                    });
            HinexFeaturesClient.addTickListener(this::tick);
            return;
        }

        if (Boolean.getBoolean("hinex.selftest.typing")) {
            UUID[] mannequin = new UUID[1];
            HinexFeaturesClient.addTickListener(client -> {
                if (mannequin[0] != null) TypingIndicators.onRemote(mannequin[0], true);
            });
            step("accept-tos", 20, () -> true, () -> HinexFeaturesClient.state().acceptTos(Gate.currentTosVersion()));
            step("create-world", 20, () -> Gate.stage() == Gate.Stage.DONE, () -> mc.createWorldOpenFlows().createFreshLevel(
                    "hinex-selftest-" + System.currentTimeMillis(),
                    new LevelSettings("Hinex selftest", GameType.CREATIVE, LevelSettings.DifficultySettings.DEFAULT, true, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(12345L, false, false),
                    WorldPresets::createTestWorldDimensions,
                    new TitleScreen()));
            step("setup", 80, () -> mc.level != null && mc.player != null && mc.gui.screen() == null, () -> {
                mc.player.connection.sendCommand("time set noon");
                mc.player.connection.sendCommand("tp @s ~ ~ ~ 0 -8");
                mc.player.connection.sendCommand("summon minecraft:mannequin ~ ~ ~3 {Rotation:[180f,0f]}");
            });
            step("find-mannequin", 30, () -> true, () -> {
                for (Entity entity : mc.level.entitiesForRendering()) {
                    if (entity instanceof Avatar && entity != mc.player) mannequin[0] = entity.getUUID();
                }
                LOGGER.info("[selftest] mannequin: {}", mannequin[0]);
            });
            step("typing-shot", 20, () -> true, () -> shot("typing-visible"));
            step("wall", 5, () -> true, () -> mc.player.connection.sendCommand("fill ~-2 ~ ~1 ~2 ~3 ~1 minecraft:stone"));
            step("wall-shot", 30, () -> true, () -> shot("typing-behind-wall"));
            step("quit", 20, () -> true, () -> {
                LOGGER.info("[selftest] DONE");
                mc.stop();
            });
            HinexFeaturesClient.addTickListener(this::tick);
            return;
        }

        if (pack != null) {
            step("accept-tos", 20, () -> true, () -> HinexFeaturesClient.state().acceptTos(Gate.currentTosVersion()));
            step("pack-screen", 20, () -> Gate.stage() == Gate.Stage.DONE
                    && mc.getResourcePackRepository().getSelectedIds().contains(pack), () -> {
                LOGGER.info("[selftest] selected packs: {}", mc.getResourcePackRepository().getSelectedIds());
                mc.gui.setScreen(new PackTestScreen(pack));
            });
            step("pack-shot", 40, () -> true, () -> shot("pack"));
            step("quit", 20, () -> true, () -> {
                LOGGER.info("[selftest] DONE");
                mc.stop();
            });
            HinexFeaturesClient.addTickListener(this::tick);
            return;
        }

        boolean docs = Integer.getInteger("hinex.selftest.guiscale") != null;
        step("gate", 60, 1200, () -> mc.gui.screen() != null && (!docs || mc.gui.screen() instanceof GateScreen), () -> {
            LOGGER.info("[selftest] Gate stage: {}, screen: {}", Gate.stage(), mc.gui.screen().getClass().getSimpleName());
            shot("01-gate");
        });
        step("accept-tos", 10, () -> true, () -> HinexFeaturesClient.state().acceptTos(Gate.currentTosVersion()));
        step("servers", 20, () -> Gate.stage() == Gate.Stage.DONE,
                () -> mc.gui.setScreen(new JoinMultiplayerScreen(new TitleScreen())));
        step("servers-shot", 80, () -> true, () -> shot("02-servers"));
        step("select-pinned", 5, () -> true, () -> {
            if (mc.gui.screen() instanceof JoinMultiplayerScreen screen) {
                ServerSelectionList list = findList(screen);
                if (list != null && list.children().size() > 1) list.setSelected(list.children().get(1));
            }
        });
        step("selected-shot", 10, () -> true, () -> shot("03-selected-pinned"));

        step("dossier", 5, () -> true, () -> {
            JsonObject dossier = ClientDossier.collect();
            LOGGER.info("[selftest] dossier: {} bytes gzip, {} mods, hwid={}", Protocol.encode(dossier).length,
                    dossier.getAsJsonArray("mods").size(), dossier.get("hwid"));
            LOGGER.info("[selftest] dossier system: {}", dossier.get("system"));
        });
        step("chat-tools", 5, () -> true, () -> {
            fakeState(true);
            mc.gui.setScreen(new ChatToolsTestScreen("Привет, <gradient:#4fc3ff:#b46cff>Hinex</gradient> <b>жирный</b> <rainbow>радуга радуга</rainbow> <red>красный"));
        });
        step("chat-tools-shot", 10, () -> true, () -> shot("04-chat-preview"));
        step("emoji-open", 5, () -> true, () -> click(mc.gui.screen(), 8, mc.gui.screen().height - 8));
        step("emoji-shot", 10, () -> true, () -> shot("05-emoji"));
        step("format-open", 5, () -> true, () -> click(mc.gui.screen(), 21, mc.gui.screen().height - 8));
        step("format-shot", 10, () -> true, () -> shot("06-format"));
        step("click-input", 5, () -> true, () -> click(mc.gui.screen(), 120, mc.gui.screen().height - 8));
        step("click-input-shot", 10, () -> true, () -> shot("06b-format-after-input-click"));
        step("locked", 5, () -> true, () -> {
            fakeState(false);
            mc.gui.setScreen(new ChatToolsTestScreen("Без подписки"));
        });
        step("locked-shot", 10, () -> true, () -> shot("07-locked"));
        step("reset-state", 1, () -> true, () -> setState(null));

        if (connect != null) {
            String role = System.getProperty("hinex.selftest.role", "");
            if (role.equals("nomod")) step("mute", 1, () -> true, SelfTest::muteNetwork);
            step("connect", 5, () -> true, () -> {
                mc.options.pauseOnLostFocus = false;
                forgeDevConfigWorkaround();
                ConnectScreen.startConnecting(new TitleScreen(), mc,
                        ServerAddress.parseString(connect), new ServerData("test", connect, ServerData.Type.OTHER), false, null);
            });
            BooleanSupplier joined = () -> mc.level != null && ClientNetwork.state() != null;
            switch (role) {
                case "nomod" -> {
                    step("kicked", 20, 2400, () -> mc.gui.screen() instanceof DisconnectedScreen, () -> {
                        LOGGER.info("[selftest] Disconnected: {}", mc.gui.screen().getNarrationMessage().getString());
                        shot("kick-nomod");
                    });
                }
                case "typer" -> {
                    step("chat", 20, 1200, joined, () -> {
                        logState();
                        mc.gui.setScreen(new ChatScreen("", false));
                        typeUntil = System.currentTimeMillis() + 45_000;
                        HinexFeaturesClient.addTickListener(SelfTest::typeTick);
                    });
                    step("typer-shot", 100, () -> true, () -> shot("typer"));
                    step("typer-done", 1, () -> System.currentTimeMillis() > typeUntil, () -> {
                    });
                }
                case "admin" -> {
                    step("joined", 20, 1200, joined, () -> {
                        logState();
                        if (mc.player.isDeadOrDying()) mc.player.respawn();
                    });
                    step("wait-watcher", 20, 3600, () -> nearestOther(mc, 8.0) != null, () ->
                            LOGGER.info("[selftest] admin: watcher {} рядом", nearestOther(mc, 8.0).getName().getString()));
                    String[][] script = {
                            {"60", "v-settings style ash"}, {"20", "v"}, {"70", "SHOT admin-vanished"}, {"30", "v"},
                            {"80", "v hologram"}, {"60", "v hologram"}, {"70", "v void"}, {"60", "v frost"},
                            {"70", "vfx world beam at=~,~,~3 duration=4"}, {"100", "vfx screen glitch to=all duration=3"},
                            {"80", "vfx world blackhole at=~2,~2,~2 duration=5"}, {"120", "vfx camera shake to=all duration=1.5 strength=2"},
                            {"60", "vfx world circle at=~,~,~2 duration=4"}, {"100", "vfx list"}, {"20", "SHOT admin-list"}};
                    for (String[] line : script) {
                        String action = line[1];
                        step("admin " + action, Integer.parseInt(line[0]), () -> true, () -> {
                            if (action.startsWith("SHOT ")) {
                                if (action.contains("list")) mc.gui.setScreen(new ChatScreen("", false));
                                shot(action.substring(5));
                            } else {
                                LOGGER.info("[selftest] admin: /{}", action);
                                mc.player.connection.sendCommand(action);
                            }
                        });
                    }
                }
                case "watcher" -> {
                    UUID[] admin = new UUID[1];
                    String[] lastState = {""};
                    step("joined", 20, 1200, joined, () -> {
                        logState();
                        mc.options.pauseOnLostFocus = false;
                        if (mc.player.isDeadOrDying()) mc.player.respawn();
                    });
                    step("wait-admin", 20, 3600, () -> nearestOther(mc, 8.0) != null, () -> {
                        Player other = nearestOther(mc, 8.0);
                        admin[0] = other.getUUID();
                        LOGGER.info("[selftest] watcher: admin {}", other.getName().getString());
                        mc.gui.setScreen(null);
                        mc.player.lookAt(EntityAnchorArgument.Anchor.EYES, other.position().add(0, 1.2, 0));
                    });
                    for (int i = 0; i < 220; i++) {
                        String frame = String.format(Locale.ROOT, "e2e-watch-%03d", i);
                        step(frame, 4, () -> true, () -> {
                            boolean entity = mc.level.getEntity(admin[0]) != null;
                            boolean tab = mc.getConnection() != null && mc.getConnection().getPlayerInfo(admin[0]) != null;
                            String state = "entity=" + entity + " tab=" + tab;
                            if (!state.equals(lastState[0])) {
                                lastState[0] = state;
                                LOGGER.info("[selftest] watch {}: {}", frame, state);
                            }
                            shot(frame);
                        });
                    }
                }
                case "deathgame" -> DeathGameTest.plan(this::step, mc, joined, connect);
                case "camera" -> DeathGameTest.planCamera(this::step, mc, joined);
                case "dialog" -> DialogTest.plan(this::step, mc, joined);
                case "adminpanel" -> AdminPanelTest.plan(this::step, mc, joined);
                case "probe" -> ProbeTest.plan(this::step, mc, joined, connect);
                case "observer" -> {
                    step("joined", 20, 1200, joined, () -> {
                        logState();
                        if (mc.player.isDeadOrDying()) mc.player.respawn();
                    });
                    step("see-typing", 10, 3600, () -> typingNear(mc) != null, () -> {
                        Entity other = typingNear(mc);
                        mc.gui.setScreen(null);
                        LOGGER.info("[selftest] {} печатает, расстояние {}", other.getName().getString(),
                                String.format(Locale.ROOT, "%.1f", mc.player.distanceTo(other)));
                        mc.player.lookAt(EntityAnchorArgument.Anchor.EYES, other.position().add(0, other.getBbHeight() + 0.4, 0));
                    });
                    step("observer-shot", 10, () -> true, () -> shot("typing-remote"));
                    step("observer-stopped", 10, 1200, () -> {
                        for (Player player : mc.level.players()) {
                            if (player != mc.player && TypingIndicators.isTyping(player.getUUID())) return false;
                        }
                        return true;
                    }, () -> {
                        LOGGER.info("[selftest] пузырь пропал после окончания набора");
                        shot("typing-remote-stopped");
                    });
                }
                default -> {
                    step("chat", 60, 1200, joined, () -> {
                        logState();
                        mc.gui.setScreen(new ChatScreen("Привет, <gradient:#4fc3ff:#b46cff>Hinex</gradient> <b>жирный</b> <rainbow>радуга</rainbow>", false));
                    });
                    step("chat-shot", 10, () -> true, () -> shot("04-chat"));
                    step("emoji-open", 5, () -> true, () -> click(mc.gui.screen(), 8, mc.gui.screen().height - 8));
                    step("emoji-shot", 10, () -> true, () -> shot("05-emoji"));
                    step("format-open", 5, () -> true, () -> click(mc.gui.screen(), 21, mc.gui.screen().height - 8));
                    step("format-shot", 10, () -> true, () -> shot("06-format"));
                    step("commands", 200, () -> true, () -> {
                        mc.gui.setScreen(null);
                        String name = mc.getUser().getName();
                        for (String command : new String[]{"hf", "hf online", "hf info " + name, "hf mods " + name, "hf alts " + name}) {
                            mc.player.connection.sendCommand(command);
                        }
                    });
                    step("commands-chat", 60, () -> true, () -> mc.gui.setScreen(new ChatScreen("", false)));
                    step("commands-shot", 10, () -> true, () -> shot("08-commands"));
                }
            }
        }
        step("quit", 40, () -> true, () -> {
            LOGGER.info("[selftest] DONE");
            mc.stop();
        });

        HinexFeaturesClient.addTickListener(this::tick);
    }

    void step(String name, int delay, BooleanSupplier waitFor, Runnable action) {
        step(name, delay, 0, waitFor, action);
    }

    void step(String name, int delay, int timeout, BooleanSupplier waitFor, Runnable action) {
        steps.add(new Step(name, delay, timeout, waitFor, action));
    }

    private void tick(Minecraft mc) {
        if (!mc.isGameLoadFinished() || mc.gui.overlay() != null || steps.isEmpty()) return;
        if (!audited) {
            audited = true;
            MixinEnvironment.getCurrentEnvironment().audit();
            LOGGER.info("[selftest] Mixin audit OK ({})", HinexFeaturesClient.platform().loaderName());
        }
        Step step = steps.peek();
        if (!step.waitFor().getAsBoolean()) {
            if (step.timeout() > 0 && ++waiting > step.timeout()) {
                LOGGER.error("[selftest] step {} TIMEOUT", step.name());
                steps.poll();
                waiting = 0;
                wait = 0;
            }
            return;
        }
        waiting = 0;
        if (++wait < step.delay()) return;
        wait = 0;
        steps.poll();
        try {
            LOGGER.info("[selftest] step {}", step.name());
            step.action().run();
        } catch (Throwable t) {
            LOGGER.error("[selftest] step {} FAILED", step.name(), t);
        }
    }

    private static void forgeDevConfigWorkaround() {
        if (!"forge".equals(HinexFeaturesClient.platform().loaderName())) return;
        try {
            Class.forName("net.minecraftforge.fml.config.ConfigTracker").getMethod("loadDefaultServerConfigs").invoke(null);
            LOGGER.info("[selftest] Forge: стандартные server-конфиги загружены");
        } catch (ReflectiveOperationException e) {
            LOGGER.warn("[selftest] Forge: не удалось загрузить server-конфиги", e);
        }
    }

    private static void logState() {
        ServerState state = ClientNetwork.state();
        if (state == null) {
            LOGGER.error("[selftest] Server state: нет ответа от сервера");
            return;
        }
        LOGGER.info("[selftest] Server state: plus={}, emojis={}, tags={}, typing={}", state.plus(),
                state.emojis().size(), state.formatTags(), state.typingIndicator());
    }

    private static void typeTick(Minecraft mc) {
        if (!(mc.gui.screen() instanceof ChatScreen chat)) return;
        if (System.currentTimeMillis() > typeUntil) {
            mc.gui.setScreen(null);
            return;
        }
        if (++typeTicks % 10 != 0) return;
        String text = "Привет, это проверка индикатора набора ";
        ((ChatScreenAccessor) chat).hinex$input().setValue(text.substring(0, typeTicks / 10 % text.length() + 1));
    }

    private static Player nearestOther(Minecraft mc, double distance) {
        if (mc.level == null || mc.player == null) return null;
        for (Player player : mc.level.players()) {
            if (player != mc.player && mc.player.distanceTo(player) < distance) return player;
        }
        return null;
    }

    private static Player typingNear(Minecraft mc) {
        if (mc.level == null || mc.player == null) return null;
        for (Player player : mc.level.players()) {
            if (player != mc.player && TypingIndicators.isTyping(player.getUUID()) && mc.player.distanceTo(player) < 6) {
                return player;
            }
        }
        return null;
    }

    private static void muteNetwork() {
        Platform real = HinexFeaturesClient.platform();
        Platform muted = new Platform() {
            @Override
            public String loaderName() {
                return real.loaderName();
            }

            @Override
            public String loaderVersion() {
                return real.loaderVersion();
            }

            @Override
            public Path gameDir() {
                return real.gameDir();
            }

            @Override
            public String modVersion() {
                return real.modVersion();
            }

            @Override
            public boolean isDevEnvironment() {
                return real.isDevEnvironment();
            }

            @Override
            public List<ModInfo> mods() {
                return real.mods();
            }

            @Override
            public Path modJar() {
                return real.modJar();
            }

            @Override
            public Path modFile(String modId) {
                return real.modFile(modId);
            }

            @Override
            public boolean sendToServer(byte[] data) {
                return false;
            }
        };
        try {
            Field field = HinexFeaturesClient.class.getDeclaredField("platform");
            field.setAccessible(true);
            field.set(null, muted);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        LOGGER.info("[selftest] мод не отвечает серверу (проверка кика без мода)");
    }

    private static void fakeState(boolean plus) {
        JsonObject json = new JsonObject();
        json.addProperty("plus", plus);
        json.addProperty("lockedHint", "{\"text\":\"Нужна подписка Plus\",\"color\":\"gray\"}");
        JsonArray tags = new JsonArray();
        for (String tag : new String[]{"color", "decorations", "gradient", "rainbow", "reset", "shadow", "transition", "pride"}) tags.add(tag);
        json.add("formatTags", tags);
        JsonArray emojis = new JsonArray();
        String symbols = "一丁丂七丄丅丆万丈三上下丌不与丏丐丑丒专且丕世丗丘丙业丛东丝丞丟丠両丢丣两严並丧丨丩丬";
        for (int i = 0; i < symbols.length(); i++) {
            JsonObject emoji = new JsonObject();
            emoji.addProperty("name", "emoji_" + i);
            emoji.addProperty("symbol", String.valueOf(symbols.charAt(i)));
            emojis.add(emoji);
        }
        json.add("emojis", emojis);
        setState(ServerState.parse(json));
    }

    private static void setState(ServerState state) {
        try {
            Field field = ClientNetwork.class.getDeclaredField("state");
            field.setAccessible(true);
            field.set(null, state);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void click(Screen screen, double x, double y) {
        screen.mouseClicked(new MouseButtonEvent(x, y, new MouseButtonInfo(1, 0)), false);
    }

    private static ServerSelectionList findList(JoinMultiplayerScreen screen) {
        for (var child : screen.children()) {
            if (child instanceof ServerSelectionList list) return list;
        }
        return null;
    }

    static void shot(String name) {
        Minecraft mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, "selftest-" + name + ".png", mc.gameRenderer.mainRenderTarget(), 1,
                message -> LOGGER.info("[selftest] {}", message.getString()));
    }
}
