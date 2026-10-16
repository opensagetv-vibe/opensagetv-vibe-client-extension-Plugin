package org.opensagetv.vibe.clientextension;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Storage/upload API only. This plugin deliberately has no browser viewer. */
public final class DiagnosticStoreTest {
    private static void check(boolean value, String why) { if (!value) throw new AssertionError(why); }
    private static byte[] zip(String extra, String text) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (String name : new String[]{"device.txt", "checksums.sha256", extra}) {
                zip.putNextEntry(new ZipEntry(name)); zip.write(text.getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
            }
        }
        return out.toByteArray();
    }
    public static void main(String[] args) throws Exception {
        Path temp = Files.createTempDirectory("vibe-diagnostic-store-unit-");
        try {
            DiagnosticStore store = new DiagnosticStore(temp);
            byte[] valid = zip("active-player.txt", "version=test\n<script>example</script>\n");
            String name = store.accept("123456abcdef", DiagnosticStore.sha256(valid), valid);
            check(Arrays.equals(valid, store.read(name)), "stored bytes/checksum unchanged");
            for (String entry : new String[]{"../escape.txt", "media.mp4", "foo/bar/baz.txt"}) {
                byte[] bad = zip(entry, "test");
                try { store.accept("123456abcdef", DiagnosticStore.sha256(bad), bad); throw new AssertionError("unsafe ZIP accepted"); }
                catch (IOException expected) { }
            }
            try { store.accept("../../x", DiagnosticStore.sha256(valid), valid); throw new AssertionError("path client accepted"); }
            catch (IllegalArgumentException expected) { }
            try { store.accept("123456abcdef", String.join("", Collections.nCopies(64, "0")), valid); throw new AssertionError("bad hash accepted"); }
            catch (IllegalArgumentException expected) { }
            for (int i = 0; i < 65; i++) store.accept("123456abcdef", DiagnosticStore.sha256(valid), valid);
            check(store.reports().size() == 64, "retention bounded");
            check(!Files.exists(temp.resolve("escape.txt")), "archive never extracted");
            int port;
            try (ServerSocket probe = new ServerSocket(0)) { port = probe.getLocalPort(); }
            DiagnosticUploadReceiver receiver = new DiagnosticUploadReceiver(temp);
            try {
                receiver.start(port);
                for (String path : new String[]{"/", "/report", "/download", "/stats", "/logs"}) {
                    HttpURLConnection request = (HttpURLConnection)new URL("http://127.0.0.1:" + port + path).openConnection();
                    try { check(request.getResponseCode() == 404, "server viewer route removed: " + path); }
                    finally { request.disconnect(); }
                }
                HttpURLConnection upload = (HttpURLConnection)new URL("http://127.0.0.1:" + port + "/vibe/diagnostics/upload").openConnection();
                try {
                    upload.setRequestMethod("POST"); upload.setDoOutput(true);
                    upload.setRequestProperty("Content-Type", "application/zip");
                    upload.setRequestProperty("X-Vibe-Diagnostics", "1");
                    upload.setRequestProperty("X-Vibe-Client", "123456abcdef");
                    upload.setRequestProperty("X-Content-SHA256", DiagnosticStore.sha256(valid));
                    try (OutputStream out = upload.getOutputStream()) { out.write(valid); }
                    check(upload.getResponseCode() == 201, "upload-only API still receives reports");
                } finally { upload.disconnect(); }
            } finally { receiver.stop(); }
            check(store.reports().size() == 64, "receiver stop keeps stored reports");
            System.out.println("DiagnosticStoreTest PASS (store/upload API; all browser viewer routes removed)");
        } finally {
            try (java.util.stream.Stream<Path> paths = Files.walk(temp)) {
                for (Path path : (Iterable<Path>) paths.sorted(Comparator.reverseOrder())::iterator) Files.delete(path);
            }
        }
    }
}
