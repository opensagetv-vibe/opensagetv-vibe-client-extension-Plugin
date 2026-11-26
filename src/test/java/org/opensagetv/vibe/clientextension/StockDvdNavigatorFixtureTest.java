package org.opensagetv.vibe.clientextension;

import java.io.File;
import sage.dvd.pci_t;

/** Explicit authored-fixture cursor check; does not claim decoder/HDMI proof. */
public final class StockDvdNavigatorFixtureTest {
    public static void main(String[] args) throws Exception {
        try (StockDvdNavigator cursor = new StockDvdNavigator(new File(args[0]), null, true)) {
            int main = cursor.mainFeatureTitle();
            int[] counts = new int[12];
            pci_t visible = null;
            StockDvdNavigator.Event retained = null;
            long retainedStart = 0;
            boolean started = false;
            for (int i = 0; i < 20000; i++) {
                StockDvdNavigator.Event event = cursor.next();
                if (event.type < 0) break;
                counts[event.type]++;
                if (retained != null && retained.pci.pci_gi.vobu_s_ptm.get() != retainedStart)
                    throw new AssertionError("Later VM reads overwrote retained presentation NAV");
                if (retained == null && event.pci != null) {
                    retained = event; retainedStart = event.pci.pci_gi.vobu_s_ptm.get();
                }
                if (event.pci != null) visible = event.pci;
                if (!started && event.type == StockDvdNavigator.NAV && visible.hli.hl_gi.btn_ns.get() > 0) {
                    System.out.println("Authored root NAV button=" + event.state.button);
                    cursor.control(208, 0, 0, visible);
                    started = true;
                }
                if (event.type == StockDvdNavigator.EMPTY) cursor.drained();
                if (event.type == StockDvdNavigator.STILL) cursor.endStill();
                if (started && event.state.domain == 4 && counts[StockDvdNavigator.DATA] > 250) break;
            }
            if (!started || counts[StockDvdNavigator.DATA] <= 250 || counts[StockDvdNavigator.NAV] < 2)
                throw new AssertionError("Authored menu/title navigation failed");
            System.out.println("PASS: actual stock VM cursor root/title; mainTitle=" + main +
                    " events=" + java.util.Arrays.toString(counts));
        }
    }
}
