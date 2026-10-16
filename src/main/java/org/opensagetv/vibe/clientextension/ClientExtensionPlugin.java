package org.opensagetv.vibe.clientextension;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import sage.SageTV;
import sage.SageTVEventListener;
import sage.SageTVPlugin;
import sage.SageTVPluginRegistry;

/** Stock SageTV Standard-plugin configuration for the Vibe player hook. */
public final class ClientExtensionPlugin implements SageTVPlugin, SageTVEventListener {
    public static final String VERSION = "0.1.0";
    public static final String PLAYER_CLASS =
            "org.opensagetv.vibe.clientextension.ClientExtensionDvdPlayer";
    private static final String PLAYER_PROPERTY = "media_player_plugin_class/dvd";
    private static final String SAGEMC_DVD_SKIP_PROPERTY = "sagemc/default_DVD_FFREW";
    private static final String PREFIX = "vibe/client_extension/";
    private static final String[] SETTINGS = {
            "enabled", "dvd.tv_skip_keys", "reload.recovery", "trace.dvd", "target.client",
            "action.enable_client", "action.disable_client", "diagnostics.enabled", "diagnostics.port"
    };
    private DiagnosticUploadReceiver diagnosticReceiver;
    private volatile String diagnosticStatus = "Off; no upload receiver";
    private volatile String status = "disabled; stock DVD player retained";
    // Standard-plugin Disable calls stop/destroy but does not erase a UI's
    // player property or unload already-defined classes. Persisted enable
    // settings therefore cannot establish that the provider is running.
    // Odd generations are active; changing generation invalidates old workers
    // even if Disable/Enable completes between their two polling iterations.
    private static final AtomicLong LIFETIME = new AtomicLong();

    public ClientExtensionPlugin(SageTVPluginRegistry registry) { this(registry, false); }
    public ClientExtensionPlugin(SageTVPluginRegistry registry, boolean reset) {
        if (reset) resetConfig();
    }

    public synchronized void start() {
        long generation = LIFETIME.get();
        if ((generation & 1L) == 0L) LIFETIME.compareAndSet(generation, generation + 1L);
        status = enabled() ? "ready; select a Vibe client and enable it" :
                "disabled; stock DVD player retained";
        configureDiagnostics();
    }
    public synchronized void stop() {
        long generation = LIFETIME.get();
        if ((generation & 1L) != 0L) LIFETIME.compareAndSet(generation, generation + 1L);
        ClientExtensionDvdPlayer.clearPendingRecovery();
        if (diagnosticReceiver != null) diagnosticReceiver.stop();
        diagnosticReceiver = null;
        diagnosticStatus = "Stopped; stored reports retained";
        status = "stopped; existing client settings unchanged";
    }
    public void destroy() { stop(); }
    static long activeLifecycle() {
        long generation = LIFETIME.get();
        return (generation & 1L) == 0L ? 0L : generation;
    }
    public void sageEvent(String name, Map variables) { }

    public String[] getConfigSettings() { return SETTINGS.clone(); }
    public String getConfigValue(String key) {
        if ("enabled".equals(key)) return serverGet("enabled", "false");
        if ("dvd.tv_skip_keys".equals(key)) return serverGet("dvd.tv_skip_keys", "false");
        if ("reload.recovery".equals(key)) return serverGet("reload.recovery", "false");
        if ("trace.dvd".equals(key)) return serverGet("trace.dvd", "false");
        if ("diagnostics.enabled".equals(key)) return serverGet(key, "false");
        if ("diagnostics.port".equals(key)) return serverGet(key, "8272");
        if ("diagnostics.status".equals(key)) return diagnosticStatus;
        if ("target.client".equals(key)) return serverGet("target", "");
        if ("status".equals(key)) return status;
        if ("ffmpeg.status".equals(key)) return ffmpegStatus();
        return "";
    }
    public String[] getConfigValues(String key) { return null; }
    public int getConfigType(String key) {
        if ("enabled".equals(key) || "dvd.tv_skip_keys".equals(key)
                || "reload.recovery".equals(key) || "trace.dvd".equals(key)
                || "diagnostics.enabled".equals(key)) return CONFIG_BOOL;
        if ("diagnostics.port".equals(key)) return CONFIG_INTEGER;
        if ("target.client".equals(key)) return CONFIG_CHOICE;
        if (key != null && key.startsWith("action.")) return CONFIG_BUTTON;
        return CONFIG_TEXT;
    }
    public void setConfigValue(String key, String value) {
        if ("diagnostics.enabled".equals(key)) {
            serverPut(key, Boolean.toString(Boolean.parseBoolean(value))); configureDiagnostics();
        } else if ("diagnostics.port".equals(key)) {
            int port = Integer.parseInt(value);
            if (port < 1024 || port > 65535) throw new IllegalArgumentException("Port must be 1024-65535");
            serverPut(key, Integer.toString(port)); configureDiagnostics();
        } else if ("enabled".equals(key)) {
            serverPut("enabled", Boolean.toString(Boolean.parseBoolean(value)));
            status = enabled() ? "ready; select a Vibe client and enable it" :
                    "disabled; existing client hook will behave as stock";
        } else if ("dvd.tv_skip_keys".equals(key)) {
            serverPut("dvd.tv_skip_keys", Boolean.toString(Boolean.parseBoolean(value)));
            applySelectedClientDvdSkipPreference();
        } else if ("reload.recovery".equals(key)) {
            serverPut("reload.recovery", Boolean.toString(Boolean.parseBoolean(value)));
        } else if ("trace.dvd".equals(key)) {
            serverPut("trace.dvd", Boolean.toString(Boolean.parseBoolean(value)));
        } else if ("target.client".equals(key)) {
            if (isActiveUi(value)) serverPut("target", value);
            else status = "Client not connected; no setting changed";
        } else if ("action.enable_client".equals(key)) {
            enableSelectedClient();
        } else if ("action.disable_client".equals(key)) {
            disableSelectedClient();
        }
    }
    public void setConfigValues(String key, String[] values) { }
    public String[] getConfigOptions(String key) {
        if (!"target.client".equals(key)) return null;
        List<String> contexts = activeUis();
        return contexts.toArray(new String[contexts.size()]);
    }
    public String getConfigHelpText(String key) {
        if ("diagnostics.enabled".equals(key))
            return "Default off. Receive redacted reports in vibe-client-logs inside the server directory. "
                    + "Upload API only; the web viewer is in Android Vibe. Trusted LAN only, no login/encryption. " + diagnosticStatus;
        if ("diagnostics.port".equals(key)) return "Upload API port, default 8272. Not a browser web viewer. " + diagnosticStatus;
        if ("enabled".equals(key))
            return "Default off. Existing DVD playback stays stock until one client is explicitly enabled. " + status;
        if ("dvd.tv_skip_keys".equals(key))
            return "Compatibility option for making dedicated FF/RW perform one timeline skip. " +
                    "Leave off for real DVD 2x-16x trick play. Android Left/Right have separate " +
                    "title-skip and authored-menu mappings.";
        if ("reload.recovery".equals(key))
            return "Default off. Resume the same authored DVD title after a matching player reload. " +
                    "Does not affect other clients or FFmpeg/MIM settings.";
        if ("trace.dvd".equals(key))
            return "Log bounded DVD adapter lifecycle and reload evidence on the server. Default off.";
        if ("target.client".equals(key))
            return "Select only the connected Vibe Android UI. Other clients are never changed. " + status;
        if ("action.enable_client".equals(key))
            return "Set this UI's DVD player class only if it has no different player override. Provider: " + ffmpegStatus();
        if ("action.disable_client".equals(key))
            return "Restore the prior player for this UI. " + status;
        return status + ". Separate FFmpeg/MIM provider: " + ffmpegStatus();
    }
    public String getConfigLabel(String key) {
        if ("diagnostics.enabled".equals(key)) return "Receive Client Diagnostic Reports";
        if ("diagnostics.port".equals(key)) return "Diagnostic Upload Port";
        if ("enabled".equals(key)) return "Enable Vibe Client Extension";
        if ("dvd.tv_skip_keys".equals(key)) return "Make DVD FF/RW Skip Instead";
        if ("reload.recovery".equals(key)) return "Recover DVD After Reload";
        if ("trace.dvd".equals(key)) return "Trace DVD Reload";
        if ("target.client".equals(key)) return "Connected Client";
        if ("action.enable_client".equals(key)) return "Enable Selected Client";
        if ("action.disable_client".equals(key)) return "Restore Selected Client";
        return key;
    }
    public void resetConfig() {
        serverPut("enabled", "false");
        serverPut("dvd.tv_skip_keys", "false");
        serverPut("reload.recovery", "false");
        serverPut("trace.dvd", "false");
        serverPut("diagnostics.enabled", "false");
        serverPut("diagnostics.port", "8272");
        configureDiagnostics();
        serverPut("target", "");
        status = "reset; existing client hooks were not modified";
    }

    /** Optional data receiver only: never make DVD depend on diagnostic HTTP. */
    private synchronized void configureDiagnostics() {
        if (diagnosticReceiver != null) diagnosticReceiver.stop();
        diagnosticReceiver = null;
        if (activeLifecycle() == 0L || !Boolean.parseBoolean(serverGet("diagnostics.enabled", "false"))) {
            diagnosticStatus = "Off; stored reports retained"; return;
        }
        DiagnosticUploadReceiver candidate = null;
        try {
            candidate = new DiagnosticUploadReceiver(java.nio.file.Paths.get(System.getProperty("user.dir", ".")));
            candidate.start(Integer.parseInt(serverGet("diagnostics.port", "8272")));
            diagnosticReceiver = candidate; diagnosticStatus = "Ready for client uploads; no browser viewer";
        } catch (Exception error) {
            if (candidate != null) candidate.stop();
            diagnosticStatus = "Unavailable: " + error.getClass().getSimpleName() + "; DVD unchanged";
        }
    }

    private void enableSelectedClient() {
        if (!enabled()) { status = "Enable the plugin first; no client changed"; return; }
        String client = serverGet("target", "");
        if (!isActiveUi(client)) { status = "Selected client is not connected"; return; }
        try {
            String current = String.valueOf(SageTV.apiUI(client, "GetProperty",
                    new Object[]{PLAYER_PROPERTY, ""}));
            if (!current.isEmpty() && !PLAYER_CLASS.equals(current)) {
                status = "Refused: selected client already has a different DVD player";
                return;
            }
            String key = backupKey(client);
            if (!PLAYER_CLASS.equals(current)) {
                serverPut(key, current);
                String previousSkip = String.valueOf(SageTV.apiUI(client, "GetProperty",
                        new Object[]{SAGEMC_DVD_SKIP_PROPERTY, ""}));
                serverPut(skipBackupKey(client), previousSkip);
            }
            SageTV.apiUI(client, "SetProperty", new Object[]{PLAYER_PROPERTY, PLAYER_CLASS});
            applyDvdSkipPreference(client);
            status = "DVD adapter enabled for " + client + "; takes effect on next DVD load";
        } catch (Exception error) { status = "Enable failed: " + error.getClass().getSimpleName(); }
    }

    private void disableSelectedClient() {
        String client = serverGet("target", "");
        if (!isActiveUi(client)) { status = "Selected client is not connected"; return; }
        try {
            String current = String.valueOf(SageTV.apiUI(client, "GetProperty",
                    new Object[]{PLAYER_PROPERTY, ""}));
            if (!PLAYER_CLASS.equals(current)) {
                status = "Selected client no longer uses this DVD adapter; no change made";
                return;
            }
            SageTV.apiUI(client, "SetProperty", new Object[]{PLAYER_PROPERTY,
                    serverGet(backupKey(client), "")});
            SageTV.apiUI(client, "SetProperty", new Object[]{SAGEMC_DVD_SKIP_PROPERTY,
                    serverGet(skipBackupKey(client), "")});
            status = "Prior DVD player restored for " + client + "; next load uses it";
        } catch (Exception error) { status = "Restore failed: " + error.getClass().getSimpleName(); }
    }

    private static boolean enabled() { return Boolean.parseBoolean(serverGet("enabled", "false")); }
    private static String backupKey(String client) {
        return "previous/" + Base64.getUrlEncoder().withoutPadding().encodeToString(
                client.getBytes(StandardCharsets.UTF_8));
    }
    private static String skipBackupKey(String client) {
        return "previous_sagemc_dvd_skip/" +
                Base64.getUrlEncoder().withoutPadding().encodeToString(
                        client.getBytes(StandardCharsets.UTF_8));
    }

    private void applySelectedClientDvdSkipPreference() {
        String client = serverGet("target", "");
        if (!isActiveUi(client)) return;
        try {
            String currentPlayer = String.valueOf(SageTV.apiUI(client, "GetProperty",
                    new Object[]{PLAYER_PROPERTY, ""}));
            if (PLAYER_CLASS.equals(currentPlayer)) applyDvdSkipPreference(client);
        } catch (Exception error) {
            status = "DVD skip setting failed: " + error.getClass().getSimpleName();
        }
    }

    private static void applyDvdSkipPreference(String client) throws Exception {
        String value = Boolean.parseBoolean(serverGet("dvd.tv_skip_keys", "false"))
                ? "true" : serverGet(skipBackupKey(client), "");
        SageTV.apiUI(client, "SetProperty", new Object[]{SAGEMC_DVD_SKIP_PROPERTY, value});
    }
    private static String serverGet(String name, String fallback) {
        try { Object value = SageTV.api("GetServerProperty", new Object[]{PREFIX + name, fallback});
              return value == null ? fallback : String.valueOf(value); }
        catch (Exception error) { return fallback; }
    }
    private static void serverPut(String name, String value) {
        try { SageTV.api("SetServerProperty", new Object[]{PREFIX + name, value}); }
        catch (Exception error) { throw new IllegalStateException("Cannot save plugin setting", error); }
    }
    private static List<String> activeUis() {
        List<String> result = new ArrayList<String>();
        try {
            Object names = SageTV.api("GetUIContextNames", new Object[0]);
            if (names instanceof String[]) for (String name : (String[]) names)
                if (name != null && !name.isEmpty()) result.add(name);
        } catch (Exception ignored) { }
        return result;
    }
    private static boolean isActiveUi(String client) {
        return client != null && !client.isEmpty() && activeUis().contains(client);
    }

    private static String ffmpegStatus() {
        try {
            Object installed = SageTV.api("GetInstalledPlugins", new Object[0]);
            if (!(installed instanceof Object[])) return "not installed";
            for (Object plugin : (Object[]) installed) {
                String id = String.valueOf(SageTV.api("GetPluginIdentifier", new Object[]{plugin}));
                if (!"SageTVFFmpegPluginLinux".equalsIgnoreCase(id) &&
                        !"SageTVFFmpegPluginWinx64".equalsIgnoreCase(id)) continue;
                Object health = SageTV.api("GetPluginConfigValue",
                        new Object[]{plugin, "health.runtime"});
                if (health == null) return "installed; status unavailable";
                String summary = String.valueOf(health).replace('\r', ' ').replace('\n', ' ').trim();
                return summary.length() > 96 ? summary.substring(0, 93) + "..." : summary;
            }
        } catch (Exception ignored) { return "status unavailable"; }
        return "not installed; native DVD available";
    }
}
