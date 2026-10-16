package sage;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Test-only stock-player boundary double; never packaged or used by a server. */
public class MiniDVDPlayer {
    protected UIManager uiMgr;
    protected File currFile;
    public final List<Long> testSeekRequests = new ArrayList<Long>();
    public int testDomain = 4;
    public int testTitle = 1;
    public volatile boolean testMalformedControl;
    public volatile boolean testOtherControlFailure;
    public volatile long testMediaTime = 10000L;
    public long testDuration = 900000L;
    public boolean testButtonsVisible;
    public final List<Integer> testControlRequests = new ArrayList<Integer>();

    public void testSetFile(File file) { currFile = file; }
    public void testSetUIName(String name) { uiMgr = new UIManager(name); }
    public void inactiveFile() { }
    public synchronized void free() { }
    public synchronized void load(byte major, byte minor, String encoding,
            File file, String host, boolean timeshifted, long size)
            throws PlaybackException { currFile = file; }
    public synchronized long seek(long requested) throws PlaybackException {
        testSeekRequests.add(requested);
        notifyAll();
        return requested;
    }
    public int getDVDDomain() { return testDomain; }
    public int getDVDTitle() { return testTitle; }
    public long getMediaTimeMillis() { return testMediaTime; }
    public long getDurationMillis() { return testDuration; }
    public boolean areDVDButtonsVisible() { return testButtonsVisible; }
    public boolean playControlEx(int code, long p1, long p2) throws PlaybackException {
        if (testMalformedControl) throw new IndexOutOfBoundsException("fixture boundary");
        if (testOtherControlFailure) throw new IllegalStateException("unrelated failure");
        testControlRequests.add(code);
        if (code == 202) { testDomain = 4; testTitle = (int)p1; }
        return true;
    }
}
