package com.betanalyzer.service.external;

/**
 * A club as the upstream provider describes it, before it becomes a
 * {@link com.betanalyzer.entity.Team}. Keeping this separate from the entity
 * means the rest of the app never depends on a provider's field names.
 */
public record ExternalTeamRef(String externalId, String name, String logoUrl) {
}
