---
title: "Breadcrumbs"
order: 7
published: true
draft: false
---

# Breadcrumbs

Parent: [Integration Guides](/mod/beacon/integration-guides) | [Home](/mod/beacon/home)

Breadcrumbs are short recent notes attached to crash and error reports. They help explain what the plugin was doing before a failure.

## Before This Page

Complete [Portal First Setup](/mod/beacon/portal-first-setup) once.

## Descriptor Block

Add this block to the shared descriptor:

```json
{
  "telemetry": {
    "events": {
      "breadcrumbs": {
        "supported": true,
        "automatic": true
      }
    }
  }
}
```

## Record Breadcrumbs

```java
project.recordBreadcrumb("lifecycle", "Settings page opened.");
project.recordBreadcrumb("config", "Reload requested.");
```

Embedded plugins can call the same helper on `EmbeddedTelemetryService`.

For operations that need a support correlation trail, use the additive
structured context:

```java
project.recordBreadcrumb(
    TelemetryBreadcrumbContext.builder("persistence", "incident opened")
        .correlationId(traceId)
        .incidentId(incidentId)
        .phase("CANONICAL_OUTCOME_UNKNOWN")
        .operation("coop_release")
        .scopeType("COOP_SLOT")
        .failureClass("OUTCOME_UNKNOWN")
        .disposition("SCOPED_QUARANTINE")
        .attribute("recoveryAttempt", 1)
        .build()
);
```

The runtime bounds every field and accepts only scalar custom attributes.
Legacy category/detail calls remain supported. Correlation values should be
randomized or hashed; do not send raw player, NPC, world, or save identifiers.

## Good Breadcrumbs

- name the subsystem
- describe a stable action
- stay short
- avoid player names, secrets, exact coordinates, tokens, and free-form chat
- use randomized or one-way-hashed correlation values instead of raw stable IDs

## Verify

1. Record a breadcrumb before a test report or real error.
2. Run `/beacon test <project-id> breadcrumb-check`.
3. Flush the project.
4. Open the issue in the portal and inspect attached breadcrumbs.

## Troubleshooting

- Breadcrumbs missing: check `telemetry.events.breadcrumbs.supported` and server consent.
- Breadcrumbs too noisy: use fewer categories and only record meaningful state changes.
- Sensitive data risk: replace raw values with stable reason codes or coarse labels.
