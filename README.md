# OpenSageTV Vibe Client Extension Plugin

Source: <https://github.com/opensagetv-vibe/opensagetv-vibe-client-extension-Plugin>.
Version0.1.0 is the first qualified **beta** release for stock Linux/Windows
SageTV9.2.2 or newer. See [releases](https://github.com/opensagetv-vibe/opensagetv-vibe-client-extension-Plugin/releases)
and `docs/STOCK_PHYSICAL_GATE.md` for the tested scope. GitHub publication,
catalog submission and availability in SageTV's generated catalog are separate
steps. [0.1.0 beta](https://github.com/opensagetv-vibe/opensagetv-vibe-client-extension-Plugin/releases/tag/v0.1.0)
is public; [catalog PR130](https://github.com/OpenSageTV/sagetv-plugin-repo/pull/130)
is submitted and awaits upstream review/aggregation, not already in-app availability.

An optional, stock-`Sage.jar`-compatible server-side companion for the Vibe
Android client. The first extension point is SageTV's existing per-UI
`media_player_plugin_class/dvd` property. Other clients keep SageTV's native
player. This project does **not** replace SageTV's global MiniClient protocol.

The plugin also provides an independent default-off,
upload-only client diagnostic receiver on port8272. It has no browser pages,
statistics viewer or report-download routes: that web server belongs inside
the Android Vibe client. Redacted
reports stay in `vibe-client-logs` inside the server directory. Android's main
settings opt-in is separate from SMB and DVD; missing service leaves local
logs/playback intact. See `docs/DIAGNOSTIC_SERVER.md` for upload setup, retention,
privacy and the remaining physical release gates.

The first build provides a stock DVD-player adapter and safe per-UI opt-in
controls through SageTV's installed-plugin Configure screen. A separate,
default-off **Recover DVD After Reload** control passed a focused generated-DVD
reconnect gate on stock `.175` and the non-Pro Fire TV: the main title and its
advancing clock survived a forced graphics-socket reconnect. Broader physical
gates and limitations are documented below. A matched stock test
shows that Stop/Watch can legitimately resume a previously played title.
Do not require a root menu when the same stock sequence resumes the title,
or treat that alone as plugin recovery. New transports remain
gated work. A build alone does not install anything on a server or Android
device.

The stock player hook also bounds negative DVD seeks and converts malformed
menu-control index failures into SageTV's checked DVD error for that control
job. These are player-boundary protections, not replacements for private VM,
push-loop, or MiniClient capability changes in modified Core. The full
feature-by-feature boundary is in `docs/DVD_CORE_PARITY.md`; stock-server
compatibility does not imply complete modified-Core DVD parity.

The Configure screen also has a default-off **Trace DVD Reload** switch. It
records adapter construction, load, inactive-file and free events in the SageTV
server log for a targeted reconnect test. It does not change playback or
replace the stock DVD navigation path.

`devmode=true` lets SageTV discover local development packages, but updating
an installed Java JAR still requires a SageTV restart. Do not restart while a
recording is active; leave the update pending and use the stock DVD property
until an idle restart window is available.

FFmpeg/MIM is a separate plugin. This project discovers its installed health
through SageTV's plugin API and will use its existing session/transform API for
future media-processing features; it must not duplicate MIM process launching,
hardware policy, captions, or INI configuration.

Run `dev.cmd all` on Windows or `./dev.sh all` on Linux/WSL with a stock
SageTV JAR at `.deps/stock/Sage.jar` or `SAGETV_JAR`. See `WORKFLOW.md` for
the opt-in, physical, and rollback gates. Building does not publish or install.

## Installation and safe defaults

Development0.1.28 includes default-off **Allow Android Owned DVD Actions**.
Android Video > DVD playback can explicitly start/save/resume/clear the current
owned DVD using its acknowledged per-load challenge. Missing/off/stale services
leave stock playback intact. Video > Server playback provides an on-demand
read-only observation, never a renderer-clock replacement or background poll.
See docs/STOCK_MITIGATIONS.md for setup, trusted-LAN limits and qualification.
Paused Start/Resume now starts the navigator, and transformed title output is
retained until the destination NAV rather than discarded on read-ahead CELL.
Linux Native/Copy/full_gpu controls and natural-end gates pass, including exact
Non-Pro preference/bookmark/hooks/options/power restoration and final idle/stock
integrity checks. Windows remains deferred; development packages are unpublished.

Unreleased development0.1.25 adds optional read-only **Publish Stock Playback
State**, explicit current-owned DVD start/save/resume/clear actions and a
reviewed imported descriptive-metadata editor. State is a public API provider,
not an automatically used Android clock replacement. Metadata changes require
Preview and Apply and also update SageTV's sidecar; structural format repair
remains unavailable. Stock232/Non-Pro focused gates and exact restoration are
recorded under VCE-018/019/020; VCE-021 finds no need for another artwork
normalizer. See [stock mitigations](docs/STOCK_MITIGATIONS.md) for usage and
limits. All optional playback features remain off by default; Windows deferred.

Development0.1.19 adds default-off **Relay STV Caption State**. It reads only
the exact connected UI's public STV caption selection and publishes bounded
Off/CC1/CC2 state on the existing LAN8273 listener. An acknowledged current
Android load can resolve its virtual caption slots locally, including DVB
bitmaps; bitmap pixels never travel through the text callback. No caption
player hook, FastLoad change, Core reflection or production MCP is needed.
Missing/disabled/older service leaves ordinary playback and the explicit
Android caption/DVB choices intact. Linux qualification is recorded in the
completed VCE-016 result, including independent legacy/GSY and Direct Copy DVB.

Development0.1.20 adds default-off **Skip DVD Previews to Root Menu** for the
owned navigator. It snapshots policy at load and makes one public VM request
to the authored root when the disc starts in a preview title. Start Longest
DVD Title takes priority. A missing/disallowed root retains native playback;
normal later Play/title/menu operations are not rewritten. Configure this in
the plugin's STV screen: the historical Core-only Android DISC preferences are
not implicitly treated as negotiated plugin settings. VCE-017 gates it using
an actual preview/root/main fixture, not a disc already starting at its root.
Development0.1.22 completes that Linux gate and retains presented title metadata
until the next NAV, rather than discarding it on an early read-ahead CELL.
See docs/LINUX_STOCK_CAPABILITIES.md for current functional scope and limits;
Windows remains deferred and no modified Core is installed.

Development0.1.10 additionally provides an **Owned DVD Navigator** for an
explicitly selected UI. It composes the public stock VM/reader and stock media
commands instead of patching MiniDVDPlayer's private loop. It defaults off;
its Native/Copy/Transcode and Start Longest Title options have bounded Linux
stock232/Non-Pro qualification, not Windows or all-format certification.
Native menus/title/seek, authored Spanish audio, transformed startup/forward
seek and long Copy/Transcode pauses have scoped Non-Pro/stock232 evidence.
Client05 plus FFmpeg1.11 also qualify Native English, Copy English/Spanish and
Transcode English subtitles to the authored fixture scope. Affected
reload/provider/lifecycle/old-client/preview/menu-end gates now pass with client07
and FFmpeg1.13; see the compact VCE-003 qualification record for exact scope.
DVD actually uses Media3 by client factory policy, not every selected engine.
Windows is DEFERRED; PAL/angles/sparse-SPU/exact-cadence remain unqualified.
All borrowed test settings/power are restored. Do not confuse these
bounded gates with complete DVD parity or the published0.1.0 beta.

Copy/Transcode requires a per-load capability acknowledgement from Vibe on
the optional LAN API port8273. This has no browser, logs viewer, files or media
controls; it only acknowledges a bounded current-load challenge. Missing/old
clients remain native. Diagnostic uploads on8272 and MCP are independent.
Only the separate FFmpeg/MIM plugin owns media transformation/processes.

Development0.1.1 adds a separate default-off **Fix TS Restart at Beginning**
option. Enable the extension, select only the intended Vibe UI, turn that
option on, then use **Enable Selected TS Client** for its next TS load.
**Restore Selected TS Client** restores the previous TS property. DVD and
other UIs are untouched; a different player override is refused. The wrapper
subclasses stock MiniPlayer and routes only an explicit zero seek through
stock Seek(1 ms), avoiding FastMpeg2Reader's stale pending-target zero shortcut.
One millisecond is below one video frame; this is not a frame-exact-zero claim.
Other targets are unchanged. Feature Off or Standard-plugin Disable delegates
stock behavior. No private Core fields, new pusher or runtime MCP dependency.
This development feature is not yet physically qualified/released;0.1.0
remains the published beta. Updating the loaded JAR may require an idle restart.

Install **OpenSageTV Vibe Client Extension** from SageTV's beta plugin catalog
once its submitted entry is aggregated, or use the versioned GitHub package.
Under Installed Plugins > Configure, select only the intended Vibe client and
enable its DVD adapter. Recovery, tracing and diagnostic reception are separate
default-off options. Other clients retain their player setting. Updating an
already loaded JAR may require an idle SageTV restart; do not reset settings.

Native DVD recovery and diagnostic uploads need neither Core MCP nor FFmpeg/MIM
at runtime. Future media transforms must use the separate FFmpeg/MIM provider;
this release does **not** implement MIM-owned DVD streaming or full modified-
Core parity. See `docs/DVD_CORE_PARITY.md` for those explicit API boundaries.
