package org.opensagetv.vibe.clientextension;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import sage.dvd.DVDReader;
import sage.dvd.DVDSource;

/** Small read-only IFO catalog; no VM internals, filesystem writes or JNI. */
final class DvdTitleCatalog {
    private static final int SECTOR = 2048, MAX_SECTORS = 8192;
    private DvdTitleCatalog() { }

    static int longest(DVDReader reader) throws IOException {
        byte[] manager = read(reader, 0, "DVDVIDEO-VMG");
        int table = sector(manager, 0xc4);
        int count = u16(manager, table);
        if (count < 1 || count > 99) throw new IOException("Invalid DVD title count");
        require(manager, table + 8, count * 12);
        Map<Integer, byte[]> sets = new HashMap<Integer, byte[]>();
        long longest = -1;
        int selected = 1;
        for (int index = 0; index < count; index++) {
            try {
                int entry = table + 8 + index * 12;
                int set = manager[entry + 6] & 255, title = manager[entry + 7] & 255;
                if (set < 1 || set > 99 || title < 1) continue;
                byte[] vts = sets.get(set);
                if (vts == null) { vts = read(reader, set, "DVDVIDEO-VTS"); sets.put(set, vts); }
                long length = titleDuration(vts, title);
                if (length > longest) { longest = length; selected = index + 1; }
            } catch (IOException malformedTitle) {
                // Skip only this malformed candidate; ties retain first title.
            }
        }
        return selected;
    }

    static long titleDuration(byte[] vts, int title) throws IOException {
        int parts = sector(vts, 0xc8), chains = sector(vts, 0xcc);
        int titleCount = u16(vts, parts), chainCount = u16(vts, chains);
        if (title < 1 || title > titleCount || titleCount > 99 || chainCount < 1)
            throw new IOException("Invalid title reference");
        int firstPart = offset(vts, parts, parts + 8 + (title - 1) * 4);
        int pgc = u16(vts, firstPart);
        if (pgc < 1 || pgc > chainCount) throw new IOException("Invalid PGC reference");
        int start = offset(vts, chains, chains + 8 + (pgc - 1) * 8 + 4);
        require(vts, start + 4, 4);
        int hour = bcd(vts[start + 4] & 255), minute = bcd(vts[start + 5] & 255),
                second = bcd(vts[start + 6] & 255);
        if (minute > 59 || second > 59) throw new IOException("Invalid DVD duration");
        int frame = vts[start + 7] & 255, rate = frame >>> 6;
        int frameNumber = bcd(frame & 63);
        if ((rate != 1 && rate != 3) || frameNumber >= (rate == 1 ? 25 : 30))
            throw new IOException("Invalid DVD frame rate");
        return ((hour * 60L + minute) * 60L + second) * 90000L +
                frameNumber * (rate == 1 ? 3600L : 3003L);
    }

    private static byte[] read(DVDReader reader, int set, String magic) throws IOException {
        DVDSource source = reader.openFile(set, DVDReader.DVD_TYPE_IFO);
        if (source == null) throw new IOException("Missing DVD IFO");
        try {
            byte[] first = new byte[SECTOR];
            if (!source.readBlocks(first, 0, 0, 1) ||
                    !magic.equals(new String(first, 0, 12, StandardCharsets.US_ASCII)))
                throw new IOException("Invalid DVD IFO header");
            long count = u32(first, 0x1c) + 1L;
            if (count < 1 || count > MAX_SECTORS) throw new IOException("DVD IFO exceeds bound");
            byte[] bytes = new byte[(int) count * SECTOR];
            if (!source.readBlocks(bytes, 0, 0, (int) count))
                throw new IOException("Incomplete DVD IFO");
            return bytes;
        } finally { source.close(); }
    }

    private static int sector(byte[] bytes, int field) throws IOException {
        long value = u32(bytes, field) * SECTOR;
        if (value == 0 || value > Integer.MAX_VALUE) throw new IOException("Invalid IFO sector");
        require(bytes, (int) value, 8);
        return (int) value;
    }
    private static int offset(byte[] bytes, int base, int field) throws IOException {
        long value = base + u32(bytes, field);
        if (value > Integer.MAX_VALUE) throw new IOException("Invalid IFO offset");
        require(bytes, (int) value, 2);
        return (int) value;
    }
    private static int bcd(int value) throws IOException {
        if ((value & 15) > 9 || (value >>> 4) > 9) throw new IOException("Invalid BCD time");
        return (value >>> 4) * 10 + (value & 15);
    }
    private static int u16(byte[] bytes, int offset) throws IOException {
        require(bytes, offset, 2);
        return ((bytes[offset] & 255) << 8) | (bytes[offset + 1] & 255);
    }
    private static long u32(byte[] bytes, int offset) throws IOException {
        require(bytes, offset, 4);
        return ((long) u16(bytes, offset) << 16) | u16(bytes, offset + 2);
    }
    private static void require(byte[] bytes, int offset, int length) throws IOException {
        if (offset < 0 || length < 0 || offset > bytes.length - length)
            throw new IOException("IFO reference outside data");
    }
}
