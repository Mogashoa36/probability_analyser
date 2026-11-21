package com.betanalyzer.esoccer.controller;

import com.betanalyzer.esoccer.service.external.BetwayDataProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/esoccer/sync")
@RequiredArgsConstructor
public class ESoccerSyncController {

    private final BetwayDataProvider provider;
    private final List<BetwayDataProvider> allProviders;

    @GetMapping("/status")
    public SyncStatusDto status() {
        return new SyncStatusDto(
                provider.name(),
                provider.describeEndpoint(),
                provider.isConfigured()
        );
    }

    @PostMapping("/providers")
    public List<String> listProviders() {
        return allProviders.stream().map(BetwayDataProvider::name).toList();
    }

    public record SyncStatusDto(String provider, String endpoint, boolean configured) {}
}
