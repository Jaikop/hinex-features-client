package me.JAKOP.hinexFeatures.mod.plus;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.Locale;

final class Legacy {

    private Legacy() {
    }

    static MutableComponent parse(String text) {
        MutableComponent result = Component.empty();
        StringBuilder buffer = new StringBuilder();
        Style style = Style.EMPTY;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c == '&' || c == '§') && i + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(i + 1));
                int hex = code == '#' ? hashHex(text, i + 2) : code == 'x' ? repeatedHex(text, i + 2) : -1;
                if (hex >= 0) {
                    flush(result, buffer, style);
                    style = Style.EMPTY.withColor(hex);
                    i += code == '#' ? 7 : 13;
                    continue;
                }
                ChatFormatting format = ChatFormatting.getByCode(code);
                if (format != null) {
                    flush(result, buffer, style);
                    style = format == ChatFormatting.RESET ? Style.EMPTY : style.applyLegacyFormat(format);
                    i++;
                    continue;
                }
            }
            buffer.append(c);
        }
        flush(result, buffer, style);
        return result;
    }

    static String toTags(String text) {
        if (text.indexOf('&') < 0 && text.indexOf('§') < 0) return text;
        StringBuilder out = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c == '&' || c == '§') && i + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(i + 1));
                int hex = code == '#' ? hashHex(text, i + 2) : code == 'x' ? repeatedHex(text, i + 2) : -1;
                if (hex >= 0) {
                    out.append(String.format(Locale.ROOT, "<reset><#%06X>", hex));
                    i += code == '#' ? 7 : 13;
                    continue;
                }
                int index = COLOR_CODES.indexOf(code);
                String tag = index >= 0 ? "<reset><" + COLOR_TAGS[index] + ">" : switch (code) {
                    case 'k' -> "<obf>";
                    case 'l' -> "<b>";
                    case 'm' -> "<st>";
                    case 'n' -> "<u>";
                    case 'o' -> "<i>";
                    case 'r' -> "<reset>";
                    default -> null;
                };
                if (tag != null) {
                    out.append(tag);
                    i++;
                    continue;
                }
            }
            out.append(c);
        }
        return out.toString();
    }

    private static final String COLOR_CODES = "0123456789abcdef";
    private static final String[] COLOR_TAGS = {"black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple",
            "gold", "gray", "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white"};

    private static void flush(MutableComponent result, StringBuilder buffer, Style style) {
        if (buffer.isEmpty()) return;
        result.append(Component.literal(buffer.toString()).withStyle(style));
        buffer.setLength(0);
    }

    private static int hashHex(String text, int from) {
        if (from + 6 > text.length()) return -1;
        return parseHex(text.substring(from, from + 6));
    }

    private static int repeatedHex(String text, int from) {
        if (from + 12 > text.length()) return -1;
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            char marker = text.charAt(from + i * 2);
            if (marker != '&' && marker != '§') return -1;
            digits.append(text.charAt(from + i * 2 + 1));
        }
        return parseHex(digits.toString());
    }

    private static int parseHex(String digits) {
        try {
            return Integer.parseInt(digits, 16);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
