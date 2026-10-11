package me.JAKOP.hinexFeatures.mod.chat;

import me.JAKOP.hinexFeatures.mod.mixin.chat.EditBoxAccessor;
import net.minecraft.client.gui.components.EditBox;

public final class ChatInsert {

    private ChatInsert() {
    }

    public static boolean wrap(EditBox input, String open, String close) {
        String value = input.getValue();
        int cursor = input.getCursorPosition();
        int highlight = ((EditBoxAccessor) input).hinex$highlightPos();
        int start = Math.min(cursor, highlight);
        int end = Math.max(cursor, highlight);

        if (value.length() + open.length() + close.length() > ((EditBoxAccessor) input).hinex$maxLength()) return false;

        String result = value.substring(0, start) + open + value.substring(start, end) + close + value.substring(end);
        input.setValue(result);
        int caret = start + open.length() + (end - start);
        input.setCursorPosition(caret);
        input.setHighlightPos(caret);
        return true;
    }

    public static boolean insert(EditBox input, String text) {
        String value = input.getValue();
        String selected = input.getHighlighted();
        if (value.length() - selected.length() + text.length() > ((EditBoxAccessor) input).hinex$maxLength()) return false;
        input.insertText(text);
        return true;
    }
}
