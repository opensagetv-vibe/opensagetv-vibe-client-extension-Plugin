package org.opensagetv.vibe.clientextension;

import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import sage.Sage;
import sage.SageTV;

/** Optional typed Vibe-service bridge; never reflect into SageTV Core. */
final class DvdTransformPort implements Closeable {
    private static final String SERVICE = "org.opensagetv.vibe.ffmpeg.StockDvdStreamService";
    private final Object session;
    private final Method write, poll, ended, closeInput, close;
    private final Method sourceSubpictures;
    private final ArrayBlockingQueue<byte[]> input = new ArrayBlockingQueue<byte[]>(8);
    private final Thread writer;
    private volatile boolean closing, finishing;
    private volatile IOException failure;
    final String transport, format;

    static DvdTransformPort open(String mode) throws IOException {
        if (!"copy".equals(mode) && !"transcode".equals(mode))
            throw new IOException("Invalid owned DVD transform mode");
        try {
            boolean enabled = false;
            Object plugins = SageTV.api("GetInstalledPlugins", new Object[0]);
            if (plugins instanceof Object[]) for (Object plugin : (Object[])plugins) {
                String id = String.valueOf(SageTV.api("GetPluginIdentifier", new Object[]{plugin}));
                if (("sagetvffmpegpluginlinux".equalsIgnoreCase(id) || "sagetvffmpegpluginwinx64".equalsIgnoreCase(id))
                        && Boolean.TRUE.equals(SageTV.api("IsPluginEnabled", new Object[]{plugin}))) enabled = true;
            }
            if (!enabled) throw new IOException("FFmpeg plugin is absent or disabled");
            Class<?> type = Class.forName(SERVICE, true, Sage.extClassLoader);
            if (type.getField("CONTRACT_VERSION").getInt(null) != 1)
                throw new IOException("Unsupported stock DVD service version");
            Object provider = type.getConstructor(String.class).newInstance(mode);
            if (!Boolean.TRUE.equals(type.getMethod("isAvailable").invoke(provider)))
                throw new IOException("MIM DVD transform capability unavailable");
            String transport = String.valueOf(type.getMethod("getTransportId").invoke(provider));
            String format = String.valueOf(type.getMethod("getOutputFormat").invoke(provider));
            if (!transport.equals("copy".equals(mode) ? "dvd_mim_copy_v1" : "dvd_mim_transcode_v1") ||
                    !format.equals("copy".equals(mode) ? "mpegps" : "mpegts"))
                throw new IOException("Unexpected DVD service representation");
            Object session = type.getMethod("open", String.class).invoke(provider, "7000k");
            return new DvdTransformPort(session, transport, format);
        } catch (ReflectiveOperationException | LinkageError unavailable) {
            throw new IOException("Stock DVD service is not available", unavailable);
        } catch (IOException error) { throw error; }
        catch (Exception error) { throw new IOException("FFmpeg provider discovery failed", error); }
    }

    private DvdTransformPort(Object session, String transport, String format) throws IOException {
        this.session = session; this.transport = transport; this.format = format;
        try {
            Class<?> type = session.getClass();
            write = type.getMethod("write", byte[].class, int.class, int.class);
            poll = type.getMethod("pollOutput", long.class);
            ended = type.getMethod("isOutputEnded");
            closeInput = type.getMethod("closeInput");
            close = type.getMethod("close");
            sourceSubpictures = type.getMethod("getSourceSubpictureIds");
        } catch (ReflectiveOperationException incompatible) {
            if (session instanceof Closeable) try { ((Closeable)session).close(); } catch (IOException ignored) { }
            throw new IOException("Incomplete DVD service contract", incompatible);
        }
        writer = new Thread(new Runnable() {
            public void run() {
                try {
                    while (!closing) {
                        byte[] bytes = input.poll(20, TimeUnit.MILLISECONDS);
                        if (bytes != null) invoke(write, bytes, 0, bytes.length);
                        else if (finishing) { invoke(closeInput); return; }
                    }
                } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                catch (IOException error) { if (!closing) failure = error; }
            }
        }, "VibeOwnedDVD-transform-input");
        writer.setDaemon(true); writer.start();
    }
    boolean offer(byte[] chunk) throws IOException {
        check();
        if (finishing) throw new IOException("DVD transform input already ended");
        if (chunk.length > 32768) throw new IOException("DVD transform chunk exceeds bound");
        return input.offer(chunk);
    }
    byte[] poll() throws IOException { check(); return (byte[])invoke(poll, 0L); }
    boolean ended() throws IOException { check(); return Boolean.TRUE.equals(invoke(ended)); }
    int[] sourceSubpictureIds() throws IOException {
        check();
        return (int[])invoke(sourceSubpictures);
    }
    void finish() throws IOException { check(); finishing = true; }
    private void check() throws IOException {
        if (failure != null) throw failure;
        if (closing) throw new IOException("DVD transform closed");
    }
    private Object invoke(Method method, Object... args) throws IOException {
        try { return method.invoke(session, args); }
        catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            if (cause instanceof IOException) throw (IOException)cause;
            throw new IOException("DVD service failed", cause);
        } catch (ReflectiveOperationException error) { throw new IOException("DVD service call failed", error); }
    }
    public void close() {
        if (closing) return;
        closing = true; input.clear();
        // FFmpeg service closes/terminates its own process first, unblocking
        // this writer. The extension never starts or kills an executable.
        try { invoke(close); } catch (IOException ignored) { }
        writer.interrupt();
    }
}
