package me.JAKOP.hinexFeatures.mod.servers;

import me.JAKOP.hinexFeatures.mod.HinexFeaturesClient;
import me.JAKOP.hinexFeatures.mod.config.RemoteConfig;
import net.minecraft.client.multiplayer.ServerData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public final class PinnedServers {

    public static final int SEPARATOR_HEIGHT = 16;

    public record Pin(boolean main, String separatorBelow, boolean last) {
    }

    private static final Map<ServerData, Pin> PINS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<ServerData, String> SHOWN_SEPARATORS = Collections.synchronizedMap(new WeakHashMap<>());

    private PinnedServers() {
    }

    public static void setShownSeparator(ServerData data, String label) {
        if (label == null) SHOWN_SEPARATORS.remove(data);
        else SHOWN_SEPARATORS.put(data, label);
    }

    public static String shownSeparator(ServerData data) {
        return SHOWN_SEPARATORS.get(data);
    }

    public static Pin pin(ServerData data) {
        return data == null ? null : PINS.get(data);
    }

    public static boolean isPinned(ServerData data) {
        return pin(data) != null;
    }

    public static String separatorBelow(ServerData data, boolean userServersBelow) {
        Pin pin = pin(data);
        if (pin == null) return null;
        if (pin.separatorBelow() != null) return pin.separatorBelow();
        return pin.last() && userServersBelow ? "Ваши сервера" : null;
    }

    public static void apply(List<ServerData> servers, List<ServerData> hidden) {
        List<RemoteConfig.ServerEntry> entries = HinexFeaturesClient.config().servers();
        Set<String> currentIps = new LinkedHashSet<>();
        for (RemoteConfig.ServerEntry entry : entries) currentIps.add(normalize(entry.ip()));

        Set<String> previous = HinexFeaturesClient.state().pinnedIps();
        previous.removeAll(currentIps);
        if (!previous.isEmpty()) {
            servers.removeIf(data -> previous.contains(normalize(data.ip)));
            hidden.removeIf(data -> previous.contains(normalize(data.ip)));
        }

        List<ServerData> pinned = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            RemoteConfig.ServerEntry entry = entries.get(i);
            String ip = normalize(entry.ip());
            ServerData existing = take(servers, ip);
            ServerData hiddenCopy = take(hidden, ip);
            ServerData data = existing != null ? existing : hiddenCopy != null ? hiddenCopy : new ServerData(entry.name(), entry.ip(), ServerData.Type.OTHER);

            data.name = entry.name();
            data.ip = entry.ip();
            data.setResourcePackStatus(ServerData.ServerPackStatus.ENABLED);

            PINS.put(data, new Pin(entry.main(), entry.separatorBelow(), i == entries.size() - 1));
            pinned.add(data);
        }

        servers.addAll(0, pinned);
        HinexFeaturesClient.state().setPinnedIps(currentIps);
    }

    private static ServerData take(List<ServerData> list, String ip) {
        ServerData found = null;
        for (int i = list.size() - 1; i >= 0; i--) {
            if (normalize(list.get(i).ip).equals(ip)) {
                found = list.remove(i);
            }
        }
        return found;
    }

    private static String normalize(String ip) {
        return ip == null ? "" : ip.trim().toLowerCase(Locale.ROOT);
    }
}
