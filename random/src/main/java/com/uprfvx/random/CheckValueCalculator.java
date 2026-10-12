package com.uprfvx.random;

import com.uprfvx.romio.gamedata.*;
import com.uprfvx.romio.gamedata.basestats.BaseStats;
import com.uprfvx.romio.gamedata.basestats.Gen1BaseStats;
import com.uprfvx.romio.romhandlers.RomHandler;

public class CheckValueCalculator {

    private final RomHandler romHandler;
    private final Settings settings;

    private int checkValue;

    public CheckValueCalculator(RomHandler romHandler, Settings settings) {
        this.romHandler = romHandler;
        this.settings = settings;
    }

    public int calculate() {

        checkValue = 0;

        addSpeciesInfo();
        addTrainerInfo();
        addEncounterInfo();
        addTMMoveInfo();
        addMTMoveInfo();
        addStaticEncounterInfo();
        addTotemInfo();

        return checkValue;
    }

    private void addSpeciesInfo() {
        for (Species pkmn : romHandler.getSpecies()) {
            if (pkmn != null) {
                BaseStats bs = pkmn.getBaseStats();
                if (bs instanceof Gen1BaseStats gen1BS) {
                    addToCV(bs.getHp(), bs.getAttack(), bs.getDefense(), bs.getSpeed(), gen1BS.getSpecial());
                } else {
                    addToCV(bs.getHp(), bs.getAttack(), bs.getDefense(), bs.getSpeed(), bs.getSpatk(), bs.getSpdef());
                }
                addToCV(pkmn.getAbility1(), pkmn.getAbility2(), pkmn.getAbility3());
            }
        }
    }

    private void addTrainerInfo() {
        for (Trainer t : romHandler.getTrainers()) {
            for (TrainerPokemon tpk : t.getPokemon()) {
                addToCV(tpk.getLevel(), tpk.getSpecies().getNumber());
            }
        }
    }

    private void addEncounterInfo() {
        boolean useTimeBasedEncounters = settings.isUseTimeBasedEncounters() ||
                (!settings.isRandomizeWildPokemon() && settings.isWildLevelsModified());
        for (EncounterArea area : romHandler.getEncounters(useTimeBasedEncounters)) {
            for (Encounter e : area) {
                addToCV(e.getLevel(), e.getSpecies().getNumber());
            }
        }

    }

    // Modified from FVX 11/10/2026: getTMMoves() can legitimately return null when a ROM's TM data
    // cannot be located (its search anchor is not found at all, as opposed to found and later
    // mutated, which Gen4RomHandler.resolveTmHmDataOffset() now protects against). Guard against it
    // here too, so a ROM type without working TM move access cannot crash check value calculation.
    private void addTMMoveInfo() {
        var tmMoves = romHandler.getTMMoves();
        if (tmMoves == null) {
            return;
        }
        for (int tmMove : tmMoves) {
            addToCV(tmMove);
        }

    }

    private void addMTMoveInfo() {
        for (int mtMove : romHandler.getMoveTutorMoves()) {
            addToCV(mtMove);
        }

    }

    private void addStaticEncounterInfo() {
        for (StaticEncounter se : romHandler.getStaticPokemon()) {
            addToCV(se.getSpecies().getNumber());
        }
    }

    private void addTotemInfo() {
        for (TotemPokemon totem : romHandler.getTotemPokemon()) {
            addToCV(totem.getSpecies().getNumber());
        }
    }

    private void addToCV(int... values) {
        for (int value : values) {
            checkValue = Integer.rotateLeft(checkValue, 3);
            checkValue ^= value;
        }
    }

}
