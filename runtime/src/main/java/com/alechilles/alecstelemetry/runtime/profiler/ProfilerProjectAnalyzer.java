package com.alechilles.alecstelemetry.runtime.profiler;

import com.alechilles.alecstelemetry.api.TelemetryProfilerAttribution;
import com.alechilles.alecstelemetry.api.TelemetryProfilerQualification;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.concurrent.TimeUnit;

/** Attributes bounded Spark parent chains to currently eligible projects. */
final class ProfilerProjectAnalyzer {
    private static final int MAX_REPRESENTATIVE_FRAMES = 8;
    private static final int MAX_FRAME_LENGTH = 512;
    private static final int MAX_PATHS_PER_PROJECT = 5;

    private ProfilerProjectAnalyzer() {
    }

    @Nonnull
    static Analysis analyze(@Nonnull SparkProfileWindow window,
                            @Nonnull ProfilerProjectOwnershipIndex ownership,
                            @Nonnull Predicate<String> currentlyEligible,
                            int maxPathsPerProject) {
        long startedAt = System.nanoTime();
        try {
            Objects.requireNonNull(window, "window");
            Objects.requireNonNull(ownership, "ownership");
            Objects.requireNonNull(currentlyEligible, "currentlyEligible");
            int boundedPathLimit = Math.min(
                    MAX_PATHS_PER_PROJECT,
                    Math.max(0, maxPathsPerProject)
            );
            return analyzeInternal(window, ownership, currentlyEligible, boundedPathLimit, startedAt);
        } catch (Throwable failure) {
            rethrowIfFatal(failure);
            return Analysis.failed(elapsedMillis(startedAt), failure);
        }
    }

    @Nonnull
    private static Analysis analyzeInternal(@Nonnull SparkProfileWindow window,
                                             @Nonnull ProfilerProjectOwnershipIndex ownership,
                                             @Nonnull Predicate<String> currentlyEligible,
                                             int maxPathsPerProject,
                                             long startedAt) {
        double denominator = 0.0d;
        for (SparkProfileWindow.ThreadFrames thread : window.threads()) {
            for (SparkProfileWindow.Frame frame : thread.frames()) {
                if (frame.selfMilliseconds() > 0.0d) {
                    denominator += frame.selfMilliseconds();
                }
            }
        }
        if (!Double.isFinite(denominator) || denominator <= 0.0d) {
            return new Analysis(List.of(), 0, elapsedMillis(startedAt));
        }

        Map<String, MutableProject> projects = new HashMap<>();
        for (SparkProfileWindow.ThreadFrames thread : window.threads()) {
            List<SparkProfileWindow.Frame> frames = thread.frames();
            ThreadOwnership threadOwnership = resolveThreadOwnership(
                    frames,
                    ownership,
                    currentlyEligible
            );
            for (int index = 0; index < frames.size(); index++) {
                SparkProfileWindow.Frame sampled = frames.get(index);
                if (sampled.selfMilliseconds() <= 0.0d) {
                    continue;
                }
                FrameOwnership sampledOwner = threadOwnership.ownership()[index];
                if (sampledOwner.owner() != null) {
                    if (!sampledOwner.eligible()) {
                        continue;
                    }
                    ProfilerProjectOwnershipIndex.Owner owner = sampledOwner.owner();
                    MutableProject project = projects.computeIfAbsent(
                            owner.projectId(), ignored -> new MutableProject(owner));
                    ProfilerPathIdentity identity = ProfilerPathIdentity.self(
                            owner.projectId(), sampled.className(), sampled.methodName(), sampled.methodDescriptor());
                    project.add(
                            identity,
                            TelemetryProfilerAttribution.SELF,
                            sampled,
                            null,
                            sampled.selfMilliseconds(),
                            selfPath(frames, index));
                    continue;
                }

                AncestorInfo nearestOwner = threadOwnership.nearestOwners()[index];
                if (nearestOwner.ownerIndex() < 0) {
                    continue;
                }
                FrameOwnership candidateOwner = threadOwnership.ownership()[nearestOwner.ownerIndex()];
                if (!candidateOwner.eligible()) {
                    continue;
                }
                ProfilerProjectOwnershipIndex.Owner owner = candidateOwner.owner();
                SparkProfileWindow.Frame candidate = frames.get(nearestOwner.ownerIndex());
                SparkProfileWindow.Frame firstExternal = frames.get(nearestOwner.firstExternalIndex());
                MutableProject project = projects.computeIfAbsent(
                        owner.projectId(), ignored -> new MutableProject(owner));
                ProfilerPathIdentity identity = ProfilerPathIdentity.downstream(
                        owner.projectId(),
                        candidate.className(), candidate.methodName(), candidate.methodDescriptor(),
                        firstExternal.className(), firstExternal.methodName(), firstExternal.methodDescriptor());
                project.add(
                        identity,
                        TelemetryProfilerAttribution.DOWNSTREAM,
                        candidate,
                        firstExternal,
                        sampled.selfMilliseconds(),
                        downstreamPath(frames, nearestOwner.representativeIndices()));
            }
        }

        final double totalSelectedSelfMilliseconds = denominator;
        ArrayList<ProfilerProjectWindow> result = new ArrayList<>(projects.size());
        int omittedPathCount = 0;
        for (MutableProject project : projects.values()) {
            List<MutablePath> ranked = project.paths.values().stream()
                    .sorted(Comparator
                            .comparingDouble((MutablePath path) -> path.sampledMilliseconds).reversed()
                            .thenComparing(path -> path.identity.fingerprint()))
                    .toList();
            omittedPathCount += Math.max(0, ranked.size() - maxPathsPerProject);
            List<ProfilerProjectWindow.Path> paths = ranked.stream()
                    .limit(maxPathsPerProject)
                    .map(path -> path.toPath(totalSelectedSelfMilliseconds))
                    .toList();
            if (!paths.isEmpty()) {
                result.add(new ProfilerProjectWindow(
                        project.owner.projectId(),
                        project.owner.logicalProjectVersion(),
                        window.sparkVersion(),
                        window.windowKey(),
                        totalSelectedSelfMilliseconds,
                        paths
                ));
            }
        }
        result.sort(Comparator.comparing(ProfilerProjectWindow::projectId));
        return new Analysis(List.copyOf(result), omittedPathCount, elapsedMillis(startedAt));
    }

    @Nonnull
    private static FrameOwnership ownerOf(
            @Nonnull SparkProfileWindow.Frame frame,
            @Nonnull ProfilerProjectOwnershipIndex ownership,
            @Nonnull Predicate<String> currentlyEligible) {
        Optional<ProfilerProjectOwnershipIndex.Owner> owner = ownership.resolve(frame.source(), frame.className());
        if (owner.isEmpty()) {
            return FrameOwnership.UNOWNED;
        }
        return new FrameOwnership(owner.get(), currentlyEligible.test(owner.get().projectId()));
    }

    @Nonnull
    private static ThreadOwnership resolveThreadOwnership(
            @Nonnull List<SparkProfileWindow.Frame> frames,
            @Nonnull ProfilerProjectOwnershipIndex ownership,
            @Nonnull Predicate<String> currentlyEligible) {
        int frameCount = frames.size();
        FrameOwnership[] resolvedOwnership = new FrameOwnership[frameCount];
        for (int index = 0; index < frameCount; index++) {
            resolvedOwnership[index] = ownerOf(frames.get(index), ownership, currentlyEligible);
        }

        AncestorInfo[] nearestOwners = new AncestorInfo[frameCount];
        int[] visitGeneration = new int[frameCount];
        int generation = 0;
        for (int index = 0; index < frameCount; index++) {
            if (nearestOwners[index] != null) {
                continue;
            }
            generation++;
            ArrayList<Integer> pending = new ArrayList<>();
            int current = index;
            AncestorInfo base;
            while (true) {
                if (nearestOwners[current] != null) {
                    base = nearestOwners[current];
                    break;
                }
                if (visitGeneration[current] == generation) {
                    throw new IllegalArgumentException("Spark frame parent chain contains a cycle.");
                }
                visitGeneration[current] = generation;
                pending.add(current);
                int parentIndex = frames.get(current).parentIndex();
                if (parentIndex < 0) {
                    base = AncestorInfo.NONE;
                    break;
                }
                if (parentIndex >= frameCount) {
                    throw new IllegalArgumentException("Spark frame parent index is outside the thread window.");
                }
                current = parentIndex;
            }

            for (int pendingIndex = pending.size() - 1; pendingIndex >= 0; pendingIndex--) {
                int frameIndex = pending.get(pendingIndex);
                FrameOwnership frameOwnership = resolvedOwnership[frameIndex];
                if (frameOwnership.owner() != null) {
                    nearestOwners[frameIndex] = AncestorInfo.owned(frameIndex);
                    continue;
                }
                int parentIndex = frames.get(frameIndex).parentIndex();
                AncestorInfo parent = parentIndex < 0
                        ? base
                        : nearestOwners[parentIndex];
                nearestOwners[frameIndex] = parent.append(frameIndex, parentIndex);
            }
        }
        return new ThreadOwnership(resolvedOwnership, nearestOwners);
    }

    @Nonnull
    private static List<String> selfPath(@Nonnull List<SparkProfileWindow.Frame> frames, int index) {
        ArrayList<String> path = new ArrayList<>();
        int current = index;
        while (current >= 0 && path.size() < MAX_REPRESENTATIVE_FRAMES) {
            path.add(frameText(frames.get(current)));
            current = frames.get(current).parentIndex();
        }
        java.util.Collections.reverse(path);
        return path;
    }

    @Nonnull
    private static List<String> downstreamPath(@Nonnull List<SparkProfileWindow.Frame> frames,
                                               @Nonnull int[] representativeIndices) {
        ArrayList<String> path = new ArrayList<>(representativeIndices.length);
        for (int representativeIndex : representativeIndices) {
            path.add(frameText(frames.get(representativeIndex)));
        }
        return path;
    }

    @Nonnull
    private static String frameText(@Nonnull SparkProfileWindow.Frame frame) {
        String text = frame.className() + "#" + frame.methodName() + frame.methodDescriptor();
        return text.length() <= MAX_FRAME_LENGTH ? text : text.substring(0, MAX_FRAME_LENGTH);
    }

    private static long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(Math.max(0L, System.nanoTime() - startedAt));
    }

    private static void rethrowIfFatal(@Nonnull Throwable failure) {
        if (failure instanceof VirtualMachineError virtualMachineError) {
            throw virtualMachineError;
        }
        if ("java.lang.ThreadDeath".equals(failure.getClass().getName())) {
            throw (Error) failure;
        }
    }

    record Analysis(@Nonnull List<ProfilerProjectWindow> projects,
                    int omittedPathCount,
                    long durationMillis,
                    @Nonnull Status status,
                    @Nonnull String detail) {
        enum Status {
            COMPLETE,
            FAILED
        }

        Analysis(@Nonnull List<ProfilerProjectWindow> projects,
                 int omittedPathCount,
                 long durationMillis) {
            this(projects, omittedPathCount, durationMillis, Status.COMPLETE, "");
        }

        Analysis {
            projects = projects == null ? List.of() : List.copyOf(projects);
            if (omittedPathCount < 0 || durationMillis < 0L) {
                throw new IllegalArgumentException("Analysis counts and duration must be non-negative");
            }
            status = status == null ? Status.FAILED : status;
            detail = detail == null ? "" : detail;
        }

        @Nonnull
        static Analysis failed(long durationMillis, @Nonnull Throwable failure) {
            String detail = failure.getClass().getSimpleName();
            String message = failure.getMessage();
            if (message != null && !message.isBlank()) {
                detail += ": " + message;
            }
            if (detail.length() > 240) {
                detail = detail.substring(0, 240);
            }
            return new Analysis(List.of(), 0, durationMillis, Status.FAILED, detail);
        }
    }

    private record FrameOwnership(ProfilerProjectOwnershipIndex.Owner owner,
                                  boolean eligible) {
        private static final FrameOwnership UNOWNED = new FrameOwnership(null, false);
    }

    private record ThreadOwnership(@Nonnull FrameOwnership[] ownership,
                                   @Nonnull AncestorInfo[] nearestOwners) {
    }

    private record AncestorInfo(int ownerIndex,
                                int firstExternalIndex,
                                @Nonnull int[] representativeIndices) {
        private static final AncestorInfo NONE = new AncestorInfo(-1, -1, new int[0]);

        private static AncestorInfo owned(int frameIndex) {
            return new AncestorInfo(frameIndex, -1, new int[]{frameIndex});
        }

        private AncestorInfo append(int frameIndex, int parentIndex) {
            if (ownerIndex < 0) {
                return NONE;
            }
            int firstExternal = ownerIndex == parentIndex ? frameIndex : firstExternalIndex;
            if (representativeIndices.length >= MAX_REPRESENTATIVE_FRAMES) {
                return new AncestorInfo(ownerIndex, firstExternal, representativeIndices);
            }
            int[] extended = java.util.Arrays.copyOf(
                    representativeIndices,
                    representativeIndices.length + 1
            );
            extended[extended.length - 1] = frameIndex;
            return new AncestorInfo(ownerIndex, firstExternal, extended);
        }
    }

    private static final class MutableProject {
        private final ProfilerProjectOwnershipIndex.Owner owner;
        private final Map<String, MutablePath> paths = new HashMap<>();

        private MutableProject(ProfilerProjectOwnershipIndex.Owner owner) {
            this.owner = owner;
        }

        private void add(ProfilerPathIdentity identity,
                         TelemetryProfilerAttribution attribution,
                         SparkProfileWindow.Frame owned,
                         SparkProfileWindow.Frame external,
                         double sampledMilliseconds,
                         List<String> representativePath) {
            MutablePath path = paths.computeIfAbsent(identity.canonical(), ignored -> new MutablePath(
                    identity,
                    attribution,
                    owned,
                    external,
                    representativePath
            ));
            path.sampledMilliseconds += sampledMilliseconds;
            if (sampledMilliseconds > path.representativeMilliseconds
                    || (sampledMilliseconds == path.representativeMilliseconds
                    && comparePath(representativePath, path.representativePath) < 0)) {
                path.representativeMilliseconds = sampledMilliseconds;
                path.representativePath = List.copyOf(representativePath);
            }
        }
    }

    private static final class MutablePath {
        private final ProfilerPathIdentity identity;
        private final TelemetryProfilerAttribution attribution;
        private final SparkProfileWindow.Frame owned;
        private final SparkProfileWindow.Frame external;
        private List<String> representativePath;
        private double representativeMilliseconds;
        private double sampledMilliseconds;

        private MutablePath(ProfilerPathIdentity identity,
                            TelemetryProfilerAttribution attribution,
                            SparkProfileWindow.Frame owned,
                            SparkProfileWindow.Frame external,
                            List<String> representativePath) {
            this.identity = identity;
            this.attribution = attribution;
            this.owned = owned;
            this.external = external;
            this.representativePath = List.copyOf(representativePath);
            this.representativeMilliseconds = 0.0d;
        }

        @Nonnull
        private ProfilerProjectWindow.Path toPath(double denominator) {
            return new ProfilerProjectWindow.Path(
                    identity,
                    attribution,
                    owned.className(),
                    owned.methodName(),
                    owned.methodDescriptor(),
                    external == null ? null : external.className(),
                    external == null ? null : external.methodName(),
                    external == null ? null : external.methodDescriptor(),
                    sampledMilliseconds,
                    sampledMilliseconds * 100.0d / denominator,
                    TelemetryProfilerQualification.OBSERVED,
                    representativePath
            );
        }
    }

    private static int comparePath(List<String> left, List<String> right) {
        int count = Math.min(left.size(), right.size());
        for (int index = 0; index < count; index++) {
            int compared = left.get(index).compareTo(right.get(index));
            if (compared != 0) {
                return compared;
            }
        }
        return Integer.compare(left.size(), right.size());
    }
}
