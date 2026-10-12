package com.uprfvx.random.yao;

/*----------------------------------------------------------------------------*/
/*--  EditArguments.java - command line forms of boosts and evolution edits.--*/
/*--                                                                        --*/
/*--  Part of "Pokemon Randomizer YAO", a fork of                           --*/
/*--  "Universal Pokemon Randomizer FVX" (UPR-FVX), licensed under the GPL  --*/
/*--  version 3 or (at your option) any later version. See LICENSE.txt.     --*/
/*----------------------------------------------------------------------------*/

import com.uprfvx.romio.gamedata.EvolutionType;
import com.uprfvx.romio.gamedata.Item;
import com.uprfvx.romio.gamedata.Move;
import com.uprfvx.romio.gamedata.Species;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Turns command line text into booster and evolution settings, resolving Pokemon, item and move names in the
 * ROM's own language (case, accents and punctuation ignored) or numeric ids. Never throws on bad input: whatever
 * cannot be understood is skipped and reported as a warning on the {@link YaoSettings}.
 *
 * <ul>
 *     <li>{@code --boost-floor <bst>}, for example {@code 300}</li>
 *     <li>{@code --evolution <from>:<to>:<method>[:<number>]}, for example {@code Eevee:Umbreon:stone:Moon Stone}
 *     or {@code Magikarp:Gyarados:level:20}</li>
 *     <li>{@code --remove-evolution <from>:<to>}</li>
 *     <li>{@code --evolution-level-scale <minLevel>:<percent>}, for example {@code 50:20}</li>
 * </ul>
 */
public final class EditArguments {

    private EditArguments() {
    }

    // ---- per-species stat edits ----

    /**
     * {@code --stat-edit <pokemon>:quick:<percent>} (for example {@code Magikarp:quick:25}) or
     * {@code --stat-edit <pokemon>:fine:<hp>:<atk>:<def>:<spa>:<spd>:<spe>} (for example
     * {@code Magikarp:fine:20:10:55:15:20:80}).
     */
    public static void applyStatEdits(YaoSettings yao, List<Species> species, List<String> arguments) {
        for (String raw : arguments) {
            String argument = raw == null ? "" : raw.strip();
            String[] parts = argument.split(":");
            if (parts.length < 3) {
                yao.addWarning("Stat edit \"" + argument + "\" needs <pokemon>:quick:<percent> or "
                        + "<pokemon>:fine:<hp>:<atk>:<def>:<spa>:<spd>:<spe>; skipped.");
                continue;
            }
            Species sp = resolveSpecies(parts[0], species, yao, argument);
            if (sp == null) {
                continue;
            }
            String mode = parts[1].strip().toLowerCase(Locale.ROOT);
            if (mode.equals("quick") && parts.length == 3) {
                Integer percent = parseInt(parts[2]);
                if (percent == null) {
                    yao.addWarning("Stat edit \"" + argument + "\" needs a percentage; skipped.");
                    continue;
                }
                yao.addQuickStatEdit(sp.getNumber(), percent);
            } else if (mode.equals("fine") && parts.length == 8) {
                Integer[] stats = new Integer[6];
                boolean ok = true;
                for (int i = 0; i < 6; i++) {
                    stats[i] = parseInt(parts[2 + i]);
                    ok &= stats[i] != null;
                }
                if (!ok) {
                    yao.addWarning("Stat edit \"" + argument + "\" needs six whole numbers (hp:atk:def:spa:spd:spe); "
                            + "skipped.");
                    continue;
                }
                yao.addFineTuneStatEdit(sp.getNumber(), stats[0], stats[1], stats[2], stats[3], stats[4], stats[5]);
            } else {
                yao.addWarning("Stat edit \"" + argument + "\" needs \"quick:<percent>\" or \"fine:<6 numbers>\"; "
                        + "skipped.");
            }
        }
    }

    public static void applyFloor(YaoSettings yao, String argument) {
        Integer floor = argument == null ? null : parseInt(argument);
        if (floor == null) {
            yao.addWarning("--boost-floor \"" + argument + "\" is not a number; ignored.");
            return;
        }
        yao.setBoostFloorBst(floor);
    }

    // ---- evolutions ----

    public static void applyEdits(YaoSettings yao, List<Species> species, List<Item> items, List<Move> moves,
                                  List<String> arguments) {
        for (String raw : arguments) {
            String argument = raw == null ? "" : raw.strip();
            String[] parts = argument.split(":", 4);
            if (parts.length < 3) {
                yao.addWarning("Evolution \"" + argument + "\" needs <from>:<to>:<method>; skipped.");
                continue;
            }
            Species from = resolveSpecies(parts[0], species, yao, argument);
            Species to = resolveSpecies(parts[1], species, yao, argument);
            EvolutionType type = parseMethod(parts[2]);
            if (from == null || to == null) {
                continue;
            }
            if (type == null) {
                yao.addWarning("Unknown evolution method \"" + parts[2].strip() + "\" in \"" + argument + "\"; skipped.");
                continue;
            }
            String parameter = parts.length == 4 ? parts[3].strip() : "";
            Integer extra = resolveParameter(type, parameter, species, items, moves, yao, argument);
            if (extra == null) {
                continue;
            }
            yao.addEvolutionEdit(from.getNumber(), to.getNumber(), type, extra);
        }
    }

    public static void applyRemovals(YaoSettings yao, List<Species> species, List<String> arguments) {
        for (String raw : arguments) {
            String argument = raw == null ? "" : raw.strip();
            String[] parts = argument.split(":", 2);
            if (parts.length < 2) {
                yao.addWarning("Evolution removal \"" + argument + "\" needs <from>:<to>; skipped.");
                continue;
            }
            Species from = resolveSpecies(parts[0], species, yao, argument);
            Species to = resolveSpecies(parts[1], species, yao, argument);
            if (from != null && to != null) {
                yao.addEvolutionRemoval(from.getNumber(), to.getNumber());
            }
        }
    }

    public static void applyScale(YaoSettings yao, String argument) {
        String[] parts = argument == null ? new String[0] : argument.split(":");
        Integer level = parts.length == 2 ? parseInt(parts[0]) : null;
        Integer percent = parts.length == 2 ? parseInt(parts[1]) : null;
        if (level == null || percent == null) {
            yao.addWarning("--evolution-level-scale \"" + argument + "\" needs <minLevel>:<percent>; ignored.");
            return;
        }
        yao.setEvolutionLevelScale(level, percent);
    }

    // ---- parsing ----

    static EvolutionType parseMethod(String text) {
        String key = text == null ? "" : text.strip().toUpperCase(Locale.ROOT).replaceAll("[\\s-]+", "_");
        switch (key.replace("_", "")) {
            case "LEVEL", "LEVELUP", "LV" -> {
                return EvolutionType.LEVEL;
            }
            case "STONE", "ITEM", "STONEITEM" -> {
                return EvolutionType.STONE;
            }
            case "FRIENDSHIP", "HAPPY" -> {
                return EvolutionType.HAPPINESS;
            }
            default -> {
                // fall through to the enum names
            }
        }
        try {
            EvolutionType type = EvolutionType.valueOf(key);
            return (type == EvolutionType.NONE || type == EvolutionType.ITEM) ? null : type;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Integer resolveParameter(EvolutionType type, String text, List<Species> species, List<Item> items,
                                            List<Move> moves, YaoSettings yao, String argument) {
        if (type.usesLevelThreshold()) {
            Integer level = parseInt(text);
            if (level == null) {
                yao.addWarning("Evolution \"" + argument + "\" needs a level; skipped.");
            }
            return level;
        }
        if (type.usesItem()) {
            Item item = resolveItem(text, items);
            if (item == null) {
                yao.addWarning("Evolution \"" + argument + "\" needs an item that exists in this game; skipped.");
                return null;
            }
            return item.getId();
        }
        if (type.usesMove()) {
            Move move = resolveMove(text, moves);
            if (move == null) {
                yao.addWarning("Evolution \"" + argument + "\" needs a move that exists in this game; skipped.");
                return null;
            }
            return move.number;
        }
        if (type.usesSpecies()) {
            Species sp = resolveSpecies(text, species, yao, argument);
            return sp == null ? null : sp.getNumber();
        }
        return 0;
    }

    private static Species resolveSpecies(String text, List<Species> species, YaoSettings yao, String argument) {
        String t = text == null ? "" : text.strip();
        if (t.isEmpty()) {
            yao.addWarning("\"" + argument + "\" has an empty Pokemon; skipped.");
            return null;
        }
        Integer id = parseInt(t);
        List<Species> exact = new ArrayList<>();
        List<Species> partial = new ArrayList<>();
        String wanted = normalize(t);
        for (Species sp : species) {
            if (sp == null) {
                continue;
            }
            if (id != null && sp.getNumber() == id) {
                if (sp.isBaseForme() || exact.isEmpty()) {
                    exact.add(sp);
                }
                continue;
            }
            String name = normalize(sp.getFullName());
            if (id == null && name.equals(wanted)) {
                exact.add(sp);
            } else if (id == null && !wanted.isEmpty() && name.contains(wanted)) {
                partial.add(sp);
            }
        }
        if (exact.size() >= 1) {
            if (exact.size() > 1 && id == null) {
                yao.addWarning("\"" + t + "\" matches more than one Pokemon; use the number. Skipped.");
                return null;
            }
            return exact.get(0);
        }
        if (id == null && partial.size() == 1) {
            return partial.get(0);
        }
        yao.addWarning(partial.size() > 1
                ? "\"" + t + "\" matches more than one Pokemon; use the number. Skipped."
                : "No Pokemon called \"" + t + "\" in this game; skipped.");
        return null;
    }

    private static Item resolveItem(String text, List<Item> items) {
        Integer id = parseInt(text);
        if (id != null) {
            return id > 0 && id < items.size() ? items.get(id) : null;
        }
        String wanted = normalize(text);
        Item found = null;
        for (Item item : items) {
            if (item != null && item.getName() != null && normalize(item.getName()).equals(wanted)) {
                if (found != null) {
                    return null;
                }
                found = item;
            }
        }
        return found;
    }

    private static Move resolveMove(String text, List<Move> moves) {
        Integer id = parseInt(text);
        if (id != null) {
            return id > 0 && id < moves.size() ? moves.get(id) : null;
        }
        String wanted = normalize(text);
        Move found = null;
        for (Move move : moves) {
            if (move != null && move.name != null && normalize(move.name).equals(wanted)) {
                if (found != null) {
                    return null;
                }
                found = move;
            }
        }
        return found;
    }

    static String normalize(String name) {
        String decomposed = Normalizer.normalize(name, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static Integer parseInt(String text) {
        try {
            return Integer.parseInt(text.strip());
        } catch (NumberFormatException | NullPointerException e) {
            return null;
        }
    }

    /** Unused helper kept for symmetry with the map based lookups elsewhere. */
    static boolean isEmpty(Map<?, ?> map) {
        return map == null || map.isEmpty();
    }
}
