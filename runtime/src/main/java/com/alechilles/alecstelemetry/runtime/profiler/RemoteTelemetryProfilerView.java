package com.alechilles.alecstelemetry.runtime.profiler;

import com.alechilles.alecstelemetry.api.TelemetryProfilerSnapshot;
import com.alechilles.alecstelemetry.api.TelemetryProfilerStatus;
import com.alechilles.alecstelemetry.api.TelemetryProfilerSubscription;
import com.alechilles.alecstelemetry.api.TelemetryProfilerView;
import com.alechilles.alecstelemetry.coordinator.TelemetryCoordinatorBridge;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Project-scoped view backed by one elected JDK-only coordinator bridge. */
public final class RemoteTelemetryProfilerView implements TelemetryProfilerView {
    private final TelemetryCoordinatorBridge bridge;
    private final String projectId;

    public RemoteTelemetryProfilerView(@Nonnull TelemetryCoordinatorBridge bridge,
                                       @Nonnull String projectId) {
        this.bridge = Objects.requireNonNull(bridge, "bridge");
        this.projectId = Objects.requireNonNull(projectId, "projectId").trim();
    }

    @Nonnull
    @Override
    public TelemetryProfilerStatus status() {
        if (!supportsProfiler()) {
            return TelemetryProfilerStatus.unavailable();
        }
        return TelemetryProfilerBridgePayload.statusFromSummary(safeStatusPayload());
    }

    @Nonnull
    @Override
    public Optional<TelemetryProfilerSnapshot> latest() {
        if (!supportsProfiler()) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(TelemetryProfilerBridgePayload.snapshotFromSummary(
                    bridge.profilerLatest(projectId)
            ));
        } catch (RuntimeException | LinkageError ignored) {
            return Optional.empty();
        }
    }

    @Nonnull
    @Override
    public List<TelemetryProfilerSnapshot> history() {
        if (!supportsProfiler()) {
            return List.of();
        }
        try {
            return TelemetryProfilerBridgePayload.snapshotsFromHistory(bridge.profilerHistory(projectId));
        } catch (RuntimeException | LinkageError ignored) {
            return List.of();
        }
    }

    @Nonnull
    @Override
    public TelemetryProfilerSubscription subscribe(
            @Nonnull Consumer<? super TelemetryProfilerSnapshot> listener) {
        Objects.requireNonNull(listener, "listener");
        if (!supportsProfiler()) {
            return TelemetryProfilerSubscription.closed();
        }
        try {
            String subscriptionId = bridge.subscribeProfiler(
                    projectId,
                    payload -> {
                        TelemetryProfilerSnapshot snapshot = TelemetryProfilerBridgePayload
                                .snapshotFromSummary(payload);
                        if (snapshot != null) {
                            try {
                                listener.accept(snapshot);
                            } catch (RuntimeException | LinkageError ignored) {
                                // The provider isolates foreign listener failures.
                            }
                        }
                    }
            );
            if (subscriptionId == null || subscriptionId.isBlank()) {
                return TelemetryProfilerSubscription.closed();
            }
            return new RemoteSubscription(bridge, subscriptionId);
        } catch (RuntimeException | LinkageError ignored) {
            return TelemetryProfilerSubscription.closed();
        }
    }

    private boolean supportsProfiler() {
        try {
            return bridge.capabilities().contains(TelemetryProfilerView.CAPABILITY);
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    @Nonnull
    private java.util.Map<String, Object> safeStatusPayload() {
        try {
            return bridge.profilerStatus(projectId);
        } catch (RuntimeException | LinkageError ignored) {
            return java.util.Map.of();
        }
    }

    private static final class RemoteSubscription implements TelemetryProfilerSubscription {
        private final TelemetryCoordinatorBridge bridge;
        private final String subscriptionId;
        private final AtomicBoolean closed = new AtomicBoolean();

        private RemoteSubscription(@Nonnull TelemetryCoordinatorBridge bridge,
                                   @Nonnull String subscriptionId) {
            this.bridge = bridge;
            this.subscriptionId = subscriptionId;
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            try {
                bridge.unsubscribeProfiler(subscriptionId);
            } catch (RuntimeException | LinkageError ignored) {
                // A provider handoff or shutdown already closes this subscription.
            }
        }
    }
}
