# Changelog

## Unreleased

- Qualify upload-only receiver on232/non-Pro: disabled/no writes, enabled/manual
  exact stored ZIP/receipt hash/privacy, unavailable-service safe playback and
  reconnect. Restore settings; keep browser pages in Android only. Fresh stock
  Linux/Windows release-target qualification remains separate; no publication.

- Add a separate default-off LAN diagnostic upload-only receiver. Bounded,
  redacted client ZIPs stay in vibe-client-logs inside the server directory;
  the browser viewer runs inside Android Vibe, not this plugin. Server HTML,
  report download/listing and runtime-statistics routes are removed.
  No archive extraction, arbitrary server-file access, playback changes or
  runtime MCP dependency. Setup/retention/LAN privacy are documented.
  Stock-JAR upload/store/retention and removed-viewer contracts pass; Windows
  actual receipt/hash/advancing A/V are partial proof. User directs remaining
  gates to232/non-Pro and forbids175 restart; no release/full closure yet.

- Repair the reproduced stock reader read-ahead mismatch near title end:
  decoded title video can remain visible after the VM has entered menu domain.
  Retain only the same player/load/lifetime's observed title; reselect it through stock public DVD
  controls before that backward seek. Visible/intentional menus, stale
  lifetimes and unknown/changed titles are excluded. Stock Linux/Windows
  late-seek, normal seek/pause and intentional-menu/submenu gates pass on
  candidate95557727. Read-ahead can exceed39s; an arbitrary20s expiry was
  disproved by opt-in physical traces and replaced by title/lifecycle custody.

- Add default-off seek trace details from stock public DVD domain/title
  getters. Visible NAV buttons and a requested-time echo are not proof that
  the server-owned reader accepted a late seek. No private VM/STC writes.

- Honor Standard-plugin Disable/Enable independently of persisted feature
  settings. Stopped adapters delegate stock seek/control behavior; recovery
  workers and snapshots belong to one started lifetime and cannot resume
  after a stop/start cycle. User settings and client overrides are preserved.
  Focused stock-JAR tests and actual stock Linux/Windows lifecycle/recovery
  gates pass on candidateeb68de89; subsequent late-seek qualification remains
  separately revisioned.

- Cancel DVD recovery retries when a later user seek or player teardown takes
  ownership. A seek near title zero also discards only that UI's old claimed
  snapshot, while genuine same-disc reload handoffs and other UIs remain intact.
  Deterministic stock-JAR tests and stock Linux/Windows physical custody gates
  pass on candidateaa1 and affected lifecycle rechecks pass on candidateeb68.
- Qualify fresh installation through SageTV's public plugin manager on stock
  Windows, with unchanged Core and no plugin-registry edits. Native DVD
  playback/recovery acceptance is tracked separately from installation.

- Correct the intentional-restart test comparison: a stock run that only
  played the menu was not equivalent to one that played the main title.
  Matched stock playback also resumes the title after Stop/Watch. Withdraw
  the resulting unproven playback changes; preserve stock resume semantics.

- Correct the configuration regression test's obsolete expectation that
  enabling the adapter forces SageMC DVD timed skips. The existing default-off
  option preserves the original UI setting; explicitly test opt-in, opt-out
  restoration and another UI's untouched setting. No runtime/key behavior change.

- Verify the post-reload DVD clock after a settle interval and retry the
  adapter-owned public `Seek(long)` when stock DVD Push acknowledges the seek
  without moving its reader STC. The Pro ALADDIN stock-server gate required
  the third bounded attempt, retained title/position and A/V, and stopped back
  to a fully rendered SageMC screen.
- Guard the stock DVD-player hook against negative seek input and malformed
  menu-control indexes, without altering other clients or packaging Core.
- Record which modified-Core DVD changes cannot be implemented by the current
  stock Standard-plugin/API boundary; FFmpeg/MIM remains a separate provider.
- Created a stock-Core-compatible Vibe Client Extension project with per-UI
  DVD adapter selection and SageTV plugin configuration.
- Added default-off, bounded DVD adapter lifecycle tracing for physical
  stock-server reload diagnosis; no playback behavior changed.
- Reproduced a stock DVD reload reset and added default-off, same-UI/same-disc
  title-and-position recovery for focused physical validation. No release
  claim is made until the reconnect, restart, and fallback gates pass.
