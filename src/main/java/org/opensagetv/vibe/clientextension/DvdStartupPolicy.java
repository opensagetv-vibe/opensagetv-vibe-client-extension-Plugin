package org.opensagetv.vibe.clientextension;

/** Immutable per-load policy; no mid-title reaction to a Configure edit. */
final class DvdStartupPolicy {
    static final int NONE=0, MAIN_FEATURE=1, ROOT=2;
    private final boolean menus, previews;
    private boolean applied;
    DvdStartupPolicy(boolean menus, boolean previews) { this.menus=menus; this.previews=previews; }
    int firstNav(int domain) {
        if (applied) return NONE;
        applied=true;
        if (menus) return MAIN_FEATURE;
        // The root menu is already the desired destination. Only skip an
        // actual first-play title; never navigate through arbitrary menus.
        return previews && domain==4 ? ROOT : NONE;
    }
}
