package com.alechilles.alecstelemetry.api;

/** Describes whether sampled work belongs directly to a project or runs below it. */
public enum TelemetryProfilerAttribution {
    SELF,
    DOWNSTREAM
}
