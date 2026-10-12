package com.uprfvx.random.yao;

/*----------------------------------------------------------------------------*/
/*--  CodePatchDisclaimer.java - the notice shown before a code patch.      --*/
/*--                                                                        --*/
/*--  Part of "Pokemon Randomizer YAO", a fork of                           --*/
/*--  "Universal Pokemon Randomizer FVX" (UPR-FVX), licensed under the GPL  --*/
/*--  version 3 or (at your option) any later version. See LICENSE.txt.     --*/
/*----------------------------------------------------------------------------*/

import java.util.Locale;

/**
 * The notice that must be accepted before an option that patches the game's program code is applied
 * (the adjustable shiny chance is the first one). The interface shows it as a dialog with Accept and
 * Reject; the command line shows it and expects {@code --accept-code-patches}. Acceptance is per run and is
 * never stored in a settings file, so a settings file or string shared with someone else cannot accept it
 * for them.
 */
public final class CodePatchDisclaimer {

    private CodePatchDisclaimer() {
    }

    public static final String ENGLISH =
            "Some options (for example the adjustable shiny chance) change the game's program code inside the "
            + "randomized ROM, not only its data. Your original ROM is never modified; only the new file is. "
            + "These patches were checked against the supported game versions, but modified code can behave "
            + "unexpectedly in versions or ROMs that were not tested, and in some emulators or flash cards. "
            + "A Pokemon made shiny this way is only shiny in this patched game: it will not be shiny if you "
            + "move it to another game. Do you want to apply these patches?";

    public static final String SPANISH =
            "Algunas opciones (por ejemplo la probabilidad ajustable de shiny) modifican el codigo del programa "
            + "del juego dentro de la ROM aleatorizada, y no solo sus datos. Tu ROM original nunca se modifica; "
            + "solo se crea un archivo nuevo. Estos parches se han comprobado con las versiones de juego "
            + "compatibles, pero el codigo modificado puede comportarse de forma inesperada en versiones o ROMs "
            + "que no se han probado, y en algunos emuladores o flash cards. Un Pokemon hecho shiny de este modo "
            + "solo es shiny en este juego parcheado: no sera shiny si lo mueves a otro juego. "
            + "Quieres aplicar estos parches?";

    /** Spanish for a Spanish language code ("es" or "es-ES" style), English otherwise. */
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
