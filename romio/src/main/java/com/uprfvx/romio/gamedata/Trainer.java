package com.uprfvx.romio.gamedata;

/*----------------------------------------------------------------------------*/
/*--  Part of "Universal Pokemon Randomizer ZX" by the UPR-ZX team          --*/
/*--  Originally part of "Universal Pokemon Randomizer" by Dabomstew        --*/
/*--  Pokemon and any associated names and the like are                     --*/
/*--  trademark and (C) Nintendo 1996-2020.                                 --*/
/*--                                                                        --*/
/*--  The custom code written here is licensed under the terms of the GPL:  --*/
/*--                                                                        --*/
/*--  This program is free software: you can redistribute it and/or modify  --*/
/*--  it under the terms of the GNU General Public License as published by  --*/
/*--  the Free Software Foundation, either version 3 of the License, or     --*/
/*--  (at your option) any later version.                                   --*/
/*--                                                                        --*/
/*--  This program is distributed in the hope that it will be useful,       --*/
/*--  but WITHOUT ANY WARRANTY; without even the implied warranty of        --*/
/*--  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the          --*/
/*--  GNU General Public License for more details.                          --*/
/*--                                                                        --*/
/*--  You should have received a copy of the GNU General Public License     --*/
/*--  along with this program. If not, see <http://www.gnu.org/licenses/>.  --*/
/*----------------------------------------------------------------------------*/

import com.uprfvx.romio.constants.MoveIDs;
import com.uprfvx.romio.romhandlers.RomHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a Trainer as seen in battle; contains a list of their {@link TrainerPokemon},
 * their name, trainerclass, and other details.<br>
 * Most Trainer objects represent opponents, but some represent your ally in tag battles.<br>
 * Multiple Trainer objects may be used to represent a single in-game character
 * (such as the Rival, or Youngster Joey), in case they have multiple battles to their name.
 * <br><br>
 * The Trainer class uses a tagging system. The following tags are used:
 * <ul>
 *     <li><b><code>GYM[n]</code></b><br>
 *     Used by gym trainers. <code>[n]</code> is an integer denoting which
 *     specific gym this trainer belongs to.<br>
 *     E.g. "<code>GYM1</code>" is used by Bird Keeper Abe in Crystal.
 *     </li>
 *     <li><b><code>THEMED:[s]</code></b><br>
 *     Used by non-gym trainers who should share a type theme. <code>[s]</code> is
 *     an arbitrary string to group these trainers.<br>
 *     E.g. "<code>THEMED:SPROUTTOWER</code>" is used by Sage Neal in Gold.
 *     </li>
 *     <li><b><code>STRONG</code></b><br>
 *     Used by evil team admins and other important "mini-boss" trainers.
 *     Should typically be used with the <code>THEMED:[s]</code> tag.<br>
 *     E.g. "<code>THEMED:WALLY-STRONG</code>" is used by Wally in Alpha Sapphire.
 *     </li>
 *     <li><b><code>LEADER</code></b><br>
 *     Used by gym leaders and evil team leaders, following the
 *     <code>GYM[n]</code> and <code>THEMED:[s]</code> tags, respectively.<br>
 *     E.g. "<code>GYM2-LEADER</code>" is used by Brawly in Emerald,
 *     "<code>THEMED:LYSANDRE-LEADER</code>" is used by Lysandre in X.
 *     </li>
 *     <li><b><code>ELITE[n]</code></b><br>
 *     Used by Elite 4 members. <code>[n]</code> is an integer denoting which
 *     member it is.<br>
 *     E.g. "<code>ELITE3</code>" is used by Agatha in Yellow.
 *     </li>
 *     <li><b><code>CHAMPION</code></b><br>
 *     Used by the Champion.<br>
 *     E.g. "<code>CHAMPION</code>" is used by Iris in Black 2.
 *     </li>
 *     <li><b><code>UBER</code></b><br>
 *     Used by extra-strong trainers, the post-game super-bosses.<br>
 *     E.g. "<code>CHAMPION</code>" is used by Red in HeartGold.
 *     </li>
 *     <li><b><code>NOTSTRONG</code></b><br>
 *     Used to denote this trainer should not get any buffs (e.g. added Pokémon, held items).
 *     E.g. "<code>NOTSTRONG</code>" is used by Successor Korrina in Y.
 *     </li>
 *     <li><b><code>RIVAL[x]-[y]</code></b><br>
 *     Used by trainers that carry the starter <i>strong</i> against the player's chosen starter.
 *     <code>[x]</code> and <code>[y]</code> are both integers.<br>
 *     <code>[x]</code> denotes which battle this is. <code>[x]</code>==1 is for
 *     the first battle, where the trainer only has 1 Pokémon: the corresponding starter.
 *     <code>[x]</code>==1 also denotes that the trainer should not get any buffs
 *     (like <code>NOTSTRONG</code>).<br>
 *     <code>[y]</code> denotes the "variant": which starter the player chose.
 *     This corresponds to the indices in {@link RomHandler#getStarters()}.<br>
 *     E.g. "<code>CHAMPION</code>" is used by Red in HeartGold.
 *     </li>
 *     <li><b><code>FRIEND[x]-[y]</code></b><br>
 *     Like <code>RIVAL[x]-[y]</code> for trainers that carry the starter
 *     <i>weak</i> against the player's chosen starter.
 *     </li>
 * </ul>
 */
public class Trainer implements Comparable<Trainer> {
    // TODO: the tagging of Trainers is inconsistent between games. Some sub-issues:
    //  - Gen 1+FRLG uses the GIO tag for earlier Giovanni battles, seemingly identical to GYM8 in function.
    //  - Gen 1+2 never uses the -STRONG tag.
    //  - HGSS tags the (presumably) kimono girls as KIMONO[n], but this is never used.
    //  - BW1 uses UBER for the final N + Ghetsis battles.
    //  - XY does not use THEMED:[s]-STRONG for team admins and non-RIVAL/FRIEND-applicable rivals (Tierno & Trevor).
    //  - Gen 7 uses ELITE[n] for the kahunas.

    private int offset;
    private int index;
    private List<TrainerPokemon> pokemon = new ArrayList<>();
    private String tag;

    private String name; // TODO: make trainer name randomization use Trainer.name in all gens, really strange it doesn't
    private TrainerClass trainerclass;

    private MultiBattleStatus multiBattleStatus = MultiBattleStatus.NEVER;
    private boolean forcedDoubleBattle; // for doubleBattleMode
    private int forceStarterPosition = -1;
    // Certain trainers (e.g., trainers in the PWT in BW2) require unique held items for all of their Pokemon to prevent a game crash.
    private boolean requiresUniqueHeldItems;
    private BattleStyle currBattleStyle = new BattleStyle(); // Defaults to "Unchanged", but need this per-trainer for the Random style option.

    public Trainer() { }

    public Trainer(Trainer original) {
        this.offset = original.offset;
        this.index = original.index;
        this.pokemon = new ArrayList<>();
        for(TrainerPokemon originalTP : original.pokemon) {
            TrainerPokemon copiedTP = new TrainerPokemon(originalTP);
            this.pokemon.add(copiedTP);
        }
        this.tag = original.tag;
        this.name = original.name;
        this.trainerclass = original.trainerclass;
        this.multiBattleStatus = original.multiBattleStatus;
        this.forcedDoubleBattle = original.forcedDoubleBattle;
        this.forceStarterPosition = original.forceStarterPosition;
        this.requiresUniqueHeldItems = original.requiresUniqueHeldItems;
        this.currBattleStyle = original.currBattleStyle;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("[");

        sb.append(getFullDisplayName());
        sb.append(" ");
        if (trainerclass != null) {
            sb.append("(").append(trainerclass.getID()).append(") - ");
        }
        if (currBattleStyle.isBattleStyleChanged()) {
            sb.append("(").append(currBattleStyle.getStyle().toString()).append(") - ");
        }
        if (offset > 0) {
            sb.append(String.format("%x", offset));
        }
        sb.append(" => ");
        boolean first = true;
        for (TrainerPokemon p : pokemon) {
            if (!first) {
                sb.append(',');
            }
            sb.append(p.getSpecies().getName()).append(" Lv").append(p.getLevel());
            first = false;
        }
        sb.append(']');
        if (tag != null) {
            sb.append(" (").append(tag).append(")");
        }
        return sb.toString();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + index;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Trainer other = (Trainer) obj;
        return index == other.index;
    }

    @Override
    public int compareTo(Trainer o) {
        return index - o.index;
    }


    public int getOffset() {
        return offset;
    }

    public void setOffset(int offset) {
        this.offset = offset;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public List<TrainerPokemon> getPokemon() {
        return pokemon;
    }

    public void setPokemon(List<TrainerPokemon> pokemon) {
        this.pokemon = pokemon;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    /**
     * Returns the "poketype", used in most Pokemon games.
     * It is a representation of "do the mons have custom moves?" + "do the mons have held items"?
     */
    public int getPoketype() {
        int pokeType = 0;
        if (pokemonHaveCustomMoves()) {
            pokeType += 1;
        }
        if (pokemonHaveItems()) {
            pokeType += 2;
        }
        return pokeType;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public TrainerClass getTrainerclass() {
        return trainerclass;
    }

    public void setTrainerclass(TrainerClass trainerclass) {
        this.trainerclass = trainerclass;
    }

    public String getFullDisplayName() {
        if (trainerclass == null && name == null) {
            return "NAMELESS";
        }
        StringBuilder sb = new StringBuilder();
        if (trainerclass != null) {
            sb.append(trainerclass.getName());
        }
        if (trainerclass != null && name != null) {
            sb.append(" ");
        }
        if (name != null) {
            sb.append(name);
        }
        return sb.toString();
    }

    public MultiBattleStatus getMultiBattleStatus() {
        return multiBattleStatus;
    }

    public void setMultiBattleStatus(MultiBattleStatus multiBattleStatus) {
        this.multiBattleStatus = multiBattleStatus;
    }

    public boolean isForcedDoubleBattle() {
        return forcedDoubleBattle;
    }

    public void setForcedDoubleBattle(boolean forcedDoubleBattle) {
        this.forcedDoubleBattle = forcedDoubleBattle;
    }

    public int getForceStarterPosition() {
        return forceStarterPosition;
    }

    public void setForceStarterPosition(int forceStarterPosition) {
        this.forceStarterPosition = forceStarterPosition;
    }

    public boolean isRequiresUniqueHeldItems() {
        return requiresUniqueHeldItems;
    }

    public void setRequiresUniqueHeldItems(boolean requiresUniqueHeldItems) {
        this.requiresUniqueHeldItems = requiresUniqueHeldItems;
    }

    public BattleStyle getCurrBattleStyle() {
        return currBattleStyle;
    }

    public void setCurrBattleStyle(BattleStyle currBattleStyle) {
        this.currBattleStyle = currBattleStyle;
    }

    public boolean isBoss() {
        // Modified from FVX (11/10/2026): added the KAHUNA prefix so the Sun/Moon and Ultra Sun/Ultra Moon
        // island kahunas (retagged out of the shared ELITE family, see Gen7Constants) keep counting as boss
        // tier, same as before the retag.
        return tag != null && (tag.startsWith("ELITE") || tag.startsWith("CHAMPION")
                || tag.startsWith("UBER") || tag.startsWith("KAHUNA") || tag.endsWith("LEADER"));
    }

    private static final java.util.regex.Pattern GYM_LEADER_TAG = java.util.regex.Pattern.compile("^GYM\\d+-LEADER$");
    private static final java.util.regex.Pattern GIOVANNI_LEADER_TAG = java.util.regex.Pattern.compile("^GIO\\d*-LEADER$");

    /**
     * Added for YAO (11/10/2026): a narrower tier than {@link #isBoss()}, used by the mega finale feature's
     * "gym leaders + league" scope. True gym leaders only ({@code GYM<n>-LEADER}, plus Giovanni's
     * FireRed/LeafGreen Rocket-boss variant {@code GIO<n>-LEADER}, who is that game's final gym leader), the
     * real Elite Four (the {@code ELITE} tag, now that the Sun/Moon and Ultra Sun/Ultra Moon kahunas have
     * their own {@code KAHUNA} tag and are counted here as leader-equivalent, being the closest thing those
     * games have to gym leaders), the Champion ({@code CHAMPION} tag), and Uber-tier trainers ({@code UBER}:
     * Red in HeartGold/SoulSilver, Cynthia/Ghetsis/N in Black/White, the Rainbow Rocket bosses in Ultra
     * Sun/Ultra Moon). Deliberately excludes evil-team "boss" tags ({@code THEMED:<name>-LEADER} and HGSS's
     * bare {@code "LEADER"} on Archer), see {@link #isEvilTeamBoss()}.
     *
     * <p>Red/Blue/Yellow/Green, FireRed/LeafGreen and Sun/Moon/Ultra Sun/Ultra Moon give the Champion role to
     * a tagged Rival battle instead of a {@code CHAMPION} tag; that case needs game context this method does
     * not have and is handled by the caller (see {@code MegaFinaleApplier}).
     */
    public boolean isGymLeaderOrLeagueTier() {
        return isGymLeader() || isLeagueTier();
    }

    /**
     * Modified from FVX (12/10/2026): split out of {@link #isGymLeaderOrLeagueTier()} so features can pick gym
     * leaders and the league independently. True gym leaders only (see that method for the tags).
     */
    public boolean isGymLeader() {
        return tag != null && (GYM_LEADER_TAG.matcher(tag).matches() || GIOVANNI_LEADER_TAG.matcher(tag).matches());
    }

    /**
     * Modified from FVX (12/10/2026): the league half of {@link #isGymLeaderOrLeagueTier()}: Elite Four, kahunas,
     * Champion and Uber-tier trainers, by tag. Games that give the Champion role to a tagged Rival need game context,
     * see {@code com.uprfvx.random.yao.TrainerGroups}.
     */
    public boolean isLeagueTier() {
        return tag != null && (tag.startsWith("ELITE") || tag.startsWith("KAHUNA") || tag.startsWith("CHAMPION")
                || tag.startsWith("UBER"));
    }

    /** Modified from FVX (12/10/2026): the highest level among this trainer's Pokemon, 0 if it has none. */
    public int getHighestLevel() {
        int highest = 0;
        for (TrainerPokemon tp : pokemon) {
            highest = Math.max(highest, tp.getLevel());
        }
        return highest;
    }

    /**
     * Added for YAO (11/10/2026): true for evil-team leader trainers only (Team Rocket/Magma/Aqua/Galactic/
     * Plasma/Flare/Skull+Aether), tagged {@code THEMED:<name>-LEADER}, plus HGSS's Archer who is tagged bare
     * {@code "LEADER"} (not gym-related). These already count as boss tier under {@link #isBoss()} (its
     * {@code endsWith("LEADER")} check is ambiguous on purpose there) but are deliberately excluded from
     * {@link #isGymLeaderOrLeagueTier()}; used by the mega finale feature's independent "include evil-team
     * bosses" choice.
     */
    public boolean isEvilTeamBoss() {
        return tag != null && ("LEADER".equals(tag) || (tag.startsWith("THEMED:") && tag.endsWith("-LEADER")));
    }

    public boolean isImportant() {
        return tag != null && (tag.startsWith("RIVAL") || tag.startsWith("FRIEND") || tag.endsWith("STRONG"));
    }

    public boolean isRegular() {
        return !isBoss() && !isImportant();
    }

    public boolean isFirstRivalOrFriend() {
        return tag != null && (tag.startsWith("RIVAL1-") || tag.startsWith("FRIEND1-"));
    }

    public boolean shouldNotGetBuffs() {
        return isFirstRivalOrFriend() || (tag != null && tag.endsWith("NOTSTRONG"));
    }

    public boolean pokemonHaveItems() {
        return pokemon.stream().anyMatch(tp -> tp.getHeldItem() != null);
    }

    public boolean pokemonHaveCustomMoves() {
        for (TrainerPokemon tp : pokemon) {
            if (tp.isResetMoves()) continue;
            for (int moveID : tp.getMoves()) {
                if (moveID != MoveIDs.none) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean pokemonHaveDifferentLevels() {
        return pokemon.stream().map(TrainerPokemon::getLevel).distinct().count() > 1;
    }

    public boolean pokemonHaveUniqueHeldItems() {
        List<Item> heldItemsForThisTrainer = new ArrayList<>();
        for (TrainerPokemon poke : this.pokemon) {
            if (poke.getHeldItem() != null) {
                if (heldItemsForThisTrainer.contains(poke.getHeldItem())) {
                    return false;
                } else {
                    heldItemsForThisTrainer.add(poke.getHeldItem());
                }
            }
        }
        return true;
    }

    public enum MultiBattleStatus {
        NEVER, POTENTIAL, ALWAYS
    }
}
