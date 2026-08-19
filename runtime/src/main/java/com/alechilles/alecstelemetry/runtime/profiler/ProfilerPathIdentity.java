package com.alechilles.alecstelemetry.runtime.profiler;

import javax.annotation.Nonnull;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Stable, version-independent identity for an attributed profiler path. */
record ProfilerPathIdentity(@Nonnull String canonical,
                            @Nonnull String fingerprint) {

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    ProfilerPathIdentity {
        canonical = canonical == null ? "" : canonical;
        fingerprint = fingerprint == null ? "" : fingerprint;
    }

    @Nonnull
    static ProfilerPathIdentity self(@Nonnull String projectId,
                                     @Nonnull String className,
                                     @Nonnull String methodName,
                                     @Nonnull String descriptor) {
        return of(projectId + "|SELF|" + className + "|" + methodName + "|" + descriptor);
    }

    @Nonnull
    static ProfilerPathIdentity downstream(@Nonnull String projectId,
                                           @Nonnull String ownedClass,
                                           @Nonnull String ownedMethod,
                                           @Nonnull String ownedDescriptor,
                                           @Nonnull String externalClass,
                                           @Nonnull String externalMethod,
                                           @Nonnull String externalDescriptor) {
        return of(projectId + "|DOWNSTREAM|" + ownedClass + "|" + ownedMethod + "|"
                + ownedDescriptor + "|" + externalClass + "|" + externalMethod + "|"
                + externalDescriptor);
    }

    @Nonnull
    static ProfilerPathIdentity of(@Nonnull String canonical) {
        if (canonical == null) {
            canonical = "";
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder fingerprint = new StringBuilder(32);
            for (int index = 0; index < 16; index++) {
                int value = digest[index] & 0xff;
                fingerprint.append(HEX[value >>> 4]).append(HEX[value & 0x0f]);
            }
            return new ProfilerPathIdentity(canonical, fingerprint.toString());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
