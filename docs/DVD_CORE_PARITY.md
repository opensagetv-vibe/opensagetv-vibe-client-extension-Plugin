# DVD behavior: modified Core versus stock-compatible plugin

This inventory compares the running Vibe Core `main` DVD delta with canonical
stock SageTV. The companion is compiled against the unchanged stock `Sage.jar`
and selected only through `media_player_plugin_class/dvd` for one opted-in UI.
It must not package replacement `sage.*` classes, rewrite Core bytecode, or
claim that an Android client can control private server DVD state.

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

The low-risk route is to keep the narrowly scoped VM, `VideoFrame`, and
MiniClient fixes in Core and submit demonstrated ones upstream, while this
plugin owns only per-client opt-in and externally provided media services.
That does not satisfy a zero-Core-change requirement.

A zero-Core-change route would be a **new DVD player/transport implementation**,
not a port of these patches into the present adapter. It would need an owned
DVD VM/menu/navigation path, a DVD-volume contract in the separate FFmpeg/MIM
provider, stock MiniClient wire compatibility or an independently negotiated
Android transport, and full disc/menu/seek/caption/old-client/Windows gates.
It must be designed and authorized as a separate architecture rather than
silently copying `MiniDVDPlayer` or injecting replacement Core classes.
