package sage;

/** Isolated load-boundary double; no SageTV bootstrap and never packaged. */
public final class VideoFrame {
    public static VideoFrame getVideoFrameForPlayer(MediaPlayer player) { return null; }
    public long getMediaTimeMillis(boolean forReload) { return 0L; }
}
