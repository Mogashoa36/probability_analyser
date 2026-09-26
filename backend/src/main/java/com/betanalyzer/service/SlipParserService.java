package com.betanalyzer.service;

import com.betanalyzer.entity.SlipSelection.Market;
import lombok.Getter;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Turns pasted, free-text betting slips into structured selections.
 *
 * <h2>One selection per line</h2>
 * <pre>
 *   "Kaizer Chiefs vs Orlando Pirates - Home Win @ 2.10"
 *   "Man City v Arsenal, Over 2.5, 1.65"
 *   "Real Madrid,Barcelona,DRAW,3.40"                (CSV: home,away,market,odds)
 *   "Liverpool vs Chelsea"                          (fixture only, for the Recommendations page)
 * </pre>
 *
 * <h2>Bookmaker betslip "cards", copied straight out of the site</h2>
 * Bookmakers don't print one line per leg - they print a small block per leg,
 * and the order of the lines inside that block differs per bookmaker. Both of
 * the shapes below are understood, with or without blank lines between legs.
 *
 * <p>Hollywoodbets prints market-and-outcome, then price, then the fixture:
 * <pre>
 *   Full Time - SLOVENIA                 market, then the outcome that was picked
 *   1.65                                 price
 *   x                                    divider glyph, ignored
 *   SLOVENIA vs SCOTLAND                 fixture
 *   26 September 2026 | 15:00            kick-off, ignored
 * </pre>
 *
 * <p>Betway prints the outcome, then price, then the market, then the fixture
 * (using " - " rather than "vs"), and frequently offers <em>combined</em>
 * markets where each part carries its own outcome:
 * <pre>
 *   Cambridge United &amp; Yes            outcome
 *   3.95                                 price
 *   1X2 &amp; Both Teams To Score          combined market
 *   Cambridge United - AFC Wimbledon      fixture
 *   Today 16:00                          kick-off, ignored
 * </pre>
 *
 * <p>Cards are anchored on the fixture line: everything printed since the
 * previous leg belongs to this one. Combined markets are priced as the product
 * of their parts, the same independence assumption the combined slip
 * probability already makes.
 *
 * <p>Lines starting with '#' or '//' are treated as comments and skipped.
 */
@Service
public class SlipParserService {

    /** Assumed goal line when a goals market is quoted without one (a bare "Over"). */
    private static final double DEFAULT_GOAL_LINE = 2.5;

    // ------------------------------------------------------------------ patterns

    /** A line holding nothing but a number, e.g. the "1.65" on a card. */
    private static final Pattern PLAIN_NUMBER = Pattern.compile("^\\d+(?:[.,]\\d+)?$");

    /** "Today 16:00" / "18:00" / "2026-09-26T18:00". */
    private static final Pattern TIME_TOKEN = Pattern.compile("\\b\\d{1,2}:\\d{2}\\b");
    private static final Pattern TEXTUAL_DATE = Pattern.compile(
            "\\b\\d{1,2}\\s+(?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\\s+\\d{4}\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern NUMERIC_DATE = Pattern.compile("\\b\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}\\b");

    /** "A vs B" / "A v. B", optionally followed by ", market" or " - market @ odds". */
    private static final Pattern VS_FIXTURE = Pattern.compile(
            "^\\s*(.+?)\\s+(?:vs\\.?|v\\.?)\\s+(.+?)\\s*(?:[-,]\\s*(.+))?$",
            Pattern.CASE_INSENSITIVE);

    /** "A - B" (Betway) - much weaker evidence than "vs", see splitFixture. */
    private static final Pattern DASH_FIXTURE = Pattern.compile("\\s[-\\u2013\\u2014]\\s");

    /** Splits market text into parts: "1X2 & BTTS", "Full Time - SLOVENIA". */
    private static final Pattern SEGMENT_SPLIT = Pattern.compile(
            "\\s*&\\s*|\\s*\\+\\s*|\\s*,\\s*|\\s*\\|\\s*|\\s*@\\s*|\\s*:\\s*|\\s*[-\\u2013\\u2014]\\s+");

    /** "over 2.5", "under 3.5", "o2.5", "u1.5". */
    private static final Pattern GOAL_LINE = Pattern.compile(
            "\\b(over|under|o|u)\\s*/?\\s*(\\d+(?:\\.\\d+)?)\\b", Pattern.CASE_INSENSITIVE);

    /** "over_2_5" - the enum spelling, where the decimal point is an underscore. */
    private static final Pattern ENUM_GOAL_LINE = Pattern.compile(
            "\\b([ou])\\s*_\\s*(\\d+)_(\\d)\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern ANY_NUMBER = Pattern.compile("(\\d+(?:\\.\\d+)?)");

    /** Club-type tokens that aren't part of a team's identity: "FC", "AFC", "CF". */
    private static final Pattern CLUB_TOKEN = Pattern.compile(
            "\\b(?:fc|afc|cf|sc|ac|as|ss|csc|sv|bsc|rcs|usf|ud|cd|if|ik)\\b", Pattern.CASE_INSENSITIVE);

    /** Pure decoration between legs; some betslips print a bare "x". */
    private static final Set<String> DIVIDER_LINES = Set.of(
            "x", "×", "✕", "✖", "-", "–", "—",
            "•", "·", "*", "+", "=", "|", "/", "\\", "_", "~", "^", ".");

    private static final List<String> RESULT_PHRASES = List.of(
            "full time", "match result", "moneyline", "3 way", "90 minutes", "90 min", "result");
    private static final Set<String> RESULT_LABELS = Set.of("1x2", "ft", "x2", "match", "match odds");
    private static final List<String> BTTS_PHRASES =
            List.of("both teams to score", "both to score", "both teams scoring");

    private static final List<String> MARKET_PHRASES = List.of(
            "full time", "half time", "first half", "second half", "match result", "moneyline", "3 way",
            "1x2", "both teams to score", "both to score", "btts", "correct score", "goals", "total goals",
            "over", "under", "over under", "handicap", "asian handicap", "home win", "away win",
            "home", "away", "draw", "yes", "no");

    // -------------------------------------------------------------------- records

    /** One pricable piece of a selection, e.g. "home win" or "over 2.5 goals". */
    public record MarketLeg(Market market, Double goalLine) {
        public static MarketLeg of(Market market) {
            return new MarketLeg(market, defaultGoalLine(market));
        }
    }

    /** What kind of market a piece of text describes. */
    private record ComponentSpec(Kind kind, Double goalLine) {
        enum Kind { RESULT, BTTS, GOALS }

        static ComponentSpec result() { return new ComponentSpec(Kind.RESULT, null); }
        static ComponentSpec btts() { return new ComponentSpec(Kind.BTTS, null); }
        static ComponentSpec goals(Double line) { return new ComponentSpec(Kind.GOALS, line); }
    }

    /** A market label plus, when that text carries one, the outcome picked. */
    private record Segment(ComponentSpec spec, String pick) {}

    /** A fixture line split into its two sides and whatever followed it. */
    private record FixtureParts(String home, String away, String trailing) {}

    private enum LineType { PRICE, KICKOFF, FIXTURE, CSV, LABEL }

    // ------------------------------------------------------------------ the model

    /**
     * A parsed leg. {@code legs} holds one entry for a simple market and two (or
     * more) for a combined market such as "1X2 &amp; Both Teams To Score"; it is
     * empty when the input named no market at all, which callers read as "no
     * market supplied" rather than as an error.
     */
    @Getter
    public static class ParsedSelection {
        private final String homeTeam;
        private final String awayTeam;
        private final List<MarketLeg> legs;
        private final Double odds; // null when no price was printed
        private final String warning; // null when the line parsed cleanly

        public ParsedSelection(String homeTeam, String awayTeam, Market market, Double odds, String warning) {
            this(homeTeam, awayTeam,
                    market == null || market == Market.UNKNOWN
                            ? Collections.emptyList() : List.of(MarketLeg.of(market)),
                    odds, warning);
        }

        public ParsedSelection(String homeTeam, String awayTeam, List<MarketLeg> legs,
                               Double odds, String warning) {
            this.homeTeam = homeTeam;
            this.awayTeam = awayTeam;
            this.legs = legs == null ? Collections.emptyList() : List.copyOf(legs);
            this.odds = odds;
            this.warning = warning;
        }

        /** Primary market, for callers that only deal in a single market. */
        public Market getMarket() {
            return legs.isEmpty() ? Market.UNKNOWN : legs.get(0).market();
        }

        /** Human-facing market, e.g. "HOME_WIN", "OVER_3_5" or "HOME_WIN & BTTS_YES". */
        public String marketLabel() {
            if (legs.isEmpty()) return Market.UNKNOWN.name();
            return legs.stream().map(SlipParserService::labelFor).collect(Collectors.joining(" & "));
        }
    }

    // ------------------------------------------------------------------ entry point

    public List<ParsedSelection> parseSlipText(String rawText) {
        List<ParsedSelection> results = new ArrayList<>();
        if (rawText == null || rawText.isBlank()) return results;

        // Everything seen since the last fixture line: this leg's price and labels.
        List<String> pending = new ArrayList<>();

        for (String rawLine : rawText.split("\\r?\\n")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) continue;
            if (isDivider(line)) continue;

            switch (classify(line)) {
                // A kick-off stamp belongs to the leg just finished and says
                // nothing about the selection itself, so it is dropped.
                case KICKOFF -> {
                }
                case PRICE, LABEL -> pending.add(line);
                case FIXTURE -> {
                    results.add(parseCard(pending, line));
                    pending.clear();
                }
                case CSV -> {
                    // A CSV line is self-describing, so it can't absorb the pending
                    // lines; report those rather than swallowing them silently.
                    flushOrphans(results, pending);
                    results.add(parseCsv(line));
                }
            }
        }

        // A price or market with no fixture behind it can never be priced. Saying
        // so beats silently dropping it and returning a suspiciously short slip.
        flushOrphans(results, pending);
        return results;
    }

    // ------------------------------------------------------------------ a betslip card

    private ParsedSelection parseCard(List<String> pending, String fixtureLine) {
        FixtureParts fixture = splitFixture(fixtureLine);

        Double price = null;
        List<String> marketTexts = new ArrayList<>();
        for (String p : pending) {
            Double p1 = asPrice(p);
            if (p1 != null) {
                if (price == null) price = p1;
            } else {
                marketTexts.add(p);
            }
        }
        if (fixture.trailing() != null) marketTexts.add(fixture.trailing());

        // A bare number sitting inside the market text is the price ("Home Win @ 2.10"),
        // never part of the market. Reading it any other way turns "Over 2.5" into
        // odds of 2.5, because the goal line is a decimal too.
        List<Segment> segments = new ArrayList<>();
        for (String text : marketTexts) {
            for (String part : SEGMENT_SPLIT.split(text)) {
                String p = part.trim();
                if (p.isEmpty()) continue;
                Double p1 = asPrice(p);
                if (p1 != null) {
                    if (price == null) price = p1;
                } else {
                    segments.add(classifySegment(p));
                }
            }
        }

        List<MarketLeg> legs = resolveLegs(segments, fixture.home(), fixture.away());
        if (legs == null) {
            return new ParsedSelection(fixture.home(), fixture.away(), Collections.emptyList(), price,
                    unsupportedMarket(String.join(" / ", marketTexts)));
        }
        return new ParsedSelection(fixture.home(), fixture.away(), legs, price, null);
    }

    /** Strict CSV: home,away[,market[,odds]]. */
    private ParsedSelection parseCsv(String line) {
        String[] parts = line.split(",");
        String home = parts[0].trim();
        String away = parts[1].trim();

        String marketText = parts.length >= 3 ? parts[2].trim() : null;
        Double odds = parts.length >= 4 ? asPrice(parts[3].trim()) : null;

        // "Real Madrid,Barcelona,1.85" - a number in the market slot is a price.
        if (marketText != null && !marketText.isEmpty()) {
            Double p = asPrice(marketText);
            if (p != null) {
                if (odds == null) odds = p;
                marketText = null;
            }
        }

        List<MarketLeg> legs = marketText == null
                ? Collections.emptyList()
                : resolveLegs(List.of(classifySegment(marketText)), home, away);

        if (legs == null) {
            return new ParsedSelection(home, away, Collections.emptyList(), odds, unsupportedMarket(marketText));
        }
        return new ParsedSelection(home, away, legs, odds, null);
    }

    private String unsupportedMarket(String text) {
        return "Could not model the market on \"" + text + "\" - this engine prices 1X2"
                + " (home/draw/away), Over/Under and Both Teams To Score legs.";
    }

    private void flushOrphans(List<ParsedSelection> results, List<String> pending) {
        for (String orphan : pending) {
            results.add(new ParsedSelection("Unknown", "Unknown", Collections.emptyList(), null,
                    "Found \"" + orphan + "\" with no \"Home vs Away\" fixture line to attach it to"
                            + " - check the paste includes a fixture line for every leg."));
        }
        pending.clear();
    }

    // ------------------------------------------------------------------ grouping

    private LineType classify(String line) {
        if (isPrice(line)) return LineType.PRICE;
        if (isKickOffStamp(line)) return LineType.KICKOFF;
        if (splitFixture(line) != null) return LineType.FIXTURE;
        if (looksLikeCsv(line)) return LineType.CSV;
        return LineType.LABEL;
    }

    private boolean isDivider(String line) {
        return DIVIDER_LINES.contains(line.toLowerCase(Locale.ROOT))
                || line.replaceAll("[^\\p{L}\\p{N}]", "").isEmpty();
    }

    private boolean isPrice(String line) {
        Double value = asPrice(line);
        // Decimal odds are always above 1, which also stops the 1X2 shorthands
        // "1" and "2" from being mistaken for a price.
        return value != null && value > 1.0;
    }

    private Double asPrice(String text) {
        String t = text == null ? "" : text.trim();
        if (!PLAIN_NUMBER.matcher(t).matches()) return null;
        try {
            return Double.parseDouble(t.replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean isKickOffStamp(String line) {
        return line.contains("|")
                || TIME_TOKEN.matcher(line).find()
                || TEXTUAL_DATE.matcher(line).find()
                || NUMERIC_DATE.matcher(line).find();
    }

    private boolean looksLikeCsv(String line) {
        if (!line.contains(",")) return false;
        String[] parts = line.split(",");
        if (parts.length < 2 || parts.length > 4) return false;
        return isTeamName(parts[0].trim()) && isTeamName(parts[1].trim());
    }

    // ------------------------------------------------------------------ fixtures

    /**
     * Splits a fixture line into home, away and anything trailing it, or returns
     * null if the line isn't a fixture at all.
     *
     * <p>"vs" is strong evidence on its own. A bare " - " is not: it is also how
     * books print a market ("Full Time - SLOVENIA"), so in the two-part form both
     * sides must look like clubs and neither may read as a market phrase. The
     * three-part form ("Manchester City - Chelsea - Home Win @ 2.10") can safely
     * let its last part be a market, which is how a dash fixture carries one.
     */
    private FixtureParts splitFixture(String line) {
        Matcher vs = VS_FIXTURE.matcher(line);
        if (vs.matches()) {
            String home = clean(vs.group(1));
            String away = clean(vs.group(2));
            if (isTeamName(home) && isTeamName(away)) {
                return new FixtureParts(home, away, cleanOrNull(vs.group(3)));
            }
        }

        String[] parts = DASH_FIXTURE.split(line);
        if (parts.length >= 2) {
            String home = clean(String.join(" - ", Arrays.copyOfRange(parts, 0, parts.length - 2)));
            String away = clean(parts[parts.length - 2]);
            String trailing = parts.length >= 3 ? cleanOrNull(parts[parts.length - 1]) : null;
            if (isTeamName(home) && isTeamName(away) && !isMarketPhrase(away) && !isMarketPhrase(trailing)) {
                return new FixtureParts(home, away, trailing);
            }
        }
        return null;
    }

    private boolean isTeamName(String value) {
        return value != null && value.length() >= 2 && value.matches(".*\\p{L}.*")
                && !PLAIN_NUMBER.matcher(value).matches();
    }

    /**
     * True when a piece of a dash-separated line is market vocabulary rather than
     * a club name. Matching is on whole words, so "Sunderland" and "Manchester
     * United" are never mistaken for an "Under" market.
     */
    private boolean isMarketPhrase(String value) {
        if (value == null) return false;
        String t = normalize(value);
        if (t.isEmpty()) return false;
        for (String phrase : MARKET_PHRASES) {
            if (isWord(t, phrase)) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ markets

    /**
     * Pairs each market component with the outcome that was picked for it.
     *
     * @return the priced legs, an empty list when no market was named at all, or
     *         null when part of the selection can't be modelled - quietly dropping
     *         that part would overstate the leg rather than understate it.
     */
    private List<MarketLeg> resolveLegs(List<Segment> segments, String home, String away) {
        // "Over 2.5 - Over" is one market stated twice: a bare direction word
        // alongside a goals line refines it rather than adding a second leg.
        boolean goalsLineNamed = segments.stream()
                .anyMatch(s -> s.spec() != null && s.spec().kind() == ComponentSpec.Kind.GOALS
                        && s.spec().goalLine() != null);

        List<ComponentSpec> specs = new ArrayList<>();
        List<String> picks = new ArrayList<>();
        for (Segment s : segments) {
            boolean bareDirection = s.spec() != null && s.spec().kind() == ComponentSpec.Kind.GOALS
                    && s.spec().goalLine() == null;
            if (s.spec() != null && !(goalsLineNamed && bareDirection)) specs.add(s.spec());
            if (s.pick() != null && !s.pick().isBlank()) picks.add(s.pick());
        }
        if (specs.isEmpty()) specs = inferSpecs(picks);
        if (specs.isEmpty()) return Collections.emptyList();

        // Books print the picks in the same order as the market parts ("Cambridge
        // United &amp; Yes" against "1X2 &amp; BTTS"), so pair them up positionally;
        // otherwise hand out whatever picks remain, in order.
        List<String> assigned = new ArrayList<>(Collections.nCopies(specs.size(), (String) null));
        if (picks.size() == specs.size()) {
            for (int i = 0; i < specs.size(); i++) assigned.set(i, picks.get(i));
        } else {
            for (int i = 0, p = 0; i < specs.size() && p < picks.size(); i++) assigned.set(i, picks.get(p++));
        }

        List<MarketLeg> legs = new ArrayList<>(specs.size());
        for (int i = 0; i < specs.size(); i++) {
            MarketLeg leg = resolvePick(specs.get(i), assigned.get(i), home, away);
            if (leg == null) return null;
            legs.add(leg);
        }
        return legs;
    }

    /** A bare outcome still implies a market: a club name implies 1X2, "Yes" implies BTTS. */
    private List<ComponentSpec> inferSpecs(List<String> picks) {
        List<ComponentSpec> specs = new ArrayList<>(picks.size());
        for (String pick : picks) {
            String t = normalize(pick);
            if (isOverUnder(t)) specs.add(ComponentSpec.goals(null));
            else if (isYesNo(t)) specs.add(ComponentSpec.btts());
            else specs.add(ComponentSpec.result());
        }
        return specs;
    }

    private MarketLeg resolvePick(ComponentSpec spec, String pick, String home, String away) {
        String p = normalize(pick);
        return switch (spec.kind()) {
            case RESULT -> resolveResult(p, home, away);
            // A bare "Both Teams To Score" with no yes/no printed reads as a yes -
            // the long-standing shorthand, and the common case.
            case BTTS -> MarketLeg.of(
                    (isWord(p, "no") || p.equals("n")) ? Market.BTTS_NO : Market.BTTS_YES);
            case GOALS -> {
                double line = spec.goalLine() != null ? spec.goalLine() : DEFAULT_GOAL_LINE;
                if (isAsianLine(line)) yield null;
                boolean under = isWord(p, "under") || p.equals("u") || p.startsWith("u");
                yield new MarketLeg(under ? Market.UNDER_2_5 : Market.OVER_2_5, line);
            }
        };
    }

    private MarketLeg resolveResult(String pick, String home, String away) {
        if (pick.isEmpty()) return null; // "1X2" on its own names no outcome
        if (isWord(pick, "draw") || pick.equals("x")) return MarketLeg.of(Market.DRAW);
        if (isWord(pick, "home") || pick.equals("1") || pick.equals("h")) return MarketLeg.of(Market.HOME_WIN);
        if (isWord(pick, "away") || pick.equals("2") || pick.equals("a")) return MarketLeg.of(Market.AWAY_WIN);
        if (sameTeam(pick, home)) return MarketLeg.of(Market.HOME_WIN);
        if (sameTeam(pick, away)) return MarketLeg.of(Market.AWAY_WIN);
        return null;
    }

    /** x.25 / x.75 lines stake half the stake each way and aren't modelled here. */
    private boolean isAsianLine(double line) {
        double fraction = line - Math.floor(line);
        return Math.abs(fraction - 0.25) < 1e-9 || Math.abs(fraction - 0.75) < 1e-9;
    }

    // ------------------------------------------------------------------ segments

    private Segment classifySegment(String raw) {
        // The enum spelling is checked first so a JSON/CSV market of "OVER_2_5" or
        // "BTTS_NO" round-trips exactly - and because "OVER_2_5" also encodes the
        // goal line, which the text rules below would otherwise read as 2.0.
        Market direct = marketFromName(raw.trim().toUpperCase(Locale.ROOT).replace(' ', '_'));
        if (direct != null && direct != Market.UNKNOWN) {
            return new Segment(specFor(direct), pickFor(direct));
        }

        String t = normalize(raw);
        if (t.isEmpty()) return new Segment(null, null);

        // 1X2 / match result: "1X2", "Full Time", "Match Result", "Moneyline".
        if (RESULT_LABELS.contains(t) || RESULT_PHRASES.stream().anyMatch(t::contains)) {
            return new Segment(ComponentSpec.result(), resultPick(t));
        }

        // "Both Teams To Score", sometimes with its outcome already attached.
        if (BTTS_PHRASES.stream().anyMatch(t::contains) || isWord(t, "btts") || isWord(t, "gg")) {
            return new Segment(ComponentSpec.btts(), yesNoPick(t));
        }

        Segment goals = classifyGoals(t);
        if (goals != null) return goals;

        // Bare outcomes: "Home Win", "Draw", "Yes".
        if (isWord(t, "draw") || t.equals("x") || isWord(t, "no draw")) {
            return new Segment(ComponentSpec.result(), "draw");
        }
        if (isWord(t, "home win") || isWord(t, "home") || t.equals("1") || t.equals("h")) {
            return new Segment(ComponentSpec.result(), "home");
        }
        if (isWord(t, "away win") || isWord(t, "away") || t.equals("2") || t.equals("a")) {
            return new Segment(ComponentSpec.result(), "away");
        }
        if (isYesNo(t)) return new Segment(null, t);

        // Anything else is an outcome we have no name for - in practice the club
        // the bettor backed, where a "vs" book would print "Home Win".
        return new Segment(null, raw);
    }

    private Segment classifyGoals(String t) {
        boolean bothWays = t.contains("over/under") || t.contains("over under") || t.contains("o/u");
        if (bothWays) {
            // "Goals Over/Under 2.5" - the side is chosen by a separate pick.
            Matcher any = ANY_NUMBER.matcher(t);
            return new Segment(ComponentSpec.goals(any.find() ? Double.parseDouble(any.group(1)) : null), null);
        }

        Matcher enumLine = ENUM_GOAL_LINE.matcher(t);
        if (enumLine.find()) {
            double line = Double.parseDouble(enumLine.group(2) + "." + enumLine.group(3));
            boolean under = enumLine.group(1).equalsIgnoreCase("u");
            return new Segment(ComponentSpec.goals(line), under ? "under" : "over");
        }

        Matcher line = GOAL_LINE.matcher(t);
        if (line.find()) {
            String word = line.group(1).toLowerCase(Locale.ROOT);
            boolean under = word.equals("under") || word.equals("u");
            return new Segment(ComponentSpec.goals(Double.parseDouble(line.group(2))), under ? "under" : "over");
        }

        if (isWord(t, "over")) return new Segment(ComponentSpec.goals(null), "over");
        if (isWord(t, "under")) return new Segment(ComponentSpec.goals(null), "under");
        if (isWord(t, "goals") || isWord(t, "total goals")) return new Segment(ComponentSpec.goals(null), null);
        return null;
    }

    private String resultPick(String t) {
        if (isWord(t, "draw")) return "draw";
        if (isWord(t, "home win") || isWord(t, "home")) return "home";
        if (isWord(t, "away win") || isWord(t, "away")) return "away";
        return null;
    }

    private String yesNoPick(String t) {
        if (isWord(t, "no") || isWord(t, "n")) return "no";
        if (isWord(t, "yes") || isWord(t, "y")) return "yes";
        return null;
    }

    private boolean isYesNo(String t) {
        return t.equals("yes") || t.equals("no") || t.equals("y") || t.equals("n");
    }

    private boolean isOverUnder(String t) {
        return isWord(t, "over") || isWord(t, "under") || t.equals("o") || t.equals("u")
                || t.contains("over/under") || t.contains("over under");
    }

    private static ComponentSpec specFor(Market market) {
        return switch (market) {
            case BTTS_YES, BTTS_NO -> ComponentSpec.btts();
            case OVER_2_5, UNDER_2_5 -> ComponentSpec.goals(defaultGoalLine(market));
            default -> ComponentSpec.result();
        };
    }

    private static String pickFor(Market market) {
        return switch (market) {
            case HOME_WIN -> "home";
            case AWAY_WIN -> "away";
            case DRAW -> "draw";
            case OVER_2_5 -> "over";
            case UNDER_2_5 -> "under";
            case BTTS_YES -> "yes";
            case BTTS_NO -> "no";
            default -> null;
        };
    }

    private static Market marketFromName(String name) {
        try {
            return Market.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ text utils

    private static Double defaultGoalLine(Market market) {
        return market == Market.OVER_2_5 || market == Market.UNDER_2_5 ? DEFAULT_GOAL_LINE : null;
    }

    private static String labelFor(MarketLeg leg) {
        boolean goals = leg.market() == Market.OVER_2_5 || leg.market() == Market.UNDER_2_5;
        if (goals && leg.goalLine() != null) {
            String line = String.valueOf(leg.goalLine());
            if (line.endsWith(".0")) line = line.substring(0, line.length() - 2);
            return (leg.market() == Market.OVER_2_5 ? "OVER_" : "UNDER_") + line.replace('.', '_');
        }
        return leg.market().name();
    }

    /** Lower-cases, turns punctuation into spaces, keeps "." and "/" intact. */
    private String normalize(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9./ ]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /** Whole-word containment, so "under" never matches inside "Sunderland". */
    private boolean isWord(String normalized, String word) {
        return normalized.equals(word)
                || normalized.startsWith(word + " ")
                || normalized.endsWith(" " + word)
                || normalized.contains(" " + word + " ");
    }

    private String clean(String value) {
        return value == null ? null : value.trim();
    }

    private String cleanOrNull(String value) {
        String cleaned = clean(value);
        return cleaned == null || cleaned.isEmpty() ? null : cleaned;
    }

    /**
     * Compares two club names: exact match on the normalised form first, then a
     * prefix match, so a betslip printing part of a long club name still resolves.
     * The parser has no database access, so this stays lexical - alias resolution
     * still happens later, in TeamStrengthService.
     */
    private boolean sameTeam(String a, String b) {
        String na = normalizeTeam(a);
        String nb = normalizeTeam(b);
        if (na.isEmpty() || nb.isEmpty()) return false;
        if (na.equals(nb)) return true;
        String shorter = na.length() <= nb.length() ? na : nb;
        String longer = na.length() <= nb.length() ? nb : na;
        return shorter.length() >= 4 && longer.startsWith(shorter);
    }

    private String normalizeTeam(String value) {
        return CLUB_TOKEN.matcher(normalize(value)).replaceAll(" ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
