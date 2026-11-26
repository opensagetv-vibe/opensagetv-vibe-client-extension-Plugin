package org.opensagetv.vibe.clientextension;

import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import sage.SageTV;

/** Deterministic supported-API refusal/identity tests, not physical evidence. */
public final class StockServiceTest {
    interface Job { void run() throws Exception; }
    static void check(boolean value,String message) { if(!value) throw new AssertionError(message); }
    static void refuses(Job job) throws Exception {
        try { job.run(); throw new AssertionError("Expected refusal"); }
        catch(IllegalArgumentException|IllegalStateException expected) { }
    }
    static int request(int port,String path,String method) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL("http://127.0.0.1:"+port+path).openConnection(Proxy.NO_PROXY);
        c.setConnectTimeout(1000);c.setReadTimeout(1000);c.setRequestMethod(method);
        try{return c.getResponseCode();}finally{c.disconnect();}
    }
    public static void main(String[] args) throws Exception {
        ClientExtensionPlugin plugin=new ClientExtensionPlugin(null);plugin.start();
        DvdCapabilityReceiver receiver=new DvdCapabilityReceiver(()->false);
        String old=SageTV.UIS[0];SageTV.UIS[0]="444556303031";
        try {
            check(!ClientExtensionPlugin.playbackStateEnabled(),"state defaults off");
            receiver.start(0); int port=receiver.port();String path="/vibe/media/state?client=444556303031";
            check(request(port,path,"GET")==404,"disabled service");
            plugin.setConfigValue("enabled","true");plugin.setConfigValue("video.state_bridge","true");
            check(!ClientExtensionPlugin.dvdClientControlsEnabled(),"DVD HTTP controls default off");
            String controls="/vibe/dvd/controls?client=444556303031";
            check(request(port,controls,"GET")==404,"controls absent unless explicitly enabled");
            plugin.setConfigValue("dvd.owned_navigation","true");plugin.setConfigValue("dvd.client_controls","true");
            DvdClientCapabilities.Lease dvdLease=DvdClientCapabilities.register("444556303031");
            check(request(port,controls,"GET")==404,"unacknowledged DVD load cannot control");
            DvdClientCapabilities.confirm(dvdLease.client,dvdLease.nonce,"3");
            check(request(port,controls,"GET")==200,"current acknowledged DVD control discovery");
            String command=controls+"&challenge="+dvdLease.nonce;
            check(request(port,command+"&operation=seek","POST")==400,"no arbitrary seek");
            check(request(port,command+"&operation=resume","POST")==409,"no active owned player, no mutation");
            check(request(port,controls+"&client=444556303031","GET")==400,"ambiguous control identity");
            DvdClientCapabilities.Lease replacement=DvdClientCapabilities.register(dvdLease.client);
            DvdClientCapabilities.confirm(replacement.client,replacement.nonce,"3");
            check(request(port,command+"&operation=beginning","POST")==409,"stale DVD challenge refuses replacement");
            plugin.setConfigValue("dvd.client_controls","false");
            check(request(port,controls,"GET")==404,"feature off refuses existing lease");
            DvdClientCapabilities.release(replacement);
            check(request(port,path,"GET")==200,"exact public UI snapshot");
            String state=PlaybackStateSnapshot.read("444556303031");
            check(state.contains("\"media_id\":1")&&state.contains("\"media_time_ms\":12345")
                    &&state.contains("\"recording\":false")&&!state.contains("generated-ts"),"typed fields, no paths");
            SageTV.recording=true;check(PlaybackStateSnapshot.read("444556303031").contains("\"recording\":true"),"public recording flag is not inferred from filename");SageTV.recording=false;
            check(request(port,path,"POST")==405,"no mutation");
            check(request(port,path+"&revision=1","GET")==400,"no extra fields");
            check(request(port,"/vibe/media/state?client=ffffffffffff","GET")==404,"unknown UI");
            SageTV.currentDvd=true;check(PlaybackStateSnapshot.read("444556303031").contains("\"dvd_title\":2"),"DVD public fields");
            SageTV.currentDvd=false;SageTV.changeMedia=true;SageTV.mediaReads=0;
            refuses(()->PlaybackStateSnapshot.read("444556303031"));SageTV.changeMedia=false;
            ImportedMetadataRepair repair=new ImportedMetadataRepair();
            SageTV.METADATA.put("Format.Container","MPEG2-TS");SageTV.METADATA.put("Title","Before");
            repair.preview("1","Title","After");check(SageTV.writes==0,"preview is read-only");
            repair.apply();check("After".equals(SageTV.METADATA.get("Title")),"approved API edit");
            refuses(()->repair.apply());
            repair.preview("1","Title","Later");SageTV.METADATA.put("Title","Concurrent");
            refuses(()->repair.apply());check("Concurrent".equals(SageTV.METADATA.get("Title")),"CAS refuses stale preview");
            refuses(()->repair.preview("1","Format.Video.FPS","60"));
            refuses(()->repair.preview("1","Duration","90000"));
            refuses(()->repair.preview("1","Title",""));
            refuses(()->repair.preview("1","Year","not-a-year"));
            refuses(()->repair.preview("999","Title","Other"));
            SageTV.tv=true;refuses(()->repair.preview("1","Title","TV"));SageTV.tv=false;
            SageTV.recording=true;refuses(()->repair.preview("1","Title","Recording"));SageTV.recording=false;
            SageTV.library=false;refuses(()->repair.preview("1","Title","Not imported"));SageTV.library=true;
            SageTV.METADATA.remove("Format.Container");refuses(()->repair.preview("1","Title","No structural format"));
            DvdPlaybackIntent.save("one","/discA",2,12345);
            check(DvdPlaybackIntent.read("one","/discA").position==12345,"owned bookmark persists");
            check(DvdPlaybackIntent.read("one","/discB")==null&&DvdPlaybackIntent.read("two","/discA")==null,"exact disc and UI");
            DvdPlaybackIntent.clear("one");check(DvdPlaybackIntent.read("one","/discA")==null,"explicit own bookmark removal");
            refuses(()->DvdPlaybackIntent.save("one","/discA",0,-1));
            check(PluginOwnedDvdPlayer.requestIntent("absent","action.dvd_resume").startsWith("Refused"),"no active owned session");
            plugin.stop();check(request(port,path,"GET")==404,"Standard Disable closes state provider");
            check(SageTV.writes==1,"no automatic metadata writes");
        } finally { receiver.stop();plugin.stop();SageTV.UIS[0]=old; }
        System.out.println("PASS: stock state/transition/refusal, reviewed metadata CAS and exact owned bookmark");
    }
}
