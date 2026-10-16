# Stock-server physical gate

Date: 2026-10-04. Target: unmodified SageTV server `192.168.10.175`,
non-Pro Fire TV `192.168.10.25` (AFTMM/API 25), UI context `444556303031`.
The SageTV server's stock `Sage.jar` SHA-256 before deployment is
`d76ded981b9bc51e25b9cec821b6abeb771b46c2996dc45e453349b5e703fcb0`.
The separate FFmpeg/MIM and Core MCP plugins are already installed on this
stock server; this gate must not replace either plugin or `Sage.jar`.

## Baseline before Client Extension installation

- The non-Pro's per-UI `media_player_plugin_class/dvd` value is blank.
- Stock Core MCP exact-path resolution returned MediaFile ID `65513423` for
  `/var/media/OpenSageTV_Vibe_Tests/OpenSageTV_Vibe_Test_DVD`.
- The non-Pro connected to `.175`; server UI contexts `444556303031` and
  `444556303032` were visible, confirming that the opt-in must target only
  `444556303031`.
- Stock Watch opened the authored generated DVD root menu; the on-device
  screenshot showed its highlighted **Play Main Feature** button. Select
  started the main title; the stock API reported approximately 90 seconds of
  duration and an accepted 30-second seek with advancing media time.
- The `.175` server had one active TV recording during this baseline. No
  SageTV restart, plugin installation, or property edit was performed.

## Gate plan and remaining checks

1. The installed-JAR update and idle restart have completed. The refreshed
   Configure control, active extension JAR, and unchanged stock JAR were
   verified as recorded below.
2. The targeted non-Pro generated-DVD root/title, Stop/restart, and GFX
   reconnect position gate passed. Authored submenus, pause, independent A/V,
   older-client hardware, and the stock-Windows comparison remain open.
3. The target's DVD override and recovery/trace settings were restored. The
   remaining Android OpenGL post-DVD menu-rendering issue is tracked
   separately; do not mark the broader physical gate complete yet.
4. The newer VCE-007 seek/control boundary build is local only. Near-zero
   negative Skip Back and an authorized malformed-navigation fixture still
   need focused physical checks after `.175` is idle. At the latest check,
   *NFL Football* was actively recording; do not restart SageTV for this gate.

Build-only evidence: two consecutive Java 8/stock-JAR `dev.sh all` runs passed
the unit, source, manifest, and package gates. The JAR SHA-256 was
`6a794e7bb0afd2013586a353af8b4da3509dc7bc82905caa44bd72cd83b56657`;
the ZIP SHA-256 was
`d63db4d82d8287f23b9cabe77e8a179365591a3b4ff9b155b103465634ec90e5`
on both runs. This is not a physical plugin-install pass.

## 2026-10-04 development-plugin staging

- `.175` has `devmode=true` persisted in `Sage.properties`; this enables
  `SageTVPluginsDev.d` discovery but does **not** waive plugin-manager restart
  requirements. No SageTV restart was performed for this setting.
- The local Java 8 build, unit, source-contract, manifest and package gates
  passed after adding a default-off `Trace DVD Reload` diagnostic control.
  The final staged ZIP SHA-256 is
  `244d24b7bac735d79f7927228182e0b7d98f9c00fda0c05845af6f5d388354e6`;
  the remote copy matches. The stock `Sage.jar` remains
  `d76ded981b9bc51e25b9cec821b6abeb771b46c2996dc45e453349b5e703fcb0`.
- The new plugin appears in SageMC's **All Available Plugins > General** list
  on the non-Pro UI. SageMC initially reopened its remembered **Installed**
  filter; the test UI's `plugin/last_list_mode` was temporarily changed from
  `xPluginModeInstalled` to `xPluginModeAll` through the public SageTV API.
  Restore that UI setting after the install gate.
- The initial package was installed from SageMC without a SageTV restart;
  the plugin was enabled and its five Configure controls rendered. The target
  UI alone received the DVD adapter; the second UI retained a blank DVD
  override. The active CBS News Sunday Morning recording progressed after
  installation, and the stock `Sage.jar` hash stayed unchanged.
- Generated authored DVD playback showed the root menu and the main feature.
  Stock Core MCP `Seek(60000)` was accepted, and main-title media time
  advanced. At approximately 64,964 ms, a debug-only graphics-socket fault
  closed **only** the non-Pro GFX read socket. Server logs show DVD adapter
  instance 2 inactive/free and instance 3 load. The client returned to the
  8,008 ms root menu, reproducing DVD-001 on stock Core.
- An opt-in recovery implementation has passed the Java 8/stock-JAR unit,
  source, manifest, and package gates, but **not the physical recovery gate**.
  The installed-JAR update requested `RESTART`. Since Face the Nation was
  actively recording, **Restart Later** was chosen. The target UI DVD player
  property was restored to blank until a safe restart and retest. The update
  has not been published.
- With the update pending/marked Failed and the per-UI override blank, the
  non-Pro still opened the generated DVD root menu through stock
  `MiniDVDPlayer`; selecting the main feature advanced to a 89,989 ms title.
  No new Client Extension adapter instance appeared in the server log. This
  passes the missing/disabled-plugin **stock fallback** gate for that UI.
  The other UI's DVD override also remains blank; older-client hardware is
  not available for a separate physical gate. The generated-menu fallback
  screenshot is under `artifacts/temp/vce-physical-20261004/fallback.png`
  outside this repository.
- The final staged ZIP SHA-256 is
  `091461846ea85484eaeea720ea7ecd10a8ed324a04051475f690c84051824a8d`.
  The staged JAR SHA-256 is
  `03697cfa50faf9701a2f5583fd07f74b6eeae33aaf3f351709b499c896203abd`;
  the server's pending `.jar.262` matches that JAR. The final update remains
  deferred until the active recording ends. Read-only inspection of
  `stagedrenames.txt` shows `.jar.258`, `.jar.260`, and `.jar.262` applied in
  that order, so the final pending replacement is `.jar.262`; verify the
  unsuffixed JAR hash after restart before testing recovery.
- At 10:34 CDT, `.175` still had one active recording with an 11:00 CDT
  scheduled end, and UI `444556303032` was actively viewing *Face the Nation*.
  No restart was performed. The stock `Sage.jar` hash remained
  `d76ded981b9bc51e25b9cec821b6abeb771b46c2996dc45e453349b5e703fcb0`.

## Idle restart and focused reload gate

- On 2026-10-04, the user explicitly approved restarting `.175` and Sagex
  reported zero active recordings. Only the SageTV container was restarted.
- After startup, the plugin reported installed/enabled/not failed. Its active
  JAR SHA-256 was
  `03697cfa50faf9701a2f5583fd07f74b6eeae33aaf3f351709b499c896203abd`;
  stock `Sage.jar` retained SHA-256
  `d76ded981b9bc51e25b9cec821b6abeb771b46c2996dc45e453349b5e703fcb0`.
  Sagex and Core MCP health returned normally.
- The non-Pro client was manually reconnected to `.175`. Only UI
  `444556303031` received the DVD adapter and the default-off recovery/trace
  options for this focused gate. UI `444556303032` kept a blank override.
- The generated main feature reported 89,989 ms duration and 9,943 ms media
  time before a debug-only GFX read-socket fault. Six seconds afterward, the
  same media/title reported 14,948 ms and the same duration. Server logs show
  a new adapter instance, `resume-scheduled`, and `resume-applied`. The
  on-device screenshot at
  `artifacts/temp/vce-physical-20261004/recovery-pass.png` shows the authored
  main feature rather than the root menu.
- Two immediate post-seek fault attempts did not prove recovery: the actual
  server DVD clock was only 1,935/4,938 ms even though public seeks had been
  accepted. A separate earlier attempt saved/applied a late title position,
  but the state check came after the short title ended. Do not equate accepted
  `Seek(long)` with completion; wait for a stable title clock in this gate.
- Intentional Stop followed by a fresh Watch returned to the 8,008 ms root
  menu, with no stale restore. Playback was stopped again. The non-Pro DVD
  override was restored blank and recovery/trace were restored false. The
  other UI's DVD override remained blank.
- SageMC later displayed missing image-backed elements/glyphs. Android logcat
  showed repeated OpenGL null image-holder errors. A full client connection
  restored the menu without any server or settings change. The issue also
  reproduced after stock DVD playback; it is tracked as an Android renderer
  cache problem, not claimed as a plugin regression or a passed UI gate.
- Authored submenu, pause, Pro ALADDIN, genuine transient type-5 rejection,
  old-client hardware, stock Windows, and broader A/V/teardown gates remain
  open. No publication is authorized yet.

## Updated adapter build and non-Pro DVD checks

- With no active recordings reported by Sagex, the user-approved `.175`
  container restart installed the current local adapter JAR. Java 8/stock-JAR
  compile, unit, source-contract, manifest, and package gates passed. The
  installed adapter SHA-256 is
  `15c23bd9ca4f2c91d6f9d96d46e62f0917fdab243deb772846c6a5f379c581e7`;
  stock `Sage.jar` remained
  `d76ded981b9bc51e25b9cec821b6abeb771b46c2996dc45e453349b5e703fcb0`.
- On non-Pro UI `444556303031`, the generated DVD root menu and main feature
  rendered. The Languages submenu rendered, but selecting every authored
  audio/subtitle/chapter option was not proved. A normal 60-second main-title
  seek reached about 60.9 seconds on the settled clock within roughly four
  seconds; pause held the clock within about 0.5 seconds over three seconds,
  and Play advanced it again. `Seek(500)` followed by `SkipBackwards()` logged
  `seek-clamped requestedMs=-9500`, physically proving the opt-in negative-seek
  boundary is reached on the stock server.
- A debug-only GFX-socket close during the main feature reproduced a DVD
  player replacement without touching the media socket. The adapter logged
  a same-file/title `resume-candidate`, `resume-scheduled`, and
  `resume-applied`. The public DVD clock first restarted at zero, reached
  about seven seconds, then stalled around 6,940 ms for approximately 25–30
  seconds before jumping to the saved position near 36 seconds and advancing.
  A device screenshot later showed the authored main feature at about 1:19;
  the short title subsequently returned to the root menu. This is **partial
  recovery, not a clean latency pass**. `resume-applied` alone is not valid
  evidence of prompt playback. The stock MiniDVDPlayer seek/push path and
  client buffering require a focused latency comparison before closing DVD-001.
- The target per-UI DVD override was returned to blank; the other UI stayed
  blank. Global recovery and trace settings were returned to `false`, while
  the pre-existing extension-enabled setting remained `true`. The non-Pro
  Android test-awake lease restored its original power settings. The test
  stopped DVD playback. No production recording, other UI, or stock JAR was
  changed.
- Genuine malformed-IFO/PGC containment remains untested without an
  authorized malformed fixture. Physical audio, language/subpicture changes,
  chapter controls, old-client hardware, and stock-Windows gates also remain
  open. The adapter must not be published or called fully DVD-parity-complete.

### Stock early-seek comparison and local follow-up

- After restoring the non-Pro's blank DVD override, the unmodified stock
  `MiniDVDPlayer` opened the same generated title. A 36-second seek sent when
  the new title clock was about one second old reached roughly 8.4 seconds,
  stalled there for more than ten seconds, then landed near 37 seconds and
  advanced. A later ordinary seek after the title was established landed in
  about five seconds. This isolates the long reconnect wait to a stock DVD
  early-seek behavior that the adapter's 250 ms readiness gate exposed; it
  does not prove a client decoder or a separate-transport defect.
- The *local, not installed* follow-up now waits for a stable title clock
  (up to 10 seconds, scaled down for short titles) before issuing the optional
  recovery seek. Its bounded wait is 20 seconds. The affected Java 8 build,
  unit, source-contract, manifest, and package gates pass. No physical result
  is claimed for this follow-up until the new JAR is installed and the
  same fault/reload gate is repeated.
- At the install check, `.175` had begun recording *NFL Football*, scheduled
  through 18:30 CDT. SageTV was **not** restarted and the active JAR remains
  `15c23bd9ca4f2c91d6f9d96d46e62f0917fdab243deb772846c6a5f379c581e7`.
  Only the test UI was stopped; its DVD override stayed blank and recovery/
  trace stayed false. Preserve the recording and retry the install when idle.

### Pro ALADDIN verified-clock recovery (2026-10-05)

- Stock `.175` retained the same `Sage.jar` SHA-256
  `d76ded981b9bc51e25b9cec821b6abeb771b46c2996dc45e453349b5e703fcb0`.
- The installed development adapter SHA-256 is
  `3b51ae250aa845903dfb5e2cc051514d10a06fda3c6fa6f11abf778d6e15b9f5`.
- Fire TV Pro `.29` used direct Media3 hardware MPEG-2/AC-3 and dynamic stock
  DVD Push. A debug-only local first-attempt rejection exercised the bounded
  type-5 retry; stock accepted the following type-5 connection once.
- The adapter rejected two false seek confirmations at `19519 ms`; its third
  public `Seek(long)` reached `520586 ms`. After another minute, Android and
  SageTV both reported about `580.9 s`, A/V counters advanced, and drop/error
  counters remained zero.
- Stop/Back returned complete SageMC artwork/text. Per-UI overrides were
  restored blank, recovery and tracing were restored false, and temporary
  on-device screenshots were removed.
- This satisfies Android DVD-001 reload recovery. The broader plugin project
  still requires the explicit fallback, intentional-restart, Windows, and
  publication gates listed in `TASKS.md`.
