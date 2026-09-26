package com.betanalyzer.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * The HTTP client used to talk to external data providers.
 *
 * <p>Deliberately explicit about timeouts: a provider that hangs must fail
 * the sync request in seconds rather than pinning a request thread for the
 * default (which is effectively forever on {@code SimpleClientHttpRequestFactory}).
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class ExternalDataConfig {

    /** Supported, key-authenticated host. */
    public static final String OFFICIAL_API = "https://api.sofascore.com/api/v1";

    /** Keyless host. Undocumented, and behind bot protection. */
    public static final String PUBLIC_API = "https://www.sofascore.com/api/v1";

    private final SyncProperties properties;

    /**
     * Resolves the base URL once at startup and writes it back into the
     * properties, so the client, the status endpoint and the UI all report the
     * same host without each re-deriving it.
     */
    @Bean
    public RestClient externalDataRestClient() {
        SyncProperties.SofaScore cfg = properties.getSofascore();
        boolean hasKey = cfg.getApiKey() != null && !cfg.getApiKey().isBlank();

        if (cfg.getBaseUrl() == null || cfg.getBaseUrl().isBlank()) {
            cfg.setBaseUrl(hasKey ? OFFICIAL_API : PUBLIC_API);
            log.info("External data: no base URL configured, using {} (API key {})",
                    cfg.getBaseUrl(), hasKey ? "present" : "absent");
        } else if (hasKey && PUBLIC_API.equals(cfg.getBaseUrl())) {
            log.warn("External data: an API key is set but the base URL is the public endpoint; "
                    + "the key will be ignored. Unset app.sync.sofascore.base-url to switch to {}.",
                    OFFICIAL_API);
        }

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(cfg.getConnectTimeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(cfg.getReadTimeoutMs()));

        return RestClient.builder()
                .baseUrl(cfg.getBaseUrl())
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.USER_AGENT, cfg.getUserAgent())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .requestInterceptor((request, body, execution) -> {
                    if (hasKey) {
                        request.getHeaders().set("X-API-Key", cfg.getApiKey());
                    }
                    return execution.execute(request, body);
                })
                .build();
    }
}
