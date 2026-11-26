# Changelog

## 0.1.29 release preparation - 2026-11-26

- User authorizes beta publication of the stock-compatible optional playback,
  DVD, resume and browser-profile integration documented below.
- Stock-JAR unit/contracts and approved deterministic packaging pass. Linux
  qualification remains scoped; Windows and original Pro field rows are not
  newly certified. Unselected clients keep their normal stock playback path.

## Task reconciliation2026-11-20 (documentation only)

- Close stale VCE-023 Linux profile qualification from final Web055 actual
  browser evidence, retaining original specification and platform limits.
  Windows/original Pro deferrals are separate. No runtime or release change.

## Unreleased development0.1.29

- Add explicit optional web-mpegps-v1 DVD capability profile. It reuses the
  existing navigator/control lease but restricts that load to native MPEG-PS
  for the browser bridge's existing HTML5 transcode. No second transform,
  global settings mutation or false Android TS/SPU claim. Existing Android
  revisions1..3 remain intact; downgrade/upshift from this browser profile is
  rejected for the same lease. Local contract/HTTP tests pass; physical Web
  integration/deployment remains pending. No stock Core changes/publication.

## Unreleased development0.1.28

- Preserve transformed title output across read-ahead menu CELL metadata;
  switch back to Native only at destination NAV after the VM's drain gate.
- Stock232/Non-Pro Copy/full_gpu resume and natural-end gates pass. Nine server
  options and136 device preferences restored, test bookmark cleared, awake
  lease inactive/original power, final0activity/stock hashes pass. See compact
  VCE-022 result; Windows remains deferred.

## Unreleased development0.1.27

- Fix explicit owned-DVD Start/Resume while paused: play the navigator so it
  can reach destination NAV and apply the pending seek. Save/Clear retain pause.

## Unreleased development0.1.26

- Add default-off Android owned-DVD controls on the existing LAN listener.
  Start/save/resume/clear require the exact acknowledged session challenge;
  recheck current player, lifetime and opt-in under serialized control.
- Refuse unknown action names rather than treating them as main-feature start.
  No Core changes, arbitrary APIs, runtime MCP dependency or clock override.

## Unreleased development0.1.25

- Add default-off read-only stock playback-state API without private renderer
  negotiation or a client clock override; current Android need not consume it.
- Add explicit current-owned DVD main-feature start and exact-disc/UI
  bookmark save/resume/clear, preserving normal stock Watch/watched policy.
- Add reviewed imported descriptive metadata Preview/Apply with bounded fields,
  stale-value refusal and readback. Disclose sidecar updates; no automatic or
  structural database/format repair. Actual generated fixture restored.
- Validate new API names against the real stock binary as well as isolated
  doubles. Stock232/Non-Pro Native/Copy/full_gpu intent and fallback gates pass;
  exact136prefs/hooks/options/power restored, final0activity/protected hashes.
- Document artwork no-change disposition: existing Java image path and
  container-only original-loader ABI fix, not a duplicate global normalizer.
  No Windows/publication/all-format parity claim.

## Unreleased development0.1.22

- Retain the presented DVD title's state/duration when the stock VM announces
  a read-ahead CELL; switch custody only at its destination NAV. Explicit
  seek/FLUSH still invalidates immediately. The authored preview exposed an
  eight-second menu duration while its thirty-second title was still visible.
- Add bounded CELL/NAV duration trace, valid authored default-button metadata
  and regression coverage; preserve the separate FFmpeg provider and stock Core.

## Unreleased development0.1.20

- Add default-off Skip DVD Previews to Root Menu in the stock plugin Configure
  screen. Snapshot startup policy once per owned load; longest-title startup
  wins and later explicit Play/menu controls are not skipped. A missing or
  disallowed root safely retains native playback. VCE-017 Linux gates complete
  in development0.1.22; Windows remains deferred.
- Preserve all existing caption, TS, diagnostic and provider boundaries. No
  native/Core payload or private VideoFrame mutation is included.

## Unreleased development0.1.19

- Add default-off, per-UI regular-media STV caption-state relay using only the
  public GetMediaPlayerClosedCaptionState API and existing LAN listener. Require an
  exact client/load acknowledgement; unknown/old/off/disabled clients stay
  stock. No caption player hook, FastLoad change or property mutation is needed.
  The stock getter preserves desired STV selection even if its decoder setter
  returns false; a successful getter alone is not caption-rendering proof.
- Keep the TS zero-restart behavior in its existing independent opt-in. No
  Core reflection, replacement Sage classes, media-socket interception or MCP
  production dependency. VCE-016 Linux qualification is complete with Android20;
  actual captions/fallbacks and restored settings are recorded in its result.

## Unreleased development0.1.17

- Complete VCE-014 extended Linux gates with actual PAL, sparse physical-SPU,
  real three-branch angle pictures and measured directional scan evidence.
  Negotiate revision3 Copy SPU identity; old/incomplete providers and clients
  retain Native. Validate physical angle branches before owned public-VM seek,
  and resume only a valid post-seek/FLUSH cursor after read-ahead Exit.

- Preserve the first input NAV as a transformed representation's clock origin;
  provider probing cannot replace it with a later read-ahead NAV.
- Negotiate actual physical SPU IDs with revision2 clients; unknown/incomplete
  inventories and sparse revision1 clients safely retain Native playback.
- Copy remux additionally requires revision3 for sparse-SPU translation:
  VOB output assigns dense IDs even when source physical IDs are sparse.
  Revision1/2 sparse Copy stays Native; ordinary Native commands are unchanged.
- Validate physical angle branches before switching; reject flattened-rip
  alternatives and stale links escaping their authored block. Keep pre-cell
  seek NAVs out of angle resynchronization.
- Resume only an owned read-ahead-ended VM after a valid destination-cell
  FLUSH. Closed/failed sessions and intentional player Stops remain terminal.
- Add reproducible PAL, sparse-SPU and real three-branch non-seamless angle
  fixtures with independent-reader checks. Physical qualification in progress;
  Windows deferred. Stock Core and published0.1.0 remain unchanged.

## Unreleased development0.1.10

- Add default-off, bounded same-disc owned-DVD reload handoff. Guard UI,
  lifecycle, requested position, expiry and newer Seek/Stop; stock free()->stop
  teardown is not an intentional Stop. Linux actual title reload passes after
  the measured6.29s teardown; the one-shot handoff is bounded to20s.
- Local tests cover missing/disabled/older provider without linking Core SPI.
  Windows remains deferred; no public release or blanket DVD parity claim.
- Complete scoped stock232/Non-Pro Native/Copy/Transcode menu/subtitle/control,
  reload/lifecycle/real-child/old-client fallback and preview/menu-end gates.
  Android07 fixes the proven empty-PES source exception without Core changes.
  All borrowed settings/power restored; Windows remains explicitly deferred.

## Unreleased development0.1.7

- Keep paused owned transforms out of the output-stall watchdog; renew its
  deadline on resume. Avoid duplicate stock Stop/DEINIT during owned unload.
- Encode actual signed stock DVD scan rates; use Native representation for
  sparse scans and wait for reopened-title NAV before applying a fallback seek.
- Retain early destination NAVs across stale clock reads, but clear previous
  menu/title custody at a VM-marked discontinuity. Add default-off, bounded
  CELL/anchor trace under the existing DVD trace option. Physical qualification
  remains open; stock Core and public0.1.0 are unchanged.

- Develop a default-off independent stock-API DVD navigator and bounded media
  transport, IFO main-title query, destination-NAV clock and menu state custody.
  First physical startup gate failed; revised handshake remains under test.
  These are development paths, not a qualified or published full-parity claim.
- Keep FFmpeg/MIM process ownership in its separate plugin. Copy/Transcode
  integration and physical compatibility gates remain open.

- Requalify unchanged public0.1.0 through the actual manager on official stock
  Linux9.2.17/Ubuntu26: adapter opt-in and hook-off native authored-DVD menu/
  title gates pass on Non-Pro25 with HDMI output. Restore global feature off
  and blank hook. This does not deploy0.1.1 or certify private-Core DVD parity.

- Add default-off, selected-UI TS restart hook. Only explicit zero uses stock
  MiniPlayer's positive1ms seek path to refresh a stale parser refinement target.
  Other targets, stock pusher, DVD, unselected clients and disabled fallback
  remain unchanged. Preserve/refuse third-party player overrides.
- Add stock-JAR config/policy/lifecycle/restoration and packaging coverage.
  Physical commissioning is pending; no new public release or Core change.

## 0.1.0 - 2026-10-16 (beta)

- Published immutable GitHub beta assets after exact-source green CI and
  independent download/hash verification; submitted SageTV catalog PR130.
  In-app availability awaits upstream review/aggregation. Later tracking-only
  commits do not replace this release's tested source/JAR/package.

- First qualified binary release for stock SageTV Linux/Windows. Existing
  per-client DVD hook and default-off recovery preserve other clients/settings;
  no modified Core or runtime MCP requirement.
- Stock Linux/Windows authored menus, seek, pause, reconnect/type5, later-user-
  seek custody, Stop/Watch and disable/fallback gates qualify. Preserve each
  original revision's evidence; no unrelated matrix rerun.
- Complete real malformed-navigation boundary acceptance on stock Linux175/
  non-Pro25: generated previous-PGC link exceeds its table; actual unchecked
  index error becomes the checked DVD error dialog while title A/V continues.
  Include a reproducible opt-in fixture generator and mutation contracts.
- Upload-only diagnostics qualify on stock Linux/Windows, including redaction,
  receipt hashes, Off/unavailable/reconnect/exit and playback continuity.
  Browser viewing stays in Android, not in this server plugin.
- Future MIM-owned DVD transport and private Core parity remain unimplemented;
  do not interpret the following development history as broader acceptance.

### Development history

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
