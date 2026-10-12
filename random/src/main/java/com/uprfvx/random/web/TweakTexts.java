package com.uprfvx.random.web;

/*----------------------------------------------------------------------------*/
/*--  TweakTexts.java - names and descriptions of the game tweaks.          --*/
/*--                                                                        --*/
/*--  Part of "Pokemon Randomizer YAO", a fork of                           --*/
/*--  "Universal Pokemon Randomizer FVX" (UPR-FVX), licensed under the GPL  --*/
/*--  version 3 or (at your option) any later version. See LICENSE.txt.     --*/
/*----------------------------------------------------------------------------*/

import java.util.MissingResourceException;
import java.util.ResourceBundle;

/** Reads the names and descriptions of the game tweaks from the original interface's text bundle. */
final class TweakTexts {

    private TweakTexts() {
    }

    private static final ResourceBundle BUNDLE = load();

    private static ResourceBundle load() {
        try {
            return ResourceBundle.getBundle("com/uprfvx/random/gui/Bundle");
        } catch (MissingResourceException e) {
            return null;
        }
    }

    static String name(String id) {
        return text("CodeTweaks." + id + ".name", id);
    }

    static String tooltip(String id) {
        String raw = text("CodeTweaks." + id + ".toolTipText", null);
        if (raw == null) {
            return null;
        }
        String plain = raw.replaceAll("(?i)<br\\s*/?>", "\n").replaceAll("<[^>]+>", "").strip();
        return plain.replaceAll("\n{3,}", "\n\n");
    }

    private static String text(String key, String fallback) {
        try {
            return BUNDLE == null ? fallback : BUNDLE.getString(key);
        } catch (MissingResourceException e) {
            return fallback;
        }
    }
}
