package org.opensagetv.vibe.clientextension;

import java.io.File;

import sage.SageTV;
import sage.DVDMediaPlayer;
import sage.MiniDVDPlayer;

public final class ConfigContractTest {
    private static final String KEY = "media_player_plugin_class/dvd";
    private static final String SAGEMC_SKIP = "sagemc/default_DVD_FFREW";

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        check(DVDMediaPlayer.class.isAssignableFrom(ClientExtensionDvdPlayer.class),
                "DVD hook must implement stock DVD controls");
        check(ClientExtensionDvdPlayer.class.getSuperclass() == MiniDVDPlayer.class,
                "adapter must preserve the stock DVD player implementation");
        ClientExtensionPlugin plugin = new ClientExtensionPlugin(null);
        plugin.start();
        check("false".equals(plugin.getConfigValue("diagnostics.enabled")), "upload API defaults off");
        check("8272".equals(plugin.getConfigValue("diagnostics.port")), "default upload port");
        check(!plugin.getConfigLabel("diagnostics.enabled").contains("Web"), "no extension web viewer");
        for (String setting : plugin.getConfigSettings()) {
            check(!"status".equals(setting) && !"ffmpeg.status".equals(setting),
                    "read-only status must not open the STV edit keypad");
        }
        plugin.setConfigValue("target.client", "VibeUI");
        plugin.setConfigValue("action.enable_client", "1");
        check(!SageTV.CLIENT.containsKey("VibeUI:" + KEY), "default must stay stock");

        plugin.setConfigValue("enabled", "true");
        plugin.setConfigValue("trace.dvd", "true");
        check("true".equals(plugin.getConfigValue("trace.dvd")),
                "DVD trace must be an explicit persisted opt-in");
        check("false".equals(plugin.getConfigValue("reload.recovery")),
                "DVD reload recovery must default off");
        check("false".equals(plugin.getConfigValue("dvd.tv_skip_keys")),
                "dedicated DVD FF/RW skip compatibility must default off");
        plugin.setConfigValue("reload.recovery", "true");
        check("true".equals(plugin.getConfigValue("reload.recovery")),
                "DVD reload recovery must be an explicit persisted opt-in");
        long now = 10_000_000_000L;
        ClientExtensionDvdPlayer.ResumeSnapshot candidate =
                new ClientExtensionDvdPlayer.ResumeSnapshot(
                        new File("dvd/VIDEO_TS").getAbsolutePath(), 2, 64000L, now);
        check(candidate.matches(new File("dvd/VIDEO_TS"), 64500L, now + 1_000_000_000L),
                "same DVD and Core reload position should permit recovery");
        check(candidate.matches(new File("dvd/VIDEO_TS"), 0L, now + 1_100_000_000L),
                "a claimed snapshot must survive an immediate retry rebuild");
        check(!candidate.matches(new File("other/VIDEO_TS"), 64500L, now + 1_000_000_000L),
                "different disc must never reuse recovery state");
        ClientExtensionDvdPlayer.ResumeSnapshot unclaimed =
                new ClientExtensionDvdPlayer.ResumeSnapshot(
                        new File("dvd/VIDEO_TS").getAbsolutePath(), 2, 64000L, now);
        check(!unclaimed.matches(new File("dvd/VIDEO_TS"), 0L, now + 1_000_000_000L),
                "intentional restart at zero must remain at the menu");
        check(ClientExtensionDvdPlayer.resumePositionReached(660_000L, 630_000L),
                "resume verification must accept the bounded DVD seek tolerance");
        check(!ClientExtensionDvdPlayer.resumePositionReached(660_000L, 75_000L),
                "resume verification must reject a reader that stayed near title start");
        check(!candidate.matches(new File("dvd/VIDEO_TS"), 73000L, now + 1_000_000_000L),
                "an unrelated seek must not be mistaken for the old reload position");
        check(!candidate.matches(new File("dvd/VIDEO_TS"), 64500L, now + 6_000_000_000L),
                "expired recovery state must be ignored");
        check(ClientExtensionDvdPlayer.clampDiscSeekTime(-2500L) == 0L,
                "negative DVD skip must not reach the stock VM");
        check(ClientExtensionDvdPlayer.clampDiscSeekTime(45000L) == 45000L,
                "valid DVD seeks must remain unchanged");
        check(ClientExtensionDvdPlayer.recoveryReadyClock(90000L) == 10000L,
                "normal titles must wait for stock DVD Push to stabilize");
        check(ClientExtensionDvdPlayer.recoveryReadyClock(9000L) == 3000L,
                "short titles must not wait past their useful playback window");
        plugin.setConfigValue("action.enable_client", "1");
        check(ClientExtensionPlugin.PLAYER_CLASS.equals(SageTV.CLIENT.get("VibeUI:" + KEY)),
                "explicit Vibe UI must receive DVD adapter");
        // The opt-in controls only this adapter-owned UI. The default must
        // preserve the user's original SageMC setting, not force timed skips
        // merely because the DVD adapter was enabled. Android's arrow mapping
        // and held FF/RW behavior are a separate client feature.
        check("".equals(SageTV.CLIENT.get("VibeUI:" + SAGEMC_SKIP)),
                "default-off compatibility must retain prior SageMC DVD keys");
        SageTV.CLIENT.put("LegacyUI:" + SAGEMC_SKIP, "true");
        plugin.setConfigValue("dvd.tv_skip_keys", "true");
        check("true".equals(SageTV.CLIENT.get("VibeUI:" + SAGEMC_SKIP)),
                "explicit compatibility opt-in must enable selected UI skips");
        check("true".equals(SageTV.CLIENT.get("LegacyUI:" + SAGEMC_SKIP)),
                "changing compatibility must not rewrite another UI");
        plugin.setConfigValue("dvd.tv_skip_keys", "false");
        check("".equals(SageTV.CLIENT.get("VibeUI:" + SAGEMC_SKIP)),
                "turning compatibility off must restore original DVD keys");
        check(!SageTV.CLIENT.containsKey("LegacyUI:" + KEY),
                "other clients must not be changed");
        plugin.setConfigValue("action.disable_client", "1");
        check("".equals(SageTV.CLIENT.get("VibeUI:" + KEY)),
                "disable must restore original blank setting");
        check("".equals(SageTV.CLIENT.get("VibeUI:" + SAGEMC_SKIP)),
                "disable must restore prior SageMC DVD key behavior");

        SageTV.CLIENT.put("VibeUI:" + KEY, "third.party.Player");
        plugin.setConfigValue("action.enable_client", "1");
        check("third.party.Player".equals(SageTV.CLIENT.get("VibeUI:" + KEY)),
                "third-party override must not be replaced");
        check(plugin.getConfigValue("ffmpeg.status").equals("healthy"),
                "FFmpeg status must come from installed provider");
        plugin.setConfigValue("target.client", "not-connected");
        check(plugin.getConfigValue("target.client").equals("VibeUI"),
                "unknown UI must not be persisted");
        System.out.println("ConfigContractTest PASS");
    }
}
