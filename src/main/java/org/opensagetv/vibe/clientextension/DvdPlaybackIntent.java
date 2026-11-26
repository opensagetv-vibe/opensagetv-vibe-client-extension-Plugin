package org.opensagetv.vibe.clientextension;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import sage.SageTV;

/** One explicit owned bookmark per UI; never touches stock watched metadata. */
final class DvdPlaybackIntent {
    static void clear(String ui) throws Exception {
        SageTV.api("RemoveServerProperty",new Object[]{key(ui)});
    }
    static void save(String ui, String disc, int title, long position) throws Exception {
        if (title<=0 || title>999 || position<0 || position>86400000L) throw new IllegalArgumentException("Invalid owned bookmark");
        String value=Base64.getUrlEncoder().withoutPadding().encodeToString(disc.getBytes(StandardCharsets.UTF_8))
                + ":" + title + ":" + position;
        SageTV.api("SetServerProperty",new Object[]{key(ui),value});
    }
    static Bookmark read(String ui,String disc) throws Exception {
        String value=String.valueOf(SageTV.api("GetServerProperty",new Object[]{key(ui),""}));
        try {
            if (value.length()>8192) return null;
            String[] parts=value.split(":",-1);
            if (parts.length!=3 || !disc.equals(new String(Base64.getUrlDecoder().decode(parts[0]),StandardCharsets.UTF_8))) return null;
            int title=Integer.parseInt(parts[1]); long position=Long.parseLong(parts[2]);
            return title>0 && title<=999 && position>=0 && position<=86400000L ? new Bookmark(title,position) : null;
        } catch (IllegalArgumentException invalid) { return null; }
    }
    private static String key(String ui) {
        return "vibe/client_extension/owned_bookmark/" + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(ui.getBytes(StandardCharsets.UTF_8));
    }
    static final class Bookmark {
        final int title; final long position;
        Bookmark(int title,long position) { this.title=title; this.position=position; }
    }
}
