package me.JAKOP.hinexFeatures.mod.gate;

import com.mojang.blaze3d.Blaze3D;
import me.JAKOP.hinexFeatures.mod.HinexFeaturesClient;
import me.JAKOP.hinexFeatures.mod.ui.Draw;
import me.JAKOP.hinexFeatures.mod.ui.HinexButton;
import me.JAKOP.hinexFeatures.mod.ui.HinexCheckbox;
import me.JAKOP.hinexFeatures.mod.ui.Theme;
import me.JAKOP.hinexFeatures.mod.update.BlockedMods;
import me.JAKOP.hinexFeatures.mod.update.ExitActions;
import me.JAKOP.hinexFeatures.mod.update.Updater;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class GateScreen extends Screen {

    private static final int BUTTON_HEIGHT = 20;
    private static final String[] LOCK_ICON = {
            "  ####  ",
            " #    # ",
            " #    # ",
            "########",
            "########",
            "###++###",
            "###++###",
            "########",
    };
    private static final String[] SCROLL_ICON = {
            "######  ",
            "#    ## ",
            "# ## # #",
            "#    ###",
            "# #### #",
            "#      #",
            "# #### #",
            "########",
    };

    private static final String[] DOWNLOAD_ICON = {
            "   ##   ",
            "   ##   ",
            "   ##   ",
            " ###### ",
            "  ####  ",
            "   ##   ",
            "        ",
            "########",
    };

    private final @Nullable Screen parent;
    private Gate.Stage shown;
    private Updater.Status shownUpdate;
    private BlockedMods.Status shownBlocked;
    private int widgetsY;

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int bodyY;
    private List<FormattedCharSequence> body = List.of();

    public GateScreen(@Nullable Screen parent) {
        super(Component.literal("Hinex"));
        this.parent = parent instanceof GateScreen ? null : parent;
    }

    @Override
    protected void init() {
        shown = Gate.stage();
        shownUpdate = Updater.status();
        shownBlocked = BlockedMods.status();
        panelW = Math.min(340, width - 32);
        int inner = panelW - 36;

        body = font.split(bodyText(shown), inner);
        int widgetsH = switch (shown) {
            case TOS, TOS_UPDATED -> 12 + 12 + 14 + 14 + BUTTON_HEIGHT;
            case NO_LICENSE, LICENSE_ERROR -> BUTTON_HEIGHT;
            case UPDATE -> BUTTON_HEIGHT + (shownUpdate == Updater.Status.FAILED ? 14 : 0);
            case BLOCKED_MODS -> BUTTON_HEIGHT + (shownBlocked == BlockedMods.Status.FAILED ? 14 : 0);
            default -> 24;
        };
        panelH = 74 + body.size() * 10 + 12 + widgetsH + 16;
        panelX = (width - panelW) / 2;
        panelY = Math.max(8, (height - panelH) / 2);
        bodyY = panelY + 74;

        int y = bodyY + body.size() * 10 + 12;
        widgetsY = y;
        int left = panelX + 18;
        int half = (inner - 8) / 2;

        switch (shown) {
            case UPDATE -> {
                if (shownUpdate == Updater.Status.IDLE || shownUpdate == Updater.Status.FAILED) {
                    if (shownUpdate == Updater.Status.FAILED) y += 14;
                    addRenderableWidget(new HinexButton(left, y, inner, BUTTON_HEIGHT, Component.literal("Обновить и перезапустить"),
                            HinexButton.Kind.PRIMARY, button -> {
                                Updater.Release release = Updater.available();
                                if (release != null) Updater.start(release);
                            }));
                }
            }
            case BLOCKED_MODS -> {
                if (shownBlocked != BlockedMods.Status.RESTARTING) {
                    if (shownBlocked == BlockedMods.Status.FAILED) y += 14;
                    addRenderableWidget(new HinexButton(left, y, inner, BUTTON_HEIGHT, Component.literal("Отключить и перезапустить"),
                            HinexButton.Kind.PRIMARY, button -> BlockedMods.disable()));
                }
            }
            case TOS, TOS_UPDATED -> {
                String url = HinexFeaturesClient.config().tosUrl();
                String label = url.replaceFirst("^https?://", "").replaceFirst("/$", "") + "  ↗";
                addRenderableWidget(new HinexButton(left, y, inner, 12, Component.literal(label), HinexButton.Kind.LINK,
                        button -> openUrl(url)));
                y += 12 + 12;

                HinexButton accept = new HinexButton(left, y + 28, half, BUTTON_HEIGHT, Component.literal("Принять"),
                        HinexButton.Kind.PRIMARY, button -> accept());
                accept.active = false;
                addRenderableWidget(new HinexCheckbox(left, y, inner,
                        Component.literal("Я прочитал(а) правила и согласен(на) с ними"), checked -> accept.active = checked));
                addRenderableWidget(accept);
                addRenderableWidget(new HinexButton(left + half + 8, y + 28, half, BUTTON_HEIGHT, Component.literal("Выйти из игры"),
                        HinexButton.Kind.SECONDARY, button -> minecraft.stop()));
            }
            case NO_LICENSE -> addRenderableWidget(new HinexButton(left, y, inner, BUTTON_HEIGHT, Component.literal("Выйти из игры"),
                    HinexButton.Kind.PRIMARY, button -> minecraft.stop()));
            case LICENSE_ERROR -> {
                addRenderableWidget(new HinexButton(left, y, half, BUTTON_HEIGHT, Component.literal("Повторить"),
                        HinexButton.Kind.PRIMARY, button -> HinexFeaturesClient.license().start()));
                addRenderableWidget(new HinexButton(left + half + 8, y, half, BUTTON_HEIGHT, Component.literal("Выйти из игры"),
                        HinexButton.Kind.SECONDARY, button -> minecraft.stop()));
            }
            default -> {
            }
        }
    }

    @Override
    public void tick() {
        if (ExitActions.shouldStop()) {
            minecraft.stop();
            return;
        }
        boolean updating = shown == Gate.Stage.UPDATE && Updater.status() != Updater.Status.IDLE && Updater.status() != Updater.Status.FAILED;
        boolean disabling = shown == Gate.Stage.BLOCKED_MODS && BlockedMods.status() == BlockedMods.Status.RESTARTING;
        if (updating || disabling) {
            if (Updater.status() != shownUpdate || BlockedMods.status() != shownBlocked) rebuildWidgets();
            return;
        }
        Gate.Stage stage = Gate.stage();
        if (stage == Gate.Stage.DONE) {
            minecraft.gui.setScreen(parent);
        } else if (stage != shown || Updater.status() != shownUpdate || BlockedMods.status() != shownBlocked) {
            rebuildWidgets();
        }
    }

    private void accept() {
        HinexFeaturesClient.state().acceptTos(Gate.currentTosVersion());
        minecraft.gui.setScreen(parent);
    }

    private static void openUrl(String url) {
        try {
            Blaze3D.openUri(URI.create(url));
        } catch (Exception e) {
            HinexFeaturesClient.LOGGER.warn("Не удалось открыть {}", url, e);
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void onClose() {
    }

    private Component titleText(Gate.Stage stage) {
        return Component.literal(switch (stage) {
            case CHECKING -> "Проверка лицензии";
            case NO_LICENSE -> "Нужна лицензия Minecraft";
            case LICENSE_ERROR -> "Не удалось проверить лицензию";
            case UPDATE -> "Доступно обновление";
            case BLOCKED_MODS -> "Запрещённые моды";
            case TOS -> "Правила сервера";
            case TOS_UPDATED -> "Правила обновлены";
            case DONE -> "";
        });
    }

    private Component bodyText(Gate.Stage stage) {
        if (stage == Gate.Stage.UPDATE) return updateText();
        if (stage == Gate.Stage.BLOCKED_MODS) return blockedText();
        return Component.literal(switch (stage) {
            case CHECKING -> "Подождите пару секунд — проверяем аккаунт и загружаем настройки сервера.";
            case NO_LICENSE -> "Играть на Hinex можно только с лицензионной копией Minecraft: Java Edition. "
                    + "Войдите через официальный лаунчер с аккаунтом Microsoft, на котором куплена игра, и запустите игру снова.";
            case LICENSE_ERROR -> "Серверы Mojang не ответили, поэтому мы не смогли подтвердить лицензию. "
                    + "Проверьте подключение к интернету и попробуйте ещё раз.";
            case TOS -> "Перед игрой ознакомьтесь с пользовательским соглашением и правилами Hinex. "
                    + "Нажимая «Принять», вы подтверждаете, что прочитали их и обязуетесь соблюдать.";
            case TOS_UPDATED -> "Мы изменили пользовательское соглашение" + formattedDate()
                    + ". Ознакомьтесь с новой редакцией и подтвердите согласие, чтобы продолжить игру.";
            case UPDATE, BLOCKED_MODS, DONE -> "";
        });
    }

    private static Component blockedText() {
        MutableComponent text = Component.literal("С этими модами на Hinex играть нельзя — они будут отключены:");
        for (BlockedMods.Found mod : BlockedMods.found()) {
            text.append(Component.literal("\n• " + mod.name()).withColor(Theme.TEXT & 0xFFFFFF));
        }
        return text;
    }

    private static Component updateText() {
        Updater.Release release = Updater.available();
        if (release == null) return Component.empty();
        MutableComponent text = Component.literal("Вышла новая версия мода — ")
                .append(Component.literal(release.version()).withColor(Theme.TEXT & 0xFFFFFF))
                .append(" (у вас " + HinexFeaturesClient.version() + "). Обновление обязательно: со старой версией играть нельзя.");
        if (!release.changelog().isEmpty()) {
            text.append("\n");
            for (String line : release.changelog().subList(0, Math.min(6, release.changelog().size()))) {
                text.append(Component.literal("\n• " + line).withColor(Theme.TEXT_MUTED & 0xFFFFFF));
            }
        }
        return text;
    }

    private static String formattedDate() {
        String updated = HinexFeaturesClient.config().tosUpdated();
        if (updated == null) return "";
        try {
            return " (редакция от " + LocalDate.parse(updated.substring(0, Math.min(10, updated.length())))
                    .format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) + ")";
        } catch (Exception e) {
            return " (редакция " + updated + ")";
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractBackground(g, mouseX, mouseY, a);
        g.fillGradient(0, 0, width, height, Theme.SCRIM_TOP, Theme.SCRIM_BOTTOM);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        Draw.roundRect(g, panelX - 3, panelY - 1, panelW + 6, panelH + 6, 8, 0x40000000);
        Draw.accentPanel(g, panelX, panelY, panelW, panelH, 6, Theme.PANEL);

        int centerX = panelX + panelW / 2;
        Draw.scaledCenteredText(g, font, Draw.gradientText("HINEX", Theme.ACCENT_BLUE, Theme.ACCENT_PURPLE, true),
                centerX, panelY + 18, 2.5f);
        Draw.gradientLine(g, centerX - 40, panelY + 18 + 26, 80, 1, Theme.ACCENT_BLUE, Theme.ACCENT_PURPLE);

        Component title = titleText(shown);
        int titleY = panelY + 18 + 26 + 10;
        String[] icon = shown == Gate.Stage.NO_LICENSE || shown == Gate.Stage.LICENSE_ERROR || shown == Gate.Stage.BLOCKED_MODS ? LOCK_ICON
                : shown == Gate.Stage.UPDATE ? DOWNLOAD_ICON : SCROLL_ICON;
        int titleWidth = font.width(title) + 12;
        int titleX = centerX - titleWidth / 2;
        if (shown == Gate.Stage.CHECKING) {
            g.text(font, title.copy().append(dots()), centerX - font.width(title) / 2, titleY + 2, Theme.TEXT, false);
        } else {
            int iconColor = shown == Gate.Stage.NO_LICENSE || shown == Gate.Stage.BLOCKED_MODS ? Theme.DANGER : Theme.ACCENT_BLUE;
            Draw.icon(g, icon, titleX, titleY + 1, iconColor, Theme.PANEL);
            g.text(font, title, titleX + 12, titleY + 2, Theme.TEXT, false);
        }

        int y = bodyY;
        for (FormattedCharSequence line : body) {
            int x = shown == Gate.Stage.UPDATE || shown == Gate.Stage.BLOCKED_MODS ? panelX + 18 : centerX - font.width(line) / 2;
            g.text(font, line, x, y, Theme.TEXT_DIM, false);
            y += 10;
        }

        if (shown == Gate.Stage.CHECKING) {
            spinner(g, centerX, y + 14);
        }
        if (shown == Gate.Stage.UPDATE) extractUpdate(g, centerX);
        if (shown == Gate.Stage.BLOCKED_MODS) extractBlocked(g, centerX);

        super.extractRenderState(g, mouseX, mouseY, a);
    }

    private void extractUpdate(GuiGraphicsExtractor g, int centerX) {
        switch (shownUpdate) {
            case FAILED -> {
                String text = "Не получилось: " + Updater.error();
                g.text(font, text, centerX - font.width(text) / 2, widgetsY + 2, Theme.DANGER, false);
            }
            case DOWNLOADING, RESTARTING -> {
                int left = panelX + 18;
                int inner = panelW - 36;
                float progress = shownUpdate == Updater.Status.RESTARTING ? 1f : Updater.progress();
                Draw.roundRect(g, left, widgetsY + 12, inner, 6, 3, Theme.PANEL_LIGHT);
                int filled = Math.round(inner * progress);
                if (filled > 0) Draw.gradientRoundRect(g, left, widgetsY + 12, Math.max(6, filled), 6, 3, Theme.ACCENT_BLUE, Theme.ACCENT_PURPLE);
                String text = shownUpdate == Updater.Status.DOWNLOADING
                        ? "Скачивание" + dots() + "  " + Math.round(progress * 100) + "%"
                        : restartText();
                g.text(font, text, centerX - font.width(text) / 2, widgetsY, Theme.TEXT, false);
            }
            default -> {
            }
        }
    }

    private void extractBlocked(GuiGraphicsExtractor g, int centerX) {
        if (shownBlocked == BlockedMods.Status.FAILED) {
            String text = "Не получилось: " + BlockedMods.error();
            g.text(font, text, centerX - font.width(text) / 2, widgetsY + 2, Theme.DANGER, false);
        } else if (shownBlocked == BlockedMods.Status.RESTARTING) {
            Draw.gradientRoundRect(g, panelX + 18, widgetsY + 12, panelW - 36, 6, 3, Theme.ACCENT_BLUE, Theme.ACCENT_PURPLE);
            String text = restartText();
            g.text(font, text, centerX - font.width(text) / 2, widgetsY, Theme.TEXT, false);
        }
    }

    private static String restartText() {
        return ExitActions.willRelaunch() ? "Перезапуск" + dots() : "Готово — игра закроется, запустите её снова";
    }

    private static String dots() {
        return ".".repeat((int) (Util.getMillis() / 400 % 4));
    }

    private void spinner(GuiGraphicsExtractor g, int centerX, int y) {
        long time = Util.getMillis();
        for (int i = 0; i < 3; i++) {
            float phase = (float) Math.sin((time / 180.0) - i * 0.9) * 0.5f + 0.5f;
            int color = Draw.withAlpha(i == 1 ? Theme.ACCENT_MID : i == 0 ? Theme.ACCENT_BLUE : Theme.ACCENT_PURPLE, 0.35f + phase * 0.65f);
            Draw.roundRect(g, centerX - 14 + i * 11, y, 6, 6, 3, color);
        }
    }
}
