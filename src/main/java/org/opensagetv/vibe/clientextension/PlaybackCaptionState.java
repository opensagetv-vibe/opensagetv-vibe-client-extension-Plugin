package org.opensagetv.vibe.clientextension;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Exact-client, one-load state custody. No Core reflection or media controls. */
final class PlaybackCaptionState {
    private static final Map<String, Lease> ACTIVE = new HashMap<String, Lease>();
    static synchronized Lease register(String client) {
        String key = identity(client);
        if (ACTIVE.size() >= 16 && !ACTIVE.containsKey(key))
            throw new IllegalStateException("Caption-state session limit");
        Lease lease = new Lease(key, UUID.randomUUID().toString().replace("-", ""));
        Lease old = ACTIVE.put(key, lease);
        if (old != null) old.confirmed = false;
        return lease;
    }
    static synchronized Lease lookup(String client) { return ACTIVE.get(identity(client)); }
    static synchronized boolean current(Lease lease) {
        return lease != null && ACTIVE.get(lease.client) == lease;
    }
    static synchronized boolean confirm(String client, String nonce, String revision) {
        Lease lease = lookup(client);
        if (lease == null || !lease.nonce.equals(nonce) || !"1".equals(revision)) return false;
        lease.confirmed = true;
        return true;
    }
    static synchronized boolean set(Lease lease, int state) {
        if (!current(lease) || !lease.confirmed || state < 0 || state > 2) return false;
        lease.state = state;
        return true;
    }
    static synchronized boolean observe(Lease lease, int state) {
        if (!current(lease) || state < 0 || state > 2) return false;
        lease.state = state; return true;
    }
    static synchronized void release(Lease lease) {
        if (current(lease)) ACTIVE.remove(lease.client);
        if (lease != null) lease.confirmed = false;
    }
    static synchronized void invalidate() {
        for (Lease lease : ACTIVE.values()) lease.confirmed = false;
        ACTIVE.clear();
    }
    private static String identity(String client) {
        if (client == null || !client.matches("(?i)([0-9a-f]{12}|([0-9a-f]{2}:){5}[0-9a-f]{2})"))
            throw new IllegalArgumentException("Exact MiniClient identity required");
        return client.replace(":", "").toLowerCase(Locale.ROOT);
    }
    static final class Lease {
        final String client, nonce;
        volatile boolean confirmed;
        volatile int state;
        Lease(String client, String nonce) { this.client = client; this.nonce = nonce; }
    }
}
