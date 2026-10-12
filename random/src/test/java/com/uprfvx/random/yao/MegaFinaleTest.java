package com.uprfvx.random.yao;

import com.uprfvx.romio.constants.Gen3Constants;
import com.uprfvx.romio.constants.Gen7Constants;
import com.uprfvx.romio.gamedata.Item;
import com.uprfvx.romio.gamedata.Species;
import com.uprfvx.romio.gamedata.SpeciesSet;
import com.uprfvx.romio.gamedata.Trainer;
import com.uprfvx.romio.gamedata.TrainerPokemon;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

public class MegaFinaleTest {

    private static Species base(int n) {
        Species s = new Species(n);
        s.setName("P" + n);
        return s;
    }

    private static Species megaOf(Species base, int forme, Item stone) {
        Species m = new Species(base.getNumber() + 1000);
        m.setName(base.getName() + "-Mega");
        base.addAltForme(forme, m);
        m.setMegaEvolution(stone);
        return m;
    }

    private static Trainer trainer(String tag, Species... team) {
        Trainer t = new Trainer();
        t.setTag(tag);
        List<TrainerPokemon> list = new ArrayList<>();
        for (Species s : team) {
            list.add(new TrainerPokemon(s, 40));
        }
        t.setPokemon(list);
        return t;
    }

    /** The old single-choice scopes, kept in the tests as shorthand for the equivalent tier selections. */
    private enum Scope {
        NONE(false, false, false, false), LEADERS_AND_LEAGUE(true, true, false, false), BOSS(true, true, true, false),
        ALL(false, false, false, true);

        final MegaFinaleApplier.Selection selection;

        Scope(boolean gym, boolean league, boolean evil, boolean everyone) {
            selection = new MegaFinaleApplier.Selection(gym, league, evil, false, everyone, 0);
        }
    }

    private static List<String> applyFull(List<Trainer> trainers, SpeciesSet megaForms, Scope scope,
                                          boolean replaceWithoutMega, boolean evilBosses, int generation, int romType,
                                          Random random) {
        MegaFinaleApplier.Selection s = scope.selection;
        if (evilBosses) {
            s = new MegaFinaleApplier.Selection(s.gymLeaders(), s.league(), true, s.otherBosses(), s.everyone(), 0);
        }
        return MegaFinaleApplier.apply(trainers, megaForms, s, replaceWithoutMega, generation, romType, random);
    }

    /** Shorthand for the common case where the game/variant does not matter (no missing-CHAMPION-tag game). */
    private static List<String> apply(List<Trainer> trainers, SpeciesSet megaForms, Scope scope,
                                       boolean replaceWithoutMega, Random random) {
        return applyFull(trainers, megaForms, scope, replaceWithoutMega, false, 0, -1, random);
    }

    @Test
    public void theLastPokemonGetsTheStoneOfItsMega() {
        Species a = base(1), b = base(2);
        Item stone = new Item(100, "Stone A");
        megaOf(a, 1, stone);
        Trainer boss = trainer("CHAMPION", b, a);
        List<String> notes = apply(List.of(boss), new SpeciesSet(a.getAltFormes()), Scope.BOSS, false, new Random(1));
        TrainerPokemon last = boss.getPokemon().get(1);
        assertSame(stone, last.getHeldItem());
        assertTrue(last.hasMegaStone());
        assertTrue(last.canMegaEvolve());
        assertNull(boss.getPokemon().get(0).getHeldItem());
        assertTrue(notes.get(0).contains("1 trainer"));
    }

    @Test
    public void bossScopeLeavesOtherTrainersAloneAndAllScopeDoesNot() {
        Species a = base(1);
        Item stone = new Item(100, "Stone A");
        megaOf(a, 1, stone);
        Trainer boss = trainer("ELITE1", a), regular = trainer("YOUNGSTER", a);
        apply(List.of(boss, regular), new SpeciesSet(a.getAltFormes()), Scope.BOSS, false, new Random(1));
        assertTrue(boss.getPokemon().get(0).hasMegaStone());
        assertFalse(regular.getPokemon().get(0).hasMegaStone());
        apply(List.of(boss, regular), new SpeciesSet(a.getAltFormes()), Scope.ALL, false, new Random(1));
        assertTrue(regular.getPokemon().get(0).hasMegaStone());
    }

    @Test
    public void aTrainerThatAlreadyHasAMegaIsLeftAlone() {
        Species a = base(1), b = base(2);
        megaOf(a, 1, new Item(100, "Stone A"));
        Trainer t = trainer("CHAMPION", a, b);
        t.getPokemon().get(0).setHasMegaStone(true);
        List<String> notes = apply(List.of(t), new SpeciesSet(a.getAltFormes()), Scope.ALL, true, new Random(1));
        assertNull(t.getPokemon().get(1).getHeldItem());
        assertSame(b, t.getPokemon().get(1).getSpecies());
        assertTrue(notes.get(0).contains("already had a Mega"));
    }

    @Test
    public void aLastPokemonWithoutAMegaIsKeptOrReplacedAsChosen() {
        Species a = base(1), b = base(2);
        Item stone = new Item(100, "Stone A");
        megaOf(a, 1, stone);
        Trainer keep = trainer("CHAMPION", b);
        List<String> notes = apply(List.of(keep), new SpeciesSet(a.getAltFormes()), Scope.ALL, false, new Random(1));
        assertSame(b, keep.getPokemon().get(0).getSpecies());
        assertFalse(keep.getPokemon().get(0).hasMegaStone());
        assertTrue(notes.get(0).contains("no Mega"));

        Trainer replace = trainer("CHAMPION", b);
        apply(List.of(replace), new SpeciesSet(a.getAltFormes()), Scope.ALL, true, new Random(1));
        assertSame(a, replace.getPokemon().get(0).getSpecies());
        assertSame(stone, replace.getPokemon().get(0).getHeldItem());
        assertTrue(replace.getPokemon().get(0).isResetMoves());
    }

    @Test
    public void megasWithoutAStoneAreNeverUsed() {
        Species a = base(1);
        megaOf(a, 1, null); // like Mega Rayquaza: no stone
        Trainer t = trainer("CHAMPION", a);
        apply(List.of(t), new SpeciesSet(a.getAltFormes()), Scope.ALL, true, new Random(1));
        assertFalse(t.getPokemon().get(0).hasMegaStone());
    }

    @Test
    public void offOrBadInputDoesNothingAndNeverThrows() {
        Species a = base(1);
        Trainer t = trainer("CHAMPION", a);
        assertTrue(apply(List.of(t), new SpeciesSet(), Scope.NONE, true, new Random(1)).isEmpty());
        assertTrue(apply(null, new SpeciesSet(), Scope.ALL, true, new Random(1)).isEmpty());
        assertDoesNotThrow(() -> apply(new ArrayList<>(java.util.Arrays.asList(null, new Trainer(), t)),
                new SpeciesSet(), Scope.ALL, true, new Random(1)));
    }

    @Test
    public void settingsRoundTripAndOldFormatConversion() {
        YaoSettings yao = new YaoSettings();
        yao.setMegaFinaleTiers(true, false, true, true, false, 25);
        yao.setMegaFinaleReplace(true);
        assertFalse(yao.isDefault());
        YaoSettings back = YaoSettings.fromBase64(yao.toBase64());
        assertTrue(back.isMegaGymLeaders());
        assertFalse(back.isMegaLeague());
        assertTrue(back.isMegaEvilBosses());
        assertTrue(back.isMegaOtherBosses());
        assertFalse(back.isMegaEveryone());
        assertTrue(back.isMegaFinaleReplace());
        assertEquals(25, back.getMegaFromLevel());
        assertTrue(back.getWarnings().isEmpty());

        YaoSettings oldBoss = YaoSettings.fromText("megafinale=boss\nmegafinale.replace=1\n");
        assertTrue(oldBoss.isMegaGymLeaders() && oldBoss.isMegaLeague() && oldBoss.isMegaEvilBosses());
        assertFalse(oldBoss.isMegaEveryone());
        assertTrue(oldBoss.isMegaFinaleReplace());
        assertEquals(1, oldBoss.getWarnings().size());
        YaoSettings oldLeaders = YaoSettings.fromText("megafinale=leaders\nmegafinale.evilbosses=1\n");
        assertTrue(oldLeaders.isMegaGymLeaders() && oldLeaders.isMegaLeague() && oldLeaders.isMegaEvilBosses());
        assertTrue(YaoSettings.fromText("megafinale=all\n").isMegaEveryone());
        assertEquals(1, YaoSettings.fromText("megafinale=sometimes\n").getWarnings().size());
        assertFalse(YaoSettings.fromText("megafinale=sometimes\n").isMegaFinaleOn());
        // a start level out of range is repaired with a warning, never an error
        YaoSettings clamp = new YaoSettings();
        clamp.setMegaFinaleTiers(true, false, false, false, false, 400);
        assertEquals(100, clamp.getMegaFromLevel());
        assertEquals(1, clamp.getWarnings().size());
    }

    @Test
    public void tiersCombineAndFromLevelSkipsEarlyTrainers() {
        Species a = base(1);
        megaOf(a, 1, new Item(100, "Stone A"));
        Trainer gym1 = trainer("GYM1-LEADER", a);
        gym1.getPokemon().get(0).setLevel(14);
        Trainer gym3 = trainer("GYM3-LEADER", a);
        gym3.getPokemon().get(0).setLevel(30);
        Trainer elite = trainer("ELITE1", a);
        Trainer evil = trainer("THEMED:MAXIE-LEADER", a);
        Trainer rival = trainer("RIVAL4-1", a);
        MegaFinaleApplier.Selection sel = new MegaFinaleApplier.Selection(true, false, true, false, false, 30);
        List<String> notes = MegaFinaleApplier.apply(List.of(gym1, gym3, elite, evil, rival),
                new SpeciesSet(a.getAltFormes()), sel, false, 0, -1, new Random(1));
        assertFalse(gym1.getPokemon().get(0).hasMegaStone()); // below level 30
        assertTrue(gym3.getPokemon().get(0).hasMegaStone());
        assertFalse(elite.getPokemon().get(0).hasMegaStone()); // league not selected
        assertTrue(evil.getPokemon().get(0).hasMegaStone()); // level 40 team
        assertFalse(rival.getPokemon().get(0).hasMegaStone()); // other bosses not selected
        assertTrue(notes.get(0).contains("1 gym leaders"), notes.get(0));
        assertTrue(notes.get(0).contains("1 evil-team bosses"), notes.get(0));
        assertTrue(notes.get(0).contains("1 were skipped because"), notes.get(0));

        Trainer rival2 = trainer("RIVAL4-1", a);
        MegaFinaleApplier.apply(List.of(rival2), new SpeciesSet(a.getAltFormes()),
                new MegaFinaleApplier.Selection(false, false, false, true, false, 0), false, 0, -1, new Random(1));
        assertTrue(rival2.getPokemon().get(0).hasMegaStone());
        Trainer firstRival = trainer("RIVAL1-1", a);
        MegaFinaleApplier.apply(List.of(firstRival), new SpeciesSet(a.getAltFormes()),
                new MegaFinaleApplier.Selection(false, false, false, true, false, 0), false, 0, -1, new Random(1));
        assertFalse(firstRival.getPokemon().get(0).hasMegaStone());
    }

    // ---------------- gym leaders + league scope ----------------

    @Test
    public void leadersAndLeagueIncludesOnlyTrueLeadersEliteChampionAndUber() {
        Species a = base(1);
        Item stone = new Item(100, "Stone A");
        megaOf(a, 1, stone);
        Trainer gymLeader = trainer("GYM3-LEADER", a);
        Trainer elite = trainer("ELITE2", a);
        Trainer champion = trainer("CHAMPION", a);
        Trainer uber = trainer("UBER", a);
        Trainer kahuna = trainer("KAHUNA1", a);
        Trainer giovanniFRLG = trainer("GIO1-LEADER", a);
        Trainer evilBoss = trainer("THEMED:MAXIE-LEADER", a);
        Trainer evilBossBare = trainer("LEADER", a); // HGSS Archer
        Trainer trialCaptain = trainer("THEMED:ILIMA-STRONG", a);
        Trainer regular = trainer("YOUNGSTER", a);

        List<Trainer> trainers = List.of(gymLeader, elite, champion, uber, kahuna, giovanniFRLG,
                evilBoss, evilBossBare, trialCaptain, regular);
        apply(trainers, new SpeciesSet(a.getAltFormes()), Scope.LEADERS_AND_LEAGUE, false, new Random(1));

        assertTrue(gymLeader.getPokemon().get(0).hasMegaStone());
        assertTrue(elite.getPokemon().get(0).hasMegaStone());
        assertTrue(champion.getPokemon().get(0).hasMegaStone());
        assertTrue(uber.getPokemon().get(0).hasMegaStone());
        assertTrue(kahuna.getPokemon().get(0).hasMegaStone());
        assertTrue(giovanniFRLG.getPokemon().get(0).hasMegaStone());
        assertFalse(evilBoss.getPokemon().get(0).hasMegaStone());
        assertFalse(evilBossBare.getPokemon().get(0).hasMegaStone());
        assertFalse(trialCaptain.getPokemon().get(0).hasMegaStone());
        assertFalse(regular.getPokemon().get(0).hasMegaStone());
    }

    @Test
    public void ambiguousLeaderSuffixDoesNotMatchGymLeaderRegex() {
        // Tags that end with "LEADER" but are not a true "GYM<n>-LEADER" must not count as gym-leader tier.
        Species a = base(1);
        megaOf(a, 1, new Item(100, "Stone A"));
        Trainer bareLeader = trainer("LEADER", a);
        Trainer themedLeader = trainer("THEMED:GUZMA-LEADER", a);
        Trainer giovanniRB = trainer("GIO1", a); // Red/Blue: no "-LEADER" suffix, not a leader tier here
        apply(List.of(bareLeader, themedLeader, giovanniRB), new SpeciesSet(a.getAltFormes()),
                Scope.LEADERS_AND_LEAGUE, false, new Random(1));
        assertFalse(bareLeader.getPokemon().get(0).hasMegaStone());
        assertFalse(themedLeader.getPokemon().get(0).hasMegaStone());
        assertFalse(giovanniRB.getPokemon().get(0).hasMegaStone());
    }

    // ---------------- evil-team bosses checkbox ----------------

    @Test
    public void includeEvilTeamBossesChecksboxAddsThemOnTopOfLeadersAndLeagueOrBoss() {
        Species a = base(1);
        megaOf(a, 1, new Item(100, "Stone A"));
        Trainer evilBoss = trainer("THEMED:LYSANDRE-LEADER", a);
        Trainer archer = trainer("LEADER", a);

        // Off by default
        List<String> notesOff = applyFull(List.of(evilBoss, archer), new SpeciesSet(a.getAltFormes()),
                Scope.LEADERS_AND_LEAGUE, false, false, 0, -1, new Random(1));
        assertFalse(evilBoss.getPokemon().get(0).hasMegaStone());
        assertFalse(archer.getPokemon().get(0).hasMegaStone());
        assertFalse(notesOff.get(0).contains("evil-team bosses"));

        // On: added for LEADERS_AND_LEAGUE
        List<String> notesOn = applyFull(List.of(evilBoss, archer), new SpeciesSet(a.getAltFormes()),
                Scope.LEADERS_AND_LEAGUE, false, true, 0, -1, new Random(1));
        assertTrue(evilBoss.getPokemon().get(0).hasMegaStone());
        assertTrue(archer.getPokemon().get(0).hasMegaStone());
        assertTrue(notesOn.get(0).contains("2 evil-team bosses"), notesOn.get(0));
    }

    @Test
    public void includeEvilTeamBossesIsNoOpForAllScope() {
        Species a = base(1);
        megaOf(a, 1, new Item(100, "Stone A"));
        Trainer evilBoss = trainer("THEMED:ARCHIE-LEADER", a);
        // Already included under ALL regardless of the checkbox, and the note should not double count it.
        List<String> notes = applyFull(List.of(evilBoss), new SpeciesSet(a.getAltFormes()),
                Scope.ALL, false, true, 0, -1, new Random(1));
        assertTrue(evilBoss.getPokemon().get(0).hasMegaStone());
        assertEquals(1, notes.size());
    }

    @Test
    public void includeEvilTeamBossesHasNoEffectWithoutMatchingScope() {
        Species a = base(1);
        megaOf(a, 1, new Item(100, "Stone A"));
        Trainer evilBoss = trainer("THEMED:CYRUS-LEADER", a);
        // Checkbox only adds on top of BOSS or LEADERS_AND_LEAGUE; it is already a boss under BOSS anyway
        // (isBoss()'s ambiguous endsWith("LEADER") already covers it), so it should hold with BOSS too.
        applyFull(List.of(evilBoss), new SpeciesSet(a.getAltFormes()), Scope.BOSS,
                false, false, 0, -1, new Random(1));
        assertTrue(evilBoss.getPokemon().get(0).hasMegaStone());
    }

    // ---------------- per-game champion identification (games with no CHAMPION tag) ----------------

    @Test
    public void gen1ChampionIsRival8Family() {
        Species a = base(1);
        megaOf(a, 1, new Item(100, "Stone A"));
        Trainer championVariant0 = trainer("RIVAL8-0", a);
        Trainer championVariant2 = trainer("RIVAL8-2", a);
        Trainer earlierRival = trainer("RIVAL7-0", a);
        List<Trainer> trs = List.of(championVariant0, championVariant2, earlierRival);
        applyFull(trs, new SpeciesSet(a.getAltFormes()), Scope.LEADERS_AND_LEAGUE,
                false, false, 1, -1, new Random(1));
        assertTrue(championVariant0.getPokemon().get(0).hasMegaStone());
        assertTrue(championVariant2.getPokemon().get(0).hasMegaStone());
        assertFalse(earlierRival.getPokemon().get(0).hasMegaStone());
    }

    @Test
    public void frlgChampionIsRival8AndRival9FamiliesOnly() {
        Species a = base(1);
        megaOf(a, 1, new Item(100, "Stone A"));
        Trainer round1 = trainer("RIVAL8-1", a);
        Trainer round2 = trainer("RIVAL9-1", a);
        Trainer earlierRival = trainer("RIVAL6-0", a);
        List<Trainer> trs = List.of(round1, round2, earlierRival);
        applyFull(trs, new SpeciesSet(a.getAltFormes()), Scope.LEADERS_AND_LEAGUE,
                false, false, 3, Gen3Constants.RomType_FRLG, new Random(1));
        assertTrue(round1.getPokemon().get(0).hasMegaStone());
        assertTrue(round2.getPokemon().get(0).hasMegaStone());
        assertFalse(earlierRival.getPokemon().get(0).hasMegaStone());
    }

    @Test
    public void rseDoesNotUseTheMissingChampionTagGen3Fallback() {
        // Ruby/Sapphire/Emerald already have an explicit CHAMPION tag; a "RIVAL8-0" tag there (if it existed)
        // must not be picked up by the FRLG-only fallback.
        Species a = base(1);
        megaOf(a, 1, new Item(100, "Stone A"));
        Trainer notChampionHere = trainer("RIVAL8-0", a);
        applyFull(List.of(notChampionHere), new SpeciesSet(a.getAltFormes()),
                Scope.LEADERS_AND_LEAGUE, false, false, 3, Gen3Constants.RomType_Em, new Random(1));
        assertFalse(notChampionHere.getPokemon().get(0).hasMegaStone());
    }

    @Test
    public void sunMoonChampionIsRival3FamilyAndUltraSunMoonIsRival2Family() {
        Species a = base(1);
        megaOf(a, 1, new Item(100, "Stone A"));

        Trainer smChampion = trainer("RIVAL3-0", a);
        Trainer smEarlierKukuiBattle = trainer("RIVAL2-0", a); // the earlier, non-champion Kukui battle
        applyFull(List.of(smChampion, smEarlierKukuiBattle), new SpeciesSet(a.getAltFormes()),
                Scope.LEADERS_AND_LEAGUE, false, false, 7, Gen7Constants.Type_SM, new Random(1));
        assertTrue(smChampion.getPokemon().get(0).hasMegaStone());
        assertFalse(smEarlierKukuiBattle.getPokemon().get(0).hasMegaStone());

        Species b = base(2);
        megaOf(b, 1, new Item(101, "Stone B"));
        Trainer usumChampion = trainer("RIVAL2-0", b);
        applyFull(List.of(usumChampion), new SpeciesSet(b.getAltFormes()),
                Scope.LEADERS_AND_LEAGUE, false, false, 7, Gen7Constants.Type_USUM, new Random(1));
        assertTrue(usumChampion.getPokemon().get(0).hasMegaStone());
    }

    @Test
    public void sunMoonKahunasAreLeaderTierButTrialCaptainsAreNot() {
        Species a = base(1);
        megaOf(a, 1, new Item(100, "Stone A"));
        Trainer kahuna = trainer("KAHUNA3", a); // Nanu, per Gen7Constants.tagTrainersSM
        Trainer realElite = trainer("ELITE6", a); // Kahili, per Gen7Constants.tagTrainersSM
        Trainer captain = trainer("THEMED:KIAWE-STRONG", a);
        applyFull(List.of(kahuna, realElite, captain), new SpeciesSet(a.getAltFormes()),
                Scope.LEADERS_AND_LEAGUE, false, false, 7, Gen7Constants.Type_SM, new Random(1));
        assertTrue(kahuna.getPokemon().get(0).hasMegaStone());
        assertTrue(realElite.getPokemon().get(0).hasMegaStone());
        assertFalse(captain.getPokemon().get(0).hasMegaStone());
    }

    // ---------------- Trainer.isBoss() stays backward compatible after the KAHUNA retag ----------------

    @Test
    public void kahunaTagStillCountsAsBossTier() {
        Trainer kahuna = trainer("KAHUNA1", base(1));
        assertTrue(kahuna.isBoss());
    }

    @Test
    public void evilTeamBossTagMatchesIsEvilTeamBossButNotLeadersAndLeagueTier() {
        Trainer guzma = trainer("THEMED:GUZMA-LEADER", base(1));
        Trainer archer = trainer("LEADER", base(1));
        assertTrue(guzma.isEvilTeamBoss());
        assertTrue(archer.isEvilTeamBoss());
        assertFalse(guzma.isGymLeaderOrLeagueTier());
        assertFalse(archer.isGymLeaderOrLeagueTier());
        // Still boss tier under the broad, pre-existing isBoss() (unchanged behaviour).
        assertTrue(guzma.isBoss());
        assertTrue(archer.isBoss());
    }
}
