# Additional stock-compatible mitigations

VCE-018 through VCE-021 extend the functional migration inventory. They do
not replace Sage.jar, shadow sage.* classes, change the original FFmpeg or
make MCP a playback dependency. Windows qualification remains deferred.

## Read-only playback state (VCE-018)

Installed Plugins > Vibe Client Extension > Configure > Enable Vibe Client
Extension, then **Publish Stock Playback State**. Both default off.

The existing optional LAN8273 capability listener accepts only GET
`/vibe/media/state?client=<exact12hexMiniClientIdentity>`. It returns protocol1,
media_id, sampled_at_ms, media_time_ms, raw_time_ms, duration_ms, file_size,
file_start_ms, file_end_ms, recording, playing and dvd. DVDs additionally have
dvd_title, dvd_chapter and dvd_domain. No media paths, titles, arbitrary APIs,
browser pages, commands or file downloads are exposed. Trusted LAN only;
there is no authentication/encryption. Do not expose the port publicly.

Every sample resolves an actually connected UI and verifies media identity
again after the public API reads. Missing media/disabled service is unavailable;
failed or changed-media observations must be discarded. These sequential API
reads are not an atomic snapshot, render timestamp or hardware STC. DVD clocks
are title-relative; ordinary GetMediaTime may use a file epoch, while
GetRawMediaTime is the stock raw player time. Never subtract a DVD file timestamp
or use this observation to manufacture a requested-position clock.

This is a service boundary, not a private MEDIA_STATE_URL negotiation property.
Android's Video > Server playback now requests an observation explicitly and
offers Refresh. It never overrides native clock/growing-file policies or polls
in the background. Existing client file-growth/fallback paths
remain authoritative if the optional service is absent. Consumers must bind
the returned media_id to their own current load before using any observation.

## Explicit current-owned DVD actions (VCE-019)

Select the connected Vibe UI, opt into Owned DVD Navigator and enable that
selected client. Its normal Native/Copy/Transcode policy remains separate.

- **Start Owned DVD Main Feature from Beginning** selects the longest valid
  authored title and seeks its beginning through the owned navigator.
- **Save Current Owned DVD Resume Point** saves the presented title/time, not
  a read-ahead CELL or requested destination. A menu cannot be bookmarked.
- **Resume Saved Owned DVD Point** works only for that exact disc path/UI and
  current active owned session. It does not start another file or use stock
  watched metadata. One saved point per UI persists across server restarts.
- **Clear Saved Owned DVD Resume Point** removes only that plugin-owned point.

Requests run on the owned session's serialized control path, invalidate the
old representation and wait for destination NAV before the deferred seek.
New explicit seek/navigation/Stop wins; a stopped or replaced lifecycle is not
an intent recipient. Standard-plugin Disable refuses requests. These explicit
actions do not intercept global Watch, rewrite title1, erase watched flags or
promise equivalence to Core's private one-shot watchFromBeginning policy.
Normal stock Watch/resume stays unchanged. For regular video, retain the
client-first restart/resume corrections; do not enable the DVD hook for TS.

### Android-owned DVD menu (VCE-022 / STOCKDVD-003)

Development0.1.26 adds **Allow Android Owned DVD Actions**, default off. The
Android Video > DVD playback menu discovers `/vibe/dvd/controls?client=...`
only when opened/refreshed. It displays unavailable/disabled actions if the
service is missing, off, unacknowledged or the current player is not an owned
DVD. Both the Standard plugin and Owned DVD Navigator must be active.

An acknowledged current load receives its exact challenge. POST accepts only
client, challenge and operation=beginning/save_resume/resume/clear_resume;
there are no arbitrary paths, API calls, seek targets or configuration writes.
Before mutation, the owned navigator rechecks session identity, challenge,
plugin lifetime and opt-in under its serialized control lock. A stale menu
cannot control a replaced disc. Android checks its own load before dispatch
and again after UI-thread handoff; no automatic command retries occur.
After an action use Refresh for current controls. Back returns to Video settings.
Start/Resume explicitly start playback, including from pause; Save/Clear preserve
pause. This lets the pump reach destination NAV before applying the seek.
Trusted LAN8273 remains unencrypted/unauthenticated; never expose it publicly.
MCP is optional commissioning only, not a production-control dependency.

## Reviewed imported metadata (VCE-020)

Configure **Imported MediaFile ID**, **Descriptive Metadata Field** and
**Replacement Metadata Value**. Select **Preview Imported Metadata Edit**,
review the before/after text in its help/status, then select **Apply Reviewed
Metadata Edit**. Editing any input invalidates the preview; Apply consumes it
once and previews expire after five minutes or plugin Stop/Disable.

Only imported non-TV/non-recording media is accepted. Allowed fields are
Title, EpisodeName, Description, Year, SeasonNumber and EpisodeNumber; numeric
fields accept1..4 digits, Title cannot be empty and values are bounded2048
characters without NUL. A structural container must already be present.
The current field must still match the preview before SetMediaFileMetadata;
successful return alone is insufficient, so the value is read back afterward.
This is optimistic conflict detection, not a transaction against concurrent
third-party database writers. Stock SageTV also updates the media's sidecar
properties. Review the edit and back up important metadata before use.

No automatic scanner/repair is installed. Duration, stream codecs, file paths,
recording flags, watched-state and database format internals cannot be edited.
SetMediaFileMetadata is not reinitializeMetadata: an absent/corrupt container
format is refused and needs a separately supported reimport/provider workflow,
not invented format fields or direct Wizard mutation. That structural limitation
is retained rather than falsely claiming full private-Core metadata parity.

## Artwork boundary (VCE-021)

The demonstrated stock PNG crash is a PNG1.2/PNG16 symbol/ABI conflict in
native ImageLoader, already qualified under container STOCK-005. Preloading
the original verified ImageLoader is an OS/launcher mitigation and keeps every
stock native/JAR byte unchanged. Normalizing a PNG cannot reliably repair a
process-wide ABI collision; rewriting global STV assets would also miss other
images and violate stock preservation.

The examined plugin-owned SageMC TMDB path already decodes through Java
ImageIO to BufferedImage (`TmdbObject.getImage`), caches it in that object and
exports selected posters as JPEG (`writeImageJpg`). Its STV calls those Java
objects, not a new Extension PNG loader. The branding pipeline already
validates/generated RGB/RGBA PNGs, but it targets Android, not server ImageLoader.
Client Extension currently has no independent artwork producer requiring a
second normalization/cache. Therefore no duplicate normalizer or global image
rewrite is added. Future distinct image failures must identify their actual
producer, dimensions/alpha/format and native-loading route before a bounded
plugin-owned correction; this disposition is not all-image certification.

## Focused testing

`StockServiceTest` covers typed state, transition invalidation, opt-in/Disable,
identity/method/extra-field refusal, metadata preview/readback/stale/format/TV/
recording refusals and exact-disc/UI bookmark persistence/removal. Compile
production only against stock Sage.jar. Tests' isolated API fakes are never
packaged. Actual Linux qualification and restored-state evidence belong in
artifacts/results/VCE-018..021; source contracts are not physical playback proof.
Only affected gates run; existing unrelated caption/format/device matrices
are preserved. MCP's fixed commissioning controls are optional test tooling.
