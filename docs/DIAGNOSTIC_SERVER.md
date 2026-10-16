# Client diagnostic upload receiver

Client Extension receives reports but does **not** host browser pages.
The diagnostic web server runs in Android Vibe (Android DIAG-003).
Server uploads/storage remain VCE-011 and Android DIAG-002, independent of
DVD, SMB, the Android viewer and Core MCP commissioning.

## Enable server uploads

1. In Installed Plugins > Vibe Client Extension > Configure, enable
   **Receive Client Diagnostic Reports** (default off). **Diagnostic Upload
   Port** defaults8272. This is an upload-only API, not a browser viewer.
2. In Android main startup Settings, enable **Send diagnostic logs to server**
   (default off). Match **Server diagnostic upload port** if changed.
3. Connect to that server. Uploads run at most once per minute while foreground,
   at existing checkpoints, and best-effort on background/exit. **Upload
   diagnostic report now** requests an immediate receipt/status.

Only GET /vibe/diagnostics/capabilities and POST /vibe/diagnostics/upload are
supported. Server root, report listing, HTML viewer, statistics and download
paths return404. For browser access separately enable Android's **Diagnostic
web server**, then open its **Diagnostic web address**, e.g. http://DEVICE:8272/.
The two default ports are on different hosts and serve different purposes.

## Storage, privacy and failure handling

Redacted reports are stored in vibe-client-logs inside the SageTV server
working directory. Names use a diagnostic installation ID, receipt time and
server UUID, never a client-selected path. ZIPs are not extracted. Limits:
8MiB compressed,32text entries,512KiB per entry,16MiB expanded; retention
64reports/256MiB. Disable/stop retains stored reports and existing client logs.

The client reuses existing redaction/log tails/traces/tests; file logging
must be enabled separately. Missing/disabled/old receivers, permissions,
disk-full or interrupted transfers never change playback, keys, media
settings or SMB credentials. Force-stop/power loss cannot guarantee a final
upload. No normal runtime MCP dependency or stock Sage.jar change is needed.

Opt-in trusted-LAN HTTP only, no login or encryption. Private addressing is
not authentication. Do not expose or forward the port. Anyone on that LAN
could submit a bounded report, but this API does not serve stored reports.
Redaction is best effort; review ZIPs before public issue submission.
No CORS, arbitrary paths, executable/media files or playback controls.

Core MCP's confirmed, checkpointed diagnostics.enabled control is for
commissioning only. Restore the exact prior client/receiver settings after
every test. The upload-only receiver is qualified for0.1.0 on stock Linux175
and Windows185/non-Pro25. Historical combined-viewer evidence is superseded;
the Android-hosted viewer remains a separate feature and opt-in.
