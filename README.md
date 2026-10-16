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
