package me.JAKOP.hinexFeatures.mod.chat;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class MiniPreview {

    public static final Map<String, Integer> NAMED = new LinkedHashMap<>();

    static {
        NAMED.put("black", 0x000000);
        NAMED.put("dark_blue", 0x0000AA);
        NAMED.put("dark_green", 0x00AA00);
        NAMED.put("dark_aqua", 0x00AAAA);
        NAMED.put("dark_red", 0xAA0000);
        NAMED.put("dark_purple", 0xAA00AA);
        NAMED.put("gold", 0xFFAA00);
        NAMED.put("gray", 0xAAAAAA);
        NAMED.put("dark_gray", 0x555555);
        NAMED.put("blue", 0x5555FF);
        NAMED.put("green", 0x55FF55);
        NAMED.put("aqua", 0x55FFFF);
        NAMED.put("red", 0xFF5555);
        NAMED.put("light_purple", 0xFF55FF);
        NAMED.put("yellow", 0xFFFF55);
        NAMED.put("white", 0xFFFFFF);
    }

    private static final int[] PRIDE = {0xE50000, 0xFF8D00, 0xFFEE00, 0x028121, 0x004CFF, 0x770088};
    private static final int[] TRANS = {0x5BCFFB, 0xF5ABB9, 0xFFFFFF, 0xF5ABB9, 0x5BCFFB};
    private static final int[] BI = {0xD60270, 0x9B4F96, 0x0038A8};

    private enum Kind { COLOR, DECORATION, GRADIENT, RAINBOW, TRANSITION, SHADOW }

    private static final class Frame {
        final String name;
        final Kind kind;
        int[] colors = new int[0];
        float phase;
        boolean reversed;
        String decoration;
        boolean value = true;
        int shadow;
        int count;

        Frame(String name, Kind kind) {
            this.name = name;
            this.kind = kind;
        }
    }

    private record Glyph(String text, Frame[] frames, int[] indexes) {
    }

    private MiniPreview() {
    }

    public static boolean hasTags(String input) {
        int open = input.indexOf('<');
        return open >= 0 && input.indexOf('>', open) > open;
    }

    public static Component render(String input, Set<String> allowed) {
        List<Frame> stack = new ArrayList<>();
        List<Glyph> glyphs = new ArrayList<>();

        int i = 0;
        while (i < input.length()) {
            char c = input.charAt(i);
            if (c == '\\' && i + 1 < input.length() && (input.charAt(i + 1) == '<' || input.charAt(i + 1) == '\\')) {
                emit(glyphs, stack, String.valueOf(input.charAt(i + 1)));
                i += 2;
                continue;
            }
            if (c == '<') {
                int close = input.indexOf('>', i);
                if (close > i + 1 && applyTag(input.substring(i + 1, close), stack, allowed)) {
                    i = close + 1;
                    continue;
                }
            }
            int cp = input.codePointAt(i);
            emit(glyphs, stack, Character.toString(cp));
            i += Character.charCount(cp);
        }

        MutableComponent result = Component.empty();
        StringBuilder run = new StringBuilder();
        Style runStyle = null;
        for (Glyph glyph : glyphs) {
            Style style = style(glyph);
            if (runStyle != null && !style.equals(runStyle)) {
                result.append(Component.literal(run.toString()).withStyle(runStyle));
                run.setLength(0);
            }
            runStyle = style;
            run.append(glyph.text());
        }
        if (runStyle != null) result.append(Component.literal(run.toString()).withStyle(runStyle));
        return result;
    }

    private static void emit(List<Glyph> glyphs, List<Frame> stack, String text) {
        Frame[] frames = stack.toArray(new Frame[0]);
        int[] indexes = new int[frames.length];
        for (int f = 0; f < frames.length; f++) {
            indexes[f] = frames[f].count++;
        }
        glyphs.add(new Glyph(text, frames, indexes));
    }

    private static Style style(Glyph glyph) {
        Style style = Style.EMPTY.withColor(0xFFFFFF);
        boolean colored = false;
        boolean shadowed = false;
        Set<String> decided = new java.util.HashSet<>();
        for (int f = glyph.frames().length - 1; f >= 0; f--) {
            Frame frame = glyph.frames()[f];
            int index = glyph.indexes()[f];
            switch (frame.kind) {
                case COLOR, GRADIENT, RAINBOW, TRANSITION -> {
                    if (!colored) {
                        style = style.withColor(color(frame, index));
                        colored = true;
                    }
                }
                case SHADOW -> {
                    if (!shadowed) {
                        style = style.withShadowColor(frame.shadow);
                        shadowed = true;
                    }
                }
                case DECORATION -> {
                    if (!decided.add(frame.decoration)) break;
                    Boolean value = frame.value;
                    style = switch (frame.decoration) {
                        case "bold" -> style.withBold(value);
                        case "italic" -> style.withItalic(value);
                        case "underlined" -> style.withUnderlined(value);
                        case "strikethrough" -> style.withStrikethrough(value);
                        default -> style.withObfuscated(value);
                    };
                }
            }
        }
        return style;
    }

    private static int color(Frame frame, int index) {
        return switch (frame.kind) {
            case COLOR -> frame.colors[0];
            case TRANSITION -> lerp(frame.colors, frame.phase);
            case GRADIENT -> {
                float t = frame.count <= 1 ? 0 : (float) index / (frame.count - 1);
                if (frame.phase != 0) t = ((t + frame.phase) % 1f + 1f) % 1f;
                yield lerp(frame.colors, t);
            }
            case RAINBOW -> {
                float t = frame.count <= 0 ? 0 : (float) index / frame.count;
                if (frame.reversed) t = 1 - t;
                yield Mth.hsvToRgb((t + frame.phase) % 1f, 1f, 1f) & 0xFFFFFF;
            }
            default -> 0xFFFFFF;
        };
    }

    private static int lerp(int[] colors, float t) {
        if (colors.length == 1) return colors[0];
        t = Mth.clamp(t, 0f, 1f);
        float scaled = t * (colors.length - 1);
        int from = Math.min((int) scaled, colors.length - 2);
        return ARGB.srgbLerp(scaled - from, colors[from] | 0xFF000000, colors[from + 1] | 0xFF000000) & 0xFFFFFF;
    }

    private static boolean applyTag(String raw, List<Frame> stack, Set<String> allowed) {
        boolean closing = raw.startsWith("/");
        String body = closing ? raw.substring(1) : raw;
        String[] args = body.split(":");
        String name = args[0].toLowerCase(Locale.ROOT);
        boolean negated = name.startsWith("!");
        if (negated) name = name.substring(1);

        String category = category(name);
        if (category == null || !allowed.contains(category)) return false;

        if (closing) {
            String canonical = canonical(name);
            for (int f = stack.size() - 1; f >= 0; f--) {
                if (stack.get(f).name.equals(canonical)) {
                    stack.remove(f);
                    break;
                }
            }
            return true;
        }

        Frame frame = switch (category) {
            case "reset" -> {
                stack.clear();
                yield null;
            }
            case "color" -> {
                Integer rgb = parseColor(name.equals("color") || name.equals("colour") || name.equals("c")
                        ? (args.length > 1 ? args[1] : "") : name);
                if (rgb == null) yield null;
                Frame color = new Frame(canonical(name), Kind.COLOR);
                color.colors = new int[]{rgb};
                yield color;
            }
            case "decorations" -> {
                Frame decoration = new Frame(canonical(name), Kind.DECORATION);
                decoration.decoration = canonical(name);
                decoration.value = !negated && !(args.length > 1 && args[1].equalsIgnoreCase("false"));
                yield decoration;
            }
            case "gradient" -> {
                Frame gradient = new Frame("gradient", Kind.GRADIENT);
                gradient.colors = colors(args, new int[]{0xFFFFFF, 0x000000});
                gradient.phase = phase(args);
                yield gradient;
            }
            case "transition" -> {
                Frame transition = new Frame("transition", Kind.TRANSITION);
                transition.colors = colors(args, new int[]{0xFFFFFF, 0x000000});
                transition.phase = Math.abs(phase(args));
                yield transition;
            }
            case "rainbow" -> {
                Frame rainbow = new Frame("rainbow", Kind.RAINBOW);
                String arg = args.length > 1 ? args[1] : "";
                rainbow.reversed = arg.startsWith("!");
                try {
                    rainbow.phase = arg.replace("!", "").isEmpty() ? 0 : Integer.parseInt(arg.replace("!", "")) / 10f;
                } catch (NumberFormatException ignored) {
                }
                yield rainbow;
            }
            case "pride" -> {
                Frame pride = new Frame("pride", Kind.GRADIENT);
                String flag = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "pride";
                pride.colors = switch (flag) {
                    case "trans" -> TRANS;
                    case "bi" -> BI;
                    default -> PRIDE;
                };
                yield pride;
            }
            case "shadow" -> {
                Frame shadow = new Frame("shadow", Kind.SHADOW);
                Integer rgb = args.length > 1 ? parseColor(args[1]) : Integer.valueOf(0x000000);
                float alpha = 0.25f;
                if (args.length > 2) {
                    try {
                        alpha = Float.parseFloat(args[2]);
                    } catch (NumberFormatException ignored) {
                    }
                }
                shadow.shadow = ARGB.color(Math.round(Mth.clamp(alpha, 0f, 1f) * 255), rgb == null ? 0 : rgb);
                yield shadow;
            }
            default -> null;
        };
        if (frame != null) stack.add(frame);
        return true;
    }

    private static String category(String name) {
        if (name.startsWith("#") || NAMED.containsKey(name) || name.equals("grey") || name.equals("dark_grey")
                || name.equals("color") || name.equals("colour") || name.equals("c")) {
            return "color";
        }
        return switch (name) {
            case "bold", "b", "italic", "i", "em", "underlined", "u", "strikethrough", "st", "obfuscated", "obf" -> "decorations";
            case "gradient", "rainbow", "transition", "reset", "shadow", "pride" -> name;
            default -> null;
        };
    }

    private static String canonical(String name) {
        return switch (name) {
            case "b" -> "bold";
            case "i", "em" -> "italic";
            case "u" -> "underlined";
            case "st" -> "strikethrough";
            case "obf" -> "obfuscated";
            case "colour", "c" -> "color";
            default -> name;
        };
    }

    private static int[] colors(String[] args, int[] fallback) {
        List<Integer> colors = new ArrayList<>();
        for (int a = 1; a < args.length; a++) {
            Integer rgb = parseColor(args[a]);
            if (rgb != null) colors.add(rgb);
        }
        if (colors.isEmpty()) return fallback;
        if (colors.size() == 1) colors.add(colors.getFirst());
        return colors.stream().mapToInt(Integer::intValue).toArray();
    }

    private static float phase(String[] args) {
        if (args.length < 2) return 0;
        try {
            return Mth.clamp(Float.parseFloat(args[args.length - 1]), -1f, 1f);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static Integer parseColor(String value) {
        String v = value.toLowerCase(Locale.ROOT);
        if (v.equals("grey")) v = "gray";
        if (v.equals("dark_grey")) v = "dark_gray";
        if (NAMED.containsKey(v)) return NAMED.get(v);
        if (v.startsWith("#") && v.length() == 7) {
            try {
                return Integer.parseInt(v.substring(1), 16);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
