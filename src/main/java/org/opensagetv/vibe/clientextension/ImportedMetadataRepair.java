package org.opensagetv.vibe.clientextension;

import java.util.Arrays;
import sage.SageTV;

/** Human-reviewed descriptive fields only; no format reconstruction or scan. */
final class ImportedMetadataRepair {
    static final String[] FIELDS = {"Title", "EpisodeName", "Description", "Year", "SeasonNumber", "EpisodeNumber"};
    private Proposal pending;
    synchronized String preview(String idText, String field, String replacement) throws Exception {
        pending = null;
        int id = Integer.parseInt(idText);
        if (id <= 0 || !Arrays.asList(FIELDS).contains(field)) throw new IllegalArgumentException("Select an imported ID and supported descriptive field");
        if (replacement == null || replacement.length() > 2048 || replacement.indexOf('\0') >= 0)
            throw new IllegalArgumentException("Value must be at most2048 characters without NUL");
        if ("Title".equals(field) && replacement.trim().isEmpty()) throw new IllegalArgumentException("Title must not be empty");
        if (("Year".equals(field) || "SeasonNumber".equals(field) || "EpisodeNumber".equals(field))
                && !replacement.matches("[0-9]{1,4}")) throw new IllegalArgumentException("Numeric field requires1..4 digits");
        Object media = imported(id);
        String before = read(media, field);
        String format = read(media, "Format.Container");
        if (format.isEmpty()) throw new IllegalStateException("Missing structural format: descriptive edits cannot rebuild it");
        pending = new Proposal(id, field, before, replacement, System.nanoTime());
        return "Preview ID=" + id + " " + field + ": [" + display(before) + "] -> [" + display(replacement)
                + "]. Apply Reviewed Edit confirms this single edit; sidecar metadata is also updated by stock SageTV.";
    }
    synchronized String apply() throws Exception {
        Proposal p = pending; pending = null; // one approval, including failed writes
        if (p == null || System.nanoTime() - p.created < 0 || System.nanoTime() - p.created > 300000000000L)
            throw new IllegalStateException("Preview the edit again before applying");
        Object media = imported(p.id);
        if (!read(media, p.field).equals(p.before)) throw new IllegalStateException("Metadata changed since preview; edit refused");
        SageTV.api("SetMediaFileMetadata", new Object[]{media, p.field, p.after});
        if (!read(media, p.field).equals(p.after)) throw new IllegalStateException("Stock API did not accept the edit");
        return "Applied ID=" + p.id + " " + p.field + "; stock API readback verified";
    }
    synchronized void clear() { pending = null; }
    private static Object imported(int id) throws Exception {
        Object media = SageTV.api("GetMediaFileForID", new Object[]{id});
        if (media == null || !Boolean.TRUE.equals(SageTV.api("IsLibraryFile", new Object[]{media}))
                || Boolean.TRUE.equals(SageTV.api("IsTVFile", new Object[]{media}))
                || Boolean.TRUE.equals(SageTV.api("IsFileCurrentlyRecording", new Object[]{media})))
            throw new IllegalArgumentException("Only existing imported, non-TV, non-recording media is eligible");
        return media;
    }
    private static String read(Object media, String key) throws Exception {
        Object value = SageTV.api("GetMediaFileMetadata", new Object[]{media, key});
        return value == null ? "" : String.valueOf(value);
    }
    private static String display(String text) {
        String value = text.replace('\n', ' ').replace('\r', ' ');
        return value.length() > 120 ? value.substring(0, 120) + " (preview shortened)" : value;
    }
    private static final class Proposal {
        final int id; final String field, before, after; final long created;
        Proposal(int id, String field, String before, String after, long created) {
            this.id=id; this.field=field; this.before=before; this.after=after; this.created=created;
        }
    }
}
