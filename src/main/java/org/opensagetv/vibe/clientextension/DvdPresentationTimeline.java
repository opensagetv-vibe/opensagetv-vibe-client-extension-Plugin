package org.opensagetv.vibe.clientextension;

import java.util.ArrayDeque;
import sage.dvd.pci_t;

/** Destination-NAV custody, separate from the cursor's read-ahead domain. */
final class DvdPresentationTimeline {
    static final int MAX_NAVS = 512;
    private final ArrayDeque<Nav> navs = new ArrayDeque<Nav>();
    private boolean pendingAnchor = true;
    private boolean pendingCell = true;
    private boolean pendingDiscontinuity;
    private long cellStart, duration;
    private pci_t precedingPci;

    synchronized void discontinuity() {
        navs.clear();
        precedingPci = null;
        pendingAnchor = pendingCell = true;
        pendingDiscontinuity = false;
    }
    synchronized void cell(long startPts, long durationPts, boolean discontinuous) {
        if (startPts < 0 || durationPts < 0) throw new IllegalArgumentException("Invalid DVD cell time");
        // CELL is read-ahead metadata, not proof of a presented transition.
        // The stock VM can announce the next menu here, then wait almost an
        // entire title for the decoder to drain before emitting its first NAV.
        // Retain the visible title's state/duration until that destination NAV.
        // Explicit seek/FLUSH still calls discontinuity() immediately.
        pendingDiscontinuity |= discontinuous;
        cellStart = startPts; duration = durationPts;
        pendingCell = true;
        // A normal CELL must not erase a pending seek's STC re-anchor.
        pendingAnchor |= discontinuous;
    }
    synchronized Nav add(StockDvdNavigator.Event event) {
        if (event.pci == null) throw new IllegalArgumentException("NAV is missing PCI");
        if (pendingDiscontinuity) {
            // Distinct epochs frequently both begin at zero. Do not inherit
            // old button commands or select an old zero-based menu afterward.
            navs.clear(); precedingPci = null; pendingDiscontinuity = false;
        }
        inheritHighlight(event.pci, precedingPci);
        precedingPci = event.pci;
        long start = cellStart + event.pci.pci_gi.e_eltm.toPTS();
        long span = event.pci.pci_gi.vobu_e_ptm.get() - event.pci.pci_gi.vobu_s_ptm.get();
        Nav nav = new Nav(start, start + Math.max(0, span), duration, event.pci,
                event.state, pendingCell, pendingAnchor);
        navs.addLast(nav);
        while (navs.size() > MAX_NAVS) navs.removeFirst();
        pendingCell = pendingAnchor = false;
        return nav;
    }
    synchronized Nav presented(long milliseconds) {
        if (navs.isEmpty()) return null;
        long pts = Math.max(0, milliseconds) * 90L;
        Nav selected = navs.peekFirst();
        // A decoder's first clock reply after FLUSH/STC can still belong to
        // the prior presentation epoch. Selecting with that sample must not
        // destroy all early destination NAVs. Retention is independently
        // bounded; a corrected clock can select the first NAV again.
        for (Nav next : navs) {
            if (next.startPts < selected.startPts || pts < next.startPts) break;
            selected = next;
            if (pts <= next.endPts) break;
        }
        return selected;
    }
    synchronized long durationMillis() { return duration / 90L; }

    /** DVD HLI 2 reuses the complete button set; 3 retains newly authored commands. */
    private static void inheritHighlight(pci_t next, pci_t previous) {
        int status = next.hli.hl_gi.hli_ss.get();
        if (previous == null || (status != 2 && status != 3)) return;
        byte[][] commands = status == 3 ? new byte[next.hli.btnit.length][] : null;
        if (commands != null) for (int i = 0; i < commands.length; i++)
            commands[i] = bytes(next.hli.btnit[i].cmd);
        put(next.hli, bytes(previous.hli));
        if (commands != null) for (int i = 0; i < commands.length; i++)
            put(next.hli.btnit[i].cmd, commands[i]);
    }
    private static byte[] bytes(sage.dvd.Struct value) {
        byte[] result = new byte[value.size()];
        java.nio.ByteBuffer source = value.getByteBuffer().duplicate();
        source.position(value.getByteBufferPosition()); source.get(result);
        return result;
    }
    private static void put(sage.dvd.Struct value, byte[] bytes) {
        java.nio.ByteBuffer target = value.getByteBuffer().duplicate();
        target.position(value.getByteBufferPosition()); target.put(bytes);
    }
    static final class Nav {
        final long startPts, endPts, durationPts;
        final pci_t pci;
        final StockDvdNavigator.Snapshot state;
        final boolean newCell, reanchor;
        Nav(long start, long end, long duration, pci_t pci, StockDvdNavigator.Snapshot state,
                boolean cell, boolean anchor) {
            startPts = start; endPts = end; durationPts = duration;
            this.pci = pci; this.state = state; newCell = cell; reanchor = anchor;
        }
    }

    /** A new representation starts at its first input NAV, not the last NAV
     * read while a provider is probing. Read-ahead must never move this origin.
     * Owned by the session's control lock and cleared with its transform.
     */
    static final class Origin {
        private Nav first;
        void capture(Nav nav) { if (first == null) first = nav; }
        Nav get() { return first; }
        void clear() { first = null; }
    }
}
