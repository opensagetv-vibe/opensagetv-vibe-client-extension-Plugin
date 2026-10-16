package org.opensagetv.vibe.clientextension;

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.*;

/** Upload-only data API. The browser web server lives in Android Vibe.
 * No HTML, statistics, report listing/downloads, filesystem or control routes.
 */
final class DiagnosticUploadReceiver {
    private final DiagnosticStore store;
    private HttpServer server;
    private final ExecutorService workers = new ThreadPoolExecutor(2, 4, 30,
            TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(8), runnable -> {
                Thread t = new Thread(runnable, "vibe-diagnostic-upload"); t.setDaemon(true); return t;
            }, new ThreadPoolExecutor.AbortPolicy());
    private final ScheduledExecutorService deadlines = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread t = new Thread(runnable, "vibe-diagnostic-upload-deadline"); t.setDaemon(true); return t;
    });
    DiagnosticUploadReceiver(Path serverDirectory) throws IOException { store = new DiagnosticStore(serverDirectory); }
    void start(int port) throws IOException {
        if (port < 1024 || port > 65535) throw new IllegalArgumentException("Invalid upload port");
        server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 8);
        server.setExecutor(workers); server.createContext("/", this::handle); server.start();
    }
    void stop() { if (server != null) server.stop(0); workers.shutdownNow(); deadlines.shutdownNow(); }
    private void handle(HttpExchange exchange) throws IOException {
        ScheduledFuture<?> deadline = deadlines.schedule(exchange::close, 15, TimeUnit.SECONDS);
        try {
            InetAddress peer = exchange.getRemoteAddress().getAddress();
            if (!peer.isLoopbackAddress() && !peer.isSiteLocalAddress()) { send(exchange, 403, "LAN access only"); return; }
            String path = exchange.getRequestURI().getPath();
            if ("GET".equals(exchange.getRequestMethod()) && "/vibe/diagnostics/capabilities".equals(path)) {
                send(exchange, 200, "{\"protocol\":1,\"upload\":true,\"maxBytes\":" + DiagnosticStore.MAX_UPLOAD + "}");
            } else if ("POST".equals(exchange.getRequestMethod()) && "/vibe/diagnostics/upload".equals(path)) {
                if (!"1".equals(exchange.getRequestHeaders().getFirst("X-Vibe-Diagnostics"))
                        || !"application/zip".equals(exchange.getRequestHeaders().getFirst("Content-Type"))) {
                    send(exchange, 400, "Invalid diagnostic upload headers"); return;
                }
                byte[] zip = DiagnosticStore.bounded(exchange.getRequestBody(), DiagnosticStore.MAX_UPLOAD);
                String file = store.accept(exchange.getRequestHeaders().getFirst("X-Vibe-Client"),
                        exchange.getRequestHeaders().getFirst("X-Content-SHA256"), zip);
                send(exchange, 201, "{\"stored\":true,\"file\":\"" + file + "\",\"sha256\":\""
                        + DiagnosticStore.sha256(zip) + "\",\"bytes\":" + zip.length + "}");
            } else send(exchange, 404, "No browser viewer here; use the Android client web address");
        } catch (IllegalArgumentException invalid) { send(exchange, 400, "Invalid diagnostic request"); }
        catch (Exception unavailable) { send(exchange, 503, "Upload unavailable; local client logs retained"); }
        finally { deadline.cancel(false); exchange.close(); }
    }
    private static void send(HttpExchange exchange, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", code < 400 ? "application/json; charset=utf-8" : "text/plain; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'");
        exchange.sendResponseHeaders(code, bytes.length); exchange.getResponseBody().write(bytes);
    }
}
