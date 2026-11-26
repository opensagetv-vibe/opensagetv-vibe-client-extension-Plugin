package org.opensagetv.vibe.clientextension;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import sage.SageTV;

/** Optional LAN DVD handshake and opted-in caption state; no browser/viewer. */
final class DvdCapabilityReceiver {
    static final int PORT = 8273;
    private HttpServer server;
    private final java.util.function.BooleanSupplier enabled;
    DvdCapabilityReceiver() { this(ClientExtensionPlugin::ownedDvdEnabled); }
    DvdCapabilityReceiver(java.util.function.BooleanSupplier enabled) { this.enabled = enabled; }
    private final ExecutorService workers = new ThreadPoolExecutor(1, 2, 15,
            TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(4), runnable -> {
                Thread thread = new Thread(runnable, "Vibe-DVD-capabilities");
                thread.setDaemon(true); return thread;
            }, new ThreadPoolExecutor.AbortPolicy());
    void start() throws IOException {
        start(PORT);
    }
    void start(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 4);
        server.setExecutor(workers); server.createContext("/", this::handle); server.start();
    }
    int port() { return server.getAddress().getPort(); }
    void stop() {
        if (server != null) server.stop(0);
        workers.shutdownNow(); DvdClientCapabilities.invalidate(); PlaybackCaptionState.invalidate();
    }
    private void handle(HttpExchange exchange) throws IOException {
        try {
            InetAddress peer = exchange.getRemoteAddress().getAddress();
            if (!peer.isLoopbackAddress() && !peer.isSiteLocalAddress()) { send(exchange, 403, "{}"); return; }
            if ("/vibe/media/captions".equals(exchange.getRequestURI().getPath())) {
                captions(exchange); return;
            }
            if ("/vibe/media/state".equals(exchange.getRequestURI().getPath())) {
                if (!ClientExtensionPlugin.playbackStateEnabled()) { send(exchange,404,"{}"); return; }
                if (!"GET".equals(exchange.getRequestMethod())) { send(exchange,405,"{}"); return; }
                Map<String,String> fields = query(exchange.getRequestURI().getRawQuery());
                if (fields.size()!=1) { send(exchange,400,"{}"); return; }
                String ui = captionContext(fields.get("client"));
                if (ui == null) { send(exchange,404,"{}"); return; }
                String snapshot = PlaybackStateSnapshot.read(ui);
                send(exchange,snapshot == null ? 404 : 200,snapshot == null ? "{}" : snapshot); return;
            }
            if ("/vibe/dvd/controls".equals(exchange.getRequestURI().getPath())) {
                controls(exchange); return;
            }
            if (!enabled.getAsBoolean() ||
                    !"/vibe/dvd/client".equals(exchange.getRequestURI().getPath())) {
                send(exchange, 404, "{}"); return;
            }
            // Metadata travels only in a short query; never wait on an untrusted
            // request body. No URLs, paths, class names or playback controls.
            Map<String, String> query = query(exchange.getRequestURI().getRawQuery());
            DvdClientCapabilities.Lease lease = DvdClientCapabilities.lookup(query.get("client"));
            if (lease == null) { send(exchange, 404, "{}"); return; }
            if ("GET".equals(exchange.getRequestMethod()))
                send(exchange, 200, "{\"protocol\":1,\"profiles\":[\"web-mpegps-v1\"],\"challenge\":\"" + lease.nonce + "\"}");
            else if ("POST".equals(exchange.getRequestMethod()) && DvdClientCapabilities.confirm(
                    query.get("client"), query.get("challenge"), query.get("revision"),query.get("profile")))
                send(exchange, 200, "{\"accepted\":true}");
            else send(exchange, 400, "{}");
        } catch (IllegalArgumentException invalid) { send(exchange, 400, "{}"); }
        catch (java.lang.reflect.InvocationTargetException unavailable) { send(exchange, 503, "{}"); }
        catch (RuntimeException unavailable) { send(exchange, 503, "{}"); }
        finally { exchange.close(); }
    }
    private void captions(HttpExchange exchange) throws IOException, java.lang.reflect.InvocationTargetException {
        if (!ClientExtensionPlugin.captionBridgeEnabled()) { send(exchange, 404, "{}"); return; }
        Map<String,String> query = query(exchange.getRequestURI().getRawQuery());
        String context = captionContext(query.get("client"));
        if (context == null) { send(exchange, 404, "{}"); return; }
        Object media = SageTV.apiUI(context, "GetCurrentMediaFile", new Object[0]);
        if (media == null || Boolean.TRUE.equals(SageTV.api("IsDVD", new Object[]{media}))) {
            send(exchange, 404, "{}"); return;
        }
        PlaybackCaptionState.Lease lease = PlaybackCaptionState.lookup(query.get("client"));
        if ("GET".equals(exchange.getRequestMethod()) && !query.containsKey("challenge"))
            lease = PlaybackCaptionState.register(query.get("client"));
        if (lease == null) { send(exchange, 404, "{}"); return; }
        if ("POST".equals(exchange.getRequestMethod())) {
            if (PlaybackCaptionState.confirm(query.get("client"), query.get("challenge"), query.get("revision")))
                send(exchange, 200, "{\"accepted\":true}");
            else send(exchange, 400, "{}");
        } else if ("GET".equals(exchange.getRequestMethod())) {
            if (query.containsKey("challenge") && !lease.nonce.equals(query.get("challenge"))) {
                send(exchange, 409, "{}"); return;
            }
            if (query.containsKey("challenge") && !lease.confirmed) { send(exchange, 409, "{}"); return; }
            String selected = String.valueOf(SageTV.apiUI(context, "GetMediaPlayerClosedCaptionState", new Object[0]));
            int state = "CC1".equals(selected) ? 1 : "CC2".equals(selected) ? 2 : 0;
            PlaybackCaptionState.observe(lease, state);
            send(exchange, 200, "{\"protocol\":1,\"challenge\":\"" + lease.nonce
                    + "\",\"cc\":" + lease.state + "}");
        } else send(exchange, 405, "{}");
    }
    private void controls(HttpExchange exchange) throws IOException, java.lang.reflect.InvocationTargetException {
        if (!ClientExtensionPlugin.dvdClientControlsEnabled()) { send(exchange,404,"{}"); return; }
        String raw=exchange.getRequestURI().getRawQuery();
        if (raw==null || raw.length()>256) throw new IllegalArgumentException("Invalid query");
        Map<String,String> fields=new HashMap<String,String>();
        for (String part:raw.split("&",-1)) {
            String[] pair=part.split("=",-1);
            if (pair.length!=2 || !("client".equals(pair[0]) || "challenge".equals(pair[0]) || "operation".equals(pair[0]))
                    || !pair[1].matches("[a-z0-9_:]{1,32}") || fields.put(pair[0],pair[1])!=null)
                throw new IllegalArgumentException("Invalid control field");
        }
        String ui=captionContext(fields.get("client"));
        DvdClientCapabilities.Lease lease=DvdClientCapabilities.lookup(fields.get("client"));
        if (ui==null || lease==null || !lease.confirmed) { send(exchange,404,"{}"); return; }
        if ("GET".equals(exchange.getRequestMethod())) {
            if (fields.size()!=1) { send(exchange,400,"{}"); return; }
            send(exchange,200,"{\"protocol\":1,\"challenge\":\""+lease.nonce+"\"}"); return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) { send(exchange,405,"{}"); return; }
        if (fields.size()!=3 || !lease.nonce.equals(fields.get("challenge"))) { send(exchange,409,"{}"); return; }
        String operation=fields.get("operation");
        if (!("save_resume".equals(operation) || "resume".equals(operation)
                || "beginning".equals(operation) || "clear_resume".equals(operation))) {
            send(exchange,400,"{}"); return;
        }
        String result=PluginOwnedDvdPlayer.requestIntent(ui,"action.dvd_"+operation,lease);
        boolean accepted=result.startsWith("Saved ") || result.startsWith("Queued ") || result.startsWith("Cleared ");
        send(exchange,accepted?200:409,accepted?"{\"accepted\":true}":"{}");
    }
    private static String captionContext(String client) throws java.lang.reflect.InvocationTargetException {
        String identity = DvdCompanionCapabilitiesIdentity(client);
        Object contexts = SageTV.api("GetUIContextNames", new Object[0]);
        if (contexts instanceof String[]) for (String context : (String[])contexts)
            if (context != null && identity.equals(context.replace(":", "").toLowerCase(java.util.Locale.ROOT)))
                return context;
        return null;
    }
    private static String DvdCompanionCapabilitiesIdentity(String client) {
        if (client == null || !client.matches("(?i)([0-9a-f]{12}|([0-9a-f]{2}:){5}[0-9a-f]{2})"))
            throw new IllegalArgumentException("Exact MiniClient identity required");
        return client.replace(":", "").toLowerCase(java.util.Locale.ROOT);
    }
    private static Map<String, String> query(String query) {
        if (query == null || query.length() > 256) throw new IllegalArgumentException("Invalid query");
        Map<String, String> result = new HashMap<String, String>();
        for (String part : query.split("&")) {
            String[] pair = part.split("=", -1);
            if (pair.length != 2 || !(pair[0].equals("client") || pair[0].equals("challenge") || pair[0].equals("revision")||pair[0].equals("profile"))
                    || !(pair[0].equals("profile")?pair[1].equals("web-mpegps-v1"):pair[1].matches("[0-9a-fA-F:]{1,32}"))
                    || result.put(pair[0], pair[1]) != null)
                throw new IllegalArgumentException("Invalid field");
        }
        return result;
    }
    private static void send(HttpExchange exchange, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(code, bytes.length); exchange.getResponseBody().write(bytes);
    }
}
