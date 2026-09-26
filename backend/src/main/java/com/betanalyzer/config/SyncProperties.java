package com.betanalyzer.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Settings for pulling real data from an external provider.
 *
 * <p>Everything lives under {@code app.sync.*} in application.properties, so
 * pointing the app at a different provider, host or API key never requires a
 * code change.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.sync")
public class SyncProperties {

    /** Master switch. When false the sync endpoints report "disabled" and make no calls. */
    private boolean enabled = true;

    /** Which registered provider to use. Only "sofascore" ships today. */
    private String provider = "sofascore";

    /**
     * Time zone used to turn the provider's UTC kick-off instants into the
     * local date/times the app displays. Empty means the server's own zone.
     */
    private String zone = "";

    /** Competitions to sync in bulk, as {@code id:Label} pairs. */
    private List<String> competitions = new ArrayList<>(List.of(
            "17:Premier League",
            "8:La Liga",
            "23:Serie A",
            "35:Bundesliga",
            "39:PSL"));

    private SofaScore sofascore = new SofaScore();

    @Getter
    @Setter
    public static class SofaScore {
        /**
         * Blank means "pick automatically": the official API host when an API
         * key is configured, otherwise the public web endpoint. Set it only to
         * override - for a proxy, a mirror, or a stub in tests.
         */
        private String baseUrl = "";

        /** Official API key. Blank means the public endpoint is used. */
        private String apiKey = "";

        private String userAgent =
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                        + "Chrome/124.0.0.0 Safari/537.36";

        /** Competition name to use when the provider omits one. */
        private String defaultLeague = "Football";

        private long connectTimeoutMs = 8_000;
        private long readTimeoutMs = 20_000;
    }
}
