package org.opensagetv.vibe.clientextension;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import sage.PlaybackException;
import sage.dvd.DVDReader;
import sage.dvd.VM;
import sage.dvd.dsi_t;
import sage.dvd.pci_t;

/**
 * Independently owned navigation cursor using the unchanged stock DVD API.
 *
 * This is composition, not a replacement sage class or reflective access to
 * MiniDVDPlayer's private reader. A player owns one cursor and closes it on
 * unload. It must never use the read-ahead cursor as the presentation clock.
 * All VM calls are serialized here; socket/transform work happens elsewhere.
 */
final class StockDvdNavigator implements Closeable {
    static final int DATA = 0, STILL = 1, EMPTY = 2, FLUSH = 3, PALETTE = 4,
            HIGHLIGHT = 5, AUDIO = 6, SUBPICTURE = 7, CELL = 8, VTS = 9,
            NAV = 10, RATE = 11, END = -1;
    private final VM vm;
    private boolean closed;
    private IOException failure;
    private final byte[] buffer = new byte[32768];
    private Snapshot lastSnapshot = Snapshot.EMPTY;

    StockDvdNavigator(File volume, String hostname, boolean wide) throws IOException {
        if (volume == null) throw new IOException("DVD volume is missing");
        vm = new VM();
        vm.setMonitor16_9(wide);
        try {
            if (!vm.reset(volume.getPath(), hostname))
                throw new IOException("Cannot open DVD volume");
        } catch (IOException error) {
            vm.close();
            throw error;
        } catch (RuntimeException malformed) {
            vm.close();
            throw new IOException("Invalid DVD navigation metadata", malformed);
        }
    }

    synchronized Event next() throws IOException {
        requireOpen();
        byte[] data = buffer;
        try {
          for (int redirects = 0; redirects < 128; redirects++) {
            int result = vm.process(data, 0);
            if (result == -1) return new Event(END, 0, new byte[0], snapshot(), null, null);
            int type = result >>> 16;
            int parameter = result & 0xffff;
            int length;
            switch (type) {
                case DATA: length = parameter; break;
                case CELL: length = 52; break;
                case PALETTE: length = 64; break;
                case NAV: length = 2048; break;
                default: length = 0;
            }
            if (length > data.length || type > RATE)
                throw new IOException("Invalid DVD navigation event");
            byte[] payload = new byte[length];
            System.arraycopy(data, 0, payload, 0, length);
            pci_t pci = null;
            dsi_t dsi = null;
            if (type == NAV) {
                pci = new pci_t(); dsi = new dsi_t();
                if (!vm.parseNav(data, 0, pci, dsi))
                    throw new IOException("Invalid DVD NAV packet");
                if (followOwnedAngle(pci, dsi)) continue;
            }
            if (type != DATA) lastSnapshot = snapshot();
            return new Event(type, parameter, payload, lastSnapshot, pci, dsi);
          }
          throw new IOException("DVD angle resynchronization exceeds bound");
        } catch (RuntimeException malformed) {
            // Private VM transitions can fail outside playControlEx. Keep the
            // failure latched: retrying the same malformed instruction forever
            // is not recovery. The caller stops only its owned session.
            failure = new IOException("DVD navigation failed", malformed);
            throw failure;
        }
    }

    private boolean followOwnedAngle(pci_t pci, dsi_t dsi) throws IOException {
        if (vm.getDVDDomain() != VM.VTS_DOMAIN || vm.getDVDTotalAngles() < 2 || vm.pgc == null
                || vm.pgc.cell_position == null || vm.cell < 1 || vm.cell > vm.pgc.cell_position.length
                || vm.pgc.cell_playback == null || vm.cell > vm.pgc.cell_playback.length
                || vm.pgc.cell_playback[vm.cell - 1].block_type.get() != 1) return false;
        // Stock seek can select the destination cell while its sector estimate
        // still precedes that cell. Such a NAV is NOT an alternative angle;
        // interpreting its unrelated angle addresses creates a resync loop.
        long sector = dsi.dsi_gi.nv_pck_lbn.get();
        int blockFirst = vm.cell - 1;
        while (blockFirst > 0 && vm.pgc.cell_playback[blockFirst].block_mode.get() >= 2
                && vm.pgc.cell_playback[blockFirst - 1].block_type.get() == 1) blockFirst--;
        if (!DvdAngleNavigation.withinCell(sector,
                vm.pgc.cell_playback[blockFirst].first_sector.get(),
                vm.pgc.cell_playback[vm.cell - 1].last_sector.get())) return false;
        int angle = vm.getDVDAngle();
        boolean wrong = dsi.dsi_gi.vobu_vob_idn.get() != vm.pgc.cell_position[vm.cell - 1].vob_id_nr.get()
                || dsi.dsi_gi.vobu_c_idn.get() != vm.pgc.cell_position[vm.cell - 1].cell_nr.get();
        int link = wrong ? DvdAngleNavigation.angleLink(angle, pci, dsi)
                : DvdAngleNavigation.continuation(angle, pci, dsi);
        if (link != DvdAngleNavigation.UNCHANGED) {
            long destination = dsi.dsi_gi.nv_pck_lbn.get() + link;
            if (destination < 0 || destination > 8388607L) throw new IOException("Invalid DVD angle destination");
            // Some flattened rips retain angle metadata and stale links back
            // to sector zero. Never let an angle link escape its authored
            // block; ordinary next_vobu already handles the following cell.
            long blockEnd = vm.pgc.cell_playback[vm.cell - 1].last_sector.get();
            if (!DvdAngleNavigation.withinCell(destination,
                    vm.pgc.cell_playback[blockFirst].first_sector.get(), blockEnd))
                link = DvdAngleNavigation.UNCHANGED;
        }
        if (link != DvdAngleNavigation.UNCHANGED) {
            vm.player_next_vobu_sector = link;
            // A seamless link's length includes its ILVU payload, not data
            // from adjacent alternatives. Change only our VM's public cursor.
            if (!wrong && pci.nsml_agli.nsml_agl_dsta[angle - 1].get() == 0) {
                long length = dsi.sml_pbi.ilvu_ea.get() + 1;
                if (length < 1 || length > 32768) throw new IOException("DVD angle unit exceeds bound");
                vm.player_vobu_length_sector = (int)length;
            }
        }
        if (!wrong) return false;
        if (link == DvdAngleNavigation.UNCHANGED) vm.player_next_vobu_sector = vm.player_vobu_length_sector;
        // Never emit video/audio from a different VOB/cell after seeking into
        // an interleaved block. Skip it before any payload reaches the client.
        vm.player_vobu_offset = vm.player_vobu_length_sector;
        vm.player_vobu_skip = 0;
        if (ClientExtensionPlugin.ownedDvdTrace()) System.out.println("[VibeClientExtension] angle resync angle="
                + angle + " sourceVob=" + dsi.dsi_gi.vobu_vob_idn.get() + " expectedVob="
                + vm.pgc.cell_position[vm.cell - 1].vob_id_nr.get());
        return true;
    }

    synchronized boolean control(int code, long first, long second, pci_t presented)
            throws PlaybackException {
        if (closed || failure != null)
            throw new PlaybackException(PlaybackException.DVD_INVALIDOP, 0);
        try {
            return vm.playControlEx(code, first, second, presented);
        } catch (RuntimeException malformed) {
            // A public control is rejected once through the checked boundary;
            // leave ordinary navigation alive, as the qualified adapter does.
            throw new PlaybackException(PlaybackException.DVD_INVALIDOP, 0);
        }
    }

    synchronized long seek(long millis) throws PlaybackException {
        if (closed || failure != null)
            throw new PlaybackException(PlaybackException.DVD_INVALIDOP, 0);
        long result = vm.seek(Math.max(0L, millis));
        // The source cursor may have reached its authored Exit while the
        // decoder still presents buffered title media. Stock seek sets the
        // new valid cell/FLUSH but does not clear that source-only stop flag.
        // Resume ONLY this owned cursor after a verified destination change;
        // never restart a closed/failed session or an intentional player Stop.
        if (vm.running < 0 && resumableDestination(vm.need_flush, vm.cell,
                vm.pgc == null ? 0 : vm.pgc.nr_of_cells.get())) vm.running = 1;
        return result;
    }

    static boolean resumableDestination(boolean flush, int cell, int count) {
        return flush && count > 0 && count <= 255 && cell >= 1 && cell <= count;
    }

    synchronized void rate(float value) { if (!closed) vm.set_rate(value); }

    static int angleTarget(int current, int count, long requested, int domain, pci_t presented) {
        if (domain != VM.VTS_DOMAIN || count < 2 || count > 9 || current < 1 || current > count
                || presented == null || presented.pci_gi.vobu_uop_ctl.angle_change.get() != 0) return 0;
        if (requested == -1) return current == count ? 1 : current + 1;
        return requested >= 1 && requested <= count ? (int)requested : 0;
    }

    synchronized boolean angle(long requested, long presentedMillis, pci_t presented) throws PlaybackException {
        if (closed || failure != null) throw new PlaybackException(PlaybackException.DVD_INVALIDOP, 0);
        int previous = vm.getDVDAngle();
        int selected = angleTarget(previous, vm.getDVDTotalAngles(), requested, vm.getDVDDomain(), presented);
        if (selected == 0) return false;
        if (selected == previous) return true;
        if (!hasPhysicalAngle(selected)) return false;
        // Stock playControlEx(ANGLE_CHANGE) is an empty case. This is ONLY our
        // independently owned VM: its public angle register is the same one
        // authored SetSTN uses. Select a validated angle then use public seek
        // to re-enter its cell branch at presentation time, not reader time.
        // No replacement sage class, private reflection or server-global VM.
        vm.SPRM[VM.AGLN] = selected;
        try { seek(Math.max(0L, presentedMillis)); }
        catch (PlaybackException | RuntimeException rejected) {
            vm.SPRM[VM.AGLN] = previous;
            throw new PlaybackException(PlaybackException.DVD_INVALIDOP, 0);
        }
        return true;
    }

    /** A title count is not proof that a rip retained alternative video.
     * Inspect at most sixteen sectors of one authored branch using the public
     * reader, not a second cursor or Core internals. Every process read seeks
     * explicitly, so this bounded check does not advance presentation state.
     */
    private boolean hasPhysicalAngle(int angle) {
        if (vm.pgc == null || vm.pgc.cell_playback == null || vm.pgc.cell_position == null
                || vm.titlefile == null) return false;
        for (int i = 0; i < vm.pgc.cell_playback.length; i++) {
            if (vm.pgc.cell_playback[i].block_type.get() != 1
                    || vm.pgc.cell_playback[i].block_mode.get() != 1) continue;
            int selected = i + angle - 1;
            if (selected >= vm.pgc.cell_playback.length || selected >= vm.pgc.cell_position.length
                    || vm.pgc.cell_playback[selected].block_type.get() != 1) return false;
            long first = vm.pgc.cell_playback[selected].first_sector.get();
            long last = Math.min(vm.pgc.cell_playback[selected].last_sector.get(), first + 15);
            byte[] data = new byte[2048];
            for (long sector = first; sector <= last && sector <= 8388607L; sector++) {
                if (sector < 0 || !vm.titlefile.readBlocks(data, 0, (int)sector, 1)) return false;
                pci_t pci = new pci_t(); dsi_t dsi = new dsi_t();
                if (!vm.parseNav(data, 0, pci, dsi)) continue;
                return DvdAngleNavigation.sameBranch(dsi.dsi_gi.vobu_vob_idn.get(), dsi.dsi_gi.vobu_c_idn.get(),
                        vm.pgc.cell_position[selected].vob_id_nr.get(), vm.pgc.cell_position[selected].cell_nr.get());
            }
            return false;
        }
        return false;
    }
    synchronized void drained() { if (!closed) vm.unempty(); }
    synchronized void endStill() { if (!closed) vm.unpause(); }

    synchronized Snapshot snapshot() {
        if (closed) return Snapshot.EMPTY;
        return new Snapshot(vm.getDVDTitle(), vm.getDVDTotalTitles(),
                vm.getDVDChapter(), vm.getDVDTotalChapters(), vm.getDVDDomain(),
                vm.getDVDAngle(), vm.getDVDTotalAngles(), vm.getDVDLanguage(),
                vm.getDVDSubpicture(), vm.getDVDAvailableLanguages(),
                vm.getDVDAvailableSubpictures(), vm.player_button,
                vm.get_video_scale_permission());
    }

    /** Read-only authored-title metadata; never reinterpret an explicit title. */
    synchronized int mainFeatureTitle() throws IOException {
        requireOpen();
        // IFO's parsed fields are package-private in the ACTUAL stock JAR.
        // Read the bounded on-disc format through DVDReader instead of making
        // those fields accessible, copying sage classes or patching Core.
        return DvdTitleCatalog.longest(vm.reader);
    }

    public synchronized void close() {
        if (closed) return;
        closed = true;
        vm.close();
    }

    private void requireOpen() throws IOException {
        if (closed) throw new IOException("DVD navigator closed");
        if (failure != null) throw failure;
    }

    static final class Event {
        final int type, parameter;
        final byte[] payload;
        final Snapshot state;
        final pci_t pci;
        final dsi_t dsi;
        Event(int type, int parameter, byte[] payload, Snapshot state, pci_t pci, dsi_t dsi) {
            this.type = type; this.parameter = parameter; this.payload = payload;
            this.state = state; this.pci = pci; this.dsi = dsi;
        }
    }

    static final class Snapshot {
        static final Snapshot EMPTY = new Snapshot(0, 0, 0, 0, 0, 0, 0, null,
                null, null, null, 0, 0);
        final int title, titles, chapter, chapters, domain, angle, angles, button, scale;
        final String audio, subpicture;
        final String[] audioTracks, subtitleTracks;
        Snapshot(int title, int titles, int chapter, int chapters, int domain,
                int angle, int angles, String audio, String subpicture,
                String[] audioTracks, String[] subtitleTracks, int button, int scale) {
            this.title = title; this.titles = titles; this.chapter = chapter;
            this.chapters = chapters; this.domain = domain; this.angle = angle;
            this.angles = angles; this.audio = audio; this.subpicture = subpicture;
            this.audioTracks = audioTracks == null ? new String[0] : audioTracks.clone();
            this.subtitleTracks = subtitleTracks == null ? new String[0] : subtitleTracks.clone();
            this.button = button; this.scale = scale;
        }
    }
}
