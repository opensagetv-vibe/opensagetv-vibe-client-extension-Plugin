package org.opensagetv.vibe.clientextension;

public final class DvdStartupPolicyTest {
    private static void check(boolean value,String reason) { if (!value) throw new AssertionError(reason); }
    public static void main(String[] args) {
        check(new DvdStartupPolicy(false,false).firstNav(4)==0,"default unchanged");
        DvdStartupPolicy previews=new DvdStartupPolicy(false,true);
        check(previews.firstNav(4)==2,"first-play title to root");
        check(previews.firstNav(4)==0,"explicit later title is never skipped");
        DvdStartupPolicy root=new DvdStartupPolicy(false,true);
        check(root.firstNav(3)==0 && root.firstNav(4)==0,"existing root then Play retained");
        DvdStartupPolicy both=new DvdStartupPolicy(true,true);
        check(both.firstNav(3)==1 && both.firstNav(4)==0,"longest title wins once");
        check(new DvdStartupPolicy(false,true).firstNav(2)==0,"VMGM menu not mistaken for preview title");
        System.out.println("PASS: immutable one-shot DVD startup/default/root/menu priority");
    }
}
