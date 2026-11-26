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
    public static final String VERSION = "0.1.29";
    public static final String PLAYER_CLASS =
            "org.opensagetv.vibe.clientextension.ClientExtensionDvdPlayer";
    public static final String OWNED_DVD_PLAYER_CLASS =
            "org.opensagetv.vibe.clientextension.PluginOwnedDvdPlayer";
    private static final String PLAYER_PROPERTY = "media_player_plugin_class/dvd";
    public static final String TS_PLAYER_CLASS =
            "org.opensagetv.vibe.clientextension.ClientExtensionTsPlayer";
    private static final String TS_PLAYER_PROPERTY = "media_player_plugin_class/ts";
    private static final String SAGEMC_DVD_SKIP_PROPERTY = "sagemc/default_DVD_FFREW";
    private static final String PREFIX = "vibe/client_extension/";
    private static final String[] SETTINGS = {
            "enabled", "dvd.tv_skip_keys", "reload.recovery", "trace.dvd", "dvd.owned_navigation",
            "dvd.owned_mode", "dvd.skip_menus", "dvd.skip_previews", "target.client",
            "action.enable_client", "action.disable_client", "diagnostics.enabled", "diagnostics.port",
            "video.restart_fix", "action.enable_ts_client", "action.disable_ts_client",
            "video.caption_bridge", "video.state_bridge", "dvd.client_controls",
            "action.dvd_save_resume", "action.dvd_resume", "action.dvd_beginning", "action.dvd_clear_resume",
            "metadata.id", "metadata.field", "metadata.value", "action.metadata_preview", "action.metadata_apply"
    };
    private DiagnosticUploadReceiver diagnosticReceiver;
    private DvdCapabilityReceiver dvdCapabilities;
    private volatile String diagnosticStatus = "Off; no upload receiver";
    private volatile String status = "disabled; stock DVD player retained";
    private final ImportedMetadataRepair metadataRepair = new ImportedMetadataRepair();
    private String metadataId = "", metadataField = "Title", metadataValue = "";
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
        configureDvdCapabilities();
    }
    public synchronized void stop() {
        long generation = LIFETIME.get();
        if ((generation & 1L) != 0L) LIFETIME.compareAndSet(generation, generation + 1L);
        ClientExtensionDvdPlayer.clearPendingRecovery();
        OwnedDvdReloadState.clear();
        PlaybackCaptionState.invalidate();
        metadataRepair.clear();
        if (diagnosticReceiver != null) diagnosticReceiver.stop();
        diagnosticReceiver = null;
        if (dvdCapabilities != null) dvdCapabilities.stop();
        dvdCapabilities = null;
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
        if ("metadata.id".equals(key)) return metadataId;
        if ("metadata.field".equals(key)) return metadataField;
        if ("metadata.value".equals(key)) return metadataValue;
        if ("video.state_bridge".equals(key) || "dvd.client_controls".equals(key)) return serverGet(key, "false");
        if ("dvd.owned_mode".equals(key)) return ownedDvdMode();
        if ("dvd.skip_menus".equals(key) || "dvd.skip_previews".equals(key)) return serverGet(key, "false");
        if ("dvd.owned_navigation".equals(key)) return serverGet(key, "false");
        if ("enabled".equals(key)) return serverGet("enabled", "false");
        if ("dvd.tv_skip_keys".equals(key)) return serverGet("dvd.tv_skip_keys", "false");
        if ("reload.recovery".equals(key)) return serverGet("reload.recovery", "false");
        if ("trace.dvd".equals(key)) return serverGet("trace.dvd", "false");
        if ("video.restart_fix".equals(key)) return serverGet(key, "false");
        if ("video.caption_bridge".equals(key)) return serverGet(key, "false");
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
        if ("video.state_bridge".equals(key) || "dvd.client_controls".equals(key)) return CONFIG_BOOL;
        if ("metadata.field".equals(key)) return CONFIG_CHOICE;
        if ("enabled".equals(key) || "dvd.tv_skip_keys".equals(key)
                || "reload.recovery".equals(key) || "trace.dvd".equals(key)
                || "diagnostics.enabled".equals(key) || "video.restart_fix".equals(key)
                || "dvd.owned_navigation".equals(key) || "dvd.skip_menus".equals(key) || "dvd.skip_previews".equals(key)
                || "video.caption_bridge".equals(key)) return CONFIG_BOOL;
        if ("dvd.owned_mode".equals(key)) return CONFIG_CHOICE;
        if ("diagnostics.port".equals(key)) return CONFIG_INTEGER;
        if ("target.client".equals(key)) return CONFIG_CHOICE;
        if (key != null && key.startsWith("action.")) return CONFIG_BUTTON;
        return CONFIG_TEXT;
    }
    public void setConfigValue(String key, String value) {
        if (key != null && key.startsWith("metadata.")) {
            metadataRepair.clear();
            if ("metadata.id".equals(key)) metadataId=value;
            else if ("metadata.field".equals(key)) metadataField=value;
            else if ("metadata.value".equals(key)) metadataValue=value;
        } else if ("action.metadata_preview".equals(key) || "action.metadata_apply".equals(key)) {
            try {
                if (activeLifecycle()==0L || !enabled()) throw new IllegalStateException("Enable the extension first");
                status="action.metadata_preview".equals(key)
                        ? metadataRepair.preview(metadataId,metadataField,metadataValue) : metadataRepair.apply();
            } catch (Exception refused) { status="Metadata edit refused: " + refused.getMessage(); }
        } else if ("action.dvd_save_resume".equals(key) || "action.dvd_resume".equals(key)
                || "action.dvd_beginning".equals(key) || "action.dvd_clear_resume".equals(key)) {
            // STV buttons use the selected UI; bounded commissioning can pass
            // one exact connected identity without changing that preference.
            status=PluginOwnedDvdPlayer.requestIntent(value!=null && value.matches("[0-9A-Fa-f]{12}")
                    ? value : serverGet("target",""),key);
        } else if ("dvd.client_controls".equals(key)) {
            // The owned-DVD listener already exists; do not invalidate the
            // active load's representation handshake merely to toggle controls.
            serverPut(key,Boolean.toString(Boolean.parseBoolean(value)));
        } else if ("video.state_bridge".equals(key)) {
            serverPut(key,Boolean.toString(Boolean.parseBoolean(value))); configureDvdCapabilities();
        } else if ("dvd.owned_mode".equals(key)) {
            if (!"native".equals(value) && !"copy".equals(value) && !"transcode".equals(value))
                throw new IllegalArgumentException("Select native, copy or transcode");
            serverPut(key, value);
        } else if ("dvd.skip_menus".equals(key) || "dvd.skip_previews".equals(key)) {
            serverPut(key, Boolean.toString(Boolean.parseBoolean(value)));
        } else if ("dvd.owned_navigation".equals(key)) {
            serverPut(key, Boolean.toString(Boolean.parseBoolean(value)));
            configureDvdCapabilities();
        } else if ("diagnostics.enabled".equals(key)) {
            serverPut(key, Boolean.toString(Boolean.parseBoolean(value))); configureDiagnostics();
        } else if ("diagnostics.port".equals(key)) {
            int port = Integer.parseInt(value);
            if (port < 1024 || port > 65535) throw new IllegalArgumentException("Port must be 1024-65535");
            serverPut(key, Integer.toString(port)); configureDiagnostics();
        } else if ("enabled".equals(key)) {
            serverPut("enabled", Boolean.toString(Boolean.parseBoolean(value)));
            configureDvdCapabilities();
            status = enabled() ? "ready; select a Vibe client and enable it" :
                    "disabled; existing client hook will behave as stock";
        } else if ("dvd.tv_skip_keys".equals(key)) {
            serverPut("dvd.tv_skip_keys", Boolean.toString(Boolean.parseBoolean(value)));
            applySelectedClientDvdSkipPreference();
        } else if ("reload.recovery".equals(key)) {
            serverPut("reload.recovery", Boolean.toString(Boolean.parseBoolean(value)));
        } else if ("trace.dvd".equals(key)) {
            serverPut("trace.dvd", Boolean.toString(Boolean.parseBoolean(value)));
        } else if ("video.restart_fix".equals(key)) {
            serverPut(key, Boolean.toString(Boolean.parseBoolean(value)));
        } else if ("video.caption_bridge".equals(key)) {
            serverPut(key, Boolean.toString(Boolean.parseBoolean(value)));
            configureDvdCapabilities();
        } else if ("target.client".equals(key)) {
            if (isActiveUi(value)) serverPut("target", value);
            else status = "Client not connected; no setting changed";
        } else if ("action.enable_client".equals(key)) {
            enableSelectedClient();
        } else if ("action.disable_client".equals(key)) {
            disableSelectedClient();
        } else if ("action.enable_ts_client".equals(key)) {
            configureSelectedTsClient(true);
        } else if ("action.disable_ts_client".equals(key)) {
            configureSelectedTsClient(false);
        }
    }
    public void setConfigValues(String key, String[] values) { }
    public String[] getConfigOptions(String key) {
        if ("metadata.field".equals(key)) return ImportedMetadataRepair.FIELDS.clone();
        if ("dvd.owned_mode".equals(key)) return new String[]{"native", "copy", "transcode"};
        if (!"target.client".equals(key)) return null;
        List<String> contexts = activeUis();
        return contexts.toArray(new String[contexts.size()]);
    }
    public String getConfigHelpText(String key) {
        if ("dvd.client_controls".equals(key)) return "Default off. Allow the current acknowledged Android DVD load to start/save/resume/clear its owned bookmark over trusted LAN8273. No arbitrary seeks or APIs.";
        if (key != null && (key.startsWith("metadata.") || key.startsWith("action.metadata_")))
            return "Imported non-TV media only. Preview, review the current/replacement values, then Apply Reviewed Edit. "
                    + "No automatic edits or structural format repair. Changes also update the stock sidecar. " + status;
        if (key != null && key.startsWith("action.dvd_")) return "Explicit selected owned DVD session only. "
                + "Save its current title/time, resume that same disc, or start its main feature. Stock Watch is unchanged. " + status;
        if ("video.state_bridge".equals(key)) return "Default off. Read-only exact-UI playback snapshot on LAN8273. "
                + "Public stock API times, not a new hardware clock or a playback control. No filenames/titles or MCP dependency.";
        if ("dvd.skip_previews".equals(key)) return "Default off; owned navigator only, next DVD load. "
                + "Skip a first-play title to the authored root once. Start Longest DVD Title takes priority. "
                + "A missing/disallowed root retains normal native playback; later menu/title choices are unchanged.";
        if ("video.caption_bridge".equals(key)) return "Default off. Relay this opted-in Vibe UI's STV Off/CC1/CC2 to a matching current Android load. "
                + "Uses the existing LAN capability API, no browser/MCP dependency. Missing or older clients keep stock behavior. "
                + "Reads the public STV selection API only; no player hook or load/seek changes.";
        if ("dvd.owned_mode".equals(key)) return "Owned navigator only, next DVD load. Native uses unchanged DVD bytes. "
                + "Copy/Transcode require this load's Vibe capability handshake and the separate FFmpeg/MIM plugin; "
                + "older/missing/disabled/unavailable providers retain native DVD. Menus always remain native.";
        if ("dvd.skip_menus".equals(key)) return "Owned navigator only, next load. Start the longest authored title "
                + "instead of the first-play/menu chain. Explicit title selection is never rewritten.";
        if ("dvd.owned_navigation".equals(key))
            return "Development-only independent DVD navigator. Default off. Select only the intended Vibe UI, "
                    + "then Enable Selected Client; takes effect on its next DVD load. Stock Core remains unchanged. "
                    + "Not yet physically qualified; do not enable for ordinary use.";
        if ("video.restart_fix".equals(key))
            return "Default off. Enable the extension and explicitly select a Vibe TS client. "
                    + "Restart at zero uses stock Seek(1 ms), avoiding its stale seek-target shortcut. "
                    + "Other seek targets, DVD and other clients are unchanged.";
        if ("action.enable_ts_client".equals(key))
            return "Install the TS-only player hook for the selected Vibe UI on its next load. "
                    + "Refuses an existing third-party player. " + status;
        if ("action.disable_ts_client".equals(key))
            return "Restore only this plugin's TS hook; leave DVD and other UIs unchanged. " + status;
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
        if ("dvd.client_controls".equals(key)) return "Allow Android Owned DVD Actions";
        if ("video.state_bridge".equals(key)) return "Publish Stock Playback State";
        if ("action.dvd_save_resume".equals(key)) return "Save Current Owned DVD Resume Point";
        if ("action.dvd_resume".equals(key)) return "Resume Saved Owned DVD Point";
        if ("action.dvd_beginning".equals(key)) return "Start Owned DVD Main Feature from Beginning";
        if ("action.dvd_clear_resume".equals(key)) return "Clear Saved Owned DVD Resume Point";
        if ("metadata.id".equals(key)) return "Imported MediaFile ID";
        if ("metadata.field".equals(key)) return "Descriptive Metadata Field";
        if ("metadata.value".equals(key)) return "Replacement Metadata Value";
        if ("action.metadata_preview".equals(key)) return "Preview Imported Metadata Edit";
        if ("action.metadata_apply".equals(key)) return "Apply Reviewed Metadata Edit";
        if ("dvd.skip_previews".equals(key)) return "Skip DVD Previews to Root Menu";
        if ("video.caption_bridge".equals(key)) return "Relay STV Caption State (Development)";
        if ("dvd.owned_mode".equals(key)) return "Owned DVD Transport (Development)";
        if ("dvd.skip_menus".equals(key)) return "Start Longest DVD Title";
        if ("dvd.owned_navigation".equals(key)) return "Owned DVD Navigator (Development)";
        if ("video.restart_fix".equals(key)) return "Fix TS Restart at Beginning";
        if ("action.enable_ts_client".equals(key)) return "Enable Selected TS Client";
        if ("action.disable_ts_client".equals(key)) return "Restore Selected TS Client";
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
        metadataRepair.clear(); metadataId=""; metadataField="Title"; metadataValue="";
        serverPut("video.state_bridge","false");
        serverPut("dvd.client_controls","false");
        serverPut("dvd.owned_mode", "native");
        serverPut("dvd.skip_menus", "false");
        serverPut("dvd.skip_previews", "false");
        serverPut("dvd.owned_navigation", "false");
        serverPut("enabled", "false");
        serverPut("dvd.tv_skip_keys", "false");
        serverPut("reload.recovery", "false");
        serverPut("trace.dvd", "false");
        serverPut("video.restart_fix", "false");
        serverPut("video.caption_bridge", "false");
        serverPut("diagnostics.enabled", "false");
        serverPut("diagnostics.port", "8272");
        configureDiagnostics();
        serverPut("target", "");
        configureDvdCapabilities();
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
            if (!current.isEmpty() && !isDvdHook(current)) {
                status = "Refused: selected client already has a different DVD player";
                return;
            }
            String key = backupKey(client);
            if (!isDvdHook(current)) {
                serverPut(key, current);
                String previousSkip = String.valueOf(SageTV.apiUI(client, "GetProperty",
                        new Object[]{SAGEMC_DVD_SKIP_PROPERTY, ""}));
                serverPut(skipBackupKey(client), previousSkip);
            }
            SageTV.apiUI(client, "SetProperty", new Object[]{PLAYER_PROPERTY,
                    ownedDvdEnabled() ? OWNED_DVD_PLAYER_CLASS : PLAYER_CLASS});
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
            if (!isDvdHook(current)) {
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

    private static boolean isDvdHook(String value) {
        return PLAYER_CLASS.equals(value) || OWNED_DVD_PLAYER_CLASS.equals(value);
    }
    static boolean ownedDvdEnabled() {
        return activeLifecycle() != 0L && enabled()
                && Boolean.parseBoolean(serverGet("dvd.owned_navigation", "false"));
    }
    static boolean captionBridgeEnabled() {
        return activeLifecycle() != 0L && enabled()
                && Boolean.parseBoolean(serverGet("video.caption_bridge", "false"));
    }
    static boolean playbackStateEnabled() {
        return activeLifecycle()!=0L && enabled() && Boolean.parseBoolean(serverGet("video.state_bridge","false"));
    }
    static boolean dvdClientControlsEnabled() {
        return ownedDvdEnabled() && Boolean.parseBoolean(serverGet("dvd.client_controls","false"));
    }
    static String ownedDvdMode() {
        String value = serverGet("dvd.owned_mode", "native");
        return "copy".equals(value) || "transcode".equals(value) ? value : "native";
    }
    static boolean ownedDvdSkipMenus() {
        return Boolean.parseBoolean(serverGet("dvd.skip_menus", "false"));
    }
    static boolean ownedDvdSkipPreviews() {
        return Boolean.parseBoolean(serverGet("dvd.skip_previews", "false"));
    }
    static boolean ownedDvdTrace() {
        return Boolean.parseBoolean(serverGet("trace.dvd", "false"));
    }
    static boolean ownedDvdRecovery() {
        return ownedDvdEnabled() && Boolean.parseBoolean(serverGet("reload.recovery", "false"));
    }
    private synchronized void configureDvdCapabilities() {
        if (dvdCapabilities != null) dvdCapabilities.stop();
        dvdCapabilities = null;
        if (!ownedDvdEnabled() && !captionBridgeEnabled() && !playbackStateEnabled()) return;
        DvdCapabilityReceiver candidate = new DvdCapabilityReceiver();
        try { candidate.start(); dvdCapabilities = candidate; }
        catch (java.io.IOException unavailable) {
            candidate.stop();
            System.out.println("[VibeClientExtension] DVD capability handshake unavailable; native only");
        }
    }

    static long tsRestartTarget(long requestedMs) {
        return requestedMs == 0L && activeLifecycle() != 0L && enabled()
                && Boolean.parseBoolean(serverGet("video.restart_fix", "false"))
                ? 1L : requestedMs;
    }

    private void configureSelectedTsClient(boolean install) {
        String client = serverGet("target", "");
        if (!isActiveUi(client)) { status = "Selected client is not connected"; return; }
        if (install && (activeLifecycle() == 0L || !enabled()
                || !Boolean.parseBoolean(serverGet("video.restart_fix", "false")))) {
            status = "Enable extension and TS restart fix first; no client changed";
            return;
        }
        try {
            String current = String.valueOf(SageTV.apiUI(client, "GetProperty",
                    new Object[]{TS_PLAYER_PROPERTY, ""}));
            String backup = "previous_ts/" + Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(client.getBytes(StandardCharsets.UTF_8));
            if (install) {
                if (!current.isEmpty() && !TS_PLAYER_CLASS.equals(current)) {
                    status = "Refused: selected client already has another TS player";
                    return;
                }
                if (!TS_PLAYER_CLASS.equals(current)) serverPut(backup, current);
                SageTV.apiUI(client, "SetProperty", new Object[]{TS_PLAYER_PROPERTY, TS_PLAYER_CLASS});
                status = "TS restart fix selected for " + client + "; next TS load";
            } else if (TS_PLAYER_CLASS.equals(current)) {
                SageTV.apiUI(client, "SetProperty",
                        new Object[]{TS_PLAYER_PROPERTY, serverGet(backup, "")});
                status = "Prior TS player restored for " + client + "; next load";
            } else {
                status = "Selected client no longer uses this TS hook; no change made";
            }
        } catch (Exception error) {
            status = "TS hook change failed: " + error.getClass().getSimpleName();
        }
    }
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
            if (isDvdHook(currentPlayer)) applyDvdSkipPreference(client);
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
