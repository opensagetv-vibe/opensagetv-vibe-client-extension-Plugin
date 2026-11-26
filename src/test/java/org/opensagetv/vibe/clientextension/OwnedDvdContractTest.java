package org.opensagetv.vibe.clientextension;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import sage.dvd.DVDReader;
import sage.dvd.DVDSource;
import sage.dvd.pci_t;

/** Public stock-JAR contracts, not a physical disc/player acceptance claim. */
public final class OwnedDvdContractTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static void rejects(RunnableWithIo action) throws Exception {
        try { action.run(); throw new AssertionError("Expected bounded IFO rejection"); }
        catch (IOException expected) { }
    }
    interface RunnableWithIo { void run() throws Exception; }

    static final class Reader implements DVDReader {
        final Map<Integer, byte[]> files = new HashMap<Integer, byte[]>();
        int opened, closed;
        public boolean open(String path, String host) { return true; }
        public void close() { }
        public DVDSource openFile(int title, int type) {
            check(type == DVD_TYPE_IFO, "catalog may read only IFOs");
            final byte[] data = files.get(title);
            if (data == null) return null;
            opened++;
            return new DVDSource() {
                public boolean readBlocks(byte[] output, int offset, int block, int count) {
                    check(count > 0, "block/count argument order must match stock");
                    if (block < 0 || count > data.length / 2048 - block) return false;
                    System.arraycopy(data, block * 2048, output, offset, count * 2048);
                    return true;
                }
                public int readBytes(byte[] output, int count) { throw new AssertionError("Unexpected mutable cursor"); }
                public int seek(int offset) { throw new AssertionError("Unexpected mutable cursor"); }
                public void close() { closed++; }
            };
        }
    }
    private static byte[] ifo(String magic) {
        byte[] data = new byte[8192];
        System.arraycopy(magic.getBytes(StandardCharsets.US_ASCII), 0, data, 0, 12);
        ByteBuffer.wrap(data).putInt(0x1c, 3);
        return data;
    }
    private static byte[] title(int seconds) {
        byte[] data = ifo("DVDVIDEO-VTS");
        ByteBuffer buffer = ByteBuffer.wrap(data);
        buffer.putInt(0xc8, 1).putInt(0xcc, 2);
        buffer.putShort(2048, (short)1).putInt(2056, 12).putShort(2060, (short)1);
        buffer.putShort(4096, (short)1).putInt(4108, 16);
        data[4118] = (byte)(((seconds / 10) << 4) | (seconds % 10));
        data[4119] = (byte)0xc0;
        return data;
    }
    private static void catalog() throws Exception {
        Reader reader = new Reader();
        byte[] manager = ifo("DVDVIDEO-VMG");
        ByteBuffer.wrap(manager).putInt(0xc4, 1).putShort(2048, (short)2);
        manager[2062] = 1; manager[2063] = 1;
        manager[2074] = 2; manager[2075] = 1;
        reader.files.put(0, manager); reader.files.put(1, title(12)); reader.files.put(2, title(55));
        check(DvdTitleCatalog.longest(reader) == 2, "title1 trailer must not beat longer authored title2");
        check(reader.opened == reader.closed, "every independent IFO handle must close");
        ByteBuffer.wrap(reader.files.get(2)).putInt(0xcc, Integer.MAX_VALUE);
        check(DvdTitleCatalog.longest(reader) == 1, "malformed candidate must not escape table bounds");
        ByteBuffer.wrap(manager).putInt(0xc4, -1);
        rejects(() -> DvdTitleCatalog.longest(reader));
        check(reader.opened == reader.closed, "malformed header must still close source");
        rejects(() -> DvdTitleCatalog.titleDuration(title(30), 2));
        byte[] badTime = title(30); badTime[4118] = (byte)0x7f;
        rejects(() -> DvdTitleCatalog.titleDuration(badTime, 1));
    }
    private static StockDvdNavigator.Event nav(long pts) {
        pci_t pci = new pci_t();
        pci.setByteBuffer(ByteBuffer.allocate(2048), 0);
        pci.pci_gi.vobu_s_ptm.set(pts);
        pci.pci_gi.vobu_e_ptm.set(pts + 45000);
        return new StockDvdNavigator.Event(StockDvdNavigator.NAV, 0, new byte[2048],
                StockDvdNavigator.Snapshot.EMPTY, pci, null);
    }
    private static void timeline() {
        DvdPresentationTimeline timeline = new DvdPresentationTimeline();
        timeline.cell(900000, 9000000, true);
        DvdPresentationTimeline.Nav first = timeline.add(nav(123456));
        check(first.newCell && first.reanchor && first.startPts == 900000,
                "first destination NAV owns STC, not PES timestamp or requested seek");
        first.pci.hli.hl_gi.hli_ss.set((short)1);
        first.pci.hli.hl_gi.btn_ns.set((short)2);
        first.pci.hli.btnit[0].x_start.set((short)47);
        first.pci.hli.btnit[0].cmd.bytes[0].set((short)11);
        StockDvdNavigator.Event sameButtons = nav(150000);
        sameButtons.pci.hli.hl_gi.hli_ss.set((short)2);
        timeline.add(sameButtons);
        check(sameButtons.pci.hli.hl_gi.btn_ns.get() == 2 &&
                sameButtons.pci.hli.btnit[0].x_start.get() == 47 &&
                sameButtons.pci.hli.btnit[0].cmd.bytes[0].get() == 11,
                "HLI2 must retain authored button geometry and commands");
        StockDvdNavigator.Event newCommands = nav(180000);
        newCommands.pci.hli.hl_gi.hli_ss.set((short)3);
        newCommands.pci.hli.btnit[0].cmd.bytes[0].set((short)22);
        timeline.add(newCommands);
        check(newCommands.pci.hli.btnit[0].x_start.get() == 47 &&
                newCommands.pci.hli.btnit[0].cmd.bytes[0].get() == 22,
                "HLI3 inherits geometry but must keep the new commands");
        timeline.discontinuity();
        timeline.cell(2700000, 9000000, false);
        DvdPresentationTimeline.Nav destination = timeline.add(nav(234567));
        check(destination.reanchor && destination.startPts == 2700000,
                "ordinary CELL must not clear seek's pending STC");
        StockDvdNavigator.Event ahead = nav(345678);
        ahead.pci.pci_gi.e_eltm.second.set((short)1);
        check(!timeline.add(ahead).reanchor, "anchor emitted only once per discontinuity");
        check(timeline.presented(30000) == destination, "presentation cursor survives read-ahead NAVs");
        DvdPresentationTimeline.Nav aheadNav = timeline.presented(31000);
        DvdPresentationTimeline.Origin origin = new DvdPresentationTimeline.Origin();
        origin.capture(destination); origin.capture(aheadNav);
        check(origin.get() == destination, "provider probe read-ahead cannot replace input origin");
        origin.clear(); origin.capture(aheadNav);
        check(origin.get() == aheadNav, "new seek/representation obtains a fresh origin");
        check(aheadNav != destination && timeline.presented(30000) == destination,
                "stale pre-STC clock must not destroy earlier destination NAV custody");
        for (int i = 0; i < 1000; i++) timeline.add(nav(i));
        check(timeline.presented(0) != null, "bounded NAV retention remains usable");
        timeline.discontinuity();
        check(timeline.presented(30000) == null, "flush must invalidate old presented NAV custody");
        timeline.cell(0, 720000, true);
        DvdPresentationTimeline.Nav menuEpoch = timeline.add(nav(12345));
        timeline.cell(0, 8100000, true);
        check(timeline.presented(3000) == menuEpoch && menuEpoch.durationPts == 720000,
                "read-ahead CELL cannot discard visible state/duration before destination NAV");
        DvdPresentationTimeline.Nav titleEpoch = timeline.add(nav(54321));
        check(timeline.presented(0) == titleEpoch,
                "a discontinuous zero-based title must not select a prior zero-based menu NAV");
    }
    private static void wire() throws Exception {
        AtomicReference<Throwable> error = new AtomicReference<Throwable>();
        try (ServerSocketChannel server = ServerSocketChannel.open()) {
            server.bind(new InetSocketAddress("127.0.0.1", 0));
            Thread remote = new Thread(() -> {
                try (SocketChannel channel = server.accept()) {
                    int[] commands = {16, 36, 32, 23, 17, 1};
                    for (int command : commands) {
                        ByteBuffer header = ByteBuffer.allocate(4); read(channel, header); header.flip();
                        int value = header.getInt();
                        check(value >>> 24 == command, "stock media command order");
                        ByteBuffer body = ByteBuffer.allocate(value & 0xffffff); read(channel, body); body.flip();
                        if (command == 16) {
                            check(body.getInt() == body.remaining(), "OPENURL includes trailing NUL");
                        } else if (command == 36) {
                            check(body.remaining() == 8 && body.getInt() == 1 && body.getInt() == 65,
                                    "DVD stream packet is two integers, no new Core command");
                        } else if (command == 32) {
                            check(body.getInt() == 4 && body.getInt() == -120, "signed PTS offset preserved");
                        } else if (command == 23) {
                            check(body.getInt() == 3 && body.getInt() == 0x100 && body.get() == 1,
                                    "bounded Push payload and flags");
                        } else if (command == 1) return;
                        ByteBuffer response = ByteBuffer.allocate(4).putInt(command == 17 ? 31415 : 65536);
                        response.flip(); while (response.hasRemaining()) channel.write(response);
                    }
                } catch (Throwable failure) { error.set(failure); }
            }, "OwnedDvd-wire-test");
            remote.setDaemon(true); remote.start();
            DvdMiniClientDriver driver = new DvdMiniClientDriver(SocketChannel.open(server.getLocalAddress()),
                    new DvdMiniClientDriver.Timeouts() {
                        public void begin(SocketChannel channel) { }
                        public void end(SocketChannel channel) { }
                    });
            try {
                driver.open("push:dvd?disc_transport=native&format=mpegps");
                driver.stream(1, 65);
                driver.bytes(32, ByteBuffer.allocate(4).putInt(-120).array());
                check(driver.push(new byte[]{1,2,3}, 0x100) == 65536, "decoder capacity reply retained");
                check(driver.command(17, new byte[0]) == 31415, "decoder clock returned without extrapolation");
                rejects(() -> driver.push(new byte[32769], 0));
            } finally { driver.close(); }
            remote.join(5000);
            check(!remote.isAlive(), "wire test thread must terminate");
            if (error.get() != null) throw new AssertionError(error.get());
        }
    }
    private static void read(SocketChannel channel, ByteBuffer bytes) throws IOException {
        while (bytes.hasRemaining()) if (channel.read(bytes) < 0) throw new IOException("Unexpected test EOF");
    }
    public static void main(String[] args) throws Exception {
        String ui = "444556303031";
        OwnedDvdReloadState.save(ui, "/disc", 2, 25000, 100, 1);
        check(OwnedDvdReloadState.claim(ui, "/disc", 25010, 200, 1) != null, "same-disc actual reload handoff");
        OwnedDvdReloadState.save(ui, "/disc", 2, 25000, 100, 1);
        check(OwnedDvdReloadState.claim(ui, "/disc", 25000, 6300000100L, 1) != null,
                "measured stock6.3s reload teardown remains inside bounded handoff");
        OwnedDvdReloadState.save(ui, "/disc", 2, 25000, 100, 1);
        check(OwnedDvdReloadState.claim(ui, "/disc", 25000, 21000000100L, 1) == null, "old reload expires");
        check(OwnedDvdReloadState.claim(ui, "/disc", 25010, 200, 1) == null, "handoff is one-shot");
        OwnedDvdReloadState.save(ui, "/disc", 2, 25000, 100, 1);
        check(OwnedDvdReloadState.claim(ui, "/other", 25000, 200, 1) == null, "different disc cannot inherit position");
        OwnedDvdReloadState.save(ui, "/disc", 2, 25000, 100, 1);
        check(OwnedDvdReloadState.claim(ui, "/disc", 0, 200, 1) == null, "new explicit zero seek wins");
        OwnedDvdReloadState.save(ui, "/disc", 2, 25000, 100, 1);
        check(OwnedDvdReloadState.claim(ui, "/disc", 25000, 200, 3) == null, "disabled lifetime cannot resume");
        OwnedDvdReloadState.save(ui, "/disc", 2, 25000, 100, 1);
        OwnedDvdReloadState.cancel(ui);
        check(OwnedDvdReloadState.claim(ui, "/disc", 25000, 200, 1) == null, "intentional stop cancels handoff");
        catalog(); timeline(); wire();
        check(DvdMiniClientDriver.payloadFlags(1, true, false) == 0x12, "first payload resets scan");
        check(DvdMiniClientDriver.payloadFlags(16, false, false) == ((512 << 16) | 9),
                "16x must carry actual signed fixed-point rate, not only discard flags");
        check(DvdMiniClientDriver.payloadFlags(-8, false, true) == (((-256 & 0x7fff) << 16) | 9),
                "reverse rate keeps its signed wire value");
        check(DvdMiniClientDriver.payloadFlags(1, false, true) == 0x12, "Play exits scan");
        check(!PluginOwnedDvdPlayer.transformTimedOut(200, 100, true),
                "a user pause is not provider output failure");
        check(PluginOwnedDvdPlayer.transformTimedOut(200, 100, false),
                "a genuine running output deadline remains bounded");
        check(!PluginOwnedDvdPlayer.transformTimedOut(50, 100, false),
                "resume's renewed output window must allow progress");
        DvdClientCapabilities.Lease old = DvdClientCapabilities.register("444556303031");
        check(!old.confirmed, "new load has no guessed client capabilities");
        check(!DvdClientCapabilities.confirm(old.client, old.nonce, "4"), "unknown protocol rejected");
        check(DvdClientCapabilities.confirm(old.client, old.nonce, "3") && old.revision == 3,
                "Copy SPU remap revision explicitly commissioned");
        check(DvdClientCapabilities.confirm(old.client, old.nonce, "1"), "explicit current-load acknowledgement");
        check(old.revision == 1, "old capability version remains explicit");
        check(DvdClientCapabilities.confirm(old.client, old.nonce, "2") && old.revision == 2,
                "source SPU identity capability explicitly acknowledged");
        check("32,34".equals(PluginOwnedDvdPlayer.subpictureIdentity(new int[]{32,34})), "sparse physical map preserved");
        pci_t anglePci = new pci_t();
        sage.dvd.dsi_t angleDsi = new sage.dvd.dsi_t();
        anglePci.nsml_agli.nsml_agl_dsta[1].set(200);
        check(DvdAngleNavigation.signedAddress(0x8000000aL) == -10, "signed backward angle address");
        check(DvdAngleNavigation.signedAddress(0x7fffffffL) == DvdAngleNavigation.UNCHANGED, "end sentinel is not a giant jump");
        check(DvdAngleNavigation.continuation(2, anglePci, angleDsi) == DvdAngleNavigation.UNCHANGED, "middle ILVU keeps reference pictures");
        angleDsi.sml_pbi.category.set(0x5000);
        check(DvdAngleNavigation.continuation(2, anglePci, angleDsi) == 200, "last ILVU follows selected alternative");
        rejects(() -> DvdAngleNavigation.angleLink(0, anglePci, angleDsi));
        rejects(() -> DvdAngleNavigation.signedAddress(0x3ffffffeL));
        check(StockDvdNavigator.angleTarget(1, 3, -1, 4, anglePci) == 2, "cycle authored angle");
        check(!DvdAngleNavigation.withinCell(90000, 90110, 92905), "seek before angle cell is not wrong-angle data");
        check(DvdAngleNavigation.withinCell(90110, 90110, 92905), "first authored angle NAV belongs to cell");
        check(!DvdAngleNavigation.withinCell(92906, 90110, 92905), "following ordinary cell is not an angle alternative");
        check(!DvdAngleNavigation.sameBranch(2, 1, 3, 1), "flattened rip cannot manufacture missing VOB3");
        check(DvdAngleNavigation.sameBranch(3, 1, 3, 1), "actual authored alternate has matching identity");
        check(!DvdAngleNavigation.sameBranch(0, 0, 0, 0), "zero-filled NAV cannot qualify an angle");
        check(StockDvdNavigator.resumableDestination(true, 1, 3), "seek can resume an ended read-ahead cursor");
        check(!StockDvdNavigator.resumableDestination(false, 1, 3), "no actual destination flush cannot revive cursor");
        check(!StockDvdNavigator.resumableDestination(true, 4, 3), "past-last-cell authored Exit remains terminal");
        check(!StockDvdNavigator.resumableDestination(true, 0, 0), "unknown PGC remains terminal");
        check(StockDvdNavigator.angleTarget(3, 3, -1, 4, anglePci) == 1, "wrap authored angle");
        check(StockDvdNavigator.angleTarget(1, 3, 3, 4, anglePci) == 3, "exact bounded angle");
        check(StockDvdNavigator.angleTarget(1, 1, -1, 4, anglePci) == 0, "single angle does not invent alternative");
        check(StockDvdNavigator.angleTarget(1, 3, 4, 4, anglePci) == 0, "out-of-range angle refused");
        check(StockDvdNavigator.angleTarget(1, 3, -1, 3, anglePci) == 0, "menu angle refused");
        check(StockDvdNavigator.angleTarget(1, 3, -1, 4, null) == 0, "missing presented NAV refused");
        anglePci.pci_gi.vobu_uop_ctl.angle_change.set((short)1);
        check(StockDvdNavigator.angleTarget(1, 3, -1, 4, anglePci) == 0, "disc prohibits angle change");
        check(PluginOwnedDvdPlayer.coversDeclaredSubpictures(new int[]{32,34}, 2), "declared sparse tracks covered");
        check(!PluginOwnedDvdPlayer.coversDeclaredSubpictures(new int[0], 2), "late missing streams cannot be silently dropped");
        check(!PluginOwnedDvdPlayer.coversDeclaredSubpictures(new int[]{32}, 2), "partial probe cannot remove language");
        check(PluginOwnedDvdPlayer.coversDeclaredSubpictures(new int[0], 0), "real no-subtitle title permitted");
        check(!PluginOwnedDvdPlayer.coversDeclaredSubpictures(null, 0), "unknown is not empty");
        check(PluginOwnedDvdPlayer.coversSelectedSubpicture(new int[]{32,34}, 66), "wire66 requires physical34");
        check(!PluginOwnedDvdPlayer.coversSelectedSubpicture(new int[]{32,33}, 66), "wrong equal-count inventory rejected");
        check(PluginOwnedDvdPlayer.coversSelectedSubpicture(new int[0], 62), "disabled SPU requires no selected packet");
        check(!PluginOwnedDvdPlayer.denseSubpictures(new int[]{32,34}), "old client cannot guess sparse ordinal");
        check(PluginOwnedDvdPlayer.denseSubpictures(new int[]{32,33}), "legacy dense case retained");
        rejects(() -> PluginOwnedDvdPlayer.subpictureIdentity(new int[]{34,32}));
        DvdClientCapabilities.Lease current = DvdClientCapabilities.register(old.client);
        check(!DvdClientCapabilities.confirm(old.client, old.nonce, "1"), "old load cannot commission a replacement");
        DvdClientCapabilities.release(old);
        check(DvdClientCapabilities.lookup(current.client) == current, "old cleanup cannot erase newer lease");
        DvdClientCapabilities.release(current);
        check(DvdClientCapabilities.lookup(current.client) == null, "unload releases capability custody");
        rejects(() -> DvdTransformPort.open("arbitrary"));
        sage.SageTV.ffmpegInstalled = false;
        rejects(() -> DvdTransformPort.open("copy"));
        sage.SageTV.ffmpegInstalled = true; sage.SageTV.ffmpegEnabled = false;
        rejects(() -> DvdTransformPort.open("transcode"));
        sage.SageTV.ffmpegEnabled = true;
        // This test JVM deliberately has no FFmpeg service on its classpath:
        // an older installed plugin is safe too, not NoClassDefFoundError.
        rejects(() -> DvdTransformPort.open("copy"));
        System.out.println("PASS: stock IFO bounds/main title, destination-NAV custody and stock media wire");
    }
}
