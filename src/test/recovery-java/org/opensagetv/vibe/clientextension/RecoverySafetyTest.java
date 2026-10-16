package org.opensagetv.vibe.clientextension;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import sage.PlaybackException;
import sage.SageTV;

/** Deterministic adapter-boundary assertions, not a physical malformed-disc gate. */
public final class RecoverySafetyTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void cancellation(final boolean free, final boolean restart) throws Exception {
        final ClientExtensionDvdPlayer player = new ClientExtensionDvdPlayer();
        final File disc = new File("dvd/VIDEO_TS");
        player.testSetFile(disc);
        Field epochField = ClientExtensionDvdPlayer.class.getDeclaredField("recoveryEpoch");
        epochField.setAccessible(true);
        final AtomicLong epoch = (AtomicLong) epochField.get(player);
        final long owned = epoch.incrementAndGet();
        final Method apply = ClientExtensionDvdPlayer.class.getDeclaredMethod(
                "applyAndVerifyResumeSeek", long.class, String.class, int.class,
                long.class, String.class, ClientExtensionDvdPlayer.ResumeSnapshot.class, long.class);
        apply.setAccessible(true);
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        final long lifetime = ClientExtensionPlugin.activeLifecycle();
        Thread worker = new Thread(new Runnable() {
            public void run() {
                try {
                    apply.invoke(player, owned, disc.getAbsolutePath(), 1, 64000L,
                            "VibeUI", new ClientExtensionDvdPlayer.ResumeSnapshot(
                                    disc.getAbsolutePath(), 1, 64000L, System.nanoTime()), lifetime);
                } catch (Throwable error) {
                    failure.set(error instanceof InvocationTargetException ?
                            ((InvocationTargetException) error).getCause() : error);
                }
            }
        }, "RecoverySafetyTest");
        worker.setDaemon(true);
        worker.start();
        synchronized (player) {
            long deadline = System.nanoTime() + 2_000_000_000L;
            while (player.testSeekRequests.isEmpty() && System.nanoTime() < deadline)
                player.wait(50L);
            check(player.testSeekRequests.size() == 1, "recovery must issue its first seek");
            if (restart) {
                ClientExtensionPlugin plugin = new ClientExtensionPlugin(null);
                plugin.stop();
                plugin.start();
            } else if (free) player.free();
            else player.seek(120000L);
        }
        worker.join(1500L);
        check(!worker.isAlive(), "seek/free must cancel recovery during clock verification");
        check(failure.get() == null, "recovery cancellation must not throw");
        synchronized (player) {
            check(player.testSeekRequests.size() == (free || restart ? 1 : 2),
                    "cancelled recovery must never submit another stale seek");
            if (!free && !restart) check(player.testSeekRequests.get(1) == 120000L,
                    "the later user target must be the final seek");
            // An already-stale worker must not make even its first request.
            apply.invoke(player, owned, disc.getAbsolutePath(), 1, 64000L,
                    "VibeUI", new ClientExtensionDvdPlayer.ResumeSnapshot(
                            disc.getAbsolutePath(), 1, 64000L, System.nanoTime()), lifetime);
            check(player.testSeekRequests.size() == (free || restart ? 1 : 2),
                    "stale attempt ownership must be rejected before issuance");
        }
    }

    private static void malformedBoundary() throws Exception {
        // PlaybackException uses the stock localization bundle. This isolated
        // JVM has no SageTV bootstrap, so initialize only that read-only bundle.
        Field translations = sage.Sage.class.getDeclaredField("coreRez");
        translations.setAccessible(true);
        translations.set(null, ResourceBundle.getBundle("SageTVCoreTranslations"));
        ClientExtensionDvdPlayer player = new ClientExtensionDvdPlayer();
        player.testMalformedControl = true;
        boolean converted = false;
        try { player.playControlEx(202, 1, 1); }
        catch (PlaybackException expected) {
            converted = expected.getMessage().contains("(-9,0x0)");
        }
        check(converted, "enabled malformed index must become a checked DVD control error");
        SageTV.SERVER.put("vibe/client_extension/enabled", "false");
        boolean stockFailure = false;
        try { player.playControlEx(202, 1, 1); }
        catch (IndexOutOfBoundsException expected) { stockFailure = true; }
        check(stockFailure, "disabled adapter must preserve stock control behavior");
        SageTV.SERVER.put("vibe/client_extension/enabled", "true");
        player.testMalformedControl = false;
        player.testOtherControlFailure = true;
        boolean unrelated = false;
        try { player.playControlEx(202, 1, 1); }
        catch (IllegalStateException expected) { unrelated = true; }
        check(unrelated, "unrelated errors must not be mislabeled malformed navigation");
    }

    private static void seekBoundary() throws Exception {
        ClientExtensionDvdPlayer player = new ClientExtensionDvdPlayer();
        SageTV.SERVER.put("vibe/client_extension/enabled", "true");
        player.seek(-2500L);
        player.seek(45000L);
        check(player.testSeekRequests.get(0) == 0L,
                "enabled negative seeks must be clamped at the actual stock call");
        check(player.testSeekRequests.get(1) == 45000L,
                "enabled valid seeks must retain their exact target");
        SageTV.SERVER.put("vibe/client_extension/enabled", "false");
        player.seek(-2500L);
        check(player.testSeekRequests.get(2) == -2500L,
                "disabled seeks must delegate the unmodified stock input");
    }

    @SuppressWarnings("unchecked")
    private static void supersedingSeekSnapshot() throws Exception {
        SageTV.SERVER.put("vibe/client_extension/enabled", "true");
        SageTV.SERVER.put("vibe/client_extension/reload.recovery", "true");
        Field pendingField = ClientExtensionDvdPlayer.class.getDeclaredField("PENDING");
        pendingField.setAccessible(true);
        ConcurrentHashMap<String, ClientExtensionDvdPlayer.ResumeSnapshot> pending =
                (ConcurrentHashMap<String, ClientExtensionDvdPlayer.ResumeSnapshot>)
                        pendingField.get(null);
        File disc = new File("dvd/VIDEO_TS");
        long now = System.nanoTime();
        ClientExtensionDvdPlayer.ResumeSnapshot old =
                new ClientExtensionDvdPlayer.ResumeSnapshot(
                        disc.getAbsolutePath(), 1, 64000L, now);
        check(old.matches(disc, 64000L, now), "reload must first claim its snapshot");
        check(old.matches(disc, 3000L, now + 1L),
                "control must reproduce claimed snapshot's immediate low-clock retry");
        pending.put("VibeUI", old);
        pending.put("OtherUI", old);
        ClientExtensionDvdPlayer player = new ClientExtensionDvdPlayer();
        player.testSetFile(disc);
        player.testSetUIName("VibeUI");
        player.seek(3000L);
        player.testMediaTime = 3000L;
        player.inactiveFile();
        player.free();
        check(!pending.containsKey("VibeUI"),
                "superseding low seek and immediate reload must not revive old claimed snapshot");
        check(pending.get("OtherUI") == old,
                "cancelling one UI must not erase another UI's recovery");
        pending.remove("OtherUI");

        player.testMediaTime = 64000L;
        player.inactiveFile();
        ClientExtensionDvdPlayer.ResumeSnapshot fresh = pending.get("VibeUI");
        check(fresh != null && fresh.positionMs == 64000L,
                "genuine reload must still capture the current title position");
        player.free();
        check(pending.get("VibeUI") == fresh,
                "free alone must preserve the genuine replacement-player handoff");
        pending.remove("VibeUI");
    }

    public static void main(String[] args) throws Exception {
        ClientExtensionPlugin plugin = new ClientExtensionPlugin(null);
        plugin.start();
        SageTV.SERVER.put("vibe/client_extension/enabled", "true");
        SageTV.SERVER.put("vibe/client_extension/reload.recovery", "true");
        cancellation(false, false);
        cancellation(true, false);
        cancellation(false, true);
        malformedBoundary();
        seekBoundary();
        supersedingSeekSnapshot();
        lateReadAheadSeek();
        File staleDisc = new File("dvd/VIDEO_TS");
        long savedAt = System.nanoTime();
        ClientExtensionDvdPlayer.ResumeSnapshot stale =
                new ClientExtensionDvdPlayer.ResumeSnapshot(
                        staleDisc.getAbsolutePath(), 1, 64000L, savedAt);
        plugin.stop();
        ClientExtensionDvdPlayer stopped = new ClientExtensionDvdPlayer();
        stopped.seek(-2500L);
        check(stopped.testSeekRequests.get(0) == -2500L,
                "Standard-plugin stop must delegate stock seek despite persisted enabled=true");
        check("true".equals(SageTV.SERVER.get("vibe/client_extension/enabled")),
                "Standard-plugin stop must preserve the user's feature setting");
        plugin.start();
        check(!stale.matches(staleDisc, 64000L, System.nanoTime()),
                "a stopped lifetime's snapshot must never be claimed after re-enable");
        stopped.seek(-2500L);
        check(stopped.testSeekRequests.get(1) == 0L,
                "Standard-plugin re-enable must reactivate policy without resurrecting old recovery");
        plugin.stop();
        System.out.println("RecoverySafetyTest PASS (boundary doubles; no physical DVD claim)");
    }

    private static void lateReadAheadSeek() throws Exception {
        SageTV.SERVER.put("vibe/client_extension/enabled", "true");
        ClientExtensionDvdPlayer player = new ClientExtensionDvdPlayer();
        player.testDuration = 90000L;
        player.testMediaTime = 78000L;
        player.areDVDButtonsVisible();
        // Physical stock175 read-ahead transitioned to the menu39.250s before
        // the late seek. Its still-valid title must not expire after20s.
        Field observed = ClientExtensionDvdPlayer.class.getDeclaredField("presentedTitle");
        observed.setAccessible(true);
        Object title = observed.get(player);
        Field seenAt = title.getClass().getDeclaredField("seenAtNs");
        seenAt.setAccessible(true);
        seenAt.setLong(title, System.nanoTime() - 40_000_000_000L);
        player.testDomain = 3;
        player.testMediaTime = 83500L;
        player.seek(30000L);
        check(player.testControlRequests.size() == 1 && player.testControlRequests.get(0) == 202,
                "read-ahead menu while decoded title continues must re-select the observed title");
        check(player.testSeekRequests.get(0) == 30000L,
                "read-ahead repair must preserve the requested seek target");
        player.testDomain = 3;
        player.testTitle = 2;
        player.seek(30000L);
        check(player.testControlRequests.size() == 1,
                "a different reader title must not inherit the previous title observation");
        player.testTitle = 1;
        player.testDomain = 3;
        player.testButtonsVisible = true;
        player.seek(30000L);
        check(player.testControlRequests.size() == 1, "visible menus must not be redirected");
        player.testButtonsVisible = false;
        player.playControlEx(201, 2, 0);
        player.seek(30000L);
        check(player.testControlRequests.size() == 2, "intentional menu navigation must clear title custody");
        ClientExtensionDvdPlayer unknown = new ClientExtensionDvdPlayer();
        unknown.testDomain = 3; unknown.testMediaTime = 83500L;
        unknown.seek(30000L);
        check(unknown.testControlRequests.isEmpty(), "never guess a title without observation");
        ClientExtensionDvdPlayer reloaded = new ClientExtensionDvdPlayer();
        reloaded.testDuration = 90000L;
        reloaded.areDVDButtonsVisible();
        reloaded.load((byte)0, (byte)0, "", new File("another/VIDEO_TS"), "", false, 0L);
        reloaded.testDomain = 3; reloaded.testMediaTime = 83500L;
        reloaded.seek(30000L);
        check(reloaded.testControlRequests.isEmpty(), "new disc loads must clear the observed title");
        ClientExtensionDvdPlayer oldLifetime = new ClientExtensionDvdPlayer();
        oldLifetime.testDuration = 90000L;
        oldLifetime.areDVDButtonsVisible();
        ClientExtensionPlugin lifecycle = new ClientExtensionPlugin(null);
        lifecycle.stop(); lifecycle.start();
        oldLifetime.testDomain = 3; oldLifetime.testMediaTime = 83500L;
        oldLifetime.seek(30000L);
        check(oldLifetime.testControlRequests.isEmpty(), "old lifetimes must not redirect seeks after re-enable");
    }
}
