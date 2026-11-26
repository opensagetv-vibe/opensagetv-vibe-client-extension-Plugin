package org.opensagetv.vibe.clientextension;

import java.util.concurrent.ConcurrentHashMap;

/** One-shot presentation handoff, never a bookmark or an inferred seek intent. */
final class OwnedDvdReloadState {
    private static final ConcurrentHashMap<String, Ticket> PENDING = new ConcurrentHashMap<String, Ticket>();
    static final class Ticket {
        final String path;
        final int title;
        final long position, time, lifecycle;
        Ticket(String path, int title, long position, long time, long lifecycle) {
            this.path = path; this.title = title; this.position = position;
            this.time = time; this.lifecycle = lifecycle;
        }
    }
    static void save(String ui, String path, int title, long position, long now, long lifecycle) {
        if (ui != null && path != null && title > 0 && position > 5000 && lifecycle > 0)
            PENDING.put(ui, new Ticket(path, title, position, now, lifecycle));
    }
    static Ticket claim(String ui, String path, long requested, long now, long lifecycle) {
        Ticket ticket = ui == null ? null : PENDING.remove(ui);
        if (ticket == null || !ticket.path.equals(path) || ticket.lifecycle != lifecycle ||
                now < ticket.time || now - ticket.time > 20000000000L ||
                Math.abs(requested - ticket.position) > 5000L) return null;
        return ticket;
    }
    static void cancel(String ui) { if (ui != null) PENDING.remove(ui); }
    static void clear() { PENDING.clear(); }
}
