---
title: "Breadcrumbs"
order: 7
published: true
draft: false
---

# Breadcrumbs

Parent: [Integration Guides](/mod/alecs-telemetry/integration-guides) | [Home](/mod/alecs-telemetry/home)

Breadcrumbs are short recent notes attached to crash and error reports. They help explain what the plugin was doing before a failure.

## Descriptor Setup

```json
{
  "events": {
    "breadcrumbs": {
      "enabled": true,
      "automatic": true
    }
  },
  "hosted": {
    "projectKey": "paste_your_project_key_here"
  }
}
```

## Record Breadcrumbs

```java
project.recordBreadcrumb("lifecycle", "Settings page opened.");
project.recordBreadcrumb("config", "Reload requested.");
```

Embedded plugins can call the same helper on `EmbeddedTelemetryService`.

## Good Breadcrumbs

- name the subsystem
- describe a stable action
- stay short
- avoid player names, secrets, exact coordinates, tokens, and free-form chat

## Verify

1. Record a breadcrumb before a test report or real error.
2. Run `/telemetry test <project-id> breadcrumb-check`.
3. Flush the project.
4. Open the issue in the portal and inspect attached breadcrumbs.

## Troubleshooting

- Breadcrumbs missing: check `events.breadcrumbs.enabled` and server consent.
- Breadcrumbs too noisy: use fewer categories and only record meaningful state changes.
- Sensitive data risk: replace raw values with stable reason codes or coarse labels.
