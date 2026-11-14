package dev.bootshift.repo;

/**
 * Whether runs must target an allowlisted repository. Enforced once a GitHub App is connected;
 * before that, Phase 1 simulated runs accept any well-formed owner/name.
 */
@FunctionalInterface
public interface AllowlistPolicy {

    boolean enforced();
}
