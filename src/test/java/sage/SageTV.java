package sage;

import java.util.HashMap;
import java.util.Map;

/** In-memory API test double; never included in the release JAR. */
public final class SageTV {
    public static final String[] UIS = {"VibeUI", "LegacyUI"};
    public static final Map<String, String> SERVER = new HashMap<String, String>();
    public static final Map<String, String> CLIENT = new HashMap<String, String>();

    public static Object api(String name, Object[] args) {
        if ("GetServerProperty".equals(name)) {
            String value = SERVER.get(String.valueOf(args[0]));
            return value == null ? args[1] : value;
        }
        if ("SetServerProperty".equals(name)) {
            SERVER.put(String.valueOf(args[0]), String.valueOf(args[1]));
            return null;
        }
        if ("GetUIContextNames".equals(name)) return UIS.clone();
        if ("GetInstalledPlugins".equals(name)) return new Object[]{"FFmpegPlugin"};
        // Stock Core normalizes repository plugin IDs to lowercase.
        if ("GetPluginIdentifier".equals(name)) return "sagetvffmpegpluginlinux";
        if ("GetPluginConfigValue".equals(name)) return "healthy";
        throw new AssertionError("Unexpected API " + name);
    }

    public static Object apiUI(String ui, String name, Object[] args) {
        if ("GetProperty".equals(name)) {
            String value = CLIENT.get(ui + ":" + args[0]);
            return value == null ? args[1] : value;
        }
        if ("SetProperty".equals(name)) {
            CLIENT.put(ui + ":" + args[0], String.valueOf(args[1]));
            return null;
        }
        throw new AssertionError("Unexpected UI API " + name);
    }
}
