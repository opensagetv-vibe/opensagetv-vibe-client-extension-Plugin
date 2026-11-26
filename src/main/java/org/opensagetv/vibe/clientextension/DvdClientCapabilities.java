package org.opensagetv.vibe.clientextension;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Per-load capability custody. No playback, arbitrary API, file or settings controls. */
final class DvdClientCapabilities {
    private static final Map<String, Lease> ACTIVE = new HashMap<String, Lease>();
    static synchronized Lease register(String client) {
        String key = normalize(client);
        if (ACTIVE.size() >= 16 && !ACTIVE.containsKey(key))
            throw new IllegalStateException("DVD capability session limit");
        Lease lease = new Lease(key, UUID.randomUUID().toString().replace("-", ""));
        ACTIVE.put(key, lease); return lease;
    }
    static synchronized Lease lookup(String client) { return ACTIVE.get(normalize(client)); }
    static synchronized boolean confirm(String client, String nonce, String revision) {
        return confirm(client,nonce,revision,null);
    }
    static synchronized boolean confirm(String client,String nonce,String revision,String profile) {
        Lease lease = lookup(client);
        boolean browser="4".equals(revision)&&"web-mpegps-v1".equals(profile);
        if (lease == null || !lease.nonce.equals(nonce) ||
                !("1".equals(revision) || "2".equals(revision) || "3".equals(revision)||browser)) return false;
        if(profile!=null&&!browser)return false;
        if(lease.nativeProgramStreamOnly&&!browser)return false;
        lease.nativeProgramStreamOnly=browser;
        lease.revision = Integer.parseInt(revision);
        lease.confirmed = true; return true;
    }
    static synchronized void release(Lease lease) {
        if (ACTIVE.get(lease.client) == lease) ACTIVE.remove(lease.client);
        lease.confirmed = false;
    }
    static synchronized void invalidate() {
        for (Lease lease : ACTIVE.values()) lease.confirmed = false;
    }
    private static String normalize(String client) {
        if (client == null || !client.matches("(?i)([0-9a-f]{12}|([0-9a-f]{2}:){5}[0-9a-f]{2})"))
            throw new IllegalArgumentException("Exact DVD MiniClient identity required");
        return client.replace(":", "").toLowerCase(java.util.Locale.ROOT);
    }
    static final class Lease {
        final String client, nonce;
        volatile boolean confirmed;
        volatile boolean nativeProgramStreamOnly;
        volatile int revision;
        Lease(String client, String nonce) { this.client = client; this.nonce = nonce; }
    }
}
