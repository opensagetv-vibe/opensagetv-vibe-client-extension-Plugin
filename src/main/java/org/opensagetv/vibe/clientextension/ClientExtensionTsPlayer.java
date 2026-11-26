package org.opensagetv.vibe.clientextension;

import sage.MiniPlayer;
import sage.PlaybackException;

/**
 * Opt-in per-UI TS player hook; all transport and decoding stay in stock Core.
 *
 * FastMpeg2Reader.seek(0) takes a shortcut that leaves a pending nonzero
 * refinement target intact. A later read can then undo Restart and seek back
 * to the old file tail. Taking stock's normal positive-seek path at 1 ms
 * refreshes that target through its public API. This is below one video frame,
 * not a retry, private-field modification, alternative pusher or key remap.
 * Only explicit zero is adjusted; every other target delegates unchanged.
 */
public final class ClientExtensionTsPlayer extends MiniPlayer {
    @Override
    public long seek(long requestedMs) throws PlaybackException {
        return super.seek(ClientExtensionPlugin.tsRestartTarget(requestedMs));
    }
}
