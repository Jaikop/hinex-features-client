package me.JAKOP.hinexFeatures.mod.dialog;

import me.JAKOP.hinexFeatures.mod.ui.Draw;
import me.JAKOP.hinexFeatures.mod.vfx.VfxClock;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class HistoryScreen extends Screen {

    private static final int SCRIM = 0xD0100A06;
    private static final int PANEL = 0xF21C130C;
    private static final int PANEL_LIGHT = 0xFF2A1D13;
    private static final int FIELD = 0xFF150E09;
    private static final int BORDER = 0xFF5E432A;
    private static final int ACCENT = 0xFFD4A25A;
    private static final int TEXT = 0xFFEEDFC8;
    private static final int DIM = 0xFFB09A7E;
    private static final int MUTED = 0xFF75604A;

    private static final String[] PERIODS = {"Всё время", "Сегодня", "7 дней", "30 дней"};
    private static final String[] ANSWERS = {"Все", "С ответом", "Без ответа"};
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", Locale.forLanguageTag("ru"));
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final @Nullable Screen parent;
    private EditBox search;
    private List<DialogHistory.Entry> all = List.of();
    private final List<DialogHistory.Entry> shown = new ArrayList<>();
    private final Map<DialogHistory.Entry, Double> replays = new HashMap<>();
    private final Map<String, Integer> speakers = new LinkedHashMap<>();
    private final List<String> servers = new ArrayList<>();
    private @Nullable String speaker;
    private @Nullable String server;
    private int period;
    private int answered;
    private boolean speakersOpen = true;
    private boolean periodOpen = true;
    private boolean answersOpen = true;
    private boolean serversOpen = true;
    private double scroll;
    private int contentHeight;
    private boolean draggingBar;
    private double sideScroll;
    private int sideContent;
    private int sideTop;
    private int sideBottom;

    private int x0;
    private int y0;
    private int x1;
    private int y1;
    private int sideX;
    private int sideW;
    private int listX;
    private int listY;
    private int listW;
    private int listH;
    private final List<Object[]> clickables = new ArrayList<>();
    private final Map<String, Integer> labels = new HashMap<>();
    private final List<Object[]> cards = new ArrayList<>();

    public HistoryScreen(@Nullable Screen parent) {
        super(Component.literal("История диалогов"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        all = DialogHistory.entries();
        speakers.clear();
        servers.clear();
        for (DialogHistory.Entry entry : all) {
            String name = entry.view().title.getString();
            if (!name.isEmpty()) speakers.merge(name, 1, Integer::sum);
            if (!servers.contains(entry.server)) servers.add(entry.server);
        }
        x0 = 8;
        y0 = 8;
        x1 = width - 8;
        y1 = height - 8;
        sideW = Math.max(120, Math.min(160, (int) (width * 0.26F)));
        sideX = x0 + 8;
        listX = sideX + sideW + 10;
        listY = y0 + 30;
        listW = x1 - 16 - listX;
        listH = y1 - 8 - listY;

        String query = search != null ? search.getValue() : "";
        search = new EditBox(font, sideX + 16, y0 + 33, sideW - 20, 12, Component.literal("Поиск"));
        search.setBordered(false);
        search.setTextColor(TEXT);
        search.setHint(Component.literal("Поиск…").withColor(MUTED & 0xFFFFFF));
        search.setValue(query);
        search.setResponder(value -> refilter());
        addRenderableWidget(search);
        refilter();
    }

    public void search(String query) {
        search.setValue(query);
        refilter();
    }

    private void refilter() {
        shown.clear();
        String query = search == null ? "" : search.getValue().strip().toLowerCase(Locale.ROOT);
        long now = System.currentTimeMillis();
        long since = switch (period) {
            case 1 -> LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
            case 2 -> now - 7L * 24 * 3600 * 1000;
            case 3 -> now - 30L * 24 * 3600 * 1000;
            default -> 0L;
        };
        for (DialogHistory.Entry entry : all) {
            DialogView view = entry.view();
            if (speaker != null && !speaker.equals(view.title.getString())) continue;
            if (server != null && !server.equals(entry.server)) continue;
            if (entry.shown < since) continue;
            if (answered == 1 && entry.answer == null) continue;
            if (answered == 2 && (entry.answer != null || !view.spec.hasAnswers())) continue;
            if (!query.isEmpty()) {
                String haystack = (view.title.getString() + " " + view.text.plain + " " + (entry.answer == null ? "" : entry.answer)
                        + " " + view.spec.sender).toLowerCase(Locale.ROOT);
                if (!haystack.contains(query)) continue;
            }
            shown.add(entry);
        }
        scroll = 0;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        g.fill(0, 0, width, height, SCRIM);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        clickables.clear();
        Draw.panel(g, x0, y0, x1 - x0, y1 - y0, 6, PANEL, BORDER);
        HistoryIcon.draw(g, x0 + 10, y0 + 7);
        g.pose().pushMatrix();
        g.pose().translate(x0 + 30, y0 + 9);
        g.pose().scale(1.4F, 1.4F);
        g.text(font, "ИСТОРИЯ", 0, 0, ACCENT, true);
        g.pose().popMatrix();
        int closeX = x1 - 18;
        boolean closeHover = mouseX >= closeX - 2 && mouseX < closeX + 10 && mouseY >= y0 + 6 && mouseY < y0 + 18;
        g.text(font, "✕", closeX, y0 + 8, closeHover ? ACCENT : DIM, false);
        clickables.add(new Object[]{closeX - 2, y0 + 6, 12, 12, (Runnable) this::onClose});

        g.fill(x0 + 8, y0 + 24, x1 - 8, y0 + 25, ARGB.color(0.5F, BORDER & 0xFFFFFF));

        drawSidebar(g, mouseX, mouseY);
        drawList(g, mouseX, mouseY);
        super.extractRenderState(g, mouseX, mouseY, a);
    }

    private void drawSidebar(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        labels.clear();
        Draw.panel(g, sideX, y0 + 30, sideW, 16, 3, FIELD, BORDER);
        drawMagnifier(g, sideX + 4, y0 + 34);

        sideTop = y0 + 52;
        sideBottom = y1 - 24;
        g.enableScissor(sideX - 2, sideTop, sideX + sideW + 2, sideBottom);
        int y = sideTop + 2 - (int) sideScroll;

        y = section(g, "Кто говорит", speakersOpen, y, mouseX, mouseY, () -> speakersOpen = !speakersOpen);
        if (speakersOpen) {
            y = option(g, "Все", speaker == null, y, mouseX, mouseY, () -> {
                speaker = null;
                refilter();
            });
            int shownSpeakers = 0;
            for (Map.Entry<String, Integer> entry : speakers.entrySet()) {
                if (shownSpeakers++ >= 8) break;
                String name = entry.getKey();
                String label = font.plainSubstrByWidth(name, sideW - 36) + " · " + entry.getValue();
                y = option(g, label, name.equals(speaker), y, mouseX, mouseY, () -> {
                    speaker = name.equals(speaker) ? null : name;
                    refilter();
                });
            }
            if (speakers.isEmpty()) {
                g.text(font, "пока никого", sideX + 10, y + 1, MUTED, false);
                y += 11;
            }
        }
        y += 4;
        y = section(g, "Когда", periodOpen, y, mouseX, mouseY, () -> periodOpen = !periodOpen);
        if (periodOpen) {
            for (int i = 0; i < PERIODS.length; i++) {
                int index = i;
                y = option(g, PERIODS[i], period == i, y, mouseX, mouseY, () -> {
                    period = index;
                    refilter();
                });
            }
        }
        y += 4;
        y = section(g, "Ответы", answersOpen, y, mouseX, mouseY, () -> answersOpen = !answersOpen);
        if (answersOpen) {
            for (int i = 0; i < ANSWERS.length; i++) {
                int index = i;
                y = option(g, ANSWERS[i], answered == i, y, mouseX, mouseY, () -> {
                    answered = index;
                    refilter();
                });
            }
        }
        if (servers.size() > 1) {
            y += 4;
            y = section(g, "Сервер", serversOpen, y, mouseX, mouseY, () -> serversOpen = !serversOpen);
            if (serversOpen) {
                y = option(g, "Все", server == null, y, mouseX, mouseY, () -> {
                    server = null;
                    refilter();
                });
                for (String name : servers) {
                    y = option(g, font.plainSubstrByWidth(name, sideW - 20), name.equals(server), y, mouseX, mouseY, () -> {
                        server = name;
                        refilter();
                    });
                }
            }
        }
        sideContent = y + (int) sideScroll - sideTop;
        g.disableScissor();
        if (sideContent > sideBottom - sideTop) {
            int track = sideBottom - sideTop;
            int knob = Math.max(10, track * track / sideContent);
            int knobY = sideTop + (int) ((track - knob) * (sideScroll / (sideContent - track)));
            g.fill(sideX + sideW + 1, knobY, sideX + sideW + 3, knobY + knob, ARGB.color(0.7F, ACCENT & 0xFFFFFF));
        }

        g.fill(sideX, y1 - 23, sideX + sideW, y1 - 22, ARGB.color(0.5F, BORDER & 0xFFFFFF));
        int bottom = y1 - 18;
        g.text(font, "Найдено: " + shown.size(), sideX, bottom, DIM, false);
        String reset = "сбросить";
        int resetX = sideX + sideW - font.width(reset);
        boolean hover = mouseX >= resetX && mouseX < resetX + font.width(reset) && mouseY >= bottom - 1 && mouseY < bottom + 9;
        g.text(font, reset, resetX, bottom, hover ? ACCENT : MUTED, false);
        if (hover) g.fill(resetX, bottom + 9, resetX + font.width(reset), bottom + 10, ACCENT);
        clickables.add(new Object[]{resetX, bottom - 1, font.width(reset), 10, (Runnable) () -> {
            speaker = null;
            server = null;
            period = 0;
            answered = 0;
            search.setValue("");
            refilter();
        }});
    }

    private int section(GuiGraphicsExtractor g, String title, boolean open, int y, int mouseX, int mouseY, @Nullable Runnable toggle) {
        boolean hover = toggle != null && mouseX >= sideX && mouseX < sideX + sideW && mouseY >= y && mouseY < y + 11;
        g.text(font, open ? "⌄" : "›", sideX, y + (open ? -2 : 1), hover ? ACCENT : DIM, false);
        g.text(font, title, sideX + 9, y + 1, hover ? ACCENT : TEXT, false);
        if (toggle != null) sideClick(y, 11, toggle);
        labels.put(title, y);
        return y + 13;
    }

    private int option(GuiGraphicsExtractor g, String label, boolean selected, int y, int mouseX, int mouseY, Runnable action) {
        boolean hover = mouseX >= sideX && mouseX < sideX + sideW && mouseY >= y && mouseY < y + 11
                && mouseY >= sideTop && mouseY < sideBottom;
        if (selected) g.fill(sideX + 4, y, sideX + sideW, y + 11, ARGB.color(0.25F, ACCENT & 0xFFFFFF));
        else if (hover) g.fill(sideX + 4, y, sideX + sideW, y + 11, ARGB.color(0.10F, ACCENT & 0xFFFFFF));
        g.fill(sideX + 6, y + 4, sideX + 9, y + 7, selected ? ACCENT : MUTED);
        g.text(font, label, sideX + 13, y + 2, selected ? TEXT : DIM, false);
        sideClick(y, 11, action);
        labels.put(label, y);
        return y + 12;
    }

    private void sideClick(int y, int h, Runnable action) {
        int top = Math.max(y, sideTop);
        int bottom = Math.min(y + h, sideBottom);
        if (bottom > top) clickables.add(new Object[]{sideX, top, sideW, bottom - top, action});
    }

    public int @Nullable [] find(String text) {
        Integer y = labels.get(text);
        return y == null ? null : new int[]{sideX + sideW / 2, y + 5};
    }

    public int found() {
        return shown.size();
    }

    private void drawList(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        cards.clear();
        Draw.panel(g, listX - 4, listY - 2, listW + 14, listH + 4, 4, PANEL_LIGHT, ARGB.color(0.6F, BORDER & 0xFFFFFF));
        g.enableScissor(listX - 3, listY - 1, listX + listW + 9, listY + listH + 1);
        if (shown.isEmpty()) {
            String message = all.isEmpty() ? "Здесь появятся диалоги, которые вы увидите" : "Ничего не найдено";
            g.centeredText(font, message, listX + listW / 2, listY + listH / 2 - 4, MUTED);
        }
        int y = listY + 4 - (int) scroll;
        double now = VfxClock.now();
        for (DialogHistory.Entry entry : shown) {
            DialogView view = entry.view();
            int cardWidth = Math.min(view.spec.width, listW - 8);
            float scale = Math.min(1.0F, (listW - 8) / (float) view.spec.width);
            int layoutWidth = scale < 1.0F ? view.spec.width : cardWidth;
            DialogRenderer.Layout layout = DialogRenderer.layout(view, font, layoutWidth);
            int cardHeight = Math.round(layout.height * scale);
            int answerHeight = entry.answer != null ? 12 : 0;
            int blockHeight = 12 + cardHeight + answerHeight + 10;
            if (y + blockHeight >= listY - 20 && y <= listY + listH + 20) {
                String header = header(entry);
                g.centeredText(font, header, listX + listW / 2, y, MUTED);
                int cardX = listX + (listW - Math.round(layoutWidth * scale)) / 2;
                int cardY = y + 12;
                Double replay = replays.get(entry);
                float textTime = replay == null ? Float.MAX_VALUE : (float) (now - replay);
                g.pose().pushMatrix();
                g.pose().translate(cardX, cardY);
                g.pose().scale(scale, scale);
                DialogRenderer.draw(g, font, view, 0, 0, layoutWidth, 1.0F, DialogRenderer.Mode.STATIC, -1, textTime);
                g.pose().popMatrix();
                boolean hover = mouseX >= cardX && mouseX < cardX + layoutWidth * scale && mouseY >= cardY && mouseY < cardY + cardHeight
                        && mouseY >= listY && mouseY < listY + listH;
                if (hover) {
                    String hint = "▶ повторить";
                    g.text(font, hint, cardX + Math.round(layoutWidth * scale) - font.width(hint) - 4, cardY + cardHeight - 11,
                            ARGB.color(0.8F, ACCENT & 0xFFFFFF), true);
                }
                cards.add(new Object[]{entry, cardX, cardY, Math.round(layoutWidth * scale), cardHeight});
                if (entry.answer != null) {
                    Component answer = Component.literal("Ваш ответ: ").withColor(DIM & 0xFFFFFF)
                            .append(Component.literal(entry.answer).withColor(ACCENT & 0xFFFFFF));
                    g.text(font, answer, cardX + 4, cardY + cardHeight + 3, 0xFFFFFFFF, false);
                }
            }
            y += blockHeight;
        }
        contentHeight = y + (int) scroll - listY;
        g.disableScissor();

        int max = maxScroll();
        int barX = listX + listW + 4;
        g.fill(barX, listY, barX + 4, listY + listH, 0xFF140D08);
        if (max > 0) {
            int knob = Math.max(16, listH * listH / Math.max(1, contentHeight));
            int knobY = listY + (int) ((listH - knob) * (scroll / max));
            g.fill(barX, knobY, barX + 4, knobY + knob, ACCENT);
        } else {
            g.fill(barX, listY, barX + 4, listY + listH, ARGB.color(0.35F, BORDER & 0xFFFFFF));
        }
    }

    private String header(DialogHistory.Entry entry) {
        LocalDateTime time = LocalDateTime.ofInstant(Instant.ofEpochMilli(entry.shown), ZoneId.systemDefault());
        LocalDate today = LocalDate.now();
        String when;
        if (time.toLocalDate().equals(today)) when = "Сегодня, " + time.format(TIME);
        else if (time.toLocalDate().equals(today.minusDays(1))) when = "Вчера, " + time.format(TIME);
        else when = time.format(DATE);
        return when + "  ·  " + entry.server;
    }

    private static void drawMagnifier(GuiGraphicsExtractor g, int x, int y) {
        int color = 0xFFB09A7E;
        g.fill(x + 1, y, x + 5, y + 1, color);
        g.fill(x, y + 1, x + 1, y + 5, color);
        g.fill(x + 5, y + 1, x + 6, y + 5, color);
        g.fill(x + 1, y + 5, x + 5, y + 6, color);
        g.fill(x + 5, y + 6, x + 7, y + 7, color);
        g.fill(x + 6, y + 7, x + 8, y + 8, color);
    }

    private int maxScroll() {
        return Math.max(0, contentHeight - listH + 4);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX < listX - 6) {
            int max = Math.max(0, sideContent - (sideBottom - sideTop));
            sideScroll = Math.max(0, Math.min(max, sideScroll - scrollY * 20));
            return true;
        }
        scroll = Math.max(0, Math.min(maxScroll(), scroll - scrollY * 28));
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();
        int barX = listX + listW + 4;
        if (mx >= barX - 1 && mx < barX + 6 && my >= listY && my < listY + listH && maxScroll() > 0) {
            draggingBar = true;
            dragTo(my);
            return true;
        }
        for (Object[] clickable : clickables) {
            int x = (int) clickable[0];
            int y = (int) clickable[1];
            if (mx >= x && mx < x + (int) clickable[2] && my >= y && my < y + (int) clickable[3]) {
                ((Runnable) clickable[4]).run();
                return true;
            }
        }
        if (my >= listY && my < listY + listH) {
            for (Object[] card : cards) {
                int x = (int) card[1];
                int y = (int) card[2];
                if (mx >= x && mx < x + (int) card[3] && my >= y && my < y + (int) card[4]) {
                    DialogHistory.Entry entry = (DialogHistory.Entry) card[0];
                    replays.put(entry, VfxClock.now());
                    entry.view().restart();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (draggingBar) {
            dragTo(event.y());
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        draggingBar = false;
        return super.mouseReleased(event);
    }

    private void dragTo(double mouseY) {
        float fraction = (float) ((mouseY - listY) / Math.max(1, listH));
        scroll = Math.max(0, Math.min(maxScroll(), fraction * maxScroll()));
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
