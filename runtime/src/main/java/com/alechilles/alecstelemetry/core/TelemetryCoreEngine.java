package com.alechilles.alecstelemetry.core;

import com.alechilles.alecstelemetry.api.TelemetryBreadcrumbContext;
import com.alechilles.alecstelemetry.api.TelemetryEventContext;
import com.alechilles.alecstelemetry.api.TelemetryDiagnosticAttachment;
import com.alechilles.alecstelemetry.api.TelemetryDiagnosticBundle;
import com.alechilles.alecstelemetry.api.TelemetryDiagnosticBundleResult;
import com.alechilles.alecstelemetry.api.TelemetryDiagnosticDisposition;
import com.alechilles.alecstelemetry.crash.CrashAttribution;
import com.alechilles.alecstelemetry.crash.CrashReportClient;
import com.alechilles.alecstelemetry.crash.CrashReportEnvelope;
import com.alechilles.alecstelemetry.crash.CrashReportStore;
import com.alechilles.alecstelemetry.event.TelemetryEventEnvelope;
import com.alechilles.alecstelemetry.event.TelemetryEventStore;
import com.alechilles.alecstelemetry.diagnostic.TelemetryDiagnosticBundleEnvelope;
import com.alechilles.alecstelemetry.project.TelemetryProjectRegistration;
import com.alechilles.alecstelemetry.project.TelemetryProjectCatalog;
import com.alechilles.alecstelemetry.report.ManualReportAttachment;
import com.alechilles.alecstelemetry.report.ManualReportEnvelope;
import com.alechilles.alecstelemetry.report.ManualReportLogCollector;
import com.alechilles.alecstelemetry.report.ManualReportReceiptStore;
import com.alechilles.alecstelemetry.report.ManualReportStore;
import com.alechilles.alecstelemetry.report.ManualReportSubmission;
import com.alechilles.alecstelemetry.report.PlayerReportRuntimeContext;
import com.alechilles.alecstelemetry.runtime.TelemetryBreadcrumbBuffer;
import com.alechilles.alecstelemetry.runtime.TelemetryDataPaths;
import com.alechilles.alecstelemetry.runtime.TelemetryRuntimeSettings;
import com.alechilles.alecstelemetry.runtime.stats.TelemetryPlayerIntervalSnapshot;
import com.alechilles.alecstelemetry.runtime.TelemetryServerIdentity;
import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.events.RemoveWorldEvent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Instant;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.Base64;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/**
 * Shared crash telemetry engine used by standalone and embedded telemetry modes.
 */
public final class TelemetryCoreEngine {

    private static final Executor DEFAULT_FLUSH_EXECUTOR = command -> Thread.ofVirtual()
            .name("AlecsTelemetry-Upload")
            .start(command);

    private static final String SOURCE_UNCAUGHT_EXCEPTION = "uncaught_exception";
    private static final String SOURCE_EXCEPTIONAL_WORLD_REMOVAL = "exceptional_world_removal";
    private static final String SOURCE_SETUP_FAILURE = "plugin_setup_failure";
    private static final String SOURCE_START_FAILURE = "plugin_start_failure";
    private static final String SOURCE_MANUAL_TEST = "manual_test";
    private static final int DEFAULT_STATS_HEARTBEAT_INTERVAL_SECONDS = 1800;
    private static final int MIN_STATS_HEARTBEAT_INTERVAL_SECONDS = 300;
    private static final int MAX_STATS_HEARTBEAT_INTERVAL_SECONDS = 3600;
    private static final int STATS_HEARTBEAT_JITTER_SECONDS = 300;
    private static final int STATS_HEARTBEAT_STARTUP_MIN_SECONDS = 120;
    private static final int STATS_HEARTBEAT_STARTUP_MAX_SECONDS = 300;
    private static final long DEFAULT_UPLOAD_RETRY_COOLDOWN_MILLIS = TimeUnit.SECONDS.toMillis(60);
    private static final long MAX_UPLOAD_RETRY_COOLDOWN_MILLIS = TimeUnit.MINUTES.toMillis(15);
    private static final int MAX_DIAGNOSTIC_ATTACHMENTS = 16;
    private static final int MAX_DIAGNOSTIC_ATTRIBUTES = 32;
    private static final int MAX_DIAGNOSTIC_ATTRIBUTE_VALUE_LENGTH = 256;
    private static final int MAX_DIAGNOSTIC_ATTRIBUTE_NUMBER_LENGTH = 128;
    private static final int MAX_DIAGNOSTIC_ATTACHMENT_BYTES = 1_048_576;
    private static final int MAX_DIAGNOSTIC_PAYLOAD_BYTES = 2_097_152;

    private final TelemetryRuntimeSettings settings;
    private final TelemetryDataPaths dataPaths;
    private final TelemetryProjectCatalog projectCatalog;
    private final List<CrashReportEnvelope.LoadedModMetadata> loadedMods;
    private final CrashReportClient client;
    private final HytaleLogger logger;
    private final ScheduledExecutorService executor;
    private final Executor flushExecutor;
    private final AtomicBoolean enabled;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean uncaughtHandlerInstalled = new AtomicBoolean(false);
    private final Object asyncFlushMonitor = new Object();
    private final ConcurrentHashMap<String, CrashReportStore> storesByProjectId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, TelemetryEventStore> eventStoresByProjectId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ManualReportStore> manualReportStoresByProjectId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CrashReportEnvelope.EnvironmentSnapshot> environmentsByProjectId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicBoolean> projectEnabledOverrides = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicBoolean> crashEnabledOverrides = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicBoolean> errorEventsEnabledOverrides = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicBoolean> diagnosticsEnabledOverrides = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicBoolean> lifecycleEventsEnabledOverrides = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicBoolean> performanceEnabledOverrides = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicBoolean> usageEnabledOverrides = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicBoolean> statsEnabledOverrides = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicBoolean> breadcrumbsEnabledOverrides = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, UploadRetryState> uploadRetryStates = new ConcurrentHashMap<>();
    private final AtomicInteger statsHeartbeatIntervalSeconds = new AtomicInteger(DEFAULT_STATS_HEARTBEAT_INTERVAL_SECONDS);
    private final TelemetryBreadcrumbBuffer breadcrumbs;
    private final String sessionId = UUID.randomUUID().toString();
    private final String serverId;
    private volatile String serverClaimToken;
    private final ManualReportLogCollector manualReportLogCollector = new ManualReportLogCollector();
    private final ManualReportReceiptStore manualReportReceiptStore = new ManualReportReceiptStore();

    private volatile Thread.UncaughtExceptionHandler previousUncaughtHandler;
    private volatile TelemetryUncaughtExceptionHandler installedUncaughtHandler;
    private volatile ScheduledFuture<?> periodicFlushFuture;
    private volatile String lastFlushResult = "No flush attempts yet.";
    private boolean asyncFlushInProgress;
    private boolean shuttingDown;

    public TelemetryCoreEngine(@Nonnull TelemetryRuntimeSettings settings,
                               @Nonnull TelemetryDataPaths dataPaths,
                               @Nonnull List<TelemetryProjectRegistration> projects,
                               @Nonnull List<CrashReportEnvelope.LoadedModMetadata> loadedMods,
                               @Nonnull CrashReportClient client,
                               @Nullable HytaleLogger logger,
                               @Nullable ScheduledExecutorService executor) {
        this(settings, dataPaths, projects, projects, loadedMods, client, logger, executor);
    }

    public TelemetryCoreEngine(@Nonnull TelemetryRuntimeSettings settings,
                               @Nonnull TelemetryDataPaths dataPaths,
                               @Nonnull List<TelemetryProjectRegistration> projects,
                               @Nonnull List<TelemetryProjectRegistration> manualReportProjects,
                               @Nonnull List<CrashReportEnvelope.LoadedModMetadata> loadedMods,
                               @Nonnull CrashReportClient client,
                               @Nullable HytaleLogger logger,
                               @Nullable ScheduledExecutorService executor) {
        this(
                settings,
                dataPaths,
                projects,
                manualReportProjects,
                loadedMods,
                client,
                logger,
                executor,
                executor == null ? null : DEFAULT_FLUSH_EXECUTOR
        );
    }

    private TelemetryCoreEngine(@Nonnull TelemetryRuntimeSettings settings,
                                @Nonnull TelemetryDataPaths dataPaths,
                                @Nonnull List<TelemetryProjectRegistration> projects,
                                @Nonnull List<TelemetryProjectRegistration> manualReportProjects,
                                @Nonnull List<CrashReportEnvelope.LoadedModMetadata> loadedMods,
                                @Nonnull CrashReportClient client,
                                @Nullable HytaleLogger logger,
                                @Nullable ScheduledExecutorService executor,
                                @Nullable Executor flushExecutor) {
        this.settings = settings;
        this.dataPaths = dataPaths;
        this.projectCatalog = new TelemetryProjectCatalog(projects, manualReportProjects);
        this.loadedMods = List.copyOf(loadedMods);
        this.client = client;
        this.logger = logger;
        this.executor = executor;
        this.flushExecutor = flushExecutor;
        this.enabled = new AtomicBoolean(settings.enabled());
        this.breadcrumbs = new TelemetryBreadcrumbBuffer(settings.maxBreadcrumbsPerProject());
        TelemetryServerIdentity.Identity identity = TelemetryServerIdentity.loadOrCreate(dataPaths.serverIdentityFile(), dataPaths.serverIdFile(), logger);
        this.serverId = identity.serverId();
        this.serverClaimToken = identity.serverClaimToken();
    }

    public void start() {
        if (!enabled.get() || projects().isEmpty()) {
            lastFlushResult = projects().isEmpty() ? "No registered telemetry projects discovered." : "Runtime disabled by settings.";
            return;
        }
        if (!started.compareAndSet(false, true)) {
            return;
        }
        synchronized (asyncFlushMonitor) {
            shuttingDown = false;
        }
        installUncaughtExceptionHandler();
        requestFlushAsync("startup", null);
        if (executor != null) {
            periodicFlushFuture = executor.scheduleWithFixedDelay(
                    () -> requestFlushAsync("periodic", null),
                    settings.flushIntervalSeconds(),
                    settings.flushIntervalSeconds(),
                    TimeUnit.SECONDS
            );
        }
    }

    public void shutdown() {
        synchronized (asyncFlushMonitor) {
            shuttingDown = true;
        }
        ScheduledFuture<?> scheduledFuture = periodicFlushFuture;
        periodicFlushFuture = null;
        if (scheduledFuture != null) {
            scheduledFuture.cancel(false);
        }
        flushPendingOnShutdown();
        uninstallUncaughtExceptionHandler();
        started.set(false);
    }

    public boolean isEnabled() {
        return enabled.get();
    }

    public int statsHeartbeatIntervalSeconds() {
        return statsHeartbeatIntervalSeconds.get();
    }

    /**
     * Validates and durably queues a project diagnostic through the event route.
     */
    @Nonnull
    public TelemetryDiagnosticBundleResult submitDiagnosticBundle(
            @Nonnull String projectId,
            @Nonnull TelemetryDiagnosticBundle bundle
    ) {
        if (!enabled.get()) {
            return diagnosticResult(TelemetryDiagnosticBundleResult.Status.DISABLED, "event_telemetry_disabled");
        }
        TelemetryProjectRegistration project = findProject(projectId);
        if (project == null) {
            return diagnosticResult(TelemetryDiagnosticBundleResult.Status.REJECTED, "project_not_found");
        }
        if (!isProjectRuntimeEnabled(project)
                || project.resolveEventDeliveryTarget(settings) == null) {
            return diagnosticResult(TelemetryDiagnosticBundleResult.Status.DISABLED, "event_telemetry_disabled");
        }
        if (!isDiagnosticsRuntimeEnabled(project)) {
            return diagnosticResult(
                    TelemetryDiagnosticBundleResult.Status.DISABLED,
                    "diagnostic_telemetry_disabled"
            );
        }
        String validationFailure = validateDiagnosticBundle(bundle);
        if (validationFailure != null) {
            return diagnosticResult(TelemetryDiagnosticBundleResult.Status.REJECTED, validationFailure);
        }
        try {
            TelemetryDiagnosticBundleEnvelope envelope = TelemetryDiagnosticBundleEnvelope.create(
                    project.projectId(),
                    project.pluginIdentifier(),
                    project.pluginVersion(),
                    sessionId,
                    serverId,
                    bundle
            );
            String payload = envelope.toJson();
            if (payload.getBytes(StandardCharsets.UTF_8).length > MAX_DIAGNOSTIC_PAYLOAD_BYTES) {
                return diagnosticResult(TelemetryDiagnosticBundleResult.Status.REJECTED, "diagnostic_payload_too_large");
            }
            if (!eventStoreFor(project).persistRaw(
                    TelemetryDiagnosticBundleEnvelope.EVENT_TYPE,
                    bundle.diagnosticId(),
                    payload
            )) {
                return diagnosticResult(TelemetryDiagnosticBundleResult.Status.FAILED, "diagnostic_queue_write_failed");
            }
            requestFlushAsync("diagnostic_bundle", project.projectId());
            return diagnosticResult(TelemetryDiagnosticBundleResult.Status.QUEUED, null);
        } catch (RuntimeException failure) {
            logWarning("Failed to queue diagnostic bundle.", failure);
            return diagnosticResult(TelemetryDiagnosticBundleResult.Status.FAILED, "diagnostic_queue_failed");
        }
    }

    @Nullable
    private static String validateDiagnosticBundle(@Nullable TelemetryDiagnosticBundle bundle) {
        if (bundle == null) return "diagnostic_bundle_missing";
        if (isBlank(bundle.diagnosticId())) return "diagnostic_id_missing";
        if (isBlank(bundle.capturedAtUtc())) return "captured_at_missing";
        try {
            Instant.parse(bundle.capturedAtUtc());
        } catch (RuntimeException ignored) {
            return "captured_at_invalid";
        }
        if (isBlank(bundle.source())) return "diagnostic_source_missing";
        if (isBlank(bundle.diagnosticKind())) return "diagnostic_kind_missing";
        if (isBlank(bundle.title())) return "diagnostic_title_missing";
        if (isBlank(bundle.summary())) return "diagnostic_summary_missing";
        if (!List.of("info", "warning", "error").contains(bundle.severity())) {
            return "diagnostic_severity_invalid";
        }
        TelemetryDiagnosticDisposition disposition = bundle.disposition();
        if (disposition == null
                || (!TelemetryDiagnosticDisposition.INFORMATIONAL.equals(disposition.mode())
                && !TelemetryDiagnosticDisposition.CREATE_OR_JOIN_ISSUE.equals(disposition.mode()))) {
            return "diagnostic_disposition_invalid";
        }
        if (TelemetryDiagnosticDisposition.CREATE_OR_JOIN_ISSUE.equals(disposition.mode())
                && isBlank(disposition.fingerprint())) {
            return "diagnostic_fingerprint_missing";
        }
        String attributeFailure = validateDiagnosticAttributes(bundle.attributes());
        if (attributeFailure != null) {
            return attributeFailure;
        }
        if (bundle.attachments().size() > MAX_DIAGNOSTIC_ATTACHMENTS) {
            return "diagnostic_attachment_count_exceeded";
        }
        for (TelemetryDiagnosticAttachment attachment : bundle.attachments()) {
            String failure = validateDiagnosticAttachment(attachment);
            if (failure != null) return failure;
        }
        return null;
    }

    @Nullable
    private static String validateDiagnosticAttributes(
            @Nonnull Map<String, Object> attributes
    ) {
        if (attributes.size() > MAX_DIAGNOSTIC_ATTRIBUTES) {
            return "diagnostic_attribute_count_exceeded";
        }
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            if (!safeIdentifier(entry.getKey())) {
                return "diagnostic_attribute_key_invalid";
            }
            Object value = entry.getValue();
            if (value instanceof String text) {
                if (text.length() > MAX_DIAGNOSTIC_ATTRIBUTE_VALUE_LENGTH) {
                    return "diagnostic_attribute_value_too_long";
                }
            } else if (!safeDiagnosticNumber(value)
                    && !(value instanceof Boolean)) {
                return "diagnostic_attribute_value_invalid";
            }
        }
        return null;
    }

    private static boolean safeDiagnosticNumber(@Nullable Object value) {
        if (value instanceof Byte || value instanceof Short
                || value instanceof Integer || value instanceof Long) {
            return true;
        }
        if (value instanceof Float number) {
            return Float.isFinite(number);
        }
        if (value instanceof Double number) {
            return Double.isFinite(number);
        }
        if (value instanceof BigInteger || value instanceof BigDecimal) {
            return value.toString().length()
                    <= MAX_DIAGNOSTIC_ATTRIBUTE_NUMBER_LENGTH;
        }
        return false;
    }

    @Nullable
    private static String validateDiagnosticAttachment(@Nullable TelemetryDiagnosticAttachment attachment) {
        if (attachment == null) return "diagnostic_attachment_missing";
        if (!safeIdentifier(attachment.attachmentId())) {
            return "diagnostic_attachment_id_invalid";
        }
        if (!safeIdentifier(attachment.kind())) {
            return "diagnostic_attachment_kind_invalid";
        }
        if (!safeFileName(attachment.fileName())) {
            return "diagnostic_attachment_file_name_invalid";
        }
        if (!safeContentType(attachment.contentType())) {
            return "diagnostic_attachment_content_type_invalid";
        }
        byte[] decoded;
        try {
            decoded = switch (attachment.contentEncoding()) {
                case "identity" -> attachment.content().getBytes(StandardCharsets.UTF_8);
                case "base64" -> {
                    byte[] value = Base64.getDecoder().decode(attachment.content());
                    if (!Base64.getEncoder().encodeToString(value).equals(attachment.content())) {
                        throw new IllegalArgumentException("non-canonical base64");
                    }
                    yield value;
                }
                default -> null;
            };
        } catch (IllegalArgumentException failure) {
            return "diagnostic_attachment_encoding_invalid";
        }
        if (decoded == null) return "diagnostic_attachment_encoding_invalid";
        if (decoded.length > MAX_DIAGNOSTIC_ATTACHMENT_BYTES) {
            return "diagnostic_attachment_too_large";
        }
        if (decoded.length != attachment.byteCount()) {
            return "diagnostic_attachment_byte_count_mismatch";
        }
        if (!sha256(decoded).equals(attachment.sha256())) {
            return "diagnostic_attachment_sha256_mismatch";
        }
        return null;
    }

    private static boolean safeIdentifier(@Nullable String value) {
        return value != null
                && value.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");
    }

    private static boolean safeFileName(@Nullable String value) {
        return value != null
                && value.matches("[A-Za-z0-9][A-Za-z0-9._ -]{0,127}")
                && !value.endsWith(".") && !value.endsWith(" ");
    }

    private static boolean safeContentType(@Nullable String value) {
        return value != null && value.length() <= 128
                && value.matches("[A-Za-z0-9!#$&^_.+-]+/[A-Za-z0-9!#$&^_.+-]+");
    }

    @Nonnull
    private static TelemetryDiagnosticBundleResult diagnosticResult(
            @Nonnull TelemetryDiagnosticBundleResult.Status status,
            @Nullable String detail
    ) {
        return new TelemetryDiagnosticBundleResult(status, detail);
    }

    private static boolean isBlank(@Nullable String value) {
        return value == null || value.isBlank();
    }

    @Nonnull
    private static String sha256(@Nonnull byte[] bytes) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder result = new StringBuilder(hash.length * 2);
            for (byte value : hash) result.append(String.format(Locale.ROOT, "%02x", value));
            return result.toString();
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 is unavailable", failure);
        }
    }

    public int statsHeartbeatDelaySeconds(boolean firstHeartbeat) {
        int seed = Math.floorMod(serverId.hashCode(), Integer.MAX_VALUE);
        if (firstHeartbeat) {
            int span = STATS_HEARTBEAT_STARTUP_MAX_SECONDS - STATS_HEARTBEAT_STARTUP_MIN_SECONDS + 1;
            return STATS_HEARTBEAT_STARTUP_MIN_SECONDS + (seed % span);
        }
        int jitter = (seed % (STATS_HEARTBEAT_JITTER_SECONDS * 2 + 1)) - STATS_HEARTBEAT_JITTER_SECONDS;
        return Math.max(MIN_STATS_HEARTBEAT_INTERVAL_SECONDS, statsHeartbeatIntervalSeconds() + jitter);
    }

    public void applyStatsHeartbeatIntervalHint(int intervalSeconds) {
        if (intervalSeconds <= 0) {
            return;
        }
        statsHeartbeatIntervalSeconds.set(Math.max(
                MIN_STATS_HEARTBEAT_INTERVAL_SECONDS,
                Math.min(intervalSeconds, MAX_STATS_HEARTBEAT_INTERVAL_SECONDS)
        ));
    }

    @Nonnull
    public List<TelemetryProjectRegistration> projects() {
        return projectCatalog.snapshot().projects();
    }

    @Nonnull
    public List<TelemetryProjectRegistration> manualReportProjects() {
        return projectCatalog.snapshot().manualReportProjects();
    }

    @Nonnull
    public TelemetryProjectCatalog projectCatalog() {
        return projectCatalog;
    }

    /** Reconciles the complete runtime project set while retaining the last valid snapshot on failure. */
    public boolean reconcileProjects(@Nonnull List<TelemetryProjectRegistration> registrations) {
        return projectCatalog.reconcile(registrations);
    }

    public boolean reconcileProjects(@Nonnull List<TelemetryProjectRegistration> registrations,
                                     @Nonnull List<TelemetryProjectRegistration> manualCandidates) {
        return projectCatalog.reconcile(registrations, manualCandidates);
    }

    @Nonnull
    public List<CrashReportEnvelope.LoadedModMetadata> loadedMods() {
        return loadedMods;
    }

    @Nonnull
    public String lastFlushResult() {
        return lastFlushResult;
    }

    public boolean flushInProgress() {
        synchronized (asyncFlushMonitor) {
            return asyncFlushInProgress;
        }
    }

    public boolean isProjectEnabled(@Nonnull String projectId) {
        TelemetryProjectRegistration project = findProject(projectId);
        return project != null && isProjectRuntimeEnabled(project);
    }

    public boolean isCrashEnabled(@Nonnull String projectId) {
        TelemetryProjectRegistration project = findProject(projectId);
        return project != null && isCrashRuntimeEnabled(project);
    }

    public boolean isErrorEventsEnabled(@Nonnull String projectId) {
        TelemetryProjectRegistration project = findProject(projectId);
        return project != null && areErrorEventsRuntimeEnabled(project);
    }

    public boolean isDiagnosticsEnabled(@Nonnull String projectId) {
        TelemetryProjectRegistration project = findProject(projectId);
        return project != null && isDiagnosticsRuntimeEnabled(project);
    }

    public boolean isLifecycleEventsEnabled(@Nonnull String projectId) {
        TelemetryProjectRegistration project = findProject(projectId);
        return project != null && areLifecycleEventsRuntimeEnabled(project);
    }

    public boolean isPerformanceEnabled(@Nonnull String projectId) {
        TelemetryProjectRegistration project = findProject(projectId);
        return project != null && isPerformanceRuntimeEnabled(project);
    }

    public boolean isUsageEnabled(@Nonnull String projectId) {
        TelemetryProjectRegistration project = findProject(projectId);
        return project != null && isUsageRuntimeEnabled(project);
    }

    public boolean isStatsEnabled(@Nonnull String projectId) {
        TelemetryProjectRegistration project = findProject(projectId);
        return project != null && isStatsRuntimeEnabled(project);
    }

    @Nullable
    public String serverClaimToken() {
        return serverClaimToken;
    }

    public boolean saveServerClaimToken(@Nonnull String claimToken) {
        TelemetryServerIdentity.Identity identity = TelemetryServerIdentity.saveClaimToken(
                dataPaths.serverIdentityFile(),
                dataPaths.serverIdFile(),
                claimToken,
                logger
        );
        if (identity == null) {
            return false;
        }
        serverClaimToken = identity.serverClaimToken();
        return serverClaimToken != null;
    }

    public boolean isBreadcrumbsEnabled(@Nonnull String projectId) {
        TelemetryProjectRegistration project = findProject(projectId);
        return project != null && areBreadcrumbsRuntimeEnabled(project);
    }

    public void setProjectEnabled(@Nonnull String projectId, boolean enabled) {
        projectEnabledOverrides.computeIfAbsent(normalizeProjectId(projectId), ignored -> new AtomicBoolean()).set(enabled);
        if (!enabled) {
            clearBreadcrumbs(projectId);
        }
    }

    public void setCrashEnabled(@Nonnull String projectId, boolean enabled) {
        crashEnabledOverrides.computeIfAbsent(normalizeProjectId(projectId), ignored -> new AtomicBoolean()).set(enabled);
    }

    public void setErrorEventsEnabled(@Nonnull String projectId, boolean enabled) {
        errorEventsEnabledOverrides.computeIfAbsent(normalizeProjectId(projectId), ignored -> new AtomicBoolean()).set(enabled);
    }

    public void setDiagnosticsEnabled(@Nonnull String projectId, boolean enabled) {
        diagnosticsEnabledOverrides.computeIfAbsent(normalizeProjectId(projectId), ignored -> new AtomicBoolean()).set(enabled);
    }

    public void setLifecycleEventsEnabled(@Nonnull String projectId, boolean enabled) {
        lifecycleEventsEnabledOverrides.computeIfAbsent(normalizeProjectId(projectId), ignored -> new AtomicBoolean()).set(enabled);
    }

    public void setPerformanceEnabled(@Nonnull String projectId, boolean enabled) {
        performanceEnabledOverrides.computeIfAbsent(normalizeProjectId(projectId), ignored -> new AtomicBoolean()).set(enabled);
    }

    public void setUsageEnabled(@Nonnull String projectId, boolean enabled) {
        usageEnabledOverrides.computeIfAbsent(normalizeProjectId(projectId), ignored -> new AtomicBoolean()).set(enabled);
    }

    public void setStatsEnabled(@Nonnull String projectId, boolean enabled) {
        statsEnabledOverrides.computeIfAbsent(normalizeProjectId(projectId), ignored -> new AtomicBoolean()).set(enabled);
    }

    public void setBreadcrumbsEnabled(@Nonnull String projectId, boolean enabled) {
        breadcrumbsEnabledOverrides.computeIfAbsent(normalizeProjectId(projectId), ignored -> new AtomicBoolean()).set(enabled);
        if (!enabled) {
            clearBreadcrumbs(projectId);
        }
    }

    public boolean triggerFlushAsync() {
        return requestFlushAsync("manual", null);
    }

    public boolean triggerFlushAsync(@Nullable String projectId) {
        return requestFlushAsync("manual", projectId);
    }

    public void recordBreadcrumb(@Nonnull String projectId,
                                 @Nonnull String category,
                                 @Nonnull String detail) {
        recordBreadcrumb(projectId, TelemetryBreadcrumbContext.of(category, detail));
    }

    public void recordBreadcrumb(@Nonnull String projectId,
                                 @Nonnull TelemetryBreadcrumbContext context) {
        if (!enabled.get()) {
            return;
        }
        TelemetryProjectRegistration project = findProject(projectId);
        if (project == null || !isProjectRuntimeEnabled(project)) {
            return;
        }
        if (!areBreadcrumbsRuntimeEnabled(project)) {
            return;
        }
        breadcrumbs.record(project.projectId(), context);
    }

    public void clearBreadcrumbs(@Nonnull String projectId) {
        TelemetryProjectRegistration project = findProject(projectId);
        breadcrumbs.clear(project == null ? projectId : project.projectId());
    }

    public void captureSetupFailure(@Nonnull String projectId, @Nullable Throwable throwable) {
        captureExplicitProject(projectId, SOURCE_SETUP_FAILURE, throwable);
    }

    public void captureStartFailure(@Nonnull String projectId, @Nullable Throwable throwable) {
        captureExplicitProject(projectId, SOURCE_START_FAILURE, throwable);
    }

    public void recordError(@Nonnull String projectId,
                            @Nonnull String eventName,
                            @Nullable Throwable throwable,
                            @Nullable String detail) {
        recordErrorWithContext(projectId, eventName, throwable, TelemetryEventContext.builder().detail(detail).build());
    }

    public void recordErrorWithContext(@Nonnull String projectId,
                                       @Nonnull String eventName,
                                       @Nullable Throwable throwable,
                                       @Nullable TelemetryEventContext context) {
        if (!enabled.get()) {
            return;
        }
        TelemetryProjectRegistration project = findProject(projectId);
        if (project == null
                || !isProjectRuntimeEnabled(project)
                || !areErrorEventsRuntimeEnabled(project)
                || project.resolveEventDeliveryTarget(settings) == null) {
            return;
        }
        TelemetryEventContext normalizedContext = normalizeContext(context);
        CrashReportEnvelope.RuntimeMetadata runtimeMetadata = CrashReportEnvelope.RuntimeMetadata.capture(loadedMods);
        LinkedHashMap<String, Object> attributes = new LinkedHashMap<>();
        putDetail(attributes, normalizedContext);
        if (throwable != null) {
            attributes.put("throwableType", throwable.getClass().getName());
            attributes.put("throwableMessage", throwable.getMessage() == null ? "<empty>" : throwable.getMessage());
        }
        LinkedHashMap<String, Object> details = new LinkedHashMap<>(
                project.events().errors().sanitizeDetails(eventName, normalizedContext.details())
        );
        putBreadcrumbDetails(project, details);
        TelemetryEventEnvelope event = TelemetryEventEnvelope.error(
                project.projectId(),
                project.displayName(),
                "runtime_api",
                sessionId,
                serverId,
                eventName,
                project.pluginIdentifier(),
                project.pluginVersion(),
                normalizedContext.worldName(),
                severityOrDefault(normalizedContext, throwable == null ? TelemetryEventEnvelope.SEVERITY_WARNING : TelemetryEventEnvelope.SEVERITY_ERROR),
                environmentFor(project, runtimeMetadata),
                attributes,
                details,
                normalizedContext,
                runtimeMetadata
        );
        if (!eventStoreFor(project).persist(event)) {
            logWarning("Failed to store telemetry error event for project " + project.projectId() + ".", null);
            return;
        }
        requestFlushAsync("event", project.projectId());
    }

    public void recordLifecycle(@Nonnull String projectId,
                                @Nonnull String eventName,
                                int durationMs,
                                boolean success,
                                @Nullable String detail) {
        recordLifecycleWithContext(projectId, eventName, durationMs, success, TelemetryEventContext.builder().detail(detail).build());
    }

    public void recordLifecycleWithContext(@Nonnull String projectId,
                                           @Nonnull String eventName,
                                           int durationMs,
                                           boolean success,
                                           @Nullable TelemetryEventContext context) {
        if (!enabled.get()) {
            return;
        }
        TelemetryProjectRegistration project = findProject(projectId);
        if (project == null
                || !isProjectRuntimeEnabled(project)
                || !areLifecycleEventsRuntimeEnabled(project)
                || project.resolveEventDeliveryTarget(settings) == null) {
            return;
        }
        TelemetryEventContext normalizedContext = normalizeContext(context);
        CrashReportEnvelope.RuntimeMetadata runtimeMetadata = CrashReportEnvelope.RuntimeMetadata.capture(loadedMods);
        LinkedHashMap<String, Object> attributes = new LinkedHashMap<>();
        putDetail(attributes, normalizedContext);
        LinkedHashMap<String, Object> details = new LinkedHashMap<>();
        if (!success) {
            putBreadcrumbDetails(project, details);
        }
        TelemetryEventEnvelope event = TelemetryEventEnvelope.lifecycle(
                project.projectId(),
                project.displayName(),
                "runtime_api",
                sessionId,
                serverId,
                eventName,
                project.pluginIdentifier(),
                project.pluginVersion(),
                normalizedContext.worldName(),
                success,
                durationMs,
                environmentFor(project, runtimeMetadata),
                attributes,
                details,
                normalizedContext,
                runtimeMetadata
        );
        if (!eventStoreFor(project).persist(event)) {
            logWarning("Failed to store telemetry lifecycle event for project " + project.projectId() + ".", null);
            return;
        }
        requestFlushAsync("event", project.projectId());
    }

    public void recordPerformance(@Nonnull String projectId,
                                  @Nonnull String eventName,
                                  int durationMs,
                                  @Nullable Double metricValue,
                                  @Nullable String detail) {
        recordPerformanceWithContext(projectId, eventName, durationMs, metricValue, TelemetryEventContext.builder().detail(detail).build());
    }

    public void recordPerformanceWithContext(@Nonnull String projectId,
                                             @Nonnull String eventName,
                                             int durationMs,
                                             @Nullable Double metricValue,
                                             @Nullable TelemetryEventContext context) {
        if (!enabled.get()) {
            return;
        }
        TelemetryProjectRegistration project = findProject(projectId);
        if (project == null || !isProjectRuntimeEnabled(project) || project.resolveEventDeliveryTarget(settings) == null) {
            return;
        }
        if (!isPerformanceRuntimeEnabled(project) || durationMs < project.performance().thresholdMs()) {
            return;
        }
        if (project.performance().sampleRate() < 1.0d && ThreadLocalRandom.current().nextDouble() > project.performance().sampleRate()) {
            return;
        }
        TelemetryEventContext normalizedContext = normalizeContext(context);
        CrashReportEnvelope.RuntimeMetadata runtimeMetadata = CrashReportEnvelope.RuntimeMetadata.capture(loadedMods);
        LinkedHashMap<String, Object> attributes = new LinkedHashMap<>();
        putDetail(attributes, normalizedContext);
        attributes.put("thresholdMs", project.performance().thresholdMs());
        Map<String, Object> details = project.performance().sanitizeDetails(eventName, normalizedContext.details());
        TelemetryEventEnvelope event = TelemetryEventEnvelope.performance(
                project.projectId(),
                project.displayName(),
                "runtime_api",
                sessionId,
                serverId,
                eventName,
                project.pluginIdentifier(),
                project.pluginVersion(),
                normalizedContext.worldName(),
                durationMs,
                metricValue,
                environmentFor(project, runtimeMetadata),
                attributes,
                details,
                normalizedContext,
                runtimeMetadata
        );
        if (!eventStoreFor(project).persist(event)) {
            logWarning("Failed to store telemetry performance event for project " + project.projectId() + ".", null);
            return;
        }
        requestFlushAsync("event", project.projectId());
    }

    public void recordUsage(@Nonnull String projectId,
                            @Nonnull String eventName,
                            @Nullable String detail) {
        recordUsageWithContext(projectId, eventName, TelemetryEventContext.builder().detail(detail).build());
    }

    public void recordUsageWithContext(@Nonnull String projectId,
                                       @Nonnull String eventName,
                                       @Nullable TelemetryEventContext context) {
        if (!enabled.get()) {
            return;
        }
        TelemetryProjectRegistration project = findProject(projectId);
        if (project == null || !isProjectRuntimeEnabled(project) || project.resolveEventDeliveryTarget(settings) == null) {
            return;
        }
        if (!isUsageRuntimeEnabled(project) || !project.usage().allows(eventName)) {
            return;
        }
        TelemetryEventContext normalizedContext = normalizeContext(context);
        CrashReportEnvelope.RuntimeMetadata runtimeMetadata = CrashReportEnvelope.RuntimeMetadata.capture(loadedMods);
        LinkedHashMap<String, Object> attributes = new LinkedHashMap<>();
        putDetail(attributes, normalizedContext);
        Map<String, Object> details = project.usage().sanitizeDetails(eventName, normalizedContext.details());
        TelemetryEventEnvelope event = TelemetryEventEnvelope.usage(
                project.projectId(),
                project.displayName(),
                "runtime_api",
                sessionId,
                serverId,
                eventName,
                project.pluginIdentifier(),
                project.pluginVersion(),
                normalizedContext.worldName(),
                environmentFor(project, runtimeMetadata),
                attributes,
                details,
                normalizedContext,
                runtimeMetadata
        );
        if (!eventStoreFor(project).persist(event)) {
            logWarning("Failed to store telemetry usage event for project " + project.projectId() + ".", null);
            return;
        }
        requestFlushAsync("event", project.projectId());
    }

    public void recordStats(@Nonnull String projectId,
                            @Nonnull String eventName,
                            @Nullable String detail) {
        recordStatsWithContext(projectId, eventName, TelemetryEventContext.stats().detail(detail).build());
    }

    public void recordStatsWithContext(@Nonnull String projectId,
                                       @Nonnull String eventName,
                                       @Nullable TelemetryEventContext context) {
        if (!enabled.get()) {
            return;
        }
        TelemetryProjectRegistration project = findProject(projectId);
        if (project == null || !isProjectRuntimeEnabled(project) || project.resolveEventDeliveryTarget(settings) == null) {
            return;
        }
        if (!isStatsRuntimeEnabled(project) || !project.stats().allows(eventName)) {
            return;
        }
        TelemetryEventContext normalizedContext = normalizeContext(context);
        CrashReportEnvelope.RuntimeMetadata runtimeMetadata = CrashReportEnvelope.RuntimeMetadata.capture(loadedMods);
        LinkedHashMap<String, Object> attributes = new LinkedHashMap<>();
        putDetail(attributes, normalizedContext);
        LinkedHashMap<String, Object> details = new LinkedHashMap<>(project.stats().sanitizeDetails(eventName, normalizedContext.details()));
        putIfPresent(details, "serverClaimToken", serverClaimToken);
        TelemetryEventEnvelope event = TelemetryEventEnvelope.stats(
                project.projectId(),
                project.displayName(),
                "runtime_api",
                sessionId,
                serverId,
                eventName,
                project.pluginIdentifier(),
                project.pluginVersion(),
                normalizedContext.worldName(),
                environmentFor(project, runtimeMetadata),
                attributes,
                details,
                normalizedContext,
                runtimeMetadata
        );
        if (!eventStoreFor(project).persist(event)) {
            logWarning("Failed to store telemetry stats event for project " + project.projectId() + ".", null);
            return;
        }
        requestFlushAsync("event", project.projectId());
    }

    public void recordAggregateStatsHeartbeat(@Nonnull List<TelemetryProjectRegistration> projects,
                                              int playersOnline) {
        recordAggregateStatsHeartbeat(projects, TelemetryPlayerIntervalSnapshot.single(playersOnline));
    }

    public void recordAggregateStatsHeartbeat(@Nonnull List<TelemetryProjectRegistration> projects,
                                              @Nonnull TelemetryPlayerIntervalSnapshot players) {
        if (!enabled.get() || projects.isEmpty()) {
            return;
        }
        LinkedHashMap<CrashReportClient.DeliveryTarget, ArrayList<TelemetryProjectRegistration>> projectsByTarget = new LinkedHashMap<>();
        for (TelemetryProjectRegistration project : projects) {
            CrashReportClient.DeliveryTarget eventTarget = project == null
                    ? null
                    : project.resolveEventDeliveryTarget(settings);
            if (project == null
                    || findProject(project.projectId()) == null
                    || !isProjectRuntimeEnabled(project)
                    || eventTarget == null
                    || !isStatsRuntimeEnabled(project)
                    || !project.stats().allows("heartbeat")) {
                continue;
            }
            projectsByTarget
                    .computeIfAbsent(eventTarget.normalize(), ignored -> new ArrayList<>())
                    .add(project);
        }
        if (projectsByTarget.isEmpty()) {
            return;
        }
        for (List<TelemetryProjectRegistration> targetProjects : projectsByTarget.values()) {
            TelemetryProjectRegistration carrier = targetProjects.getFirst();
            CrashReportEnvelope.RuntimeMetadata runtimeMetadata = CrashReportEnvelope.RuntimeMetadata.capture(loadedMods);
            TelemetryEventContext context = TelemetryEventContext.stats()
                    .featureKey("stats")
                    .entryPoint("heartbeat")
                    .runtimeSide("server")
                    .detail("playersOnline", Math.max(0, players.currentPlayers()))
                    .detail("maxPlayersSinceLastReport", Math.max(0, players.maxPlayersSinceLastReport()))
                    .detail("avgPlayersSinceLastReport", Math.max(0.0, players.avgPlayersSinceLastReport()))
                    .build();
            TelemetryEventContext normalizedContext = normalizeContext(context);
            LinkedHashMap<String, Object> attributes = new LinkedHashMap<>();
            putDetail(attributes, normalizedContext);
            LinkedHashMap<String, Object> details = new LinkedHashMap<>();
            details.put("playersOnline", Math.max(0, players.currentPlayers()));
            details.put("maxPlayersSinceLastReport", Math.max(0, players.maxPlayersSinceLastReport()));
            details.put("avgPlayersSinceLastReport", Math.max(0.0, players.avgPlayersSinceLastReport()));
            details.put("projects", aggregateProjectDetails(targetProjects));
            putIfPresent(details, "serverClaimToken", serverClaimToken);
            TelemetryEventEnvelope event = TelemetryEventEnvelope.stats(
                    carrier.projectId(),
                    carrier.displayName(),
                    "runtime_stats",
                    sessionId,
                    serverId,
                    "heartbeat",
                    carrier.pluginIdentifier(),
                    carrier.pluginVersion(),
                    normalizedContext.worldName(),
                    environmentFor(carrier, runtimeMetadata),
                    attributes,
                    details,
                    normalizedContext,
                    runtimeMetadata
            );
            if (!eventStoreFor(carrier).persist(event)) {
                logWarning("Failed to store aggregate telemetry stats heartbeat for project " + carrier.projectId() + ".", null);
                continue;
            }
            requestFlushAsync("event", carrier.projectId());
        }
    }

    public boolean captureTestReport(@Nonnull String projectId, @Nullable String detail) {
        TelemetryProjectRegistration project = findProject(projectId);
        if (project == null || !isProjectRuntimeEnabled(project)) {
            return false;
        }
        String suffix = detail == null || detail.isBlank() ? "" : ": " + detail.trim();
        recordBreadcrumb(project.projectId(), "telemetry", "Manual test report requested" + suffix + ".");
        captureExplicitProject(
                project.projectId(),
                SOURCE_MANUAL_TEST,
                new RuntimeException("Manual telemetry test for " + project.displayName() + suffix)
        );
        return true;
    }

    @Nonnull
    private static List<Map<String, Object>> aggregateProjectDetails(@Nonnull List<TelemetryProjectRegistration> projects) {
        ArrayList<Map<String, Object>> details = new ArrayList<>(projects.size());
        for (TelemetryProjectRegistration project : projects) {
            LinkedHashMap<String, Object> projectDetails = new LinkedHashMap<>();
            projectDetails.put("projectId", project.projectId());
            projectDetails.put("projectDisplayName", project.displayName());
            projectDetails.put("pluginIdentifier", project.pluginIdentifier());
            projectDetails.put("pluginVersion", project.pluginVersion());
            details.add(Map.copyOf(projectDetails));
        }
        return List.copyOf(details);
    }

    public void captureExceptionalWorldRemoval(@Nullable World world,
                                               @Nullable RemoveWorldEvent.RemovalReason removalReason) {
        if (!enabled.get()
                || world == null
                || removalReason != RemoveWorldEvent.RemovalReason.EXCEPTIONAL
                || world.getFailureException() == null) {
            return;
        }
        captureExceptionalWorldRemoval(
                world.getFailureException(),
                world.getName(),
                removalReason.name(),
                world.getPossibleFailureCause() == null ? null : world.getPossibleFailureCause().toString()
        );
    }

    public boolean captureExceptionalWorldRemoval(@Nullable Throwable throwable,
                                                  @Nullable String worldName,
                                                  @Nullable String removalReason,
                                                  @Nullable String possibleFailureCause) {
        if (!enabled.get()
                || throwable == null
                || !RemoveWorldEvent.RemovalReason.EXCEPTIONAL.name().equals(removalReason)) {
            return false;
        }
        captureAcrossProjects(
                SOURCE_EXCEPTIONAL_WORLD_REMOVAL,
                throwable,
                Thread.currentThread(),
                worldName,
                removalReason,
                pluginIdentifierFrom(possibleFailureCause)
        );
        return true;
    }

    /** Captures an exceptional world removal for one explicit project scope. */
    public boolean captureExceptionalWorldRemovalForProject(@Nonnull String projectId,
                                                            @Nullable Throwable throwable,
                                                            @Nullable String worldName,
                                                            @Nullable String removalReason,
                                                            @Nullable String possibleFailureCause) {
        if (!enabled.get()
                || throwable == null
                || !RemoveWorldEvent.RemovalReason.EXCEPTIONAL.name().equals(removalReason)) {
            return false;
        }
        TelemetryProjectRegistration project = findProject(projectId);
        if (project == null
                || !isProjectRuntimeEnabled(project)
                || !isCrashRuntimeEnabled(project)
                || !project.capturesSource(SOURCE_EXCEPTIONAL_WORLD_REMOVAL)) {
            return false;
        }
        try {
            CrashAttribution.AttributionResult attribution = CrashAttribution.explicit(
                    throwable,
                    project.pluginIdentifier(),
                    project.packagePrefixes()
            );
            persist(
                    project,
                    SOURCE_EXCEPTIONAL_WORLD_REMOVAL,
                    attribution,
                    throwable,
                    Thread.currentThread().getName(),
                    worldName,
                    removalReason,
                    possibleFailureCause
            );
            requestFlushAsync("capture", project.projectId());
            return true;
        } catch (Throwable captureFailure) {
            logWarning("Explicit exceptional world removal capture failed for project " + projectId + ".", captureFailure);
            return false;
        }
    }

    @Nullable
    private static PluginIdentifier pluginIdentifierFrom(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return PluginIdentifier.fromString(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    @Nonnull
    public ManualReportEnvelope.CreateResult submitManualReport(@Nonnull String projectId,
                                                                @Nonnull ManualReportSubmission submission,
                                                                @Nullable PlayerReportRuntimeContext playerContext) {
        TelemetryProjectRegistration project = findManualReportProject(projectId);
        if (project == null) {
            return new ManualReportEnvelope.CreateResult(null, List.of("project_not_found"));
        }
        PlayerReportRuntimeContext safeContext = playerContext == null ? PlayerReportRuntimeContext.UNKNOWN : playerContext;
        TelemetryRuntimeSettings.ManualReportSettings reportSettings = settings.manualReports();
        boolean includeLoadedMods = submission.includeLoadedModList() && reportSettings.allowLoadedModList();
        boolean includeDiagnostics = submission.includeDiagnostics() && reportSettings.allowDiagnostics();
        List<CrashReportEnvelope.LoadedModMetadata> reportLoadedMods = includeLoadedMods
                ? (safeContext.loadedMods().isEmpty() ? loadedMods : safeContext.loadedMods())
                : List.of();
        CrashReportEnvelope.RuntimeMetadata runtimeMetadata = CrashReportEnvelope.RuntimeMetadata.capture(reportLoadedMods);
        List<ManualReportAttachment> attachments = collectManualReportAttachments(submission);
        ManualReportEnvelope.CreateResult result = ManualReportEnvelope.createWithAttachments(
                project,
                settings,
                submission,
                safeContext.toSnapshot(includeLoadedMods, includeDiagnostics),
                attachments,
                environmentFor(project, runtimeMetadata),
                runtimeMetadata,
                sessionId,
                serverId
        );
        if (!result.accepted()) {
            return result;
        }
        try {
            ManualReportEnvelope envelope = result.envelope();
            ManualReportStore store = manualReportStoreFor(project);
            if (reportSettings.manualReviewRequired()) {
                store.saveForReview(envelope);
            } else {
                store.savePending(envelope);
                requestFlushAsync("manual_report", project.projectId());
            }
            manualReportReceiptStore.saveReceipt(
                    dataPaths.manualReportReceiptsFile(),
                    new ManualReportReceiptStore.Receipt(
                            envelope.reportId(),
                            envelope.projectId(),
                            envelope.reportKind(),
                            envelope.title(),
                            envelope.submittedAtUtc(),
                            result.followUpToken() == null ? "" : result.followUpToken(),
                            reportSettings.manualReviewRequired() ? "review" : "queued",
                            null
                    )
            );
            return result;
        } catch (Exception ex) {
            logWarning("Failed to store manual report for project " + project.projectId() + ".", ex);
            return new ManualReportEnvelope.CreateResult(null, List.of("store_failed"));
        }
    }

    public int pendingReports(@Nullable String projectId) {
        return totalPendingCount(projectId);
    }

    @Nonnull
    public String manualReportReceiptStatus(@Nonnull String reportId) {
        return manualReportReceiptStore.findReceipt(dataPaths.manualReportReceiptsFile(), reportId)
                .map(ManualReportReceiptStore.Receipt::lastKnownStatus)
                .orElse("queued");
    }

    @Nonnull
    public List<ManualReportEnvelope> manualReportsForReview(int maxReportsPerProject) {
        ArrayList<ManualReportEnvelope> reports = new ArrayList<>();
        for (TelemetryProjectRegistration project : retainedManualReportProjects()) {
            ManualReportStore store = manualReportStoreFor(project);
            for (ManualReportStore.PendingReport pending : store.listReviewReports(project.projectId(), maxReportsPerProject)) {
                ManualReportEnvelope envelope = parseManualReport(pending.payload());
                if (envelope != null) {
                    reports.add(envelope);
                }
            }
        }
        return List.copyOf(reports);
    }

    public boolean approveManualReport(@Nonnull String reportId) {
        for (TelemetryProjectRegistration project : retainedManualReportProjects()) {
            ManualReportStore store = manualReportStoreFor(project);
            if (store.approveReviewed(project.projectId(), reportId).isPresent()) {
                requestFlushAsync("manual_report_approved", project.projectId());
                return true;
            }
        }
        return false;
    }

    public boolean rejectManualReport(@Nonnull String reportId) {
        for (TelemetryProjectRegistration project : retainedManualReportProjects()) {
            ManualReportStore store = manualReportStoreFor(project);
            ManualReportEnvelope envelope = findReviewEnvelope(store, project.projectId(), reportId);
            if (store.rejectReviewed(project.projectId(), reportId).isPresent()) {
                if (envelope != null) {
                    store.appendSubmittedAudit(envelope, "rejected", false);
                }
                return true;
            }
        }
        return false;
    }

    @Nonnull
    public List<String> submittedManualReportAuditLines(int maxLines) {
        try {
            if (!Files.isRegularFile(dataPaths.submittedManualReportsLog())) {
                return List.of();
            }
            List<String> lines = Files.readAllLines(dataPaths.submittedManualReportsLog(), StandardCharsets.UTF_8);
            int start = Math.max(0, lines.size() - Math.max(1, maxLines));
            return List.copyOf(lines.subList(start, lines.size()));
        } catch (Exception ignored) {
            return List.of();
        }
    }

    @Nullable
    public TelemetryProjectRegistration findProject(@Nonnull String projectId) {
        return projectCatalog.find(projectId);
    }

    @Nullable
    public TelemetryProjectRegistration findManualReportProject(@Nonnull String projectId) {
        for (TelemetryProjectRegistration project : manualReportProjects()) {
            if (project.projectId().equalsIgnoreCase(projectId)) {
                return project;
            }
        }
        return null;
    }

    private boolean isProjectRuntimeEnabled(@Nonnull TelemetryProjectRegistration project) {
        AtomicBoolean override = projectEnabledOverrides.get(normalizeProjectId(project.projectId()));
        return override == null ? project.isEnabled() : override.get();
    }

    private boolean areBreadcrumbsRuntimeEnabled(@Nonnull TelemetryProjectRegistration project) {
        AtomicBoolean override = breadcrumbsEnabledOverrides.get(normalizeProjectId(project.projectId()));
        return override == null ? project.events().breadcrumbs().enabled() : override.get();
    }

    private boolean isCrashRuntimeEnabled(@Nonnull TelemetryProjectRegistration project) {
        AtomicBoolean override = crashEnabledOverrides.get(normalizeProjectId(project.projectId()));
        return override == null ? project.isCrashTelemetryEnabled() : override.get();
    }

    private boolean areErrorEventsRuntimeEnabled(@Nonnull TelemetryProjectRegistration project) {
        AtomicBoolean override = errorEventsEnabledOverrides.get(normalizeProjectId(project.projectId()));
        return override == null ? project.events().errors().enabled() : override.get();
    }

    private boolean isDiagnosticsRuntimeEnabled(@Nonnull TelemetryProjectRegistration project) {
        AtomicBoolean override = diagnosticsEnabledOverrides.get(normalizeProjectId(project.projectId()));
        return override == null ? project.diagnostics().enabled() : override.get();
    }

    private boolean areLifecycleEventsRuntimeEnabled(@Nonnull TelemetryProjectRegistration project) {
        AtomicBoolean override = lifecycleEventsEnabledOverrides.get(normalizeProjectId(project.projectId()));
        return override == null ? project.events().lifecycle().enabled() : override.get();
    }

    private boolean isPerformanceRuntimeEnabled(@Nonnull TelemetryProjectRegistration project) {
        AtomicBoolean override = performanceEnabledOverrides.get(normalizeProjectId(project.projectId()));
        return override == null ? project.performance().enabled() : override.get();
    }

    private boolean isUsageRuntimeEnabled(@Nonnull TelemetryProjectRegistration project) {
        AtomicBoolean override = usageEnabledOverrides.get(normalizeProjectId(project.projectId()));
        return override == null ? project.usage().enabled() : override.get();
    }

    private boolean isStatsRuntimeEnabled(@Nonnull TelemetryProjectRegistration project) {
        AtomicBoolean override = statsEnabledOverrides.get(normalizeProjectId(project.projectId()));
        return override == null ? project.stats().enabled() : override.get();
    }

    @Nonnull
    private static String normalizeProjectId(@Nonnull String projectId) {
        return projectId.trim().toLowerCase(Locale.ROOT);
    }

    @Nonnull
    public FlushSummary flushPendingReportsNow(@Nonnull String reason) {
        return flushPendingReportsNow(reason, null);
    }

    @Nonnull
    public synchronized FlushSummary flushPendingReportsNow(@Nonnull String reason, @Nullable String projectIdFilter) {
        if (!enabled.get()) {
            FlushSummary summary = new FlushSummary(0, 0, totalPendingCount(projectIdFilter), "disabled");
            updateFlushStatus(reason, summary, null);
            return summary;
        }

        int attempted = 0;
        int uploaded = 0;
        String lastFailure = null;
        try {
            for (TelemetryProjectRegistration project : matchingProjects(projectIdFilter)) {
                if (!isProjectRuntimeEnabled(project)) {
                    continue;
                }
                CrashReportClient.DeliveryTarget target = project.resolveDeliveryTarget(settings);
                if (target != null && !target.endpoint().isBlank()) {
                    CrashReportStore store = storeFor(project);
                    List<CrashReportStore.PendingReport> pendingReports = store.listPendingReports(settings.maxUploadsPerFlush());
                    if (!pendingReports.isEmpty()) {
                        String cooldownFailure = uploadCooldownFailure(target, reason);
                        if (cooldownFailure != null) {
                            lastFailure = cooldownFailure;
                        } else {
                            for (CrashReportStore.PendingReport pending : pendingReports) {
                                attempted++;
                                CrashReportClient.UploadResult uploadResult = client.upload(target, pending.payload());
                                applyUploadHints(uploadResult);
                                if (uploadResult.success()) {
                                    clearUploadRetryCooldown(target);
                                    if (store.delete(pending.path())) {
                                        uploaded++;
                                    } else {
                                        lastFailure = "Uploaded but failed to remove local file " + pending.path().getFileName();
                                    }
                                } else {
                                    lastFailure = uploadResult.describeFailure();
                                    recordUploadFailure(target, uploadResult);
                                    break;
                                }
                            }
                        }
                    }
                }

                CrashReportClient.DeliveryTarget eventTarget = project.resolveEventDeliveryTarget(settings);
                if (eventTarget != null && !eventTarget.endpoint().isBlank()) {
                    TelemetryEventStore eventStore = eventStoreFor(project);
                    List<TelemetryEventStore.PendingEvent> pendingEvents = eventStore.listPendingEvents(settings.maxUploadsPerFlush());
                    if (!pendingEvents.isEmpty()) {
                        String cooldownFailure = uploadCooldownFailure(eventTarget, reason);
                        if (cooldownFailure != null) {
                            lastFailure = cooldownFailure;
                        } else {
                            for (TelemetryEventStore.PendingEvent pending : pendingEvents) {
                                attempted++;
                                CrashReportClient.UploadResult uploadResult = client.upload(eventTarget, pending.payload());
                                applyUploadHints(uploadResult);
                                if (uploadResult.success()) {
                                    clearUploadRetryCooldown(eventTarget);
                                    if (eventStore.delete(pending.path())) {
                                        uploaded++;
                                    } else {
                                        lastFailure = "Uploaded but failed to remove local event file " + pending.path().getFileName();
                                    }
                                } else {
                                    lastFailure = uploadResult.describeFailure();
                                    recordUploadFailure(eventTarget, uploadResult);
                                    break;
                                }
                            }
                        }
                    }
                }
            }

            for (TelemetryProjectRegistration project : matchingManualReportProjects(projectIdFilter)) {
                CrashReportClient.DeliveryTarget reportTarget = project.resolveReportDeliveryTarget(settings);
                if (reportTarget != null && !reportTarget.endpoint().isBlank()) {
                    ManualReportStore manualStore = manualReportStoreFor(project);
                    List<ManualReportStore.PendingReport> pendingReports = manualStore.listPendingReports(project.projectId(), settings.maxUploadsPerFlush());
                    if (!pendingReports.isEmpty()) {
                        String cooldownFailure = uploadCooldownFailure(reportTarget, reason);
                        if (cooldownFailure != null) {
                            lastFailure = cooldownFailure;
                        } else {
                            for (ManualReportStore.PendingReport pending : pendingReports) {
                                attempted++;
                                ManualReportEnvelope envelope = parseManualReport(pending.payload());
                                CrashReportClient.UploadResult uploadResult = client.upload(reportTarget, pending.payload());
                                applyUploadHints(uploadResult);
                                if (uploadResult.success()) {
                                    clearUploadRetryCooldown(reportTarget);
                                    if (manualStore.delete(pending.path())) {
                                        uploaded++;
                                        updateManualReportReceiptStatus(envelope, "uploaded");
                                        appendManualReportAudit(manualStore, pending.payload(), true);
                                    } else {
                                        lastFailure = "Uploaded but failed to remove local manual report file " + pending.path().getFileName();
                                    }
                                } else {
                                    updateManualReportReceiptStatus(envelope, "upload_failed");
                                    lastFailure = uploadResult.describeFailure();
                                    recordUploadFailure(reportTarget, uploadResult);
                                    break;
                                }
                            }
                        }
                    }
                }
            }
            FlushSummary summary = new FlushSummary(attempted, uploaded, totalPendingCount(projectIdFilter), lastFailure);
            updateFlushStatus(reason, summary, lastFailure);
            return summary;
        } catch (Exception ex) {
            FlushSummary summary = new FlushSummary(attempted, uploaded, totalPendingCount(projectIdFilter), ex.getMessage());
            updateFlushStatus(reason, summary, ex.getMessage());
            logWarning("Crash telemetry flush pass failed.", ex);
            return summary;
        }
    }

    private void applyUploadHints(@Nonnull CrashReportClient.UploadResult uploadResult) {
        Integer intervalSeconds = uploadResult.recommendedHeartbeatIntervalSec();
        if (intervalSeconds == null) {
            return;
        }
        String lane = uploadResult.intakeLane();
        if (lane == null || "stats".equalsIgnoreCase(lane)) {
            applyStatsHeartbeatIntervalHint(intervalSeconds);
        }
    }

    @Nullable
    private String uploadCooldownFailure(@Nonnull CrashReportClient.DeliveryTarget target,
                                         @Nonnull String reason) {
        if (bypassesUploadRetryCooldown(reason)) {
            return null;
        }
        UploadRetryState state = uploadRetryStates.get(uploadRetryKey(target));
        if (state == null) {
            return null;
        }
        long remainingMillis = state.remainingCooldownMillis(System.currentTimeMillis());
        if (remainingMillis <= 0) {
            return null;
        }
        long remainingSeconds = Math.max(1, (remainingMillis + 999) / 1000);
        return "Upload retry cooldown active for " + remainingSeconds + "s after previous failure.";
    }

    private void recordUploadFailure(@Nonnull CrashReportClient.DeliveryTarget target,
                                     @Nonnull CrashReportClient.UploadResult uploadResult) {
        uploadRetryStates
                .computeIfAbsent(uploadRetryKey(target), ignored -> new UploadRetryState())
                .recordFailure(uploadResult, System.currentTimeMillis());
    }

    private void clearUploadRetryCooldown(@Nonnull CrashReportClient.DeliveryTarget target) {
        uploadRetryStates.remove(uploadRetryKey(target));
    }

    @Nonnull
    private static String uploadRetryKey(@Nonnull CrashReportClient.DeliveryTarget target) {
        return target.normalize().endpoint();
    }

    private static boolean bypassesUploadRetryCooldown(@Nonnull String reason) {
        return "manual".equalsIgnoreCase(reason)
                || "shutdown".equalsIgnoreCase(reason)
                || "server-verification".equalsIgnoreCase(reason);
    }

    private static final class UploadRetryState {
        private int consecutiveFailures;
        private long retryAfterMillis;

        private synchronized void recordFailure(@Nonnull CrashReportClient.UploadResult uploadResult, long nowMillis) {
            consecutiveFailures++;
            retryAfterMillis = nowMillis + retryDelayMillis(uploadResult, consecutiveFailures);
        }

        private synchronized long remainingCooldownMillis(long nowMillis) {
            return retryAfterMillis - nowMillis;
        }

        private static long retryDelayMillis(@Nonnull CrashReportClient.UploadResult uploadResult,
                                             int consecutiveFailures) {
            if (uploadResult.retryAfterSec() > 0) {
                return Math.min(MAX_UPLOAD_RETRY_COOLDOWN_MILLIS, TimeUnit.SECONDS.toMillis(uploadResult.retryAfterSec()));
            }
            int exponent = Math.min(4, Math.max(0, consecutiveFailures - 1));
            long delay = DEFAULT_UPLOAD_RETRY_COOLDOWN_MILLIS << exponent;
            return Math.min(MAX_UPLOAD_RETRY_COOLDOWN_MILLIS, delay);
        }
    }

    private void captureExplicitProject(@Nonnull String projectId,
                                        @Nonnull String source,
                                        @Nullable Throwable throwable) {
        if (!enabled.get() || throwable == null) {
            return;
        }
        TelemetryProjectRegistration project = findProject(projectId);
        if (project == null || !isProjectRuntimeEnabled(project) || !isCrashRuntimeEnabled(project) || !project.capturesSource(source)) {
            return;
        }

        try {
            CrashAttribution.AttributionResult attribution = CrashAttribution.explicit(
                    throwable,
                    project.pluginIdentifier(),
                    project.packagePrefixes()
            );
            persist(project, source, attribution, throwable, Thread.currentThread().getName(), null, null, null);
            requestFlushAsync("capture", project.projectId());
        } catch (Throwable captureFailure) {
            logWarning("Explicit crash telemetry capture failed for project " + projectId + ".", captureFailure);
        }
    }

    private void captureAcrossProjects(@Nonnull String source,
                                       @Nullable Throwable throwable,
                                       @Nullable Thread thread,
                                       @Nullable String worldName,
                                       @Nullable String worldRemovalReason,
                                       @Nullable PluginIdentifier worldFailurePluginIdentifier) {
        if (!enabled.get() || throwable == null) {
            return;
        }

        boolean capturedAny = false;
        String identifiedPluginHint = worldFailurePluginIdentifier == null ? null : worldFailurePluginIdentifier.toString();
        String threadName = thread == null ? Thread.currentThread().getName() : thread.getName();
        for (TelemetryProjectRegistration project : projects()) {
            if (!isProjectRuntimeEnabled(project) || !isCrashRuntimeEnabled(project) || !project.capturesSource(source)) {
                continue;
            }
            try {
                CrashAttribution.AttributionResult attribution = CrashAttribution.classify(
                        throwable,
                        project.ownerPluginIdentifiers(),
                        project.packagePrefixes(),
                        identifiedPluginHint,
                        projectCatalog.snapshot().ambiguousPackagePrefixes()
                );
                if (!attribution.attributed()) {
                    continue;
                }
                persist(
                        project,
                        source,
                        attribution,
                        throwable,
                        threadName,
                        worldName,
                        worldRemovalReason,
                        identifiedPluginHint
                );
                capturedAny = true;
            } catch (Throwable captureFailure) {
                logWarning("Crash telemetry capture failed for project " + project.projectId() + ".", captureFailure);
            }
        }

        if (capturedAny) {
            requestFlushAsync("capture", null);
        }
    }

    private void persist(@Nonnull TelemetryProjectRegistration project,
                         @Nonnull String source,
                         @Nonnull CrashAttribution.AttributionResult attribution,
                         @Nonnull Throwable throwable,
                         @Nonnull String threadName,
                         @Nullable String worldName,
                         @Nullable String worldRemovalReason,
                         @Nullable String worldFailurePluginIdentifier) {
        CrashReportEnvelope.RuntimeMetadata runtimeMetadata = CrashReportEnvelope.RuntimeMetadata.capture(loadedMods);
        CrashReportEnvelope envelope = CrashReportEnvelope.create(
                project.projectId(),
                project.displayName(),
                source,
                sessionId,
                serverId,
                attribution.fingerprint(),
                project.pluginIdentifier(),
                project.pluginVersion(),
                threadName,
                worldName,
                worldRemovalReason,
                worldFailurePluginIdentifier,
                environmentFor(project, runtimeMetadata),
                attribution,
                breadcrumbs.snapshot(project.projectId()),
                throwable,
                runtimeMetadata
        );
        CrashReportStore.WriteResult result = storeFor(project).persist(envelope);
        if (result == CrashReportStore.WriteResult.FAILED) {
            logWarning("Failed to store crash telemetry report for project " + project.projectId() + ".", null);
        }
    }

    @Nonnull
    private static TelemetryEventContext normalizeContext(@Nullable TelemetryEventContext context) {
        return context == null ? TelemetryEventContext.empty() : context.normalize();
    }

    private static void putDetail(@Nonnull Map<String, Object> attributes,
                                  @Nonnull TelemetryEventContext context) {
        if (context.detail() != null && !context.detail().isBlank()) {
            attributes.put("detail", context.detail().trim());
        }
    }

    private static void putIfPresent(@Nonnull Map<String, Object> details,
                                     @Nonnull String key,
                                     @Nullable Object value) {
        if (value != null) {
            details.put(key, value);
        }
    }

    private void putBreadcrumbDetails(@Nonnull TelemetryProjectRegistration project,
                                      @Nonnull Map<String, Object> details) {
        if (!areBreadcrumbsRuntimeEnabled(project)) {
            return;
        }
        List<CrashReportEnvelope.BreadcrumbEntry> snapshot = breadcrumbs.snapshot(project.projectId());
        if (!snapshot.isEmpty()) {
            details.put("breadcrumbs", snapshot);
        }
    }

    @Nonnull
    private static String severityOrDefault(@Nonnull TelemetryEventContext context,
                                            @Nonnull String fallback) {
        return context.severity() == null || context.severity().isBlank() ? fallback : context.severity();
    }

    @Nonnull
    private CrashReportEnvelope.EnvironmentSnapshot environmentFor(@Nonnull TelemetryProjectRegistration project,
                                                                   @Nonnull CrashReportEnvelope.RuntimeMetadata runtimeMetadata) {
        String key = project.projectId().toLowerCase(Locale.ROOT);
        return environmentsByProjectId.computeIfAbsent(
                key,
                ignored -> CrashReportEnvelope.EnvironmentSnapshot.capture(
                        project.projectId(),
                        project.pluginIdentifier(),
                        project.pluginVersion(),
                        project.runtimeMode(),
                        runtimeMetadata
                )
        );
    }

    private boolean requestFlushAsync(@Nonnull String reason, @Nullable String projectIdFilter) {
        if (!enabled.get()
                || flushExecutor == null
                || (matchingProjects(projectIdFilter).isEmpty() && matchingManualReportProjects(projectIdFilter).isEmpty())) {
            return false;
        }
        synchronized (asyncFlushMonitor) {
            if (shuttingDown || asyncFlushInProgress) {
                return false;
            }
            asyncFlushInProgress = true;
        }
        try {
            flushExecutor.execute(() -> {
                try {
                    flushPendingReportsNow(reason, projectIdFilter);
                } finally {
                    finishAsyncFlush();
                }
            });
            return true;
        } catch (Exception ex) {
            finishAsyncFlush();
            logWarning("Crash telemetry flush scheduling failed.", ex);
            return false;
        }
    }

    private void flushPendingOnShutdown() {
        boolean interrupted = awaitAsyncFlushCompletion();
        try {
            flushPendingOnShutdownNow();
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void flushPendingOnShutdownNow() {
        if (!enabled.get()
                || (projects().isEmpty() && manualReportProjects().isEmpty())) {
            return;
        }
        flushPendingReportsNow("shutdown", null);
    }

    private boolean awaitAsyncFlushCompletion() {
        boolean interrupted = false;
        synchronized (asyncFlushMonitor) {
            while (asyncFlushInProgress) {
                try {
                    asyncFlushMonitor.wait();
                } catch (InterruptedException ignored) {
                    interrupted = true;
                }
            }
        }
        return interrupted;
    }

    private void finishAsyncFlush() {
        synchronized (asyncFlushMonitor) {
            asyncFlushInProgress = false;
            asyncFlushMonitor.notifyAll();
        }
    }

    @Nonnull
    private List<TelemetryProjectRegistration> matchingProjects(@Nullable String projectIdFilter) {
        if (projectIdFilter == null || projectIdFilter.isBlank()) {
            LinkedHashMap<String, TelemetryProjectRegistration> all = new LinkedHashMap<>();
            for (TelemetryProjectRegistration project : projects()) {
                all.put(project.projectId().toLowerCase(Locale.ROOT), project);
            }
            for (TelemetryProjectRegistration project : projectCatalog.snapshot().retiredProjects()) {
                all.putIfAbsent(project.projectId().toLowerCase(Locale.ROOT), project);
            }
            return List.copyOf(all.values());
        }
        TelemetryProjectRegistration project = findProject(projectIdFilter);
        if (project != null) {
            return List.of(project);
        }
        for (TelemetryProjectRegistration retired : projectCatalog.snapshot().retiredProjects()) {
            if (retired.projectId().equalsIgnoreCase(projectIdFilter.trim())) {
                return List.of(retired);
            }
        }
        return List.of();
    }

    @Nonnull
    private List<TelemetryProjectRegistration> matchingManualReportProjects(@Nullable String projectIdFilter) {
        List<TelemetryProjectRegistration> retained = retainedManualReportProjects();
        if (projectIdFilter == null || projectIdFilter.isBlank()) {
            return retained;
        }
        String normalized = projectIdFilter.trim();
        return retained.stream()
                .filter(project -> project.projectId().equalsIgnoreCase(normalized))
                .findFirst()
                .map(List::of)
                .orElseGet(List::of);
    }

    @Nonnull
    private List<TelemetryProjectRegistration> retainedManualReportProjects() {
        LinkedHashMap<String, TelemetryProjectRegistration> retained = new LinkedHashMap<>();
        for (TelemetryProjectRegistration project : manualReportProjects()) {
            retained.put(project.projectId().toLowerCase(Locale.ROOT), project);
        }
        for (TelemetryProjectRegistration project : projectCatalog.snapshot().retiredProjects()) {
            retained.putIfAbsent(project.projectId().toLowerCase(Locale.ROOT), project);
        }
        return List.copyOf(retained.values());
    }

    private CrashReportStore storeFor(@Nonnull TelemetryProjectRegistration project) {
        return storesByProjectId.computeIfAbsent(
                project.projectId().toLowerCase(Locale.ROOT),
                ignored -> new CrashReportStore(
                        dataPaths.pendingDirectory(project.projectId()),
                        settings.maxPendingReportsPerProject(),
                        logger
                )
        );
    }

    private TelemetryEventStore eventStoreFor(@Nonnull TelemetryProjectRegistration project) {
        return eventStoresByProjectId.computeIfAbsent(
                project.projectId().toLowerCase(Locale.ROOT),
                ignored -> new TelemetryEventStore(
                        dataPaths.pendingEventsDirectory(project.projectId()),
                        settings.maxPendingEventsPerProject(),
                        logger
                )
        );
    }

    private ManualReportStore manualReportStoreFor(@Nonnull TelemetryProjectRegistration project) {
        return manualReportStoresByProjectId.computeIfAbsent(
                project.projectId().toLowerCase(Locale.ROOT),
                ignored -> new ManualReportStore(dataPaths)
        );
    }

    @Nonnull
    private List<ManualReportAttachment> collectManualReportAttachments(@Nonnull ManualReportSubmission submission) {
        TelemetryRuntimeSettings.ManualReportSettings reportSettings = settings.manualReports();
        ArrayList<ManualReportAttachment> attachments = new ArrayList<>();
        int maxBytes = reportSettings.maxLogAttachmentBytes();
        if (submission.includeCurrentServerLog() && reportSettings.allowCurrentServerLog()) {
            manualReportLogCollector.collectCurrentServerLog(dataPaths, maxBytes).ifPresent(attachments::add);
        }
        if (submission.includePreviousServerLog() && reportSettings.allowPreviousServerLog()) {
            manualReportLogCollector.collectPreviousServerLog(dataPaths, maxBytes).ifPresent(attachments::add);
        }
        return List.copyOf(attachments);
    }

    private void appendManualReportAudit(@Nonnull ManualReportStore store,
                                         @Nonnull String payloadJson,
                                         boolean uploaded) {
        try {
            ManualReportEnvelope envelope = parseManualReport(payloadJson);
            if (envelope != null) {
                store.appendSubmittedAudit(envelope, "submitted", uploaded);
            }
        } catch (Exception ignored) {
            // Malformed local payloads should not break the rest of the flush pass.
        }
    }

    private void updateManualReportReceiptStatus(@Nullable ManualReportEnvelope envelope,
                                                 @Nonnull String status) {
        if (envelope == null) {
            return;
        }
        manualReportReceiptStore.updateStatus(
                dataPaths.manualReportReceiptsFile(),
                envelope.reportId(),
                status,
                Instant.now().toString()
        );
    }

    @Nullable
    private ManualReportEnvelope findReviewEnvelope(@Nonnull ManualReportStore store,
                                                    @Nonnull String projectId,
                                                    @Nonnull String reportId) {
        for (ManualReportStore.PendingReport pending : store.listReviewReports(projectId, settings.maxUploadsPerFlush())) {
            if (pending.path().getFileName().toString().contains(reportId)) {
                return parseManualReport(pending.payload());
            }
        }
        return null;
    }

    @Nullable
    private static ManualReportEnvelope parseManualReport(@Nonnull String payloadJson) {
        try {
            return ManualReportEnvelope.fromJson(payloadJson);
        } catch (Exception ignored) {
            return null;
        }
    }

    private int totalPendingCount(@Nullable String projectIdFilter) {
        int total = 0;
        for (TelemetryProjectRegistration project : matchingProjects(projectIdFilter)) {
            total += storeFor(project).pendingCount();
            total += eventStoreFor(project).pendingCount();
        }
        for (TelemetryProjectRegistration project : matchingManualReportProjects(projectIdFilter)) {
            total += manualReportStoreFor(project).pendingCount(project.projectId());
        }
        return total;
    }

    private void installUncaughtExceptionHandler() {
        if (!uncaughtHandlerInstalled.compareAndSet(false, true)) {
            return;
        }
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        TelemetryUncaughtExceptionHandler handler = new TelemetryUncaughtExceptionHandler(previous);
        previousUncaughtHandler = previous;
        installedUncaughtHandler = handler;
        Thread.setDefaultUncaughtExceptionHandler(handler);
    }

    private void uninstallUncaughtExceptionHandler() {
        if (!uncaughtHandlerInstalled.compareAndSet(true, false)) {
            return;
        }
        Thread.UncaughtExceptionHandler current = Thread.getDefaultUncaughtExceptionHandler();
        TelemetryUncaughtExceptionHandler installed = installedUncaughtHandler;
        if (installed != null && current == installed) {
            Thread.setDefaultUncaughtExceptionHandler(skipStoppedTelemetryHandlers(previousUncaughtHandler));
        }
        installedUncaughtHandler = null;
        previousUncaughtHandler = null;
    }

    @Nullable
    private Thread.UncaughtExceptionHandler skipStoppedTelemetryHandlers(
            @Nullable Thread.UncaughtExceptionHandler candidate) {
        Thread.UncaughtExceptionHandler current = candidate;
        while (current instanceof TelemetryUncaughtExceptionHandler telemetryHandler
                && !telemetryHandler.owner.isActiveInstalledHandler(telemetryHandler)) {
            current = telemetryHandler.previous;
        }
        return current;
    }

    private boolean isActiveInstalledHandler(@Nonnull TelemetryUncaughtExceptionHandler handler) {
        return uncaughtHandlerInstalled.get() && installedUncaughtHandler == handler;
    }

    private void updateFlushStatus(@Nonnull String reason,
                                   @Nonnull FlushSummary summary,
                                   @Nullable String detail) {
        lastFlushResult = "reason=" + reason
                + ", attempted=" + summary.attempted()
                + ", uploaded=" + summary.uploaded()
                + ", pending=" + summary.pendingAfter()
                + (detail == null || detail.isBlank() ? "" : ", detail=" + detail);
        if (logger != null) {
            logger.at(Level.FINE).log("Crash telemetry flush: " + lastFlushResult);
        }
    }

    private void logWarning(@Nonnull String message, @Nullable Throwable throwable) {
        if (logger == null) {
            return;
        }
        if (throwable == null) {
            logger.at(Level.WARNING).log(message + " Last flush status: " + lastFlushResult);
            return;
        }
        logger.at(Level.WARNING).withCause(throwable).log(message);
    }

    /**
     * One flush pass summary.
     */
    public record FlushSummary(int attempted, int uploaded, int pendingAfter, @Nullable String lastFailure) {
    }

    /**
     * Chained uncaught handler that becomes a no-op once its owning engine is shut down.
     */
    private final class TelemetryUncaughtExceptionHandler implements Thread.UncaughtExceptionHandler {

        private final Thread.UncaughtExceptionHandler previous;
        private final TelemetryCoreEngine owner;

        private TelemetryUncaughtExceptionHandler(@Nullable Thread.UncaughtExceptionHandler previous) {
            this.previous = previous;
            this.owner = TelemetryCoreEngine.this;
        }

        @Override
        public void uncaughtException(@Nullable Thread thread, @Nonnull Throwable throwable) {
            if (owner.isActiveInstalledHandler(this)) {
                try {
                    owner.captureAcrossProjects(SOURCE_UNCAUGHT_EXCEPTION, throwable, thread, null, null, null);
                } catch (Exception captureFailure) {
                    owner.logWarning("Crash telemetry uncaught handler capture failed.", captureFailure);
                }
            }

            if (previous != null) {
                try {
                    previous.uncaughtException(thread, throwable);
                } catch (Exception delegateFailure) {
                    owner.logWarning("Delegated uncaught exception handler failed.", delegateFailure);
                }
            }
        }
    }
}
