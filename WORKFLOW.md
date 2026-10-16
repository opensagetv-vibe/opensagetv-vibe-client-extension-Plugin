# Workflow

## Task fix and server-boundary policy

Apply this policy to every task workflow, including dependency fixes across
Vibe repositories. Fix and test necessary plugins and update the test-server
plugin without asking again solely for repository-boundary approval.

Prefer the Android client, then a stock-compatible plugin. Change non-stock
`.232` Core only for a proven production defect that neither can correct;
document the API gap and alternatives, keep optional negotiation and safe
stock/older-client fallback, and run affected compatibility tests. Never patch
Core merely to simplify testing.

Stock `.175` installation changes are limited to plugin installation/update.
Do not modify its stock Sage.jar, stock FFmpeg, Core binaries, or server
installation/configuration files. Preserve user settings, recordings and
unrelated clients; reversible supported SageTV playback APIs remain allowed.

Non-stock `.232` restarts are authorized for task updates without asking
again; coordinate them with active test guards and preserve data/settings.
  Always ask the user before restarting stock `.175`, even when it appears idle,
  unless an explicit user-granted bounded restart window is active. Record
  its UTC expiry in the task/handoff, and check expiry and revocation before
  every restart. After expiry or revocation, ask again; stock files stay protected.

Update owning TASKS.md, linked dependencies and the workspace suggested order
as work changes; move completed checkoffs into the checklist change ledger.
Test only affected gates, preserve unrelated completed matrices, and do not
stop independent authorized work for a status question or a dependency-only
permission request. Unrelated work, publication, destructive actions and
interruption of recordings/other users still require their own authority.

1. Build and test only against an unmodified stock `Sage.jar` (`dev.cmd all` or
   `./dev.sh all`). The plugin JAR must contain no `sage/` classes.
2. Back up the target client's `clients/<id>.properties`, record the stock
   `Sage.jar` checksum, and install the plugin JAR as a SageTV Standard plugin.
3. In the STV's Installed Plugins > Configure screen, enable the plugin, select
   **only** the Vibe client UI, and choose **Enable selected client**. An
   existing third-party DVD player setting must not be overwritten.
4. Verify a non-Vibe UI still uses the original player. On the Vibe UI, prove
   authored DVD menus, title A/V, seek, stop, reconnect, and return to the STV.
   Record Android and server logs, exact versions, and physical display evidence.
   Enable **Recover DVD After Reload** only for its focused same-disc reconnect
   gate; it defaults off. Compare intentional STOP/restart with a matched
   stock run that actually played the title first. Preserve stock bookmark/
   resume semantics; a menu-only baseline cannot require a root after title
   playback. A different DVD must never inherit the previous title.
   For the player-hook parity gate, Skip Back near title zero must not send a
   negative time to the VM; verify a normal positive seek is unchanged. Do not
   mark malformed-PGC containment physically passed without an authorized
   fixture that actually triggers the stock control-boundary exception.
5. Disable the selected client and verify the original property is restored.
   Preserve all user media, settings, and recordings. Never publish until the
   full `.175` stock-server/non-Pro gate passes and the user approves.

Plugin configuration is the initial STV settings menu. A custom STVi is a
separate visual gate and must not claim to be complete until tested on SageMC
and stock SageTV7 STVs.

## Physical setup and lifecycle evidence

- Use valid generated authored media, with burned title/PTS/chapter labels.
  Before a reconnect test, start through its verified menu/Play command and
  allow natural mid-title playback; independently confirm remaining duration.
  On the90second fixture an8second capture plus capture startup can cross EOS.
  A root-menu result after natural end is not a failed reconnect. Log late
  seeks independently instead of using an unconfirmed seek as fault setup.
- Review actual screenshots/HDMI frames. SageMC embeds default-STV screens,
  so menuName/popupName may lag or not describe the visible child dialog.
  Do not replay a navigation macro based solely on those strings. Confirm
  the actual focused control before selecting Enable, Disable, Reset or
  Uninstall; never confirm Reset/Uninstall to restore a test session.
- Test Standard-plugin Disable separately from the feature Enable switch.
  Stock Disable calls stop/destroy without unloading the selected player
  class or erasing its per-UI property. Retain user settings, delegate stock
  behavior while stopped, and prove old recovery workers/snapshots cannot
  cross a disable/re-enable lifetime. Restore the original hook/options,
  Android preferences and borrowed power state at the end of each session.

`devmode=true` changes development-package discovery, not Java class loading.
An update of an installed JAR can return `RESTART`. If recording is active,
select **Restart Later**, restore the target UI's DVD player property to its
pre-test value, and wait for a verified idle window before restarting SageTV.
