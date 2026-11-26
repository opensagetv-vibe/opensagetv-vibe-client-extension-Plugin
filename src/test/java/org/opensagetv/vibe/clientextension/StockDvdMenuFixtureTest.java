package org.opensagetv.vibe.clientextension;

import java.io.File;
import sage.dvd.pci_t;

/** Explicit menu fixture probe using the public unchanged VM, no private fields. */
public final class StockDvdMenuFixtureTest {
    public static void main(String[] args) throws Exception {
        try (StockDvdNavigator cursor = new StockDvdNavigator(new File(args[0]), null, true)) {
            DvdPresentationTimeline timeline = new DvdPresentationTimeline();
            int phase = 0, navs = 0;
            for (int i = 0; i < 50000; i++) {
                StockDvdNavigator.Event event = cursor.next();
                if (event.type == StockDvdNavigator.END) break;
                if (event.type == StockDvdNavigator.EMPTY) cursor.drained();
                if (event.type == StockDvdNavigator.STILL) cursor.endStill();
                if (event.type == StockDvdNavigator.FLUSH) timeline.discontinuity();
                if (event.type != StockDvdNavigator.NAV) continue;
                pci_t pci = timeline.add(event).pci;
                navs++;
                int buttons = pci.hli.hl_gi.btn_ns.get();
                System.out.println("NAV phase=" + phase + " domain=" + event.state.domain +
                        " hli=" + pci.hli.hl_gi.hli_ss.get() + " buttons=" + buttons +
                        " groups=" + pci.hli.hl_gi.btngr_ns.get() + " selected=" + event.state.button +
                        " rect=" + pci.hli.btnit[0].x_start.get() + "," + pci.hli.btnit[0].y_start.get());
                System.out.println("displayGroups=" + pci.hli.hl_gi.btngr1_dsp_ty.get() + "," +
                        pci.hli.hl_gi.btngr2_dsp_ty.get() + " secondRect=" +
                        pci.hli.btnit[18].x_start.get() + "," + pci.hli.btnit[18].y_start.get());
                if (phase == 0 && buttons == 4) {
                    cursor.control(210, 3, 0, pci); cursor.control(208, 0, 0, pci); phase = 1;
                } else if (phase == 1 && buttons == 5) {
                    if (pci.hli.btnit[0].x_start.get() <= 0) throw new AssertionError("Language menu has no geometry");
                    cursor.control(208, 0, 0, pci); phase = 2;
                } else if (phase == 2 && buttons == 4) {
                    cursor.control(210, 3, 0, pci); cursor.control(208, 0, 0, pci); phase = 3;
                } else if (phase == 3 && buttons == 5) {
                    cursor.control(210, 3, 0, pci); cursor.control(210, 3, 0, pci);
                    cursor.control(208, 0, 0, pci); phase = 4;
                } else if (phase == 4 && event.state.domain == 4) {
                    if (!event.state.audio.toLowerCase().contains("spanish"))
                        throw new AssertionError("Authored audio selection lost: " + event.state.audio);
                    System.out.println("PASS: root/languages/audio/Spanish/title NAVs=" + navs); return;
                }
            }
            throw new AssertionError("Menu sequence incomplete phase=" + phase);
        }
    }
}
