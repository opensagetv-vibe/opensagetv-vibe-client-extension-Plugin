package org.opensagetv.vibe.clientextension;

import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import sage.SageTV;

public final class PlaybackCaptionStateTest {
    private static void check(boolean condition, String reason) { if (!condition) throw new AssertionError(reason); }
    private static int request(int port, String path, String method) throws Exception {
        HttpURLConnection connection = (HttpURLConnection)new URL("http://127.0.0.1:"+port+path).openConnection(Proxy.NO_PROXY);
        connection.setConnectTimeout(1000); connection.setReadTimeout(1000); connection.setRequestMethod(method);
        try { return connection.getResponseCode(); } finally { connection.disconnect(); }
    }
    public static void main(String[] args) throws Exception {
        ClientExtensionPlugin plugin = new ClientExtensionPlugin(null);
        plugin.start();
        check(!ClientExtensionPlugin.captionBridgeEnabled(), "default off");
        plugin.setConfigValue("enabled", "true");
        plugin.setConfigValue("video.caption_bridge", "true");
        check(ClientExtensionPlugin.captionBridgeEnabled(), "explicit opt-in");
        String originalUi = SageTV.UIS[0];
        SageTV.UIS[0] = "44:45:56:30:30:31";
        java.util.Map<String,String> properties = new java.util.HashMap<String,String>(SageTV.CLIENT);
        DvdCapabilityReceiver receiver = new DvdCapabilityReceiver(() -> false);
        try {
            receiver.start(0); int port = receiver.port();
            String query="/vibe/media/captions?client=444556303031";
            check(request(port,query,"GET")==200,"public API initial read");
            PlaybackCaptionState.Lease first = PlaybackCaptionState.lookup("444556303031");
            check(!first.confirmed,"GET is not acknowledgement");
            check(request(port,query+"&challenge="+first.nonce,"GET")==409,"unacknowledged poll refused");
            check(request(port,query+"&challenge="+first.nonce+"&revision=2","POST")==400,"unknown protocol rejected");
            check(request(port,query+"&challenge="+first.nonce+"&revision=1","POST")==200,"current load acknowledged");
            for (int slot=0;slot<=2;slot++) {
                SageTV.captionState=slot==0?"Off":"CC"+slot;
                check(request(port,query+"&challenge="+first.nonce,"GET")==200 && first.state==slot,"public API virtual slot");
            }
            check(request(port,query,"GET")==200,"replacement lease");
            PlaybackCaptionState.Lease next=PlaybackCaptionState.lookup(first.client);
            check(!first.confirmed && !PlaybackCaptionState.set(first,1),"replacement cancels old owner");
            check(request(port,query+"&challenge="+first.nonce,"GET")==409,"stale load rejected");
            check(request(port,query+"&challenge="+first.nonce+"&revision=1","POST")==400,"stale acknowledgement rejected");
            check(request(port,query+"&cc=2","POST")==400,"no remote caption mutation");
            check(request(port,"/vibe/media/captions?client=ffffffffffff","GET")==404,"no other UI selected");
            SageTV.currentDvd=true;
            check(request(port,query,"GET")==404,"DVD subtitle policy unchanged");
            SageTV.currentDvd=false; SageTV.currentMedia=null;
            check(request(port,query,"GET")==404,"no media");
            SageTV.currentMedia="generated-ts";
            check(properties.equals(SageTV.CLIENT),"no hooks, FastLoad or user properties changed");
            check(request(port,"/","GET")==404 && request(port,"/reports","GET")==404,"no browser/viewer");
            plugin.stop();
            check(request(port,query,"GET")==404 && !PlaybackCaptionState.current(next),"Standard Disable invalidates registry");
        } finally { receiver.stop(); plugin.stop(); SageTV.UIS[0]=originalUi; SageTV.captionState="Off"; }
        System.out.println("PASS: public caption API identity/load/state, no player mutation, refusal and Disable");
    }
}
