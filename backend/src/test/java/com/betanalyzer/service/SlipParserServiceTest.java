package com.betanalyzer.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.betanalyzer.entity.SlipSelection.Market;
import org.junit.jupiter.api.Test;

/**
 * Validates the free-text slip parser against the documented input shapes.
 *
 * <p>The parser must extract the fixture, market and price from each supported
 * form, ignore comments and divider lines, and attach an actionable warning
 * (never silently drop) when a line cannot be modelled.
 */
class SlipParserServiceTest {

    private final SlipParserService parser = new SlipParserService();

    @Test
    void vsFormWithAtPrice() {
        var s = parser.parseSlipText("Kaizer Chiefs vs Orlando Pirates - Home Win @ 2.10").get(0);
        assertThat(s.getHomeTeam()).isEqualTo("Kaizer Chiefs");
        assertThat(s.getAwayTeam()).isEqualTo("Orlando Pirates");
        assertThat(s.getMarket()).isEqualTo(Market.HOME_WIN);
        assertThat(s.getOdds()).isEqualTo(2.10);
        assertThat(s.getWarning()).isNull();
    }

    @Test
    void commaFormWithGoalsMarket() {
        var s = parser.parseSlipText("Man City v Arsenal, Over 2.5, 1.65").get(0);
        assertThat(s.getHomeTeam()).isEqualTo("Man City");
        assertThat(s.getAwayTeam()).isEqualTo("Arsenal");
        assertThat(s.getMarket()).isEqualTo(Market.OVER_2_5);
        assertThat(s.getOdds()).isEqualTo(1.65);
        assertThat(s.getWarning()).isNull();
    }

    @Test
    void strictCsvForm() {
        var s = parser.parseSlipText("Real Madrid,Barcelona,DRAW,3.40").get(0);
        assertThat(s.getHomeTeam()).isEqualTo("Real Madrid");
        assertThat(s.getAwayTeam()).isEqualTo("Barcelona");
        assertThat(s.getMarket()).isEqualTo(Market.DRAW);
        assertThat(s.getOdds()).isEqualTo(3.40);
        assertThat(s.getWarning()).isNull();
    }

    @Test
    void fixtureOnlyHasNoMarket() {
        var s = parser.parseSlipText("Liverpool vs Chelsea").get(0);
        assertThat(s.getHomeTeam()).isEqualTo("Liverpool");
        assertThat(s.getAwayTeam()).isEqualTo("Chelsea");
        assertThat(s.getMarket()).isEqualTo(Market.UNKNOWN);
        assertThat(s.getLegs()).isEmpty();
        assertThat(s.getWarning()).isNull();
    }

    @Test
    void commentLinesAreIgnored() {
        var list = parser.parseSlipText(
                "# this is a comment\n"
                + "// also a comment\n"
                + "Kaizer Chiefs vs Orlando Pirates - Home Win @ 2.10\n"
        );
        assertThat(list).hasSize(1);
        assertThat(list.get(0).getHomeTeam()).isEqualTo("Kaizer Chiefs");
        assertThat(list.get(0).getWarning()).isNull();
    }

    @Test
    void multipleLegsAreParsed() {
        var list = parser.parseSlipText(
                "Kaizer Chiefs vs Orlando Pirates - Home Win @ 2.10\n"
                + "Man City vs Arsenal - Over 2.5 @ 1.65\n"
                + "Real Madrid vs Barcelona - Draw @ 3.40\n"
        );
        assertThat(list).hasSize(3);
        assertThat(list.get(0).getMarket()).isEqualTo(Market.HOME_WIN);
        assertThat(list.get(1).getMarket()).isEqualTo(Market.OVER_2_5);
        assertThat(list.get(2).getMarket()).isEqualTo(Market.DRAW);
    }

    @Test
    void priceWithNoFixtureProducesWarningNotSilentDrop() {
        // A price printed after the fixture line has no fixture to attach to ->
        // flushed as an orphan with a warning at end of input.
        var list = parser.parseSlipText("Kaizer Chiefs vs Orlando Pirates\n2.10");
        assertThat(list).anyMatch(s -> s.getWarning() != null);
        assertThat(list).anyMatch(s -> "Kaizer Chiefs".equals(s.getHomeTeam()) && s.getWarning() == null);
    }

    @Test
    void blankInputReturnsEmptyList() {
        assertThat(parser.parseSlipText("")).isEmpty();
        assertThat(parser.parseSlipText(null)).isEmpty();
        assertThat(parser.parseSlipText("   ")).isEmpty();
    }

    @Test
    void unsupportedMarketProducesWarning() {
        var s = parser.parseSlipText("Kaizer Chiefs vs Orlando Pirates - Correct Score 2-1 @ 5.0").get(0);
        assertThat(s.getWarning()).isNotNull();
        assertThat(s.getLegs()).isEmpty();
    }
}
