package me.JAKOP.hinexFeatures.mod.net;

import com.google.gson.JsonObject;
import me.JAKOP.hinexFeatures.mod.util.Json;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class Protocol {

    public static final int VERSION = 1;

    public static final String HELLO = "hello";
    public static final String DOSSIER = "dossier";
    public static final String REQUEST_HELLO = "request_hello";
    public static final String STATE = "state";
    public static final String TYPING = "typing";
    public static final String VFX = "vfx";
    public static final String VFX_STOP = "vfx_stop";
    public static final String VANISH_FX = "vanish_fx";
    public static final String VANISH_STATE = "vanish_state";
    public static final String DEATH_GAME = "death_game";
    public static final String DIALOG = "dialog";
    public static final String DIALOG_CLOSE = "dialog_close";
    public static final String DIALOG_ANSWER = "dialog_answer";
    public static final String DIALOG_EDITOR = "dialog_editor";
    public static final String WORKSPACE = "workspace";
    public static final String PLAYER_MARK = "player_mark";
    public static final String PLUS = "plus";
    public static final String ESSENCE = "essence";
    public static final String KNOWLEDGE = "knowledge";
    public static final String BORDER = "border";
    public static final String PRINTS = "prints";

    private static final int MAX_DECOMPRESSED = 1 << 20;

    private Protocol() {
    }

    public static byte[] encode(JsonObject message) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(Json.GSON.toJson(message).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return out.toByteArray();
    }

    public static JsonObject decode(byte[] data) throws IOException {
        try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(data))) {
            byte[] raw = in.readNBytes(MAX_DECOMPRESSED + 1);
            if (raw.length > MAX_DECOMPRESSED) throw new IOException("Сообщение слишком большое");
            return Json.GSON.fromJson(new String(raw, StandardCharsets.UTF_8), JsonObject.class);
        }
    }

    public static JsonObject message(String type) {
        JsonObject object = new JsonObject();
        object.addProperty("type", type);
        return object;
    }
}
