package org.opensagetv.vibe.clientextension;

import java.lang.reflect.InvocationTargetException;
import sage.SageTV;

/** Read-only stock API snapshot. Times are deliberately not a substitute STC. */
final class PlaybackStateSnapshot {
    static String read(String ui) throws InvocationTargetException {
        Object media = SageTV.apiUI(ui, "GetCurrentMediaFile", new Object[0]);
        if (media == null) return null;
        long id = number(SageTV.api("GetMediaFileID", new Object[]{media}));
        if (id <= 0) return null;
        boolean dvd = Boolean.TRUE.equals(SageTV.api("IsDVD", new Object[]{media}));
        StringBuilder out = new StringBuilder("{\"protocol\":1,\"clock_source\":\"stock_api\"");
        field(out, "media_id", id);
        field(out, "sampled_at_ms", System.currentTimeMillis());
        field(out, "media_time_ms", number(SageTV.apiUI(ui, "GetMediaTime", new Object[0])));
        field(out, "raw_time_ms", number(SageTV.apiUI(ui, "GetRawMediaTime", new Object[0])));
        field(out, "duration_ms", number(SageTV.apiUI(ui, "GetMediaDuration", new Object[0])));
        field(out, "file_size", number(SageTV.api("GetSize", new Object[]{media})));
        field(out, "file_start_ms", number(SageTV.api("GetFileStartTime", new Object[]{media})));
        field(out, "file_end_ms", number(SageTV.api("GetFileEndTime", new Object[]{media})));
        out.append(",\"recording\":").append(Boolean.TRUE.equals(
                SageTV.api("IsFileCurrentlyRecording", new Object[]{media})));
        out.append(",\"playing\":").append(Boolean.TRUE.equals(SageTV.apiUI(ui, "IsPlaying", new Object[0])));
        out.append(",\"dvd\":").append(dvd);
        if (dvd) {
            field(out, "dvd_title", number(SageTV.apiUI(ui, "GetDVDCurrentTitle", new Object[0])));
            field(out, "dvd_chapter", number(SageTV.apiUI(ui, "GetDVDCurrentChapter", new Object[0])));
            field(out, "dvd_domain", number(SageTV.apiUI(ui, "GetDVDCurrentDomain", new Object[0])));
        }
        // apiUI calls are not a database transaction. A transition invalidates
        // this whole observation; never combine one file's clock with another.
        Object current = SageTV.apiUI(ui, "GetCurrentMediaFile", new Object[0]);
        if (current == null || id != number(SageTV.api("GetMediaFileID", new Object[]{current})))
            throw new IllegalStateException("Media changed during state sample");
        return out.append('}').toString();
    }
    private static long number(Object value) {
        if (!(value instanceof Number)) throw new IllegalStateException("Stock numeric state unavailable");
        return ((Number)value).longValue();
    }
    private static void field(StringBuilder out, String key, long value) {
        out.append(",\"").append(key).append("\":").append(value);
    }
}
