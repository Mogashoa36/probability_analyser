package com.betanalyzer.service;

import com.betanalyzer.entity.SlipSelection.Market;
import lombok.Getter;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns pasted, free-text betting slips (copy-pasted from a Betway /
 * Hollywoodbets betslip, or typed by hand) into structured selections.
 *
 * Supported line formats, one selection per line:
 *   "Kaizer Chiefs vs Orlando Pirates - Home Win @ 2.10"
 *   "Man City v Arsenal, Over 2.5, 1.65"
 *   "Real Madrid,Barcelona,DRAW,3.40"                (CSV: home,away,market,odds)
 *   "Liverpool vs Chelsea"                            (fixture only, no market/odds - for the Recommendations page)
 *
 * Lines starting with '#' or '//' are treated as comments and skipped.
 */
@Service
public class SlipParserService {

    // "<home> vs|v <away>" with an optional trailing " - market @ odds" / ", market, odds"
    private static final Pattern FIXTURE_PATTERN = Pattern.compile(
            "^\\s*(.+?)\\s+(?:vs\\.?|v\\.?)\\s+(.+?)\\s*(?:[-,]\\s*(.+))?$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern ODDS_PATTERN = Pattern.compile("(\\d+\\.\\d+)");

    @Getter
    public static class ParsedSelection {
        private final String homeTeam;
        private final String awayTeam;
        private final Market market;
        private final Double odds; // null when the line was fixture-only
        private final String warning; // null when the line parsed cleanly

        public ParsedSelection(String homeTeam, String awayTeam, Market market, Double odds, String warning) {
            this.homeTeam = homeTeam;
            this.awayTeam = awayTeam;
            this.market = market;
            this.odds = odds;
            this.warning = warning;
        }
    }

    public List<ParsedSelection> parseSlipText(String rawText) {
        List<ParsedSelection> results = new ArrayList<>();
        if (rawText == null || rawText.isBlank()) return results;

        for (String rawLine : rawText.split("\\r?\\n")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) continue;

            // Natural language is tried FIRST: a line such as
            // "Man City v Arsenal, Over 2.5, 1.65" also contains commas, so the
            // CSV reader would otherwise swallow the whole fixture and treat the
            // market label as the away team.
            ParsedSelection parsed = parseNaturalLanguage(line);
            if (parsed == null) parsed = parseCsv(line);

            if (parsed == null) {
                results.add(new ParsedSelection("Unknown", "Unknown", Market.UNKNOWN, null,
                        "Could not parse line: \"" + line + "\" - expected e.g. \"Team A vs Team B - Home Win @ 1.85\""));
            } else {
                results.add(parsed);
            }
        }
        return results;
    }

    private ParsedSelection parseCsv(String line) {
        if (!line.contains(",")) return null;
        String[] parts = line.split(",");
        if (parts.length < 2) return null;

        String home = parts[0].trim();
        String away = parts[1].trim();
        Market market = parts.length >= 3 ? mapMarket(parts[2].trim()) : Market.UNKNOWN;
        Double odds = null;
        if (parts.length >= 4) {
            try {
                odds = Double.parseDouble(parts[3].trim());
            } catch (NumberFormatException ignored) {
                // leave odds null - handled by caller as "no odds supplied"
            }
        }
        return new ParsedSelection(home, away, market, odds, null);
    }

    private ParsedSelection parseNaturalLanguage(String line) {
        Matcher m = FIXTURE_PATTERN.matcher(line);
        if (!m.matches()) return null;

        String home = m.group(1).trim();
        String away = m.group(2).trim();
        String rest = m.group(3);

        Market market = Market.UNKNOWN;
        Double odds = null;

        if (rest != null && !rest.isBlank()) {
            // Everything after an '@' is the price; without an '@' the price is
            // simply the last decimal number in the remainder.
            String marketText = rest;
            String oddsText = null;
            int at = rest.lastIndexOf('@');
            if (at >= 0) {
                marketText = rest.substring(0, at);
                oddsText = rest.substring(at + 1);
            }

            // Take the LAST decimal number rather than the first: market labels
            // carry decimals of their own ("Over 2.5"), so matching the first one
            // would silently treat the goal line as the price.
            Matcher oddsMatcher = ODDS_PATTERN.matcher(oddsText != null ? oddsText : marketText);
            String quotedOdds = null;
            while (oddsMatcher.find()) {
                quotedOdds = oddsMatcher.group(1);
            }

            if (quotedOdds != null) {
                odds = Double.parseDouble(quotedOdds);
                if (oddsText == null) {
                    // Strip only the price, leaving "Over 2.5" intact as the market.
                    marketText = marketText.replaceFirst(Pattern.quote(quotedOdds) + "\\s*$", "");
                }
            }
            market = mapMarket(marketText.trim());
        }

        return new ParsedSelection(home, away, market, odds, null);
    }

    private Market mapMarket(String text) {
        String t = text.toLowerCase().replaceAll("[^a-z0-9. ]", "").trim();
        if (t.isEmpty()) return Market.UNKNOWN;

        return switch (t) {
            case "home win", "home", "1", "h" -> Market.HOME_WIN;
            case "away win", "away", "2", "a" -> Market.AWAY_WIN;
            case "draw", "x" -> Market.DRAW;
            case "over 2.5", "over2.5", "o2.5", "over" -> Market.OVER_2_5;
            case "under 2.5", "under2.5", "u2.5", "under" -> Market.UNDER_2_5;
            case "btts yes", "btts", "gg", "both teams to score" -> Market.BTTS_YES;
            case "btts no", "ng", "no goal" -> Market.BTTS_NO;
            default -> Market.UNKNOWN;
        };
    }
}
