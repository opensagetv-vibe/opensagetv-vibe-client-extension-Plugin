package sage;

import java.util.HashMap;
import java.util.Map;

/** In-memory API test double; never included in the release JAR. */
public final class SageTV {
    public static final String[] UIS = {"VibeUI", "LegacyUI"};
    public static final Map<String, String> SERVER = new HashMap<String, String>();
    public static final Map<String, String> CLIENT = new HashMap<String, String>();
    public static boolean ffmpegInstalled = true, ffmpegEnabled = true;
    public static Object currentMedia = "generated-ts";
    public static boolean currentDvd;
    public static String captionState = "Off";
    public static final Map<String,String> METADATA=new HashMap<String,String>();
    public static boolean library=true, tv=false, recording=false, changeMedia;
    public static int mediaReads, writes;

    public static Object api(String name, Object[] args) {
        if("RemoveServerProperty".equals(name)){SERVER.remove(String.valueOf(args[0]));return null;}
        if ("GetMediaFileID".equals(name)) return "changed".equals(args[0]) ? 2 : 1;
        if ("GetMediaFileForID".equals(name)) return ((Number)args[0]).intValue()==1 ? currentMedia : null;
        if ("IsLibraryFile".equals(name)) return library;
        if ("IsTVFile".equals(name)) return tv;
        if ("IsFileCurrentlyRecording".equals(name)) return recording;
        if ("GetMediaFileMetadata".equals(name)) return METADATA.get(String.valueOf(args[1]));
        if ("SetMediaFileMetadata".equals(name)) {
            writes++; METADATA.put(String.valueOf(args[1]),String.valueOf(args[2])); return null;
        }
        if ("GetSize".equals(name)) return 123456789L;
        if ("GetFileStartTime".equals(name)) return 100000L;
        if ("GetFileEndTime".equals(name)) return 190000L;
        if ("GetServerProperty".equals(name)) {
            String value = SERVER.get(String.valueOf(args[0]));
            return value == null ? args[1] : value;
        }
        if ("SetServerProperty".equals(name)) {
            SERVER.put(String.valueOf(args[0]), String.valueOf(args[1]));
            return null;
        }
        if ("GetUIContextNames".equals(name)) return UIS.clone();
        if ("IsDVD".equals(name)) return currentDvd;
        if ("GetInstalledPlugins".equals(name)) return ffmpegInstalled ? new Object[]{"FFmpegPlugin"} : new Object[0];
        if ("IsPluginEnabled".equals(name)) return ffmpegEnabled;
        // Stock Core normalizes repository plugin IDs to lowercase.
        if ("GetPluginIdentifier".equals(name)) return "sagetvffmpegpluginlinux";
        if ("GetPluginConfigValue".equals(name)) return "healthy";
        throw new AssertionError("Unexpected API " + name);
    }

    public static Object apiUI(String ui, String name, Object[] args) {
        if ("GetCurrentMediaFile".equals(name)) return changeMedia && ++mediaReads>1 ? "changed" : currentMedia;
        if ("GetMediaTime".equals(name) || "GetRawMediaTime".equals(name)) return 12345L;
        if ("GetMediaDuration".equals(name)) return 90000L;
        if ("IsPlaying".equals(name)) return true;
        if ("GetDVDCurrentTitle".equals(name)) return 2;
        if ("GetDVDCurrentChapter".equals(name)) return 3;
        if ("GetDVDCurrentDomain".equals(name)) return 4;
        if ("GetMediaPlayerClosedCaptionState".equals(name)) return captionState;
        if ("GetProperty".equals(name)) {
            String value = CLIENT.get(ui + ":" + args[0]);
            return value == null ? args[1] : value;
        }
        if ("SetProperty".equals(name)) {
            CLIENT.put(ui + ":" + args[0], String.valueOf(args[1]));
            return null;
        }
        throw new AssertionError("Unexpected UI API " + name);
    }
}
