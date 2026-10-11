package me.JAKOP.hinexFeatures.mod;

import java.util.List;

public final class HinexConstants {

    public static final String MOD_ID = "hinexfeatures";

    public static final List<String> CONFIG_URLS = List.of(
            "https://raw.githubusercontent.com/Jaikop/hinex-features/main/config.json",
            "https://cdn.jsdelivr.net/gh/Jaikop/hinex-features@main/config.json"
    );

    public static final String DEFAULT_TOS_URL = "https://tos.hinex-server.ru/";

    public static final String DISCORD_APP_ID = "1555167324955480156";
    public static final String DISCORD_IMAGE = "https://raw.githubusercontent.com/Jaikop/hinex-features/main/discord/hinex.png";
    public static final String TELEGRAM_URL = "https://t.me/hinex_minecraft";

    public static final List<String> BLOCKED_MODS = List.of("freecam");
    public static final String MINIMESSAGE_DOCS_URL = "https://docs.papermc.io/adventure/minimessage/format/";

    private HinexConstants() {
    }

    public static List<String> configUrls() {
        String override = System.getProperty("hinex.config");
        return override != null && !override.isBlank() ? List.of(override) : CONFIG_URLS;
    }
}
