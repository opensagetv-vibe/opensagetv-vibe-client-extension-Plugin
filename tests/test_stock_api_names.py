"""Check literal API names against the actual stock binary, not an API fake."""
import os
from pathlib import Path
import re
import unittest
import zipfile

ROOT=Path(__file__).resolve().parents[1]

class StockApiNames(unittest.TestCase):
    def test_read_ahead_cell_cannot_open_native_representation(self):
        text=(ROOT/'src/main/java/org/opensagetv/vibe/clientextension/PluginOwnedDvdPlayer.java').read_text()
        cell=text.split('case StockDvdNavigator.CELL:',1)[1].split('case StockDvdNavigator.NAV:',1)[0]
        self.assertNotIn('nativeRepresentation()',cell)
        nav=text.split('DvdPresentationTimeline.Nav nav = timeline.add(event);',1)[1].split('if (event.state.domain == 4)',1)[0]
        self.assertIn('representationNav = nav;',nav)
        self.assertIn('nativeRepresentation()',nav)

    def test_explicit_intent_can_reach_nav_when_initially_paused(self):
        text=(ROOT/'src/main/java/org/opensagetv/vibe/clientextension/PluginOwnedDvdPlayer.java').read_text()
        method=text.split('private void applyIntent()',1)[1].split('private void resumeReload()',1)[0]
        self.assertLess(method.index('wire.command(22'),method.index('if (currState==PAUSE_STATE)'))
        self.assertIn('wire.command(21,new byte[0])',method)
        self.assertIn('currState=PLAY_STATE',method)
        self.assertIn('transformDeadline=System.nanoTime()+15000000000L',method)
        # Save/Clear return in the request path and never queue a playback intent.
        request=text.split('static String requestIntent(String ui,String action,DvdClientCapabilities.Lease expected)',1)[1].split('@Override public synchronized void load',1)[0]
        self.assertIn('session.capabilities!=expected',request)
        self.assertIn('ACTIVE.get(ui)!=player',request)
        self.assertIn('Refused: unknown DVD action',request)

    def test_new_services_reference_actual_stock_api_names(self):
        jar=Path(os.environ.get('SAGETV_JAR',str(ROOT/'.deps/stock/Sage.jar')))
        if not jar.is_file():self.skipTest('Compile-only stock Sage.jar not provided')
        source=ROOT/'src/main/java/org/opensagetv/vibe/clientextension'
        with zipfile.ZipFile(jar) as archive:
            api=b''.join(archive.read(name) for name in [
                'sage/api/MediaPlayerAPI.class','sage/api/MediaFileAPI.class','sage/api/Configuration.class'])
        for name in ['PlaybackStateSnapshot.java','ImportedMetadataRepair.java','DvdPlaybackIntent.java']:
            text=(source/name).read_text(encoding='utf-8')
            methods=re.findall(r'SageTV\.api(?:UI)?\([^\n]*?"([A-Za-z0-9]+)"',text)
            self.assertTrue(methods,name)
            for method in methods:
                with self.subTest(source=name,method=method):self.assertIn(method.encode('ascii'),api)

if __name__=='__main__':unittest.main()
