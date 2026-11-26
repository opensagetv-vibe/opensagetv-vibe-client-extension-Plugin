# Linux stock-Core capability disposition

This is a functional migration inventory, not a claim that every historical
Vibe Core patch was copied, merged upstream, or can be reproduced by a plugin.
The authoritative deployment is official SageTV9.2.17 on Ubuntu26/Java11.
Sage.jar, bundled native libraries and original FFmpeg remain protected by
the container's stock-file manifest. MCP is optional commissioning only.

## Implemented stock-compatible paths

| Historical requirement | Owner and stock-compatible path | Qualification |
|---|---|---|
| Newer Linux and GPU userspace | Container OS/drivers and loader integration; no add-on JAR/encoder payload seeded by the image. Install add-ons through the SageTV plugin manager. | STOCK-003/005, Intel VAAPI/QSV; no claim for absent AMD/NVIDIA hardware. |
| FFmpeg/MIM identification, hardware and fallback | Separate FFmpeg Plugin, its existing provider/runtime and supported server integration. Never replace original stock FFmpeg. | STOCK-003, MIM-MAP-001/STOCK-MAP-001 and VCE-014. |
| Native/Copy/Transcode DVD | Extension-owned public VM/reader and existing MiniClient media commands; separate FFmpeg StockDvdStreamService owns transformation. | VCE-003/014 Linux Native/Copy/full_gpu; Windows deferred. |
| Optional DVD capability/transport negotiation | Per-load client/nonce acknowledgement on the plugin's LAN capability listener, not modified MiniClient Core properties. | Revision1/2/3 fallback and actual Native old-client no-ack gates, VCE-014. |
| DVD seek, clock and title custody | Destination NAV, stock STC/PTS commands and current Android renderer epoch; never a requested-position or wall-clock simulation. | VCE-003/014; VCE-017 additionally checks natural preview-to-menu custody. |
| Decoder replacement/reconnect | Same-disc, same-UI/lifetime bounded one-shot recovery; explicit Stop/new seek wins. Client repair first; no private event233 required. | VCE-003 Linux, default-off recovery; intentional Stop matched to stock baseline. |
| Main-feature startup | Bounded public DVDReader IFO-duration catalog and explicit plugin Skip Menus setting. Do not reinterpret all title1 requests. | VCE-003/014, longest authored title selection. |
| Preview startup | Explicit default-off plugin Skip DVD Previews to Root Menu. One-shot per load, Skip Menus wins; unavailable root retains native first play. | VCE-017; see its final result for the authored preview/root/main gate. |
| DVD menus, highlight and language | Presented PCI plus serialized public VM controls; actual on-disc audio/subpicture order, sparse physical identity and bounded group/button indices. | VCE-003/014 English/Spanish, menu controls, sparse0x22, actual angle pictures. |
| DVD pause, scan and representation transition | Existing stock media commands; native menus/scans and finite provider sessions. | VCE-014 measured signed scans and recovery, not exact multiplier certification. |
| Malformed public DVD control | Convert the owned control-boundary runtime index failure to checked DVD_INVALIDOP once; ordinary title can continue. | VCE-015 actual invalid previous-PGC fixture on stock232/Non-Pro. |
| Malformed source transition | Latch IOException and stop only the owned cursor/session, not an infinite unchecked retry. | Bounded stock-JAR failure tests; not a repair of stock VM internals. |
| Stock STV broadcast CC state | Public GetMediaPlayerClosedCaptionState, exact-UI/load optional relay; Android applies only to current non-DVD session. No player hook/FastLoad change. | VCE-016 Off/CC1/CC2, CEA/Teletext/DVB, Disable and local fallback. |
| Explicit zero TS restart workaround | Separate default-off per-UI public MiniPlayer wrapper maps explicit0 to1ms; all other seeks delegate unchanged. | VCE-013 stock bytecode gap and Linux bounded gates. Original175/Pro stale-tail failure not reproduced on232. |
| Commissioning events230–232 and exact path/channel controls | Bounded supported SageTV api/apiUI MCP actions; no private MiniClient events, arbitrary APIs or production dependency. | Existing MCP stock acceptance plus VCE-015 path/control readbacks. |
| Diagnostics/report upload | Optional upload-only Extension service; browser diagnostics stays in Android. Missing service leaves local reports/playback intact. | Existing VCE-011 stock Linux/Windows gate, unchanged this migration. |
| Rich playback observation | Default-off exact-UI read-only public-API state service. Android Video > Server playback requests an explicit snapshot; no background polling, renderer-clock substitution or private MEDIA_STATE_URL negotiation. | VCE-018 and STOCKSTATE-001 ordinary/DVD observation, seek/pause and Off continuation. |
| Explicit owned DVD start/resume | Current-owned main-feature start and exact-disc/UI save/resume/clear, without changing normal Watch/watched policy. Optional Android actions require the acknowledged current-load challenge and a separate default-off setting. | VCE-019/022 Native/actual Copy/full_gpu Transcode, paused resume, natural end, Stop/other-disc/Disable fallback. |
| Reviewed imported descriptive metadata | Explicit ID/field/value Preview and Apply, expected-value/readback and imported/non-TV/non-recording guard; no automatic structural repair. | VCE-020 actual generated fixture edit/stale refusal/exact restoration, media bytes unchanged. |

## Preserve stock behavior rather than injecting private Core policy

- DVD/regular-file Watch, resume and watched-state policy stay with stock
  SageTV. Public Watch followed by Seek is sufficient for commissioning a
  position, but it is not identical to the removed private one-shot
  watchFromBeginning implementation. Do not erase user watched metadata or
  override all title selections to imitate it.
- Exact indexed DVD root/VIDEO_TS aliases resolve through supported APIs in
  MCP. No Wizard/import database rewrite is installed. Invalid imported-media
  structural metadata repair remains unavailable. VCE-020 supplies only an
  explicitly reviewed descriptive-field editor; it does not authorize automatic
  database rewrites or reconstruct the private container/stream format.
- Rich MEDIA_STATE_URL/private channel acknowledgement is not injected into
  stock renderer negotiation. Existing client loader-thread file-growth
  probes and MIM-owned transport provide bounded stock-compatible playback;
  missing metadata uses conservative behavior, not invented channel state.
  VCE-018's optional read-only service is consumed only for an explicit Android
  snapshot; it does not replace the renderer clock or a native STC.
- Private VIDEO_PLAYBACK_RATE/CC properties are not sent by reflection into
  MiniClientSageRenderer. Client-local scan/seek scheduling uses the stock
  transport; captions use the explicitly negotiated optional service above.
- Global shutdown, network encoder, SD, native build and launcher maintenance
  remain stock/upstream responsibilities. A Standard plugin must not silently
  patch those classes, inject replacement sage.* classes or mutate bytecode.

## Qualification limits are not unfinished Linux implementations

All-format certification is not implied by PAL/NTSC generated fixtures and
tested commercial discs. Exact scan multipliers, every commercial seamless
ILVU layout, GPU vendors not physically supplied and independent Exo/GSY/IJK
DVD decoders are not certified. The current DVD factory actually uses Media3
for those selected preferences; do not record four independent DVD engines.
IJK's native DVB-bitmap track interface is unavailable; unsupported tracks
must not be fabricated. Its normal stock/locally supported captions remain
independent of the optional relay. Windows185 is explicitly deferred.

No further Core patch is required to run the implemented Linux paths. That
does not mean inaccessible private Core methods have become plugin APIs.
Use the compact VCE-015/016/017 results and VCE-014 carry-forward report for
exact builds, physical evidence, restoration and remaining platform limits.
Additional VCE-018/019/020/021 scope, usage and evidence are recorded in
docs/STOCK_MITIGATIONS.md and their compact result directories. Artwork021
retains the qualified container loader fix/existing Java image route; no
duplicate global image normalization or private-Core parity is asserted.

VCE-022/STOCKDVD-003 functional Linux gates pass with Extension0.1.28 and
Android development23. Final Non-Pro136prefs/bookmark/hooks/options and original
power are restored, awake lease inactive, app stopped and final0activity/hashes
pass. Linux integration is complete at Android/root revision465.
See artifacts/results/VCE-022/RESULT.md for current closure status. No Windows
qualification or publication is implied by these Linux results.
