package org.opensagetv.vibe.clientextension;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLong;
import sage.MiniClientSageRenderer;
import sage.MiniDVDPlayer;
import sage.PlaybackException;
import sage.SageTV;
import sage.VideoFrame;
import sage.dvd.pci_t;

/**
 * Opt-in independent DVD navigation/presentation session over stock media
 * commands. The superclass supplies stock load/ISO/renderer lifetime plumbing
 * and the entire fallback path. Its private reader is never inspected or
 * modified, and its push thread is not started for an owned session.
 */
public final class PluginOwnedDvdPlayer extends MiniDVDPlayer {
    private static final java.util.concurrent.ConcurrentHashMap<String,PluginOwnedDvdPlayer> ACTIVE =
            new java.util.concurrent.ConcurrentHashMap<String,PluginOwnedDvdPlayer>();
    private volatile Session owned;
    private String hostname;
    private boolean freeing;

    /** Explicit Configure action, never an implicit override of Watch policy. */
    static String requestIntent(String ui,String action) {
        return requestIntent(ui,action,null);
    }
    /** HTTP callers must retain custody of the exact acknowledged DVD load. */
    static String requestIntent(String ui,String action,DvdClientCapabilities.Lease expected) {
        if (!("action.dvd_save_resume".equals(action) || "action.dvd_resume".equals(action)
                || "action.dvd_beginning".equals(action) || "action.dvd_clear_resume".equals(action)))
            return "Refused: unknown DVD action";
        if (expected==null && "action.dvd_clear_resume".equals(action) && ClientExtensionPlugin.ownedDvdEnabled()
                && ui!=null && ui.matches("[0-9A-Fa-f]{12}")) {
            try { DvdPlaybackIntent.clear(ui); return "Cleared owned DVD resume point"; }
            catch (Exception unavailable) { return "Refused: bookmark clear unavailable"; }
        }
        PluginOwnedDvdPlayer player=ACTIVE.get(ui);
        Session session=player==null ? null : player.owned;
        if (!ClientExtensionPlugin.ownedDvdEnabled() || session==null || !session.running
                || !session.initialized || session.lifetime!=ClientExtensionPlugin.activeLifecycle())
            return "Refused: no active opted-in owned DVD for this selected UI";
        synchronized (session.control) {
            try {
                // Repeat lifecycle/session checks at mutation, not just before
                // acquiring control: Stop/replacement may have won the race.
                if (player.owned!=session || ACTIVE.get(ui)!=player || !session.running
                        || session.lifetime!=ClientExtensionPlugin.activeLifecycle()
                        || !ClientExtensionPlugin.ownedDvdEnabled()
                        || (expected!=null && (!ClientExtensionPlugin.dvdClientControlsEnabled()
                        || session.capabilities!=expected || !expected.confirmed
                        || DvdClientCapabilities.lookup(expected.client)!=expected)))
                    return "Refused: DVD load changed or controls disabled";
                if ("action.dvd_clear_resume".equals(action)) {
                    DvdPlaybackIntent.clear(ui); return "Cleared owned DVD resume point";
                }
                if ("action.dvd_save_resume".equals(action)) {
                    DvdPresentationTimeline.Nav nav=session.presented();
                    if (nav==null || nav.state.domain!=4) return "Refused: save a playing DVD title, not a menu";
                    DvdPlaybackIntent.save(ui,player.currFile.getAbsolutePath(),nav.state.title,session.lastClock);
                    return "Saved owned DVD title="+nav.state.title+" positionMs="+session.lastClock;
                }
                DvdPlaybackIntent.Bookmark target="action.dvd_resume".equals(action)
                        ? DvdPlaybackIntent.read(ui,player.currFile.getAbsolutePath()) : new DvdPlaybackIntent.Bookmark(0,0);
                if (target==null) return "Refused: no saved owned bookmark for this exact disc/UI";
                session.intent=target;
                session.reload=null; // explicit user intent wins over recovery
                return "Queued explicit owned DVD "+("action.dvd_resume".equals(action)?"resume":"main-feature start");
            } catch (Exception invalid) { return "DVD intent refused: "+invalid.getClass().getSimpleName(); }
        }
    }

    @Override public synchronized void load(byte major, byte minor, String encoding,
            File file, String host, boolean timeshifted, long size) throws PlaybackException {
        hostname = host;
        super.load(major, minor, encoding, file, host, timeshifted, size);
        Session session = owned;
        if (session != null && reloadEnabled()) {
            VideoFrame vf = VideoFrame.getVideoFrameForPlayer(this);
            long requested = vf.getMediaTimeMillis(true);
            session.reload = OwnedDvdReloadState.claim(uiMgr.getLocalUIClientName(),
                    file.getAbsolutePath(), requested,
                    System.nanoTime(), session.lifetime);
            session.trace("reload-claim", "matched=" + (session.reload != null) + " requestedMs=" + requested);
        }
    }

    private boolean reloadEnabled() {
        return ClientExtensionPlugin.ownedDvdRecovery();
    }

    @Override public void inactiveFile() {
        Session session = owned;
        if (session != null && session.running && reloadEnabled() && currFile != null) {
            DvdPresentationTimeline.Nav shown = session.presented();
            if (shown != null && shown.state.domain == 4) {
                OwnedDvdReloadState.save(uiMgr.getLocalUIClientName(), currFile.getAbsolutePath(),
                        shown.state.title, session.lastClock, System.nanoTime(), session.lifetime);
                session.trace("reload-save", "title=" + shown.state.title + " sourceMs=" + session.lastClock);
            }
        }
        super.inactiveFile();
    }

    @Override protected void createPushThread() {
        if (!ClientExtensionPlugin.ownedDvdEnabled()) { super.createPushThread(); return; }
        StockDvdNavigator cursor = null;
        try {
            VideoFrame vf = VideoFrame.getVideoFrameForPlayer(this);
            MiniClientSageRenderer renderer = (MiniClientSageRenderer) uiMgr.getRootPanel().getRenderEngine();
            cursor = new StockDvdNavigator(currFile, hostname, vf.getDisplayAspectRatio() > 1.4f);
            DvdMiniClientDriver wire = new DvdMiniClientDriver(renderer.getPlayerSocketChannel());
            // Ownership changes only before any owned payload has been sent.
            owned = new Session(cursor, wire);
            pushThread = new Thread(owned, "VibeOwnedDVD-" + uiMgr.getLocalUIClientName());
            pushThread.setDaemon(true);
            pushThread.start();
        } catch (IOException | RuntimeException unavailable) {
            if (cursor != null) cursor.close();
            owned = null;
            System.out.println("[VibeClientExtension] owned DVD unavailable before start: " +
                    unavailable.getClass().getSimpleName() + "; stock fallback");
            super.createPushThread();
        }
    }

    @Override public synchronized void free() {
        Session session = owned;
        if (session != null) session.close();
        // Keep the ownership marker until the superclass has released its
        // unused reader/ISO mount. Its protected close hook must not issue a
        // second DEINIT on the socket already closed by our session.
        freeing = true;
        try { super.free(); }
        finally { owned = null; freeing = false; }
    }
    @Override protected boolean closeDriver0(long pointer) {
        Session session = owned;
        if (session == null) return super.closeDriver0(pointer);
        session.close();
        return true;
    }
    @Override public long getDurationMillis() {
        Session session = owned;
        if (session == null) return super.getDurationMillis();
        DvdPresentationTimeline.Nav nav = session.presented();
        return nav == null ? session.timeline.durationMillis() : nav.durationPts / 90L;
    }
    @Override public long getMediaTimeMillis() {
        Session session = owned;
        return session == null ? super.getMediaTimeMillis() : session.mediaTime();
    }
    @Override public float getPlaybackRate() {
        Session session = owned; return session == null ? super.getPlaybackRate() : session.rate;
    }
    @Override public boolean pause() {
        Session session = owned;
        if (session == null) return super.pause();
        synchronized (session.control) {
            try { session.wire.command(20, new byte[0]); currState = PAUSE_STATE; return true; }
            catch (IOException error) { session.failed(error); return false; }
        }
    }
    @Override public boolean play() {
        Session session = owned;
        if (session == null) return super.play();
        synchronized (session.control) {
            try {
                session.wire.command(21, new byte[0]);
                // Paused input/output queues intentionally stop progressing.
                // Give the resumed provider a fresh progress window rather
                // than immediately treating the user's pause as a stall.
                session.transformDeadline = System.nanoTime() + 15000000000L;
                currState = PLAY_STATE;
                return true;
            }
            catch (IOException error) { session.failed(error); return false; }
        }
    }
    @Override public void stop() {
        // Stock free() invokes stop() too. That teardown must retain an
        // inactiveFile handoff; only an independent Stop cancels it.
        if (!freeing && uiMgr != null) OwnedDvdReloadState.cancel(uiMgr.getLocalUIClientName());
        Session session = owned;
        if (session == null) { super.stop(); return; }
        synchronized (session.control) {
            session.running = false;
            session.reload = null;
            session.intent = null;
            currState = STOPPED_STATE;
            try { session.wire.command(19, new byte[0]); }
            catch (IOException ignored) { }
        }
    }
    @Override public long seek(long millis) throws PlaybackException {
        if (uiMgr != null) OwnedDvdReloadState.cancel(uiMgr.getLocalUIClientName());
        Session session = owned;
        if (session == null) return super.seek(millis);
        synchronized (session.control) {
            try {
                session.reload = null;
                session.intent = null;
                DvdPresentationTimeline.Nav shown = session.presented();
                StockDvdNavigator.Snapshot reader = session.cursor.snapshot();
                boolean titleReopened = false;
                if (!session.explicitMenu && shown != null && shown.state.domain == 4 &&
                        shown.state.title > 0 && reader.domain != 4 &&
                        shown.pci.hli.hl_gi.hli_ss.get() == 0) {
                    session.cursor.control(202, shown.state.title, shown.state.title, null);
                    titleReopened = true;
                }
                session.epoch.incrementAndGet();
                session.closeTransform();
                session.capacity = 0;
                session.wire.command(22, new byte[0]);
                session.timeline.discontinuity();
                if (titleReopened) {
                    session.deferredSeek = Math.max(0, millis);
                    session.deferredSeekTitle = shown.state.title;
                } else session.cursor.seek(Math.max(0, millis));
                session.lastClock = Math.max(0, millis);
                session.lastClockRead = 0;
                return Math.max(0, millis);
            } catch (IOException failure) {
                session.failed(failure);
                throw new PlaybackException(PlaybackException.DVD_INVALIDOP, 0);
            }
        }
    }
    @Override public float setPlaybackRate(float value) {
        Session session = owned;
        if (session == null) return super.setPlaybackRate(value);
        if (Float.isNaN(value) || Float.isInfinite(value) || value == 0) value = 1;
        synchronized (session.control) {
            if (session.state().domain != 4) value = 1;
            // Remux time is linear; sparse VM scan VOBUs are not. Use the
            // native stock DVD representation for scans rather than inventing
            // a multiplied clock for the transformed stream.
            if (value != 1 && session.transform != null) {
                try { session.fallback(new IOException("native representation for DVD scan")); }
                catch (IOException failure) { session.failed(failure); return 1; }
            }
            session.cursor.rate(value);
            session.rate = value;
            return value;
        }
    }
    @Override public boolean playControlEx(int code, long first, long second) throws PlaybackException {
        Session session = owned;
        if (session == null) return super.playControlEx(code, first, second);
        synchronized (session.control) {
            if (code == 0) return true; // Core's request to refresh public state.
            session.intent = null; // a newer actual user/control job wins
            DvdPresentationTimeline.Nav shown = session.presented();
            boolean result = code == 213
                    ? session.cursor.angle(first, session.lastClock, shown == null ? null : shown.pci)
                    : session.cursor.control(code, first, second, shown == null ? null : shown.pci);
            if (code == 213) session.trace("angle", "requested=" + first + " accepted=" + result
                    + " clockMs=" + session.lastClock + " uop=" + (shown == null ? -1
                    : shown.pci.pci_gi.vobu_uop_ctl.angle_change.get()));
            session.latest = session.cursor.snapshot();
            session.lastHighlight = null;
            if (code == 201 || code == 209) session.explicitMenu = true;
            if ((code >= 201 && code <= 209) || code == 212 || (code == 213 && result)) {
                session.deferredSeek = -1;
                session.epoch.incrementAndGet();
                // Only actual navigation marks a new representation epoch;
                // directional highlight changes do not discard media.
            }
            return result;
        }
    }
    private StockDvdNavigator.Snapshot state() {
        Session session = owned; return session == null ? StockDvdNavigator.Snapshot.EMPTY : session.state();
    }
    @Override public int getDVDTitle() { return owned == null ? super.getDVDTitle() : state().title; }
    @Override public int getDVDTotalTitles() { return owned == null ? super.getDVDTotalTitles() : state().titles; }
    @Override public int getDVDChapter() { return owned == null ? super.getDVDChapter() : state().chapter; }
    @Override public int getDVDTotalChapters() { return owned == null ? super.getDVDTotalChapters() : state().chapters; }
    @Override public int getDVDDomain() { return owned == null ? super.getDVDDomain() : state().domain; }
    @Override public int getDVDAngle() { return owned == null ? super.getDVDAngle() : state().angle; }
    @Override public int getDVDTotalAngles() { return owned == null ? super.getDVDTotalAngles() : state().angles; }
    @Override public String getDVDLanguage() { return owned == null ? super.getDVDLanguage() : state().audio; }
    @Override public String getDVDSubpicture() { return owned == null ? super.getDVDSubpicture() : state().subpicture; }
    @Override public String[] getDVDAvailableLanguages() {
        return owned == null ? super.getDVDAvailableLanguages() : state().audioTracks.clone();
    }
    @Override public String[] getDVDAvailableSubpictures() {
        return owned == null ? super.getDVDAvailableSubpictures() : state().subtitleTracks.clone();
    }
    @Override public boolean areDVDButtonsVisible() {
        if (owned == null) return super.areDVDButtonsVisible();
        DvdPresentationTimeline.Nav nav = owned.presented();
        return nav != null && nav.pci.hli.hl_gi.hli_ss.get() != 0 && nav.pci.hli.hl_gi.btn_ns.get() > 0;
    }
    @Override public Dimension getVideoDimensions() {
        return owned == null ? super.getVideoDimensions() : new Dimension(720, 480);
    }
    @Override public void setMute(boolean mute) {
        Session session = owned;
        if (session == null) { super.setMute(mute); return; }
        try { session.wire.integer(18, mute ? 1 : 0); currMute = mute; }
        catch (IOException error) { session.failed(error); }
    }
    @Override public float setVolume(float volume) {
        Session session = owned;
        if (session == null) return super.setVolume(volume);
        if (Float.isNaN(volume)) return curVolume;
        try { curVolume = session.wire.integer(27, (int)(Math.max(0, Math.min(1, volume)) * 65535)) / 65535f; }
        catch (IOException error) { session.failed(error); }
        return curVolume;
    }
    @Override public synchronized float getVolume() { return owned == null ? super.getVolume() : curVolume; }
    @Override public synchronized void setVideoRectangles(Rectangle source, Rectangle target, boolean hidden) {
        Session session = owned;
        if (session == null) { super.setVideoRectangles(source, target, hidden); return; }
        try { session.wire.rectangles(source, target); }
        catch (IOException error) { session.failed(error); }
    }
    @Override public boolean frameStep(int amount) { return owned == null && super.frameStep(amount); }
    @Override public float getCurrentAspectRatio() {
        return owned == null ? super.getCurrentAspectRatio() : 0;
    }

    static boolean transformTimedOut(long now, long deadline, boolean paused) {
        return !paused && now - deadline > 0;
    }

    static boolean coversDeclaredSubpictures(int[] ids, int declaredCount) {
        // A bounded FFmpeg probe cannot prove that a late-authored subtitle
        // stream does not exist. IFO/VM declarations are authoritative. A
        // conservative Native fallback is safer than silently losing tracks.
        return ids != null && declaredCount >= 0 && declaredCount <= 32 && ids.length >= declaredCount;
    }

    static boolean coversSelectedSubpicture(int[] ids, int selector) {
        if (selector < 0 || selector == 62 || (selector & 0x80) != 0 || (selector & 0x40) == 0) return true;
        return ids != null && java.util.Arrays.binarySearch(ids, 32 + (selector & 31)) >= 0;
    }

    static String subpictureIdentity(int[] ids) throws IOException {
        if (ids == null || ids.length > 32) throw new IOException("Invalid DVD SPU inventory");
        StringBuilder value = new StringBuilder(); int previous = 31;
        for (int id : ids) {
            if (id <= previous || id > 63) throw new IOException("Invalid DVD SPU identity order");
            if (value.length() > 0) value.append(',');
            value.append(id); previous = id;
        }
        return value.toString();
    }
    static boolean denseSubpictures(int[] ids) {
        for (int i = 0; i < ids.length; i++) if (ids[i] != 32 + i) return false;
        return true;
    }

    private final class Session implements Runnable {
        final StockDvdNavigator cursor;
        final DvdMiniClientDriver wire;
        final DvdPresentationTimeline timeline = new DvdPresentationTimeline();
        final Object control = new Object();
        final AtomicLong epoch = new AtomicLong();
        final long lifetime = ClientExtensionPlugin.activeLifecycle();
        final String requestedMode = ClientExtensionPlugin.ownedDvdMode();
        final DvdStartupPolicy startupPolicy = new DvdStartupPolicy(
                ClientExtensionPlugin.ownedDvdSkipMenus(), ClientExtensionPlugin.ownedDvdSkipPreviews());
        final DvdClientCapabilities.Lease capabilities;
        volatile boolean running = true;
        volatile boolean initialized;
        volatile float rate = 1;
        volatile long lastClock;
        volatile long lastClockRead;
        volatile boolean explicitMenu;
        volatile StockDvdNavigator.Snapshot latest = StockDvdNavigator.Snapshot.EMPTY;
        private long stillStarted;
        private int stillSeconds;
        private int capacity;
        private volatile DvdTransformPort transform;
        private byte[] transformedPending;
        private boolean nativeWire = true;
        private boolean nativeOnly;
        private boolean transformOpened;
        private int selectedSubpicture = -1;
        private long transformDeadline;
        private long transformedBytes;
        private DvdPresentationTimeline.Nav representationNav;
        private final DvdPresentationTimeline.Origin transformOrigin = new DvdPresentationTimeline.Origin();
        private DvdPresentationTimeline.Nav lastHighlight;
        private long deferredSeek = -1;
        private int deferredSeekTitle;
        private boolean firstPayload = true, scanSent;
        private volatile OwnedDvdReloadState.Ticket reload;
        private volatile DvdPlaybackIntent.Bookmark intent;
        private final long started = System.nanoTime();
        Session(StockDvdNavigator cursor, DvdMiniClientDriver wire) {
            this.cursor = cursor; this.wire = wire;
            capabilities = DvdClientCapabilities.register(uiMgr.getLocalUIClientName());
        }
        long mediaTime() {
            // Before OPENURL marks this socket as DVD, a client may still
            // use the ordinary five-byte detailed GETMEDIATIME reply. A clock
            // query racing that first OPENURL leaves one byte queued and
            // shifts every later DVD reply. No network clock read is allowed
            // until our four-byte DVD handshake has completed.
            if (!running || !initialized) return lastClock;
            long now = System.nanoTime();
            if (now - lastClockRead >= 0 && now - lastClockRead < 100000000L) return lastClock;
            try {
                int raw = wire.command(17, new byte[0]);
                if (raw >= 0) { lastClock = raw; lastClockRead = now; }
            } catch (IOException failure) { failed(failure); }
            return lastClock;
        }
        DvdPresentationTimeline.Nav presented() { return timeline.presented(mediaTime()); }
        StockDvdNavigator.Snapshot state() {
            DvdPresentationTimeline.Nav nav = presented();
            return nav == null ? latest : nav.state;
        }
        public void run() {
            byte[] pending = new byte[32768];
            int length = 0;
            long pendingEpoch = epoch.get();
            try {
                wire.open("push:dvd?disc_transport=native&format=mpegps");
                wire.command(21, new byte[0]);
                initialized = true;
                ACTIVE.put(uiMgr.getLocalUIClientName(),PluginOwnedDvdPlayer.this);
                while (running) {
                    applyIntent();
                    resumeReload();
                    if (currState == PAUSE_STATE) { Thread.sleep(20); continue; }
                    pumpTransformed();
                    refreshHighlight();
                    if (capacity < 32768) {
                        capacity = wire.push(null, 0);
                        if (capacity < 32768) { Thread.sleep(20); continue; }
                    }
                    long generation = epoch.get();
                    StockDvdNavigator.Event event = cursor.next();
                    if (generation != epoch.get()) { length = 0; pendingEpoch = epoch.get(); continue; }
                    if (pendingEpoch != generation) { length = 0; pendingEpoch = generation; }
                    if (event.type == StockDvdNavigator.DATA) {
                        latest = event.state;
                        if (event.payload.length > pending.length - length) {
                            send(pending, length, generation); length = 0;
                        }
                        System.arraycopy(event.payload, 0, pending, length, event.payload.length);
                        length += event.payload.length;
                        if (length == pending.length) { send(pending, length, generation); length = 0; }
                    } else {
                        // A CELL for the next title must not relabel the last
                        // menu bytes as title input to a transform. A VM flush
                        // discards old-generation pending bytes outright.
                        if (length > 0 && event.type != StockDvdNavigator.FLUSH)
                            send(pending, length, generation);
                        length = 0;
                        latest = event.state;
                        if (generation != epoch.get()) continue;
                        if ((event.type == StockDvdNavigator.EMPTY || event.type == StockDvdNavigator.STILL ||
                                event.type == StockDvdNavigator.END) && !finishTransform()) {
                            Thread.sleep(20); continue;
                        }
                        synchronized (control) {
                            if (generation == epoch.get()) handle(event);
                        }
                    }
                }
            } catch (IOException | RuntimeException failure) { failed(failure); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            finally {
                ACTIVE.remove(uiMgr.getLocalUIClientName(),PluginOwnedDvdPlayer.this);
                closeTransform(); DvdClientCapabilities.release(capabilities); cursor.close();
            }
        }
        private void applyIntent() throws IOException {
            synchronized (control) {
                DvdPlaybackIntent.Bookmark target=intent;
                if (target==null) return;
                intent=null;
                if (!running || !ClientExtensionPlugin.ownedDvdEnabled()
                        || ClientExtensionPlugin.activeLifecycle()!=lifetime) return;
                int title=target.title==0 ? cursor.mainFeatureTitle() : target.title;
                epoch.incrementAndGet(); closeTransform(); capacity=0;
                selectTitle(title);
                deferredSeek=target.position; deferredSeekTitle=title;
                timeline.discontinuity(); wire.command(22,new byte[0]);
                // Explicit Start/Resume means play. Remaining paused here
                // prevents the pump from ever reaching destination NAV and
                // applying its deferred seek. Save/Clear do not enter this path.
                if (currState==PAUSE_STATE) {
                    wire.command(21,new byte[0]);
                    transformDeadline=System.nanoTime()+15000000000L;
                    currState=PLAY_STATE;
                }
                trace("explicit-intent","title="+title+" sourceMs="+target.position);
            }
        }
        private void resumeReload() throws IOException {
            OwnedDvdReloadState.Ticket ticket = reload;
            if (ticket == null || System.nanoTime() - started < 3000000000L) return;
            synchronized (control) {
                if (reload != ticket) return;
                if (!reloadEnabled() || ClientExtensionPlugin.activeLifecycle() != lifetime) {
                    reload = null; return;
                }
                if (timeline.presented(lastClock) == null) return;
                reload = null; // one handoff only; a newer public Seek cancels it
                epoch.incrementAndGet(); closeTransform(); capacity = 0;
                selectTitle(ticket.title);
                deferredSeek = ticket.position; deferredSeekTitle = ticket.title;
                timeline.discontinuity(); wire.command(22, new byte[0]);
                trace("reload", "title=" + ticket.title + " sourceMs=" + ticket.position);
            }
        }
        private void send(byte[] bytes, int length, long generation) throws IOException, InterruptedException {
            if (length == 0) return;
            byte[] chunk = new byte[length]; System.arraycopy(bytes, 0, chunk, 0, length);
            maybeStartTransform(generation);
            while (running && generation == epoch.get()) {
                synchronized (control) {
                    if (generation != epoch.get()) return;
                    if (transform != null) {
                        try { if (transform.offer(chunk)) return; }
                        catch (IOException error) { fallback(error); return; }
                    } else {
                        if (!nativeWire) nativeRepresentation();
                        if (capacity >= length) {
                            capacity = wire.push(chunk, payloadFlags()); return;
                        }
                        capacity = wire.push(null, 0);
                    }
                }
                pumpTransformed();
                Thread.sleep(10);
            }
        }
        private void maybeStartTransform(long generation) throws IOException, InterruptedException {
            if (transform != null || nativeOnly || "native".equals(requestedMode) || latest.domain != 4) return;
            // The capability exchange is per player/load, not a guessed client
            // identity or a new Core property. Old clients never enter MIM.
            long deadline = System.nanoTime() + 1000000000L;
            while (!capabilities.confirmed && running && generation == epoch.get() && System.nanoTime() < deadline)
                Thread.sleep(10);
            if (generation != epoch.get() || !running) return;
            if (!capabilities.confirmed || !ClientExtensionPlugin.ownedDvdEnabled() ||
                    ClientExtensionPlugin.activeLifecycle() != lifetime) { nativeOnly = true; return; }
            // The HTML5 MiniClient consumes this native MPEG-PS through its
            // existing browser HLS producer. It does not claim Android's TS/
            // SPU-remapping contract and must never receive a second transform.
            if(capabilities.nativeProgramStreamOnly){nativeOnly=true;return;}
            DvdTransformPort candidate;
            try { candidate = DvdTransformPort.open(requestedMode); }
            catch (IOException unavailable) {
                nativeOnly = true;
                System.out.println("[VibeClientExtension] DVD transform unavailable; native: " + unavailable.getMessage());
                return;
            }
            synchronized (control) {
                if (generation != epoch.get() || !running) { candidate.close(); return; }
                transform = candidate;
                transformOrigin.capture(representationNav);
                // Input stream IDs are known only after the provider probes
                // this actual segment. Retain Native until first output and
                // its bounded inventory are ready, not a guessed dense map.
                transformOpened = false; transformedBytes = 0;
                transformDeadline = System.nanoTime() + 15000000000L;
            }
        }
        private void reapplyRepresentationClock() throws IOException {
            applyRepresentationClock(representationNav);
        }
        private void applyRepresentationClock(DvdPresentationTimeline.Nav nav) throws IOException {
            if (nav == null) return;
            wire.bytes(32, ByteBuffer.allocate(4).putInt((int)((nav.startPts -
                    nav.pci.pci_gi.vobu_s_ptm.get()) / 2)).array());
            wire.integer(35, (int)(nav.startPts / 2));
        }
        private void pumpTransformed() throws IOException {
            synchronized (control) {
                if (transform == null) return;
                if (!ClientExtensionPlugin.ownedDvdEnabled() || ClientExtensionPlugin.activeLifecycle() != lifetime) {
                    fallback(new IOException("DVD provider lifecycle ended")); return;
                }
                try {
                    if (transformedPending == null) transformedPending = transform.poll();
                    if (transformedPending != null) {
                        if (!transformOpened) {
                            int[] sourceIds = transform.sourceSubpictureIds();
                            if (sourceIds == null) {
                                if (transformTimedOut(System.nanoTime(), transformDeadline, false))
                                    fallback(new IOException("DVD input identity unavailable"));
                                return;
                            }
                            String identity = subpictureIdentity(sourceIds);
                            if (!coversDeclaredSubpictures(sourceIds, latest.subtitleTracks.length)
                                    || !coversSelectedSubpicture(sourceIds, selectedSubpicture)) {
                                fallback(new IOException("DVD probe omitted declared subpictures")); return;
                            }
                            if (capabilities.revision < 2 && !denseSubpictures(sourceIds)) {
                                fallback(new IOException("DVD client lacks sparse-SPU mapping")); return;
                            }
                            if ("mpegps".equals(transform.format) && capabilities.revision < 3 && !denseSubpictures(sourceIds)) {
                                fallback(new IOException("DVD client lacks Copy SPU remap")); return;
                            }
                            initialized = false;
                            wire.open("push:dvd?disc_transport=" + transform.transport + "&format=" + transform.format
                                    + "&clock=nav_v1&spu_ids=" + identity);
                            wire.command(21, new byte[0]); nativeWire = false; initialized = true;
                            firstPayload = true; scanSent = false; capacity = 0; lastClockRead = 0;
                            applyRepresentationClock(transformOrigin.get()); transformOpened = true;
                        }
                        if (capacity < transformedPending.length) capacity = wire.push(null, 0);
                        if (capacity >= transformedPending.length) {
                            capacity = wire.push(transformedPending, payloadFlags());
                            transformedBytes += transformedPending.length;
                            transformedPending = null; transformDeadline = System.nanoTime() + 15000000000L;
                        }
                    } else if (transformTimedOut(System.nanoTime(), transformDeadline,
                            currState == PAUSE_STATE) && !transform.ended())
                        fallback(new IOException("DVD transform output deadline"));
                } catch (IOException failure) { fallback(failure); }
            }
        }
        private boolean finishTransform() throws IOException {
            synchronized (control) {
                if (transform == null) return true;
                try { transform.finish(); }
                catch (IOException failure) { fallback(failure); return false; }
            }
            pumpTransformed();
            synchronized (control) {
                if (transform == null) return false;
                try {
                    if (transformedPending != null || !transform.ended()) return false;
                    if (transformedBytes == 0) { fallback(new IOException("DVD transform emitted no media")); return false; }
                    closeTransform(); return true;
                } catch (IOException failure) { fallback(failure); return false; }
            }
        }
        synchronized void closeTransform() {
            DvdTransformPort current = transform; transform = null; transformedPending = null; transformOpened = false;
            transformOrigin.clear();
            if (current != null) current.close();
        }
        private void nativeRepresentation() throws IOException {
            initialized = false;
            wire.open("push:dvd?disc_transport=native&format=mpegps");
            wire.command(currState == PAUSE_STATE ? 20 : 21, new byte[0]);
            nativeWire = true; initialized = true;
            firstPayload = true; scanSent = false;
            capacity = 0; lastClockRead = 0; reapplyRepresentationClock();
        }
        private void fallback(IOException failure) throws IOException {
            long target = lastClock;
            DvdPresentationTimeline.Nav shown = timeline.presented(target);
            closeTransform(); nativeOnly = true; epoch.incrementAndGet();
            representationNav = shown;
            nativeRepresentation();
            if (shown != null && shown.state.domain == 4 && shown.state.title > 0) {
                StockDvdNavigator.Snapshot reader = cursor.snapshot();
                if (reader.domain != 4 || reader.title != shown.state.title) {
                    selectTitle(shown.state.title);
                    // Public VM title selection does not prime the new PGC's
                    // cell map until its first NAV. Seeking immediately scans
                    // the previous menu's VOB sectors and can replay a scene.
                    deferredSeek = target; deferredSeekTitle = shown.state.title;
                } else {
                    try { cursor.seek(target); }
                    catch (PlaybackException invalid) { throw new IOException("DVD fallback seek rejected", invalid); }
                }
            }
            wire.command(22, new byte[0]); timeline.discontinuity();
            System.out.println("[VibeClientExtension] DVD transform fallback to native: " + failure.getMessage());
        }
        private void selectTitle(int title) throws IOException {
            try { cursor.control(202, title, title, null); }
            catch (PlaybackException invalid) { throw new IOException("DVD title selection rejected", invalid); }
        }
        private int payloadFlags() {
            int flags = DvdMiniClientDriver.payloadFlags(rate, firstPayload, scanSent);
            firstPayload = false; scanSent = rate > 1 || rate < 0;
            return flags;
        }
        private void trace(String event, String detail) {
            if (ClientExtensionPlugin.ownedDvdTrace())
                System.out.println("[VibeClientExtension] owned DVD " + event + " " + detail);
        }
        private void handle(StockDvdNavigator.Event event) throws IOException, InterruptedException {
            switch (event.type) {
                case StockDvdNavigator.FLUSH:
                    closeTransform(); capacity = 0;
                    firstPayload = true; scanSent = false;
                    wire.command(22, new byte[0]); timeline.discontinuity(); break;
                case StockDvdNavigator.CELL:
                    // CELL describes VM read-ahead, not a presented domain.
                    // Opening Native here discards buffered Copy/Transcode
                    // title output before the VM's existing EMPTY/drain gate.
                    ByteBuffer cell = ByteBuffer.wrap(event.payload);
                    timeline.cell(cell.getLong(32), cell.getLong(24), cell.getInt(48) != 0);
                    trace("cell", "domain=" + event.state.domain + " title=" + event.state.title +
                            " startPts=" + cell.getLong(32) + " durationPts=" + cell.getLong(24)
                            + " discontinuous=" + (cell.getInt(48) != 0));
                    wire.integer(37, (event.state.scale & 1) == 0 ? 0 : (event.state.scale & 2) == 0 ? 1 : 2);
                    break;
                case StockDvdNavigator.NAV:
                    if (deferredSeek >= 0 && event.state.domain == 4 &&
                            event.state.title == deferredSeekTitle) {
                        long target = deferredSeek; deferredSeek = -1;
                        epoch.incrementAndGet();
                        timeline.discontinuity(); wire.command(22, new byte[0]);
                        try { cursor.seek(target); }
                        catch (PlaybackException invalid) { throw new IOException("DVD destination seek rejected", invalid); }
                        break;
                    }
                    int startupAction = startupPolicy.firstNav(event.state.domain);
                    if (startupAction == DvdStartupPolicy.MAIN_FEATURE) {
                        int title = cursor.mainFeatureTitle();
                        epoch.incrementAndGet(); selectTitle(title);
                        timeline.discontinuity(); wire.command(22, new byte[0]); break;
                    }
                    if (startupAction == DvdStartupPolicy.ROOT) {
                        boolean accepted=false;
                        try { accepted=cursor.control(201, 2, 0, event.pci); }
                        catch (PlaybackException unavailable) { /* Unsupported root retains native first play. */ }
                        trace("preview-skip", "rootAccepted=" + accepted);
                        if (accepted) {
                            epoch.incrementAndGet();
                            timeline.discontinuity(); wire.command(22, new byte[0]); break;
                        }
                    }
                    DvdPresentationTimeline.Nav nav = timeline.add(event);
                    representationNav = nav;
                    if (event.state.domain != 4 && !nativeWire) nativeRepresentation();
                    if (event.state.domain == 4) explicitMenu = false;
                    if (nav.newCell) {
                        trace("nav-cell", "domain=" + nav.state.domain + " title=" + nav.state.title
                                + " durationPts=" + nav.durationPts + " sourceMs=" + nav.startPts / 90L);
                        long offset = (nav.startPts - nav.pci.pci_gi.vobu_s_ptm.get()) / 2;
                        wire.bytes(32, ByteBuffer.allocate(4).putInt((int)offset).array());
                    }
                    if (nav.reanchor) {
                        wire.integer(35, (int)(nav.startPts / 2));
                        lastClock = nav.startPts / 90L; lastClockRead = 0;
                        trace("anchor", "domain=" + event.state.domain + " title=" + event.state.title +
                                " sourceMs=" + lastClock + " native=" + nativeWire);
                    }
                    break;
                case StockDvdNavigator.PALETTE: wire.bytes(33, event.payload); break;
                case StockDvdNavigator.AUDIO: wire.stream(0, event.parameter); break;
                case StockDvdNavigator.SUBPICTURE:
                    selectedSubpicture = event.parameter;
                    wire.stream(1, event.parameter); break;
                case StockDvdNavigator.HIGHLIGHT: lastHighlight = null; refreshHighlight(); break;
                case StockDvdNavigator.RATE: rate = 1; break;
                case StockDvdNavigator.STILL:
                    if (wire.push(null, 0x100) == -2) {
                        if (stillStarted == 0 || stillSeconds != (event.parameter & 255)) {
                            stillStarted = System.nanoTime(); stillSeconds = event.parameter & 255;
                        }
                        if (stillSeconds != 255 && System.nanoTime() - stillStarted >= stillSeconds * 1000000000L) {
                            cursor.endStill(); stillStarted = 0;
                        }
                    }
                    Thread.sleep(20); break;
                case StockDvdNavigator.EMPTY:
                    if (wire.push(null, 0x100) == -2) cursor.drained();
                    else Thread.sleep(20);
                    break;
                case StockDvdNavigator.END:
                    if (wire.push(null, 0x80) == -1) {
                        eos = true; running = false;
                        VideoFrame.getVideoFrameForPlayer(PluginOwnedDvdPlayer.this).kick();
                    } else Thread.sleep(20);
                    break;
                default: break;
            }
            if (event.type == StockDvdNavigator.CELL || event.type == StockDvdNavigator.AUDIO ||
                    event.type == StockDvdNavigator.SUBPICTURE)
                VideoFrame.getVideoFrameForPlayer(PluginOwnedDvdPlayer.this).playbackControl(0);
        }
        private void refreshHighlight() throws IOException {
            DvdPresentationTimeline.Nav nav = timeline.presented(lastClock);
            if (nav == null || nav == lastHighlight) return;
            lastHighlight = nav;
            pci_t pci = nav.pci;
            int button = latest.button > 0 ? latest.button : nav.state.button;
            ByteBuffer highlight = ByteBuffer.allocate(20);
            int count = pci.hli.hl_gi.btn_ns.get(), groups = pci.hli.hl_gi.btngr_ns.get();
            if (pci.hli.hl_gi.hli_ss.get() != 0 && button >= 1 && button <= count && groups >= 1 && groups <= 3) {
                // Use the matching authored display group. No button index is
                // accepted before validating both declared and parsed bounds.
                int type = VideoFrame.getVideoFrameForPlayer(PluginOwnedDvdPlayer.this).getDisplayAspectRatio() > 1.4 ? 1 :
                        (latest.scale & 1) == 0 ? 2 : 4;
                int group = 0;
                int[] types = {pci.hli.hl_gi.btngr1_dsp_ty.get(), pci.hli.hl_gi.btngr2_dsp_ty.get(), pci.hli.hl_gi.btngr3_dsp_ty.get()};
                for (int i = 0; i < groups; i++) if ((types[i] & type) != 0) group = i;
                int index = button - 1 + group * 36 / groups;
                if (index >= 0 && index < pci.hli.btnit.length) {
                    int color = pci.hli.btnit[index].btn_coln.get();
                    highlight.putInt(pci.hli.btnit[index].x_start.get()).putInt(pci.hli.btnit[index].x_end.get())
                            .putInt(pci.hli.btnit[index].y_start.get()).putInt(pci.hli.btnit[index].y_end.get())
                            .putInt(color >= 1 && color <= 3 ? (int)pci.hli.btn_colit.btn_coli[(color - 1) * 2].get() : 0);
                }
            }
            wire.bytes(34, highlight.array());
        }
        void failed(Exception error) {
            if (!running) return;
            running = false; currState = STOPPED_STATE;
            System.out.println("[VibeClientExtension] owned DVD stopped: " + error.toString());
        }
        void close() {
            running = false; intent=null; epoch.incrementAndGet();
            ACTIVE.remove(uiMgr.getLocalUIClientName(),PluginOwnedDvdPlayer.this);
            wire.close(); closeTransform(); DvdClientCapabilities.release(capabilities); cursor.close();
        }
    }
}
