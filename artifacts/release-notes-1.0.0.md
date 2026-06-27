# Alec's Telemetry v1.0.0

## Summary
This is the first public Alec's Telemetry runtime release line, including the
standalone dependency mod, embeddable runtime jar, portal-first setup docs, and
descriptor schema for supported/default-enabled telemetry categories.

## Changed
- Descriptor telemetry categories now use `supported` and `defaultEnabled`.
- Omitted categories are unsupported and hidden from consent.
- Supported categories default on unless `defaultEnabled` is false.
- Stats-only setup can declare only `telemetry.stats.supported` without listing
  unrelated categories as false.
- Public setup docs now separate portal project creation from descriptor
  reporting setup.

## Fixes
- The example consumer manifest now depends on `Alechilles:Alec's Telemetry!`.

## Compatibility
- Hytale: 0.5.x

## Files
- Alec's Telemetry v1.0.0.jar
- alecstelemetry-runtime-1.0.0.jar
