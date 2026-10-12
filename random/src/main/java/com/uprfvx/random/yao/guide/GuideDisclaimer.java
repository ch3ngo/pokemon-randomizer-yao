package com.uprfvx.random.yao.guide;

/*----------------------------------------------------------------------------*/
/*--  GuideDisclaimer.java - the notice shown before the guide is made.     --*/
/*--                                                                        --*/
/*--  Part of "Pokemon Randomizer YAO", a fork of                           --*/
/*--  "Universal Pokemon Randomizer FVX" (UPR-FVX), licensed under the GPL  --*/
/*--  version 3 or (at your option) any later version. See LICENSE.txt.     --*/
/*----------------------------------------------------------------------------*/

import java.util.Locale;

/**
 * The question asked after randomizing: whether to generate the guide. The interface shows it as a dialog with
 * Yes and No (default No); the command line only generates the guide when asked with {@code --html-guide}.
 */
public final class GuideDisclaimer {

    private GuideDisclaimer() {
    }

    public static final String ENGLISH =
            "Do you want to generate a guide with information about this game? It shows what was randomized "
            + "(evolutions, moves, wild Pokemon, trainers and more). This could be cheating if you are competing "
            + "against someone.";

    public static final String SPANISH =
            "¿Quieres generar una guía con información sobre este juego? Muestra lo que se ha "
            + "aleatorizado (evoluciones, movimientos, Pokémon salvajes, entrenadores y más). Esto "
            + "podría considerarse trampa si compites contra alguien.";

    public static String forLanguage(String languageCode) {
        if (languageCode != null && languageCode.toLowerCase(Locale.ROOT).startsWith("es")) {
            return SPANISH;
        }
        return ENGLISH;
    }

    public static String forDefaultLocale() {
        return forLanguage(Locale.getDefault().getLanguage());
    }
}
