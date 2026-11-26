package org.opensagetv.vibe.clientextension;

import sage.SageTV;
import sage.MiniPlayer;

/** Public hook/policy tests; never packages stock classes or private-field access. */
public final class TsRestartContractTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        ClientExtensionPlugin plugin = new ClientExtensionPlugin(null);
        plugin.start();
        check(ClientExtensionTsPlayer.class.getSuperclass() == MiniPlayer.class, "stock pusher retained");
        check("false".equals(plugin.getConfigValue("video.restart_fix")), "default off");
        plugin.setConfigValue("target.client", "VibeUI");
        plugin.setConfigValue("action.enable_ts_client", "1");
        String key = "VibeUI:media_player_plugin_class/ts";
        check(!SageTV.CLIENT.containsKey(key), "default leaves stock hook");
        plugin.setConfigValue("enabled", "true");
        plugin.setConfigValue("video.restart_fix", "true");
        plugin.setConfigValue("action.enable_ts_client", "1");
        check(ClientExtensionPlugin.TS_PLAYER_CLASS.equals(SageTV.CLIENT.get(key)), "selected TS hook");
        check(!SageTV.CLIENT.containsKey("VibeUI:media_player_plugin_class/dvd"), "DVD unchanged");
        check(!SageTV.CLIENT.containsKey("LegacyUI:media_player_plugin_class/ts"), "other UI unchanged");
        check(ClientExtensionPlugin.tsRestartTarget(0) == 1, "zero takes normal seek path");
        for (long target : new long[]{-1, 1, 60000, Long.MAX_VALUE})
            check(ClientExtensionPlugin.tsRestartTarget(target) == target, "other target unchanged");
        plugin.setConfigValue("video.restart_fix", "false");
        check(ClientExtensionPlugin.tsRestartTarget(0) == 0, "feature off delegates stock");
        plugin.setConfigValue("video.restart_fix", "true");
        plugin.stop();
        check(ClientExtensionPlugin.tsRestartTarget(0) == 0, "Standard Disable delegates stock");
        plugin.start();
        check(ClientExtensionPlugin.tsRestartTarget(0) == 1, "explicit setting retained on start");
        plugin.setConfigValue("action.enable_ts_client", "1");
        plugin.setConfigValue("action.disable_ts_client", "1");
        check("".equals(SageTV.CLIENT.get(key)), "idempotent enable preserves original backup");
        SageTV.CLIENT.put(key, "third.party.Player");
        plugin.setConfigValue("action.enable_ts_client", "1");
        check("third.party.Player".equals(SageTV.CLIENT.get(key)), "third-party hook refused");
        plugin.setConfigValue("action.disable_ts_client", "1");
        check("third.party.Player".equals(SageTV.CLIENT.get(key)), "do not overwrite newer owner");
        plugin.stop();
        System.out.println("PASS: opt-in TS restart hook, exact other seeks, lifecycle and restoration");
    }
}
