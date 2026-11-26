package org.opensagetv.vibe.clientextension;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.locks.ReentrantLock;
import sage.TimeoutHandler;

/**
 * Stock MiniClient media commands only. A fair lock gives controls a turn
 * between bounded data chunks; no socket lock is held while waiting for
 * decoder capacity, reading a disc or waiting for transformed output.
 */
final class DvdMiniClientDriver implements Closeable {
    private final SocketChannel channel;
    private final Timeouts timeouts;
    private final ReentrantLock lock = new ReentrantLock(true);
    private volatile boolean closed;
    DvdMiniClientDriver(SocketChannel channel) throws IOException {
        this(channel, new Timeouts() {
            public void begin(SocketChannel socket) { TimeoutHandler.registerTimeout(30000L, socket); }
            public void end(SocketChannel socket) { TimeoutHandler.clearTimeout(socket); }
        });
    }
    interface Timeouts {
        void begin(SocketChannel channel);
        void end(SocketChannel channel);
    }
    DvdMiniClientDriver(SocketChannel channel, Timeouts timeouts) throws IOException {
        if (channel == null || !channel.isOpen()) throw new IOException("Missing player socket");
        this.channel = channel;
        this.timeouts = timeouts;
    }

    int command(int command, byte[] payload) throws IOException {
        return exchange(command, payload, null, true);
    }
    int integer(int command, int value) throws IOException {
        return command(command, ByteBuffer.allocate(4).putInt(value).array());
    }
    int bytes(int command, byte[] value) throws IOException {
        return command(command, ByteBuffer.allocate(4 + value.length)
                .putInt(value.length).put(value).array());
    }
    void open(String transport) throws IOException {
        byte[] url = transport.getBytes(StandardCharsets.UTF_8);
        if (url.length > 1024 || command(16, ByteBuffer.allocate(url.length + 5)
                .putInt(url.length + 1).put(url).put((byte)0).array()) == 0)
            throw new IOException("Client rejected DVD transport");
    }
    int push(byte[] data, int flags) throws IOException {
        if (data != null && data.length > 32768) throw new IOException("DVD chunk exceeds bound");
        return exchange(23, ByteBuffer.allocate(8).putInt(data == null ? 0 : data.length)
                .putInt(flags).array(), data, true);
    }
    /** Existing DVD Push bit layout, including signed 10.5 scan rate. */
    static int payloadFlags(float rate, boolean first, boolean leavingScan) {
        if (first) return 0x12;
        if (rate > 1 || rate < 0) {
            int fixed = (int)(Math.max(-512f, Math.min(511.96875f, rate)) * 32f);
            return 0x09 | ((fixed & 0x7fff) << 16);
        }
        return leavingScan ? 0x12 : 0;
    }
    void stream(int type, int stream) throws IOException {
        command(36, ByteBuffer.allocate(8).putInt(type).putInt(stream).array());
    }
    void rectangles(java.awt.Rectangle source, java.awt.Rectangle target) throws IOException {
        if (source == null || target == null) return;
        command(25, ByteBuffer.allocate(32).putInt(source.x).putInt(source.y)
                .putInt(source.width).putInt(source.height).putInt(target.x)
                .putInt(target.y).putInt(target.width).putInt(target.height).array());
    }
    private int exchange(int command, byte[] payload, byte[] data, boolean reply)
            throws IOException {
        lock.lock();
        try {
            if (closed) throw new IOException("DVD player socket closed");
            int length = payload.length + (data == null ? 0 : data.length);
            ByteBuffer header = ByteBuffer.allocate(4).putInt((command << 24) | length);
            header.flip();
            timeouts.begin(channel);
            try {
                write(header); write(ByteBuffer.wrap(payload));
                if (data != null) write(ByteBuffer.wrap(data));
                if (!reply) return 0;
                ByteBuffer response = ByteBuffer.allocate(4);
                while (response.hasRemaining()) {
                    if (channel.read(response) < 0) throw new EOFException("DVD media reply ended");
                }
                response.flip();
                return response.getInt();
            } finally { timeouts.end(channel); }
        } finally { lock.unlock(); }
    }
    private void write(ByteBuffer bytes) throws IOException {
        while (bytes.hasRemaining()) {
            if (channel.write(bytes) < 0) throw new EOFException("DVD media socket ended");
        }
    }
    public void close() {
        if (closed) return;
        // Close can interrupt a blocked worker exchange; never wait forever
        // behind its lock to stop an already-failed or unloaded session.
        if (lock.tryLock()) {
            try {
                if (!closed && channel.isOpen()) {
                    try { exchange(1, new byte[0], null, false); }
                    catch (IOException ignored) { }
                }
            } finally { lock.unlock(); }
        }
        closed = true;
        try { channel.close(); } catch (IOException ignored) { }
    }
}
