# DVD behavior: modified Core versus stock-compatible plugin

## Current functional inventory (VCE-015/017, 2026-11-10)

Use [Linux stock capability disposition](LINUX_STOCK_CAPABILITIES.md) for
the current owned implementation and its qualification limits. The historical
published-adapter table below is NOT the capability inventory of the newer
owned navigator: its statements that STC, highlights, longest-title and
transforms need Core are superseded by the independently implemented public
VM/reader path. Stock Core remains unchanged; no historical private fix is
silently installed. Preview startup uses an explicit plugin Configure option,
not unnegotiated Android/Core flags. Windows remains deferred.

This inventory compares the historical Vibe Core `main` DVD delta with canonical
stock SageTV. `.232` now runs official stock9.2.17 (2026-11-09). The companion is compiled against the unchanged stock `Sage.jar`
and selected only through `media_player_plugin_class/dvd` for one opted-in UI.
It must not package replacement `sage.*` classes, rewrite Core bytecode, or
claim that an Android client can control private server DVD state.

Current policy supersedes historical "keep Core fix" recommendations in the
comparison below: **no modified Core is installed or allowed as a fallback**.
Those historical adapter rows describe API gaps, not released behavior. Leave an
unavailable enhancement off, retain normal stock DVD behavior and prefer a
qualified client-side replacement. MCP controls are commissioning only.

## Current independent-player development (VCE-003)

VCE-014 extended Linux evidence (2026-11-10) is recorded in
`artifacts/results/VCE-014/RESULT.md`. Real manager candidates Extension1.17 /
FFmpeg1.17 / MCP1.11 and Android114 development13 retain stock Core. PAL
Native/Copy/Transcode, sparse physical SPU0x22, actual non-seamless angle1/2/3
picture selection and measured signed scans have affected physical evidence.
Exact-rate scan cadence, every commercial seamless layout, independent Exo/
GSY/IJK DVD engines and Windows are not inferred from these rows.

The commercial RAYA/SOUL copies' tested blocks advertise alternatives that
their physical VOBs do not contain. They are useful missing-branch/fallback
cases, not proof of genuine angle switching. The generated fixture instead
encodes three separate branches; an independent libdvdnav reader verifies
red/green/blue ANGLE1/2/3 at3.003s before Vibe testing. Recreate with:

```sh
python3 scripts/create_multi_angle_fixture.py \
  --output /new/fixture/VIBE_AUTHORED_DVD_MULTIANGLE \
  --work /new/temporary/authoring \
  --ffmpeg /path/to/ffmpeg.real
```

Both directories must be new. It needs dvdauthor and DejaVuSans. The generator
assembles equal-duration physical cells/IFO/BUP and actual signed next-NAV
links; it never changes original/user media. Run its five unit tests and the
independent `ffmpeg -f dvdvideo -title 1 -angle N` checks for N1/2/3. A changed
angle register without a changed captured picture is never a physical pass.

Multi-angle format references (independently implemented, no vendor code or
replacement Sage classes): public [libdvdnav NAV handling](https://github.com/xbmc/libdvdnav/blob/master/src/dvdnav.c)
and [ILVU flag definitions](https://github.com/xbmc/libdvdnav/blob/master/src/dvdnav_internal.h).
Only the final interleaved unit follows its signed angle address. After a seek,
match actual NAV VOB/cell identity to the selected authored cell before emitting
payload. Bounded policy tests distinguish unknown/sentinel addresses and forbid
unbounded scans. This operates solely on the plugin-owned VM's public fields;
actual stock JAR declarations are verified. Genuine three-branch picture
selection/wrap passes on the generated fixture; untested commercial seamless
layouts still require independent physical evidence.

Extended VCE-014 acceptance is complete to the report's Linux scope. The old
1.10/1.13/client07 sparse0x22 failure proved output DVB IDs/language lose origin;
client-only inference cannot recover Spanish. Actual bounded input inventory
now preserves it. Revision3 also remaps Copy mux's dense SPU output; revision1
sparse Transcode/revision2 sparse Copy safely choose Native. PAL Native/Copy/
Transcode, sparse Spanish after seek/long pause, actual three-branch angles,
measured directional scans/recovery and lifecycle/old-client fallbacks pass.
Client13 retains BOTH stock media/GFX preview routes across decoder replacement;
all-three DVD previews/SPU alignment and ordinary Push pass. RAYA/SOUL tested
blocks are flattened missing-branch negatives, not genuine angle positives.
Exact multipliers/every seamless commercial layout/all DVD engines are not
certified; Windows185 is DEFERRED. All borrowed settings/power restored, final
idle/protected stock hashes/no child. These are DVD subtitles, not broadcast CC.

The table below inventories the **published adapter**, not the new, default-off
`PluginOwnedDvdPlayer` development prototype. The latter composes the public
stock VM/reader with an independent stock-media wire pump. It does not modify
MiniDVDPlayer's private reader/loop or ship replacement `sage.*` classes.

It now has its own bounded destination-NAV retention, STC-after-seek, authored
highlight, fair media-command serialization and finite Native/Copy/Transcode
sessions. The separate FFmpeg plugin's `StockDvdStreamService` loads without
the optional modified-Core SPI. Only FFmpeg owns processes/INI/hardware policy.
Copy/Transcode requires a bounded per-load Vibe capability acknowledgement;
missing/old clients stay Native. MCP is only used to commission test settings.

On stock232/Non-Pro, Native root/title/seek, Languages/Audio/Subtitles highlights,
Spanish audio, chapter/basic scan, transformed forward seek and long pauses
have scoped evidence. Client05/FFmpeg1.11 also show Native English, Copy English/
Spanish and full_gpu Transcode English subtitles. Preserve source physical-SPU
order; discovery order can reverse languages. This is not full parity: affected
Linux lifecycle/child-failure/reload/preview/natural-end and physical old-client
no-ack fallback now pass with Extension1.10/FFmpeg1.13/client07. An empty-PES
source exception is fixed in Android, not by patching Core. Earlier rapid IO
has no equivalent stack, so retain its minimum evidence without assigning a
speculative cause; current repeated menu gates pass. Existing DVD factory forces
actual Media3, including selected Exo/GSY/IJK; those are preference fallbacks,
not independent DVD engines. Windows is DEFERRED by user direction. Sparse physical-SPU
layouts/angle/PAL/precise scan cadence are not inferred from this NTSC fixture.
Unchecked Core VM errors are contained by stopping this owned session,
not by claiming the underlying stock VM is repaired. Core's private language
default/watch-policy state is still a separate boundary; do not hide tracks or
reinterpret explicit title1 selections to work around it.

The owned implementation closes these Linux boundaries without migrating
private Core methods into the plugin:

| Need | Stock-compatible owned implementation |
|---|---|
| Destination seek/STC/title clock | Public VM/reader NAV, bounded presentation-NAV retention, actual client renderer epoch; no wall/requested-target simulation. |
| Menu highlights/selected state | Presented PCI plus serialized public VM control and bounded authored button indices. |
| Transform/scan boundaries | Existing stock media commands, native menus and sparse scan; only acknowledged clients enter finite Copy/Transcode. |
| Hardware/process ownership | Separate FFmpeg plugin owns its existing MIM hardware policy, output queues and child lifecycle; Extension never starts a second encoder. |
| Reload/cancellation | Same-disc/lifetime/request-checked one-shot20s handoff, intentional Stop/new seek wins, Disable clears custody. |
| Main feature | Bounded on-disc IFO duration lookup via public DVDReader, only explicit opt-in longest-title startup; no reinterpretation of arbitrary title1 requests. |
| Compatibility | Missing/disabled/older provider rejection and actual public114 no-ack Native playback; other UIs retain their original hook. |

This does NOT repair stock VM internals, import classification, global watch
policy, guarantee exact scan cadence, or claim all DVD formats/Android engines.
Those limits remain unavailable/unqualified rather than requiring a modified
Sage.jar. Optional owned mode remains default off; Windows must qualify before
a cross-platform release claim.

See `artifacts/results/VCE-003/stock-migration-qualification.md` for actual
builds/settings, corrected failures and acceptance scope. Published0.1.0 and
normal stock playback remain unchanged. No modified Core fallback is allowed.

## Published-adapter boundary inventory

| Modified-Core behavior | Stock-plugin disposition |
|---|---|
| Preserve title and position after a same-disc GFX/player reload | Implemented as the default-off, bounded same-UI/same-path recovery in `ClientExtensionDvdPlayer`; generated-DVD `.175`/non-Pro gate passed. |
| Intentional Stop/Watch versus reload | Matched stock playback also resumes the title after playing it. The earlier menu-only baseline did not establish a plugin defect. Unproven cancellation/lifecycle changes were withdrawn; preserve stock resume policy and independently gate genuine reconnect recovery. |
| Clamp a negative DVD Skip Back seek before it enters the VM | Implemented at the public `MiniDVDPlayer.seek(long)` override. Valid seeks pass through unchanged. Physical stock175 trace proves requestedMs=-9500 clamped to0; ordinary positive seek remains unchanged. |
| Prevent a malformed PGC/menu-control index from retrying the same VideoFrame job indefinitely | The adapter converts an `IndexOutOfBoundsException` escaping `playControlEx` into SageTV's checked `DVD_INVALIDOP`, which stock `VideoFrame` catches and removes. On2026-10-16 stock175/non-Pro25, an owned generated DVD's invalid previous-PGC link triggered the REAL ArrayIndexOutOfBoundsException once; expected DVD error dialog appeared while title A/V continued. This covers the public control boundary only; malformed links processed inside the private VM/push thread still require Core VM validation. |
| Validate a PGC link inside Ogle `VM.set_PGCN` | Not available at the `DVDMediaPlayer` hook. The private VM transition can occur on the pusher thread without an adapter method call. Keep the upstream VM safety patch; do not shadow Ogle classes in a Standard plugin. |
| Select the longest authored title when Core's Skip Menus option runs | Core chooses the title inside `VideoFrame`; stock calls title 1. The required IFO-duration query is absent from stock `MiniDVDPlayer`/`DVDMediaPlayer`. Rewriting all title-1 requests would also change a user's explicit title selection. Needs an upstream API or a separately proven provider contract. |
| Preserve language/subpicture chosen by a menu's SetSTN command | `VideoFrame` owns its `alreadySelectedDefaultDVDAudio/Sub` flags and applies defaults after the adapter returns. No supported plugin setter exists. Keep the narrow upstream change; do not hide available tracks to trick Core. |
| Synchronize highlighted menu button with the VM after a PGC change | The private `MiniDVDPlayer` NAV tracker and push-loop `ProcessHighlight` choose the button. A subclass cannot replace that branch without copying Core's full push loop. Keep Core fix. |
| Re-anchor the DVD STC after VM seek and the destination NAV packet | `forceStcAfterSeek`, `ProcessCell`, and `ProcessNAVPacket` are private Core state. The adapter cannot manufacture the exact destination NAV PTS. Keep Core fix; do not send a guessed STC. |
| Yield the DVD push-loop decoder lock for pause/seek/stop and transformed output | Private `createPushThread` body, decoder-lock protocol, `pushBuffer0`, and transform queue. The hook cannot patch the existing loop without replacing it. Keep Core fix or expose a supported, narrow runtime hook. |
| Native/Hybrid/MIM DVD representation switching and finite segment flushing | Requires Core's private DVD push loop, `DVD_DISC_*` negotiation in `MiniClientSageRenderer`, and the optional transform SPI absent from stock `Sage.jar`. The separate FFmpeg/MIM plugin owns any future transform; this companion must not launch a second FFmpeg. A new stock-compatible DVD-volume provider plus an independently implemented DVD player would be a distinct architecture and needs its own full compatibility gate. |
| Client-driven remote DVD navigation and Skip Menus/Previews policy | Stock `MiniClientSageRenderer` does not query `DVD_REMOTE_NAV`/`DVD_DISC_*`; stock `VideoFrame` makes the server-navigation/skip decision before the adapter can override it. A Standard plugin cannot add negotiated protocol reads. |
| Remove a failed DVD DirectControl job even after an unchecked Core error | The adapter's checked-error conversion covers its public `playControlEx` boundary. Core's `watchQueue` is private, so full `finally` cleanup for every possible failure remains Core-only. |
| Explicit `watchFromBeginning` without prior disc-resume metadata | Modified Core adds a `VideoFrame` watch method and private one-shot state. A DVD player is instantiated after watch selection, so the adapter cannot implement that request. Existing stock watch/resume semantics remain unchanged. |
| Normalize a volume root at media-import time | This is staged in a separate `Wizard` upstream-review branch, not in the running Vibe Core `main` DVD delta. The DVD-player hook occurs after import/classification and cannot repair an import that did not become a DVD MediaFile. |

The plugin-only promise is therefore bounded: it preserves ordinary stock DVD
playback when absent/disabled, adds explicitly opted-in hook behavior where
SageTV exposes a method, and documents Core-only fixes honestly. It cannot
deliver complete modified-Core DVD parity on an unmodified stock `Sage.jar`
under the current supported extension API.

## If full parity is required

Do not install the historical VM/VideoFrame/MiniClient patches to satisfy this
deployment. The stock requirement is authoritative even where a private Core
method has no plugin equivalent. Installation of the native adapter does not
complete VCE-003 or certify every historical Core behavior.

A zero-Core-change route would be a **new DVD player/transport implementation**,
not a port of these patches into the present adapter. It would need an owned
DVD VM/menu/navigation path, a DVD-volume contract in the separate FFmpeg/MIM
provider, stock MiniClient wire compatibility or an independently negotiated
Android transport, and full disc/menu/seek/caption/old-client/Windows gates.
VCE-003 records that distinct architecture and its acceptance criteria. Its
default-off prototype is under qualification as described above; it is not
implemented by the regular-file Direct endpoint or the published adapter.
Never silently copy `MiniDVDPlayer` or inject replacement Core classes.
