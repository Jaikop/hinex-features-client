package me.JAKOP.hinexFeatures.mod.dialog;

import me.JAKOP.hinexFeatures.mod.ui.Draw;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class RichText {

    public static final int SHAKE = 1;
    public static final int WAVE = 2;
    public static final int PULSE = 4;
    public static final int GLITCH = 8;
    public static final int RAINBOW = 16;
    public static final int BLINK = 32;
    public static final int GLOW = 64;

    public static final Map<String, Integer> NAMED = Map.ofEntries(
            Map.entry("black", 0x000000), Map.entry("dark_blue", 0x0000AA), Map.entry("dark_green", 0x00AA00),
            Map.entry("dark_aqua", 0x00AAAA), Map.entry("dark_red", 0xAA0000), Map.entry("dark_purple", 0xAA00AA),
            Map.entry("gold", 0xFFAA00), Map.entry("gray", 0xAAAAAA), Map.entry("grey", 0xAAAAAA), Map.entry("dark_gray", 0x555555),
            Map.entry("blue", 0x5555FF), Map.entry("green", 0x55FF55), Map.entry("aqua", 0x55FFFF), Map.entry("red", 0xFF5555),
            Map.entry("light_purple", 0xFF55FF), Map.entry("yellow", 0xFFFF55), Map.entry("white", 0xFFFFFF),
            Map.entry("orange", 0xFF9A3C), Map.entry("pink", 0xFF8FC8), Map.entry("brown", 0x9A6A3E), Map.entry("cream", 0xF2E3C2),
            Map.entry("blood", 0xB3001B), Map.entry("sky", 0x7FD4FF), Map.entry("mint", 0x8CF0C0), Map.entry("violet", 0xB46CFF));

    private static final String GLITCH_CHARS = "#$%&@!?*/\\<>=+~";

    public static final class Glyph {
        final String text;
        final Component component;
        final boolean bold;
        int color;
        final int effects;
        final float shake;
        final float wave;
        final float appearAt;
        final boolean newline;
        final boolean space;
        int width;
        int line;
        int x;

        Glyph(String text, Style style, boolean bold, int color, int effects, float shake, float wave, float appearAt, boolean newline) {
            this.text = text;
            this.component = Component.literal(text).withStyle(style);
            this.bold = bold;
            this.color = color;
            this.effects = effects;
            this.shake = shake;
            this.wave = wave;
            this.appearAt = appearAt;
            this.newline = newline;
            this.space = text.equals(" ");
        }
    }

    public static final class Parsed {
        public final List<Glyph> glyphs;
        public final float duration;
        public final int visible;
        public final String plain;
        private int layoutWidth = -1;
        private int lines;
        private int widest;

        Parsed(List<Glyph> glyphs, float duration, String plain) {
            this.glyphs = glyphs;
            this.duration = duration;
            this.plain = plain;
            int count = 0;
            for (Glyph glyph : glyphs) if (!glyph.newline && !glyph.space) count++;
            this.visible = count;
        }

        public int typed(float time) {
            int count = 0;
            for (Glyph glyph : glyphs) {
                if (glyph.appearAt > time) break;
                if (!glyph.newline && !glyph.space) count++;
            }
            return count;
        }

        public int lines() {
            return lines;
        }

        public int widest() {
            return widest;
        }
    }

    private static final class Tag {
        final String name;
        @Nullable Integer color;
        int[] gradient;
        final List<Glyph> members = new ArrayList<>();
        Boolean bold;
        Boolean italic;
        Boolean underline;
        Boolean strike;
        Boolean obfuscated;
        int effects;
        float shake;
        float wave;
        float speed = 1.0F;

        Tag(String name) {
            this.name = name;
        }
    }

    private RichText() {
    }

    public static Parsed parse(String markup, int defaultColor, float speed, String appear) {
        List<Glyph> glyphs = new ArrayList<>();
        Deque<Tag> stack = new ArrayDeque<>();
        StringBuilder plain = new StringBuilder();
        float time = 0.0F;
        float wordStart = 0.0F;
        boolean inWord = false;
        String text = markup.replace("\r", "");

        for (int i = 0; i < text.length(); ) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < text.length()) {
                char next = text.charAt(i + 1);
                if (next == 'n') {
                    glyphs.add(newline(time));
                    plain.append(' ');
                    i += 2;
                    continue;
                }
                if (next == '<') {
                    i += 1;
                    c = '<';
                }
            } else if (c == '<') {
                int end = text.indexOf('>', i);
                if (end > i && end - i <= 64) {
                    String tag = text.substring(i + 1, end).strip();
                    float pause = handleTag(tag, stack, glyphs, defaultColor);
                    if (pause >= 0.0F) {
                        if (!appear.equals("instant")) time += pause;
                        i = end + 1;
                        continue;
                    }
                }
            }
            if (c == '\n') {
                glyphs.add(newline(time));
                plain.append(' ');
                i++;
                continue;
            }
            int cp = text.codePointAt(i);
            String str = new String(Character.toChars(cp));
            i += Character.charCount(cp);
            plain.append(str);

            Integer color = null;
            Tag colorTag = null;
            Boolean bold = null;
            Boolean italic = null;
            Boolean underline = null;
            Boolean strike = null;
            Boolean obfuscated = null;
            int effects = 0;
            float shake = 0.0F;
            float wave = 0.0F;
            float rate = 1.0F;
            for (Tag tag : stack) {
                if (color == null && colorTag == null && (tag.color != null || tag.gradient != null)) {
                    if (tag.gradient != null) colorTag = tag;
                    else color = tag.color;
                }
                if (bold == null) bold = tag.bold;
                if (italic == null) italic = tag.italic;
                if (underline == null) underline = tag.underline;
                if (strike == null) strike = tag.strike;
                if (obfuscated == null) obfuscated = tag.obfuscated;
                effects |= tag.effects;
                if (shake == 0.0F && tag.shake > 0.0F) shake = tag.shake;
                if (wave == 0.0F && tag.wave > 0.0F) wave = tag.wave;
                rate *= tag.speed;
            }
            Style style = Style.EMPTY.withBold(Boolean.TRUE.equals(bold)).withItalic(Boolean.TRUE.equals(italic))
                    .withUnderlined(Boolean.TRUE.equals(underline)).withStrikethrough(Boolean.TRUE.equals(strike))
                    .withObfuscated(Boolean.TRUE.equals(obfuscated));

            boolean space = str.equals(" ");
            float appearAt = switch (appear) {
                case "instant" -> 0.0F;
                case "fade" -> glyphs.size() * 0.006F;
                case "words" -> {
                    if (space) {
                        inWord = false;
                        yield time;
                    }
                    if (!inWord) {
                        inWord = true;
                        wordStart = time;
                    }
                    yield wordStart;
                }
                default -> time;
            };
            Glyph glyph = new Glyph(str, style, Boolean.TRUE.equals(bold), color != null ? color : defaultColor, effects,
                    shake, wave, appearAt, false);
            glyphs.add(glyph);
            if (colorTag != null) colorTag.members.add(glyph);

            if (appear.equals("typewriter") || appear.equals("words")) {
                float step = 1.0F / (speed * Math.max(0.05F, rate));
                time += space ? step * 0.5F : step;
                if (".!?…".indexOf(c) >= 0) time += 0.22F / Math.max(0.2F, rate);
                else if (",;:—".indexOf(c) >= 0) time += 0.09F / Math.max(0.2F, rate);
            }
        }
        for (Tag tag : stack) applyGradient(tag);
        float duration = switch (appear) {
            case "instant" -> 0.0F;
            case "fade" -> glyphs.size() * 0.006F + 0.35F;
            default -> glyphs.isEmpty() ? 0.0F : Math.max(time, glyphs.getLast().appearAt) + 0.12F;
        };
        return new Parsed(glyphs, duration, plain.toString().strip());
    }

    private static Glyph newline(float time) {
        return new Glyph("", Style.EMPTY, false, 0, 0, 0, 0, time, true);
    }

    private static float handleTag(String raw, Deque<Tag> stack, List<Glyph> glyphs, int defaultColor) {
        if (raw.isEmpty()) return -1.0F;
        String lower = raw.toLowerCase(Locale.ROOT);
        if (lower.startsWith("/")) {
            String name = lower.substring(1).strip();
            if (name.isEmpty()) {
                if (!stack.isEmpty()) applyGradient(stack.pop());
                return 0.0F;
            }
            String canonical = canonical(name.contains(":") ? name.substring(0, name.indexOf(':')) : name);
            var iterator = stack.iterator();
            while (iterator.hasNext()) {
                Tag tag = iterator.next();
                if (tag.name.equals(canonical)) {
                    iterator.remove();
                    applyGradient(tag);
                    return 0.0F;
                }
            }
            return 0.0F;
        }
        String name = lower.contains(":") ? lower.substring(0, lower.indexOf(':')) : lower;
        String argument = lower.contains(":") ? raw.substring(raw.indexOf(':') + 1).strip() : "";
        switch (name) {
            case "br", "newline" -> {
                glyphs.add(newline(glyphs.isEmpty() ? 0.0F : glyphs.getLast().appearAt));
                return 0.0F;
            }
            case "pause", "p", "wait" -> {
                try {
                    return Math.max(0.0F, Math.min(10000.0F, Float.parseFloat(argument))) / 1000.0F;
                } catch (NumberFormatException e) {
                    return 0.5F;
                }
            }
            case "reset" -> {
                while (!stack.isEmpty()) applyGradient(stack.pop());
                return 0.0F;
            }
            default -> {
            }
        }
        Tag tag = new Tag(canonical(name));
        switch (tag.name) {
            case "bold" -> tag.bold = true;
            case "italic" -> tag.italic = true;
            case "underlined" -> tag.underline = true;
            case "strikethrough" -> tag.strike = true;
            case "obfuscated" -> tag.obfuscated = true;
            case "shake" -> {
                tag.effects = SHAKE;
                tag.shake = amount(argument, 1.0F);
            }
            case "wave" -> {
                tag.effects = WAVE;
                tag.wave = amount(argument, 1.6F);
            }
            case "pulse" -> tag.effects = PULSE;
            case "glitch" -> tag.effects = GLITCH;
            case "rainbow" -> tag.effects = RAINBOW;
            case "blink" -> tag.effects = BLINK;
            case "glow" -> tag.effects = GLOW;
            case "speed" -> tag.speed = Math.max(0.05F, Math.min(20.0F, amount(argument, 1.0F)));
            case "slow" -> tag.speed = 0.4F;
            case "fast" -> tag.speed = 2.5F;
            case "gradient" -> {
                List<Integer> colors = new ArrayList<>();
                for (String part : argument.split(":")) {
                    int color = DialogSpec.parseColor(part, -1);
                    if (color >= 0) colors.add(color);
                }
                if (colors.size() == 1) colors.add(defaultColor);
                if (colors.isEmpty()) return -1.0F;
                tag.gradient = colors.stream().mapToInt(Integer::intValue).toArray();
            }
            case "color" -> {
                int color = DialogSpec.parseColor(argument.isEmpty() ? raw : argument, -1);
                if (color < 0) return -1.0F;
                tag.color = color;
            }
            default -> {
                int color = DialogSpec.parseColor(raw, -1);
                if (color < 0 || (!raw.startsWith("#") && !NAMED.containsKey(lower))) return -1.0F;
                tag.color = color;
            }
        }
        stack.push(tag);
        return 0.0F;
    }

    private static String canonical(String name) {
        return switch (name) {
            case "b" -> "bold";
            case "i", "em" -> "italic";
            case "u", "underline" -> "underlined";
            case "st", "s", "strike" -> "strikethrough";
            case "obf", "magic" -> "obfuscated";
            case "c", "colour" -> "color";
            case "shaky", "tremble" -> "shake";
            case "wavy" -> "wave";
            default -> name.startsWith("#") || NAMED.containsKey(name) ? "color" : name;
        };
    }

    private static float amount(String argument, float fallback) {
        if (argument.isEmpty()) return fallback;
        try {
            return Float.parseFloat(argument);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static void applyGradient(Tag tag) {
        if (tag.gradient == null || tag.members.isEmpty()) return;
        int count = tag.members.size();
        for (int i = 0; i < count; i++) {
            float t = count == 1 ? 0.0F : i / (float) (count - 1);
            float scaled = t * (tag.gradient.length - 1);
            int index = Math.min(tag.gradient.length - 2, (int) scaled);
            tag.members.get(i).color = ARGB.srgbLerp(scaled - index, tag.gradient[index] | 0xFF000000,
                    tag.gradient[index + 1] | 0xFF000000) & 0xFFFFFF;
        }
    }

    private static final Map<Long, Integer> WIDTHS = new HashMap<>();

    private static int width(Font font, Glyph glyph) {
        if (glyph.newline) return 0;
        long key = ((long) glyph.text.hashCode() << 1) | (glyph.bold ? 1 : 0);
        Integer cached = WIDTHS.get(key);
        if (cached != null) return cached;
        int width = font.width(glyph.component);
        if (WIDTHS.size() < 4096) WIDTHS.put(key, width);
        return width;
    }

    public static void layout(Parsed parsed, Font font, int maxWidth) {
        if (parsed.layoutWidth == maxWidth) return;
        parsed.layoutWidth = maxWidth;
        List<Glyph> glyphs = parsed.glyphs;
        int line = 0;
        int x = 0;
        int lineStart = 0;
        int widest = 0;
        for (int i = 0; i < glyphs.size(); i++) {
            Glyph glyph = glyphs.get(i);
            glyph.width = width(font, glyph);
            if (glyph.newline) {
                glyph.line = line;
                glyph.x = x;
                widest = Math.max(widest, x);
                line++;
                x = 0;
                lineStart = i + 1;
                continue;
            }
            if (x + glyph.width > maxWidth && x > 0 && !glyph.space) {
                int breakAt = -1;
                for (int j = i - 1; j >= lineStart; j--) {
                    if (glyphs.get(j).space) {
                        breakAt = j;
                        break;
                    }
                }
                int restart = breakAt >= 0 ? breakAt + 1 : i;
                int lineWidth = breakAt >= 0 ? glyphs.get(breakAt).x : x;
                widest = Math.max(widest, lineWidth);
                line++;
                x = 0;
                for (int j = restart; j < i; j++) {
                    Glyph moved = glyphs.get(j);
                    moved.line = line;
                    moved.x = x;
                    x += moved.width;
                }
                lineStart = restart;
            }
            glyph.line = line;
            glyph.x = x;
            x += glyph.width;
        }
        widest = Math.max(widest, x);
        parsed.lines = glyphs.isEmpty() ? 0 : line + 1;
        parsed.widest = widest;
    }

    public static int @Nullable [] draw(GuiGraphicsExtractor g, Font font, Parsed parsed, int x, int y, int lineHeight,
                                        float time, float clock, float alpha, float reveal, boolean shadow) {
        int[] cursor = null;
        List<Glyph> glyphs = parsed.glyphs;
        for (int i = 0; i < glyphs.size(); i++) {
            Glyph glyph = glyphs.get(i);
            float local = time - glyph.appearAt;
            if (local < 0.0F) {
                if (cursor == null) cursor = new int[]{x + glyph.x, y + glyph.line * lineHeight};
                continue;
            }
            if (glyph.newline || glyph.space) continue;
            float a = reveal <= 0.0F ? 1.0F : Math.min(1.0F, local / reveal);
            float charAlpha = alpha * a;
            float dx = 0.0F;
            float dy = (1.0F - a) * 3.0F;
            int rgb = glyph.color;
            String text = null;
            int effects = glyph.effects;
            if ((effects & RAINBOW) != 0) {
                float hue = (i * 0.055F + clock * 0.3F) % 1.0F;
                rgb = Mth.hsvToRgb(hue, 0.62F, 1.0F) & 0xFFFFFF;
            }
            if ((effects & PULSE) != 0) {
                float k = 0.55F + 0.45F * (0.5F + 0.5F * (float) Math.sin(clock * 5.0F - i * 0.35F));
                rgb = ARGB.scaleRGB(rgb | 0xFF000000, k) & 0xFFFFFF;
            }
            if ((effects & BLINK) != 0 && Math.sin(clock * 7.0F) < -0.25) charAlpha *= 0.15F;
            if ((effects & SHAKE) != 0) {
                int bucket = (int) (clock * 24.0F);
                dx += (hash(i, bucket, 1) - 0.5F) * 2.0F * glyph.shake;
                dy += (hash(i, bucket, 2) - 0.5F) * 2.0F * glyph.shake;
            }
            if ((effects & WAVE) != 0) dy += (float) Math.sin(clock * 7.0F - i * 0.45F) * glyph.wave;
            if ((effects & GLITCH) != 0) {
                int bucket = (int) (clock * 12.0F);
                float r = hash(i, bucket, 3);
                if (r < 0.2F) {
                    dx += (hash(i, bucket, 4) - 0.5F) * 4.0F;
                    rgb = hash(i, bucket, 5) < 0.5F ? 0x55FFFF : 0xFF55FF;
                }
                if (r < 0.07F) text = String.valueOf(GLITCH_CHARS.charAt((int) (hash(i, bucket, 6) * GLITCH_CHARS.length())));
            }
            if (charAlpha < 0.04F) continue;
            Component component = text == null ? glyph.component : Component.literal(text).withStyle(glyph.component.getStyle());
            if (glyph.bold) component = Component.literal(component.getString()).withStyle(component.getStyle().withBold(false));
            float gx = x + glyph.x + dx;
            float gy = y + glyph.line * lineHeight + dy;
            if ((effects & GLOW) != 0) {
                int glow = ARGB.color(Math.min(1.0F, charAlpha * 0.35F), ARGB.scaleRGB(rgb | 0xFF000000, 1.0F) & 0xFFFFFF);
                if (charAlpha * 0.35F >= 0.04F) {
                    for (int k = 0; k < 4; k++) {
                        text(g, font, component, gx + (k == 0 ? 1 : k == 1 ? -1 : 0), gy + (k == 2 ? 1 : k == 3 ? -1 : 0), glow, false);
                    }
                }
            }
            int color = ARGB.color(Math.min(1.0F, charAlpha), rgb);
            if (glyph.bold) Draw.crispBold(g, font, component, gx, gy, color, shadow);
            else text(g, font, component, gx, gy, color, shadow);
        }
        return cursor;
    }

    private static void text(GuiGraphicsExtractor g, Font font, Component component, float x, float y, int color, boolean shadow) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.text(font, component, 0, 0, color, shadow);
        g.pose().popMatrix();
    }

    private static float hash(int index, int bucket, int salt) {
        int h = index * 374761393 + bucket * 668265263 + salt * 1274126177;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / (float) 0x1000000;
    }
}
