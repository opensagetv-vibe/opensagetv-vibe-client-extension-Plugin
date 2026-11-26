package org.opensagetv.vibe.clientextension;

import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;

/** Real bounded HTTP handshake; no arbitrary playback or configuration routes. */
public final class DvdCapabilityTest {
    private static int request(int port, String path, String method) throws Exception {
        HttpURLConnection connection = (HttpURLConnection)new URL("http://127.0.0.1:" + port + path).openConnection(Proxy.NO_PROXY);
        connection.setConnectTimeout(1000); connection.setReadTimeout(1000);
        connection.setRequestMethod(method);
        try { return connection.getResponseCode(); }
        finally { connection.disconnect(); }
    }
    private static void check(boolean value, String reason) { if (!value) throw new AssertionError(reason); }
    public static void main(String[] args) throws Exception {
        AtomicBoolean enabled = new AtomicBoolean(true);
        DvdCapabilityReceiver receiver = new DvdCapabilityReceiver(enabled::get);
        DvdClientCapabilities.Lease lease = DvdClientCapabilities.register("444556303031");
        try {
            receiver.start(0); int port = receiver.port();
            String query = "/vibe/dvd/client?client=" + lease.client;
            check(request(port, "/", "GET") == 404, "no browser/control surface");
            check(request(port, query, "GET") == 200 && !lease.confirmed, "read does not commission");
            check(request(port, query + "&challenge=" + lease.nonce + "&revision=4", "POST") == 400, "unknown revision rejected");
            check(request(port, query + "&challenge=" + lease.nonce + "&revision=1", "POST") == 200 && lease.confirmed,
                    "exact current load can acknowledge supported representation");
            check(request(port, query + "&challenge=" + lease.nonce + "&revision=2", "POST") == 200 && lease.revision == 2,
                    "physical SPU identity is explicitly negotiated");
            check(request(port, query + "&command=stop", "POST") == 400, "no arbitrary command accepted");
            check(request(port, query + "&client=" + lease.client, "GET") == 400, "ambiguous identity rejected");
            check(request(port,query+"&challenge="+lease.nonce+"&revision=4&profile=unknown","POST")==400,"Unknown browser profile accepted");
            check(request(port,query+"&challenge="+lease.nonce+"&revision=4&profile=web-mpegps-v1","POST")==200&&lease.nativeProgramStreamOnly,"Explicit browser-native profile not commissioned");
            check(request(port,query+"&challenge="+lease.nonce+"&revision=3","POST")==400&&lease.nativeProgramStreamOnly,"Browser load upshifted to transformed transport");
            enabled.set(false);
            check(request(port, query, "GET") == 404, "feature off removes capability surface");
        } finally { receiver.stop(); DvdClientCapabilities.release(lease); }
        check(!lease.confirmed, "disable invalidates old acknowledgements");
        System.out.println("PASS: bounded DVD per-load capability HTTP, off/stale/unknown/control refusal");
    }
}
