package org.opensagetv.vibe.clientextension;

import java.io.File;
/** Actual stock VM cursor, not decoder/HDMI evidence. */
public final class DvdPreviewFixtureTest {
    public static void main(String[] args) throws Exception {
        try (StockDvdNavigator cursor=new StockDvdNavigator(new File(args[0]),null,true)) {
            boolean preview=false,root=false,main=false;
            for (int i=0;i<10000;i++) {
                StockDvdNavigator.Event e=cursor.next();
                if (e.type<0) break;
                if (e.type==StockDvdNavigator.CELL && !preview) {
                    long duration=java.nio.ByteBuffer.wrap(e.payload).getLong(24)/90;
                    if (duration<29000 || duration>31000) throw new AssertionError("Preview CELL duration="+duration);
                }
                if (e.type==StockDvdNavigator.NAV && !preview) {
                    if (e.state.domain!=4 || e.state.title!=1) throw new AssertionError("No actual first-play preview");
                    preview=true;
                    if (!cursor.control(201,2,0,e.pci)) throw new AssertionError("Authored root rejected");
                } else if (e.type==StockDvdNavigator.NAV && preview && !root && e.state.domain==3) {
                    root=true;
                    if (e.pci.hli.hl_gi.btn_ns.get()!=1 || !cursor.control(208,0,0,e.pci))
                        throw new AssertionError("Root requires actual HLI Play button");
                } else if (e.type==StockDvdNavigator.NAV && root && e.state.domain==4 && e.state.title==2) {main=true;break;}
                if (e.type==StockDvdNavigator.EMPTY) cursor.drained();
                if (e.type==StockDvdNavigator.STILL) cursor.endStill();
            }
            if (!preview || !root || !main || cursor.mainFeatureTitle()!=2) throw new AssertionError("Preview/root/main semantics");
            System.out.println("PASS actual stock VM preview1 -> authored root/HLI Play -> main2; longest title2");
        }
    }
}
