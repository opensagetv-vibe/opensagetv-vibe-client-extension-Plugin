package org.opensagetv.vibe.clientextension;

import java.io.File;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import sage.MiniDVDPlayer;
import sage.PlaybackException;
import sage.SageTV;
import sage.VideoFrame;

/**
 * Stock-compatible DVD player hook for a single opted-in SageTV UI.
 *
 * Keeping the original MiniDVDPlayer implementation underneath the first
 * release is intentional. It preserves the authored DVD VM, stock MiniClient
 * media protocol, and old-client behavior while the hook/fallback gates are
 * proved. Future repairs must be individually gated before overriding any
 * playback method.
 */
public final class ClientExtensionDvdPlayer extends MiniDVDPlayer {
    private static final AtomicLong NEXT_INSTANCE = new AtomicLong();
    private static final ConcurrentHashMap<String, ResumeSnapshot> PENDING =
            new ConcurrentHashMap<String, ResumeSnapshot>();
    private static final long MAX_RELOAD_AGE_NS = 5_000_000_000L;
    private static final long MAX_TIME_DRIFT_MS = 5000L;
    private static final long RECOVERY_WAIT_NS = 20_000_000_000L;
    // Let the stock MiniClient graphics reconnect finish before DVD navigation.
    // Navigating inside load() overlaps the graphics handshake on stock Core.
    private static final long NAV_SETTLE_NS = 3_000_000_000L;
    private static final long STABLE_TITLE_CLOCK_MS = 10_000L;
    private static final int RESUME_SEEK_ATTEMPTS = 3;
    private static final long RESUME_SEEK_VERIFY_NS = 5_000_000_000L;
    private static final long RESUME_SEEK_SETTLE_NS = 3_000_000_000L;
    private static final long RESUME_SEEK_TOLERANCE_MS = 30_000L;
    private final long instance = NEXT_INSTANCE.incrementAndGet();
    private final AtomicLong recoveryEpoch = new AtomicLong();
    private volatile boolean inactiveBeforeFree;
    private volatile PresentedTitle presentedTitle;

    public ClientExtensionDvdPlayer() {
        super();
        trace("constructed", "");
    }

    @Override
    public void inactiveFile() {
        inactiveBeforeFree = true;
        if (recoveryEnabled() && uiMgr != null && currFile != null && getDVDDomain() == 4) {
            long position = getMediaTimeMillis();
            int title = getDVDTitle();
            if (title > 0 && position > 5000L) {
                PENDING.put(uiMgr.getLocalUIClientName(), new ResumeSnapshot(
                        currFile.getAbsolutePath(), title, position, System.nanoTime()));
                trace("resume-candidate", "title=" + title + " timeMs=" + position);
            }
        }
        if (traceEnabled())
            trace("inactive", "timeMs=" + getMediaTimeMillis() + " title=" + getDVDTitle());
        super.inactiveFile();
    }

    @Override
    public synchronized void free() {
        presentedTitle = null;
        recoveryEpoch.incrementAndGet();
        if (traceEnabled())
            trace("free", "inactive=" + inactiveBeforeFree + " timeMs=" +
                    getMediaTimeMillis() + " title=" + getDVDTitle());
        inactiveBeforeFree = false;
        super.free();
    }

    @Override
    public synchronized void load(byte majorTypeHint, byte minorTypeHint,
            String encodingHint, File file, String hostname, boolean timeshifted,
            long bufferSize) throws PlaybackException {
        presentedTitle = null;
        long epoch = recoveryEpoch.incrementAndGet();
        trace("load-start", "file=" + (file == null ? "null" : file.getName()));
        super.load(majorTypeHint, minorTypeHint, encodingHint, file, hostname,
                timeshifted, bufferSize);
        VideoFrame vf = VideoFrame.getVideoFrameForPlayer(this);
        long requestedTime = vf == null ? 0 : vf.getMediaTimeMillis(true);
        if (traceEnabled()) trace("load-complete", "title=" + getDVDTitle() +
                " requestedTimeMs=" + requestedTime);
        if (recoveryEnabled() && uiMgr != null && file != null) {
            String uiName = uiMgr.getLocalUIClientName();
            ResumeSnapshot previous = PENDING.get(uiName);
            if (previous != null) {
                if (previous.matches(file, requestedTime, System.nanoTime())) {
                    // A title change inside load() races the stock graphics
                    // reconnect. Defer navigation until the replacement player
                    // has completed its normal root-menu startup.
                    scheduleResumeAfterNav(epoch, file.getAbsolutePath(),
                            previous.title, previous.positionMs, uiName, previous);
                    trace("resume-scheduled", "title=" + previous.title +
                            " timeMs=" + previous.positionMs);
                } else {
                    PENDING.remove(uiName, previous);
                    trace("resume-skipped", "requestedTimeMs=" + requestedTime +
                            " previousTimeMs=" + previous.positionMs);
                }
            }
        }
    }

    @Override
    public synchronized long seek(long seekTimeMillis) throws PlaybackException {
        // A later user/Core seek supersedes the optional reconnect recovery.
        // Use stock MiniDVDPlayer's existing player monitor so cancellation
        // and the guarded recovery request cannot overtake one another.
        recoveryEpoch.incrementAndGet();
        // The pending handoff is shared with a replacement player. Cancelling
        // only this instance's worker leaves a claimed old snapshot eligible
        // for an immediate reload at a new <=5s user target. Supersede only
        // this UI's handoff; recovery's own super.seek bypasses this override,
        // and free() must retain genuine inactiveFile()/reload snapshots.
        if (uiMgr != null) {
            String uiName = uiMgr.getLocalUIClientName();
            if (uiName != null) PENDING.remove(uiName);
        }
        if (!extensionEnabled())
            return super.seek(seekTimeMillis);
        // Stock VideoFrame does not clamp DVD TIME_ADJUST before passing it to
        // the server-owned VM. A Skip Back near the title start can be negative.
        long boundedTime = clampDiscSeekTime(seekTimeMillis);
        if (boundedTime != seekTimeMillis)
            trace("seek-clamped", "requestedMs=" + seekTimeMillis);
        // IsShowingDVDMenu reflects visible NAV buttons, not necessarily the
        // read-ahead VM's current domain. Opt-in diagnostics use stock public
        // getters only; never inspect/change the private VM or guess its STC.
        boolean tracing = traceEnabled();
        if (tracing) trace("seek-reader-before", "requestedMs=" + boundedTime +
                " domain=" + getDVDDomain() + " title=" + getDVDTitle());
        restoreReadAheadTitle(boundedTime);
        long result = super.seek(boundedTime);
        if (tracing) trace("seek-reader-after", "returnedMs=" + result +
                " domain=" + getDVDDomain() + " title=" + getDVDTitle());
        return result;
    }

    @Override
    public synchronized boolean areDVDButtonsVisible() {
        boolean visible = super.areDVDButtonsVisible();
        // This getter already takes the stock player monitor before consulting
        // NAV/decoder state. Preserve that lock order; do not instrument the
        // media-clock getter (the push thread may hold decoderLock there).
        if (!visible && extensionEnabled() && super.getDVDDomain() == 4) {
            int title = super.getDVDTitle();
            long duration = super.getDurationMillis();
            if (title > 0 && duration > 15000L)
                presentedTitle = new PresentedTitle(title, duration,
                        System.nanoTime(), ClientExtensionPlugin.activeLifecycle());
        }
        return visible;
    }

    private void restoreReadAheadTitle(long target) throws PlaybackException {
        PresentedTitle last = presentedTitle;
        if (traceEnabled()) trace("seek-presented-title", "observed=" + (last != null) +
                " cachedTitle=" + (last == null ? 0 : last.title) +
                " durationMs=" + (last == null ? 0 : last.durationMs) +
                " ageMs=" + (last == null ? -1 : (System.nanoTime() - last.seenAtNs) / 1000000L) +
                " lifetimeMatches=" + (last != null && last.lifetime == ClientExtensionPlugin.activeLifecycle()) +
                " domain=" + super.getDVDDomain() +
                " buttons=" + super.areDVDButtonsVisible() + " displayedMs=" + super.getMediaTimeMillis());
        if (last == null || last.lifetime != ClientExtensionPlugin.activeLifecycle() ||
                last.title != super.getDVDTitle() ||
                super.getDVDDomain() == 4 || super.areDVDButtonsVisible()) return;
        long age = System.nanoTime() - last.seenAtNs;
        long clock = super.getMediaTimeMillis();
        // Stock read-ahead can enter the next menu >39s before decoded title
        // video ends. Wall-clock expiry would reject that proven valid case
        // (and a paused title). Custody instead follows this exact player/load,
        // active lifecycle and observed reader title; explicit navigation and
        // free/load clear it. The displayed near-end clock/button guards below
        // still exclude actual menus and ordinary seeks.
        if (age < 0L || target >= last.durationMs ||
                clock < last.durationMs - 15000L || clock >= last.durationMs) return;
        // Proven stock read-ahead case: VM already entered menu domain3 while
        // decoded title video still plays near its end. VM.seek silently applies
        // its target to the menu PGC, then returns the requested time anyway.
        // Re-select only this recently observed title through the existing
        // public control before seeking. Never modify VM/STC state or infer a
        // title from a disc filename; visible/intentional menus remain stock.
        if (super.playControlEx(202, last.title, last.title))
            trace("seek-read-ahead-title-restored", "title=" + last.title +
                    " requestedMs=" + target + " displayedMs=" + clock);
    }

    private static final class PresentedTitle {
        final int title;
        final long durationMs, seenAtNs, lifetime;
        PresentedTitle(int title, long durationMs, long seenAtNs, long lifetime) {
            this.title = title; this.durationMs = durationMs;
            this.seenAtNs = seenAtNs; this.lifetime = lifetime;
        }
    }

    /** Keep the stock DVD VM's seek input inside its non-negative title domain. */
    static long clampDiscSeekTime(long requestedMs) {
        return Math.max(0L, requestedMs);
    }

    @Override
    public synchronized boolean playControlEx(int playCode, long param1, long param2)
            throws PlaybackException {
        // Serialize with the stock player's monitor so an observation cannot
        // revive title custody while an explicit menu/title command executes.
        boolean changesTitle = playCode == 201 || playCode == 202 ||
                playCode == 208 || playCode == 209;
        if (changesTitle) presentedTitle = null;
        if (!extensionEnabled())
            return super.playControlEx(playCode, param1, param2);
        try {
            return super.playControlEx(playCode, param1, param2);
        } catch (IndexOutOfBoundsException malformedNavigation) {
            // Stock VideoFrame catches PlaybackException and removes this
            // DirectControl job. An unchecked malformed-IFO/PGC index would
            // instead escape before queue removal and can retry indefinitely.
            trace("malformed-nav", "code=" + playCode + " type=" +
                    malformedNavigation.getClass().getSimpleName());
            throw new PlaybackException(PlaybackException.DVD_INVALIDOP, 0);
        } finally {
            if (changesTitle) presentedTitle = null;
        }
    }

    private void scheduleResumeAfterNav(final long epoch, final String path,
            final int title, final long requestedTime, final String uiName,
            final ResumeSnapshot snapshot) {
        final long lifetime = ClientExtensionPlugin.activeLifecycle();
        Thread worker = new Thread(new Runnable() {
            public void run() {
                long deadline = System.nanoTime() + RECOVERY_WAIT_NS;
                long navigateAfter = System.nanoTime() + NAV_SETTLE_NS;
                boolean titleSelected = false;
                while (System.nanoTime() - deadline < 0) {
                    if (!ownsRecovery(epoch, lifetime) || currFile == null ||
                            !path.equals(currFile.getAbsolutePath())) return;
                    if (!titleSelected && System.nanoTime() - navigateAfter >= 0) {
                        try {
                            synchronized (ClientExtensionDvdPlayer.this) {
                                if (!ownsRecovery(epoch, lifetime) ||
                                        currFile == null ||
                                        !path.equals(currFile.getAbsolutePath())) return;
                                ClientExtensionDvdPlayer.super.playControlEx(202, title, title);
                                ClientExtensionDvdPlayer.super.playControlEx(0, 0, 0);
                            }
                            titleSelected = true;
                            trace("resume-title-selected", "title=" + title);
                        } catch (PlaybackException failure) {
                            trace("resume-failed", failure.getClass().getSimpleName());
                            return;
                        } catch (RuntimeException failure) {
                            trace("resume-failed", failure.getClass().getSimpleName());
                            return;
                        }
                    }
                    if (!titleSelected) {
                        try { Thread.sleep(100L); }
                        catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                        continue;
                    }
                    // A first NAV packet is not sufficient on stock Core. A
                    // physical stock-player gate showed that seeking at about
                    // one second stalls its DVD push clock near eight seconds
                    // for 25-30 seconds. Wait for the new title stream to
                    // stabilize before issuing the optional recovery seek.
                    long duration = getDurationMillis();
                    long readyClock = recoveryReadyClock(duration);
                    if (getDVDDomain() == 4 && getDVDTitle() == title &&
                            duration >= requestedTime &&
                            getMediaTimeMillis() >= readyClock) {
                        if (recoveryEpoch.compareAndSet(epoch, epoch + 1))
                            applyAndVerifyResumeSeek(epoch + 1, path, title, requestedTime,
                                    uiName, snapshot, lifetime);
                        return;
                    }
                    try { Thread.sleep(100L); }
                    catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                trace("resume-timeout", "title=" + title +
                        " timeMs=" + requestedTime);
                PENDING.remove(uiName, snapshot);
            }
        }, "VibeDVDResume-" + instance);
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * Stock's DVD Push reader can acknowledge the first seek immediately after
     * a reload without advancing its STC. Retry only this plugin-owned reload
     * recovery, verify the server player's real clock after each request, and
     * never route the operation through the Android client or a private Core
     * event. A normal user/Core seek still supersedes recovery through
     * {@link #seek(long)} and {@code recoveryEpoch}.
     */
    private void applyAndVerifyResumeSeek(long epoch, String path, int title,
            long requestedTime, String uiName, ResumeSnapshot snapshot, long lifetime) {
        for (int attempt = 1; attempt <= RESUME_SEEK_ATTEMPTS; attempt++) {
            if (!ownsRecovery(epoch, lifetime) || currFile == null ||
                    !path.equals(currFile.getAbsolutePath()) ||
                    getDVDDomain() != 4 || getDVDTitle() != title) return;
            try {
                // Stock seek/free/load already serialize on this player.
                // Check ownership under that same monitor: a newer user seek
                // must not slip between this check and a stale recovery seek.
                // Do not hold the monitor while waiting for the stream clock.
                synchronized (this) {
                    if (!ownsRecovery(epoch, lifetime) ||
                            currFile == null || !path.equals(currFile.getAbsolutePath()) ||
                            getDVDDomain() != 4 || getDVDTitle() != title) return;
                    ClientExtensionDvdPlayer.super.seek(requestedTime);
                }
                trace("resume-seek-request", "attempt=" + attempt +
                        " title=" + title + " timeMs=" + requestedTime);
            } catch (PlaybackException failure) {
                trace("resume-failed", failure.getClass().getSimpleName());
                return;
            } catch (RuntimeException failure) {
                trace("resume-failed", failure.getClass().getSimpleName());
                return;
            }

            long seekRequestedAt = System.nanoTime();
            long verifyDeadline = seekRequestedAt + RESUME_SEEK_VERIFY_NS;
            while (System.nanoTime() - verifyDeadline < 0) {
                if (!ownsRecovery(epoch, lifetime) || currFile == null ||
                        !path.equals(currFile.getAbsolutePath()) ||
                        getDVDDomain() != 4 || getDVDTitle() != title) return;
                long actualTime = getMediaTimeMillis();
                // MiniDVDPlayer reports the requested value optimistically
                // before the replacement Push stream exposes whether its STC
                // really moved. Do not accept that transient echo as proof.
                if (System.nanoTime() - seekRequestedAt >= RESUME_SEEK_SETTLE_NS &&
                        resumePositionReached(requestedTime, actualTime)) {
                    PENDING.remove(uiName, snapshot);
                    trace("resume-applied", "attempt=" + attempt +
                            " title=" + title + " requestedMs=" + requestedTime +
                            " actualMs=" + actualTime);
                    return;
                }
                try { Thread.sleep(100L); }
                catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            trace("resume-seek-unconfirmed", "attempt=" + attempt +
                    " requestedMs=" + requestedTime +
                    " actualMs=" + getMediaTimeMillis());
        }
        trace("resume-failed", "seek-unconfirmed title=" + title +
                " timeMs=" + requestedTime);
        PENDING.remove(uiName, snapshot);
    }

    static boolean resumePositionReached(long requestedMs, long actualMs) {
        return actualMs >= Math.max(0L, requestedMs - RESUME_SEEK_TOLERANCE_MS);
    }

    private void trace(String event, String detail) {
        if (!traceEnabled()) return;
        String ui = uiMgr == null ? "unbound" : uiMgr.getLocalUIClientName();
        System.out.println("[VibeClientExtension] dvd instance=" + instance +
                " ui=" + ui + " event=" + event + " " + detail);
    }

    private static boolean traceEnabled() {
        return serverBoolean("trace.dvd");
    }

    private static boolean recoveryEnabled() {
        return extensionEnabled() && serverBoolean("reload.recovery");
    }

    private static boolean extensionEnabled() {
        return ClientExtensionPlugin.activeLifecycle() != 0L && serverBoolean("enabled");
    }

    private boolean ownsRecovery(long epoch, long lifetime) {
        return lifetime != 0L && ClientExtensionPlugin.activeLifecycle() == lifetime &&
                recoveryEpoch.get() == epoch && recoveryEnabled();
    }

    static void clearPendingRecovery() { PENDING.clear(); }

    /** Delay a reload seek until stock DVD Push has a stable title clock. */
    static long recoveryReadyClock(long durationMs) {
        return Math.min(STABLE_TITLE_CLOCK_MS,
                Math.max(250L, Math.max(0L, durationMs) / 3L));
    }

    private static boolean serverBoolean(String name) {
        try {
            Object setting = SageTV.api("GetServerProperty", new Object[]{
                    "vibe/client_extension/" + name, "false"});
            return Boolean.parseBoolean(String.valueOf(setting));
        } catch (Exception ignored) { return false; }
    }

    static final class ResumeSnapshot {
        final String path;
        final int title;
        final long positionMs;
        final long savedAtNs;
        final long lifetime = ClientExtensionPlugin.activeLifecycle();
        private boolean claimed;

        ResumeSnapshot(String path, int title, long positionMs, long savedAtNs) {
            this.path = path;
            this.title = title;
            this.positionMs = positionMs;
            this.savedAtNs = savedAtNs;
        }

        synchronized boolean matches(File file, long requestedMs, long nowNs) {
            long age = nowNs - savedAtNs;
            if (lifetime == 0L || ClientExtensionPlugin.activeLifecycle() != lifetime ||
                    !path.equals(file.getAbsolutePath()) || title <= 0 ||
                    positionMs <= 5000L || age < 0 || age > MAX_RELOAD_AGE_NS)
                return false;
            if (requestedMs > 5000L &&
                    Math.abs(requestedMs - positionMs) <= MAX_TIME_DRIFT_MS) {
                claimed = true;
                return true;
            }
            // Once Core has supplied the matching reload target, retain the
            // snapshot across an immediate second player rebuild caused by a
            // retried type-5 handshake. An ordinary fresh DVD start cannot
            // enter this branch because it has not first claimed the snapshot.
            return claimed && requestedMs <= 5000L;
        }
    }
}
