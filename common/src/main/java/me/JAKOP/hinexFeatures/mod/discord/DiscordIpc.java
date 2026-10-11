package me.JAKOP.hinexFeatures.mod.discord;

import com.google.gson.JsonObject;
import me.JAKOP.hinexFeatures.mod.util.Json;
import org.jspecify.annotations.Nullable;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class DiscordIpc implements Closeable {

    private static final int HANDSHAKE = 0;
    static final int FRAME = 1;
    private static final int CLOSE = 2;

    private final RandomAccessFile pipe;
    private final SocketChannel socket;

    private DiscordIpc(@Nullable RandomAccessFile pipe, @Nullable SocketChannel socket) {
        this.pipe = pipe;
        this.socket = socket;
    }

    static @Nullable DiscordIpc connect(String appId) throws IOException {
        DiscordIpc ipc = open();
        if (ipc == null) return null;
        JsonObject hello = new JsonObject();
        hello.addProperty("v", 1);
        hello.addProperty("client_id", appId);
        try {
            ipc.write(HANDSHAKE, hello);
            JsonObject ready = ipc.read();
            if (!"READY".equals(Json.string(ready, "evt", null))) throw new IOException("Discord не принял подключение: " + ready);
            return ipc;
        } catch (IOException e) {
            ipc.close();
            throw e;
        }
    }

    private static @Nullable DiscordIpc open() {
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
        for (int i = 0; i < 10; i++) {
            if (windows) {
                try {
                    return new DiscordIpc(new RandomAccessFile("\\\\.\\pipe\\discord-ipc-" + i, "rw"), null);
                } catch (IOException ignored) {
                }
                continue;
            }
            for (Path dir : unixDirs()) {
                Path path = dir.resolve("discord-ipc-" + i);
                if (!Files.exists(path)) continue;
                try {
                    SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX);
                    channel.connect(UnixDomainSocketAddress.of(path));
                    return new DiscordIpc(null, channel);
                } catch (IOException ignored) {
                }
            }
        }
        return null;
    }

    private static List<Path> unixDirs() {
        List<Path> dirs = new ArrayList<>();
        for (String variable : List.of("XDG_RUNTIME_DIR", "TMPDIR", "TMP", "TEMP")) {
            String value = System.getenv(variable);
            if (value == null || value.isBlank()) continue;
            Path base = Path.of(value);
            dirs.add(base);
            dirs.add(base.resolve("app/com.discordapp.Discord"));
            dirs.add(base.resolve("snap.discord"));
        }
        dirs.add(Path.of("/tmp"));
        return dirs;
    }

    void write(int op, JsonObject payload) throws IOException {
        byte[] json = Json.GSON.toJson(payload).getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(8 + json.length).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(op).putInt(json.length).put(json).flip();
        if (pipe != null) {
            pipe.write(buffer.array());
        } else {
            while (buffer.hasRemaining()) socket.write(buffer);
        }
    }

    JsonObject read() throws IOException {
        ByteBuffer header = ByteBuffer.wrap(readFully(8)).order(ByteOrder.LITTLE_ENDIAN);
        int op = header.getInt();
        int length = header.getInt();
        if (length < 0 || length > 1 << 20) throw new IOException("кривой ответ Discord");
        JsonObject body = Json.GSON.fromJson(new String(readFully(length), StandardCharsets.UTF_8), JsonObject.class);
        if (op == CLOSE) throw new IOException("Discord закрыл подключение: " + body);
        return body == null ? new JsonObject() : body;
    }

    private byte[] readFully(int length) throws IOException {
        byte[] bytes = new byte[length];
        if (pipe != null) {
            pipe.readFully(bytes);
            return bytes;
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        while (buffer.hasRemaining()) {
            if (socket.read(buffer) < 0) throw new EOFException();
        }
        return bytes;
    }

    @Override
    public void close() {
        try {
            write(CLOSE, new JsonObject());
        } catch (IOException ignored) {
        }
        try {
            if (pipe != null) pipe.close();
            if (socket != null) socket.close();
        } catch (IOException ignored) {
        }
    }
}
