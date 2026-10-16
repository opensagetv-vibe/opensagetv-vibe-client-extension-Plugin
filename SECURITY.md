# Security

Report security concerns privately to the OpenSageTV Vibe maintainers before
opening a public issue. Do not attach credentials, local server URLs, media
files, or unredacted SageTV/Android logs to a public report.

DVD extensions remain off by default, target one explicitly selected UI, and
never overwrite another player's per-UI setting.

The diagnostic upload-only receiver is a separate default-off, explicitly enabled
LAN HTTP surface. It accepts loopback/private IPv4 peers, not public addresses.
It has no login or encryption: private addressing is a boundary, not proof
that every LAN device is trusted. Anyone on that LAN could submit a bounded
report, but there are no read/list/download routes. The viewer is in Android
Vibe. Do not enable it on an untrusted/shared network or port-forward it.
The Android upload option also defaults off, pins the connected server's LAN
address and refuses redirects. Core MCP bearer credentials are never reused.

Only validated, size/count-bounded text ZIPs are stored in the plugin-owned
server subfolder. No archives are extracted; arbitrary file paths and server
configuration downloads are refused. Responses are non-cacheable and
scripts/CORS/frame embedding are disabled. The upload route
requires its explicit custom header, not a browser form POST. This is a
report intake API, not a browser viewer or server/playback-control interface.

Client redaction is best effort. Review a bundle before posting publicly;
programme names and manually logged private details might still be present.
Authentication/encryption would be required for a future untrusted-network
deployment and must receive its own review/gates. This LAN candidate is not
published until the receiver and stock-compatibility gates pass.
