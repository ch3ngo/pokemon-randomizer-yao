package com.uprfvx.random.yao.guide;

/*----------------------------------------------------------------------------*/
/*--  GuideOrder.java - game order numbers for the HTML guide.              --*/
/*--                                                                        --*/
/*--  Part of "Pokemon Randomizer YAO", a fork of                           --*/
/*--  "Universal Pokemon Randomizer FVX" (UPR-FVX), licensed under the GPL  --*/
/*--  version 3 or (at your option) any later version. See LICENSE.txt.     --*/
/*----------------------------------------------------------------------------*/

import com.uprfvx.romio.romhandlers.RomHandler;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Added for YAO (12/10/2026). Writes an {@code n} field (1 for the earliest, in game order) on the entries of the
 * wild, trainer, static, trade and shop sections, so the page can sort by "game order". There is no single field in
 * any ROM that says when the player meets something, so each section uses the best signal that exists, and the
 * limits are documented in {@code wiki/HTML-guide.md}:
 *
 * <ul>
 *   <li>wild places: the program's own story order list of locations for that game (already used by the randomizer);</li>
 *   <li>trainers: the highest level of the trainer's team in the unmodified game, with gym leaders forced into gym
 *       order and the league after them (the same boss tiers the randomizer uses); placeholder trainers whose team is
 *       a lone level 1 Pokemon go last;</li>
 *   <li>static and gift Pokemon: their level in the unmodified game, ties in the order the game stores them;</li>
 *   <li>trades: the order the game stores them (no level or place is known);</li>
 *   <li>shops: badge progressive shops first by badge count, then shops named after a town in that town's position of
 *       the story order list, the rest last in the order the game stores them.</li>
 * </ul>
 *
 * The numbers are computed from the original game's data and written to both data sets, so a row has the same
 * number before and after randomization.
 */
final class GuideOrder {

    private GuideOrder() {
    }

    @SuppressWarnings("unchecked")
    static void apply(Map<String, Object> original, Map<String, Object> randomized, RomHandler rom) {
        numberWild(original);
        numberWild(randomized);
        orderTrainers(original, randomized);
        orderStatics(original, randomized);
        for (Map<String, Object> d : List.of(original, randomized)) {
            Object trades = d.get("trades");
            if (trades instanceof List<?> list) {
                for (int i = 0; i < list.size(); i++) {
                    ((Map<String, Object>) list.get(i)).put("n", i + 1);
                }
            }
        }
        orderShops(original, rom);
        orderShops(randomized, rom);
    }

    @SuppressWarnings("unchecked")
    private static void numberWild(Map<String, Object> d) {
        if (d.get("wild") instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) {
                ((Map<String, Object>) list.get(i)).put("n", i + 1);
            }
        }
    }

    /** Fills n for every item of a list from a sort key, in a stable order. */
    @SuppressWarnings("unchecked")
    private static void assignRanks(List<?> list, double[] keys, int[] tie) {
        Integer[] idx = new Integer[list.size()];
        for (int i = 0; i < idx.length; i++) {
            idx[i] = i;
        }
        Arrays.sort(idx, Comparator.comparingDouble((Integer i) -> keys[i]).thenComparingInt(i -> tie[i]).thenComparingInt(i -> i));
        for (int r = 0; r < idx.length; r++) {
            ((Map<String, Object>) list.get(idx[r])).put("n", r + 1);
        }
    }

    private static int intOf(Object o) {
        return o instanceof Number n ? n.intValue() : 0;
    }

    // ---- trainers ----

    @SuppressWarnings("unchecked")
    private static double[] trainerKeys(List<?> trainers, List<?> levelSource) {
        int n = trainers.size();
        double[] keys = new double[n];
        for (int i = 0; i < n; i++) {
            Map<String, Object> t = (Map<String, Object>) levelSource.get(i);
            int lv = intOf(t.get("lv"));
            // Several games keep unused placeholder trainers ("no data", a lone level 1 Pokemon). They are not part of the
            // story, so they go after everyone else.
            keys[i] = lv <= 1 ? 1e9 : lv;
        }
        // Gym leaders in gym order, never earlier than the previous gym; then the league in its own order. Only the
        // first fight of each leader counts for this: later fights with the same leader (rematches, which have a much
        // higher level) keep their own level, so they do not drag the next gyms after them.
        double floor = 0;
        for (String tier : new String[]{"gym", "league"}) {
            Integer[] entries = indexesOf(levelSource, tier);
            Map<Integer, Integer> lowest = new HashMap<>();
            for (int i : entries) {
                int rank = intOf(((Map<String, Object>) levelSource.get(i)).get("rank"));
                lowest.merge(rank, intOf(((Map<String, Object>) levelSource.get(i)).get("lv")), Math::min);
            }
            List<Integer> mains = new ArrayList<>();
            for (int i : entries) {
                Map<String, Object> t = (Map<String, Object>) levelSource.get(i);
                if (intOf(t.get("lv")) <= lowest.get(intOf(t.get("rank")))) {
                    mains.add(i);
                }
            }
            mains.sort(Comparator.comparingInt((Integer i) -> intOf(((Map<String, Object>) levelSource.get(i)).get("rank")))
                    .thenComparingInt(i -> i));
            for (int i : mains) {
                floor = Math.max(keys[i], floor + 0.001);
                keys[i] = floor;
            }
        }
        return keys;
    }

    @SuppressWarnings("unchecked")
    private static Integer[] indexesOf(List<?> trainers, String tier) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < trainers.size(); i++) {
            if (tier.equals(((Map<String, Object>) trainers.get(i)).get("tier"))) {
                out.add(i);
            }
        }
        return out.toArray(new Integer[0]);
    }

    @SuppressWarnings("unchecked")
    private static void orderTrainers(Map<String, Object> original, Map<String, Object> randomized) {
        List<?> o = original.get("trainers") instanceof List<?> l ? l : null;
        List<?> r = randomized.get("trainers") instanceof List<?> l ? l : null;
        if (o != null && r != null && o.size() == r.size()) {
            double[] keys = trainerKeys(o, o);
            int[] tie = tierTie(o);
            assignRanks(o, keys, tie);
            assignRanks(r, keys, tie);
        } else {
            for (List<?> l : new List<?>[]{o, r}) {
                if (l != null) {
                    assignRanks(l, trainerKeys(l, l), tierTie(l));
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static int[] tierTie(List<?> trainers) {
        int[] tie = new int[trainers.size()];
        for (int i = 0; i < tie.length; i++) {
            Object tier = ((Map<String, Object>) trainers.get(i)).get("tier");
            // at an equal level, the gym leader comes after the trainers of that gym and the league last
            tie[i] = "gym".equals(tier) ? 1 : "league".equals(tier) ? 2 : 0;
        }
        return tie;
    }

    // ---- statics ----

    @SuppressWarnings("unchecked")
    private static void orderStatics(Map<String, Object> original, Map<String, Object> randomized) {
        List<?> o = original.get("statics") instanceof List<?> l ? l : null;
        List<?> r = randomized.get("statics") instanceof List<?> l ? l : null;
        List<?> source = o != null ? o : r;
        if (source == null) {
            return;
        }
        double[] keys = new double[source.size()];
        for (int i = 0; i < keys.length; i++) {
            keys[i] = intOf(((Map<String, Object>) source.get(i)).get("level"));
        }
        int[] tie = new int[keys.length];
        for (List<?> l : new List<?>[]{o, r}) {
            if (l != null && l.size() == keys.length) {
                assignRanks(l, keys, tie);
            } else if (l != null) {
                double[] own = new double[l.size()];
                for (int i = 0; i < own.length; i++) {
                    own[i] = intOf(((Map<String, Object>) l.get(i)).get("level"));
                }
                assignRanks(l, own, new int[own.length]);
            }
        }
    }

    // ---- shops ----

    private static final Pattern BADGES = Pattern.compile("(?i)(\\d+)\\s+badges?");

    @SuppressWarnings("unchecked")
    private static void orderShops(Map<String, Object> d, RomHandler rom) {
        if (!(d.get("shops") instanceof List<?> list)) {
            return;
        }
        List<String> order = GuideWild.traverseOrder(rom);
        double[] keys = new double[list.size()];
        for (int i = 0; i < keys.length; i++) {
            keys[i] = shopKey(String.valueOf(((Map<String, Object>) list.get(i)).get("name")), order, i);
        }
        assignRanks(list, keys, new int[keys.length]);
    }

    static double shopKey(String name, List<String> order, int index) {
        String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (lower.startsWith("primary") || lower.startsWith("progressive")) {
            Matcher m = BADGES.matcher(lower);
            int badges = m.find() ? Integer.parseInt(m.group(1)) : lower.contains("before") ? -1 : 0;
            return -1000 + badges * 10 + index * 0.001;
        }
        String shop = GuideNames.norm(name);
        int best = -1, bestLen = 0;
        for (int k = 0; k < order.size(); k++) {
            String tag = order.get(k);
            String stem = tag;
            int sp = tag.lastIndexOf(' ');
            if (sp > 0) {
                String last = tag.substring(sp + 1);
                if (last.equals("CITY") || last.equals("TOWN") || last.equals("VILLAGE")) {
                    stem = tag.substring(0, sp);
                }
            }
            String n = GuideNames.norm(stem);
            if (n.length() >= 4 && shop.startsWith(n) && n.length() > bestLen) {
                best = k;
                bestLen = n.length();
            }
        }
        return best >= 0 ? best + index * 0.001 : 100000 + index;
    }
}
