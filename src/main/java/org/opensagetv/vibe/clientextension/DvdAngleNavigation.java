package org.opensagetv.vibe.clientextension;

import java.io.IOException;
import sage.dvd.dsi_t;
import sage.dvd.pci_t;

/** Bounded DVD NAV address interpretation, not a copied vendor navigator.
 * Address sign/magnitude and ILVU flag meanings were checked against public
 * libdvdnav format handling; see DVD_CORE_PARITY.md. No filesystem/VM access.
 */
final class DvdAngleNavigation {
    static final int UNCHANGED = Integer.MIN_VALUE;
    private DvdAngleNavigation() { }

    static boolean withinCell(long sector, long first, long last) {
        return first >= 0 && last >= first && sector >= first && sector <= last;
    }
    static boolean sameBranch(long actualVob, long actualCell, long expectedVob, long expectedCell) {
        return actualVob > 0 && actualCell > 0 && actualVob == expectedVob && actualCell == expectedCell;
    }

    static int signedAddress(long encoded) throws IOException {
        long magnitude = encoded & 0x3fffffffL;
        if (magnitude == 0 || magnitude == 0x3fffffffL) return UNCHANGED;
        if (magnitude > 8388607L) throw new IOException("DVD angle address exceeds volume bound");
        return (int)((encoded & 0x80000000L) == 0 ? magnitude : -magnitude);
    }
    static int angleLink(int angle, pci_t pci, dsi_t dsi) throws IOException {
        if (angle < 1 || angle > 9) throw new IOException("Invalid DVD angle");
        long encoded = pci.nsml_agli.nsml_agl_dsta[angle - 1].get();
        if (encoded == 0) encoded = dsi.sml_agli.data[angle - 1].address.get();
        return signedAddress(encoded);
    }
    static int continuation(int angle, pci_t pci, dsi_t dsi) throws IOException {
        // Only the final unit may use the next interleaved-unit address.
        // Applying it to intermediate NAVs skips valid reference pictures.
        if ((dsi.sml_pbi.category.get() & 0xf000) != 0x5000) return UNCHANGED;
        return angleLink(angle, pci, dsi);
    }
}
