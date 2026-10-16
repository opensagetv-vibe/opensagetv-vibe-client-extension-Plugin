# OpenSageTV Vibe Client Extension Plugin

Source: <https://github.com/opensagetv-vibe/opensagetv-vibe-client-extension-Plugin>.
Source publication is approved; binary release and SageTV catalog availability
remain gated by the unfinished DVD acceptance in `TASKS.md`. Do not interpret
the development0.1.0 package or source repository as a qualified public plugin
release. The diagnostic receiver's stock Linux/Windows acceptance is complete.

An optional, stock-`Sage.jar`-compatible server-side companion for the Vibe
Android client. The first extension point is SageTV's existing per-UI
`media_player_plugin_class/dvd` property. Other clients keep SageTV's native
player. This project does **not** replace SageTV's global MiniClient protocol.

The current unreleased candidate also provides an independent default-off,
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
gates remain open, and this is not a released feature. A matched stock test
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
the opt-in, physical, and rollback gates. No GitHub publication is implied.
