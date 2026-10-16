package org.opensagetv.vibe.clientextension;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** Plugin-owned, bounded support reports. ZIPs are validated, never extracted. */
final class DiagnosticStore {
    static final int MAX_UPLOAD = 8 * 1024 * 1024;
    static final int MAX_ENTRY = 512 * 1024;
    static final int MAX_EXPANDED = 16 * 1024 * 1024;
    static final int MAX_FILES = 64;
    static final long MAX_BYTES = 256L * 1024 * 1024;
    private final Path directory;

    DiagnosticStore(Path serverDirectory) throws IOException {
        Path root = serverDirectory.toRealPath();
        directory = root.resolve("vibe-client-logs");
        if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS)
                && (Files.isSymbolicLink(directory) || !Files.isDirectory(directory)))
            throw new IOException("Diagnostic storage is not a real directory");
        Files.createDirectories(directory);
        if (!directory.toRealPath().getParent().equals(root))
            throw new IOException("Diagnostic storage escapes server directory");
    }

    synchronized String accept(String client, String expectedSha, byte[] zip) throws Exception {
        if (client == null || !client.matches("[0-9a-f]{12}"))
            throw new IllegalArgumentException("Invalid diagnostic client ID");
        if (expectedSha == null || !expectedSha.matches("[0-9a-f]{64}")
                || !expectedSha.equals(sha256(zip)))
            throw new IllegalArgumentException("Diagnostic checksum mismatch");
        if (zip.length == 0 || zip.length > MAX_UPLOAD)
            throw new IllegalArgumentException("Diagnostic upload size exceeded");
        validate(zip);
        requireDirectory();
        // Ownership is established by this exact pattern. Retention never
        // removes arbitrary files or follows links placed in the directory.
        List<Path> owned = reports();
        long total = 0;
        for (Path path : owned) total += Files.size(path);
        while (!owned.isEmpty() && (owned.size() >= MAX_FILES || total + zip.length > MAX_BYTES)) {
            Path oldest = owned.remove(0);
            long size = Files.size(oldest);
            Files.delete(oldest);
            total -= size;
        }
        if (directory.toFile().getUsableSpace() < zip.length + 16L * 1024 * 1024)
            throw new IOException("Insufficient diagnostic storage space");
        String name = client + "-" + System.currentTimeMillis() + "-"
                + UUID.randomUUID().toString().replace("-", "") + ".zip";
        Path pending = Files.createTempFile(directory, "upload-", ".pending");
        try {
            Files.write(pending, zip, StandardOpenOption.TRUNCATE_EXISTING);
            requireDirectory();
            Files.move(pending, directory.resolve(name), StandardCopyOption.ATOMIC_MOVE);
        } finally { Files.deleteIfExists(pending); }
        return name;
    }

    synchronized List<Path> reports() throws IOException {
        requireDirectory();
        List<Path> result = new ArrayList<Path>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path path : stream)
                if (reportName(path.getFileName().toString())
                        && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    result.add(path);
                    if (result.size() > MAX_FILES) throw new IOException("Diagnostic file count exceeded");
                }
        }
        Collections.sort(result, Comparator.comparing(path -> path.getFileName().toString().substring(13, 26)));
        // Never retain an unbounded listing even if an administrator populated
        // the folder outside this service. Refuse rather than adopt/delete it.
        if (result.size() > MAX_FILES) throw new IOException("Diagnostic file count exceeded");
        return result;
    }

    synchronized byte[] read(String name) throws IOException {
        requireDirectory();
        if (!reportName(name)) throw new IllegalArgumentException("Unknown diagnostic report");
        Path file = directory.resolve(name);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) > MAX_UPLOAD)
            throw new FileNotFoundException("Diagnostic report unavailable");
        // NOFOLLOW is repeated at open, not merely at the preceding stat.
        try (InputStream in = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            return bounded(in, MAX_UPLOAD);
        }
    }

    static String preview(byte[] zip) throws IOException {
        StringBuilder result = new StringBuilder();
        int total = 0;
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                byte[] text = bounded(in, MAX_ENTRY);
                total += text.length;
                if (total > MAX_EXPANDED) throw new IOException("Expanded report exceeded limit");
                if (result.length() >= 1024 * 1024) {
                    result.append("\n[Preview capped at 1 MiB; download the ZIP for the remaining entries.]\n");
                    break;
                }
                result.append("\n--- ").append(entry.getName()).append(" ---\n")
                        .append(new String(text, StandardCharsets.UTF_8));
            }
        }
        return result.toString();
    }

    private static void validate(byte[] zip) throws IOException {
        if (zip.length < 4 || zip[0] != 'P' || zip[1] != 'K' || zip[2] != 3 || zip[3] != 4)
            throw new IOException("Expected a diagnostic ZIP");
        // DiagnosticBundleManager writes a standard non-ZIP64 archive without
        // a comment. Reject truncated bundles whose local entries happen to
        // remain readable after the central directory was lost in transit.
        int end = zip.length - 22;
        if (end < 0 || zip[end] != 'P' || zip[end + 1] != 'K'
                || zip[end + 2] != 5 || zip[end + 3] != 6
                || zip[zip.length - 2] != 0 || zip[zip.length - 1] != 0)
            throw new IOException("Incomplete diagnostic ZIP directory");
        Set<String> names = new HashSet<String>();
        int total = 0;
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                String name = entry.getName();
                // Only the existing redacted support bundle's text interface
                // is accepted. No executable, media or arbitrary path input.
                if (!name.matches("[A-Za-z0-9_-]+(?:/[A-Za-z0-9_-]+)?\\.(?:txt|log|jsonl|sha256)")
                        || entry.isDirectory() || !names.add(name) || names.size() > 32)
                    throw new IOException("Unsupported diagnostic ZIP entry");
                total += bounded(in, MAX_ENTRY).length;
                if (total > MAX_EXPANDED) throw new IOException("Expanded report exceeded limit");
            }
        }
        if (!names.contains("device.txt") || !names.contains("checksums.sha256"))
            throw new IOException("Incomplete diagnostic bundle");
    }

    static byte[] bounded(InputStream input, int limit) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[16 * 1024];
        int read;
        while ((read = input.read(buffer)) != -1) {
            if (out.size() + read > limit) throw new IOException("Diagnostic size exceeded limit");
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    static String sha256(byte[] value) throws Exception {
        StringBuilder out = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(value))
            out.append(String.format(Locale.ROOT, "%02x", b & 255));
        return out.toString();
    }

    static boolean reportName(String name) {
        return name != null && name.matches("[0-9a-f]{12}-[0-9]{13}-[0-9a-f]{32}\\.zip");
    }

    private void requireDirectory() throws IOException {
        if (Files.isSymbolicLink(directory) || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)
                || !directory.toRealPath().equals(directory))
            throw new IOException("Diagnostic directory changed");
    }
}
