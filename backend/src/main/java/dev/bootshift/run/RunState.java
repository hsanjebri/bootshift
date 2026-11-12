package dev.bootshift.run;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Run lifecycle from README section 6.
 *
 * <p>Two additions to the diagram there: any active state may go to {@link #FAILED} (an
 * infrastructure error can happen in any stage), and any non-terminal state may go to
 * {@link #CANCELLED} (the API has a cancel endpoint).
 */
public enum RunState {
    QUEUED,
    CLONING,
    BASELINE,
    SCANNING,
    RECIPE,
    VERIFY,
    REPAIR,
    REVIEW,
    PR_OPENED,
    DRAFT,
    NOT_ELIGIBLE,
    FAILED,
    CANCELLED;

    private static final Set<RunState> TERMINAL = EnumSet.of(PR_OPENED, DRAFT, NOT_ELIGIBLE, FAILED, CANCELLED);

    private static final Map<RunState, Set<RunState>> FORWARD = Map.of(
            QUEUED, EnumSet.of(CLONING),
            CLONING, EnumSet.of(BASELINE),
            BASELINE, EnumSet.of(SCANNING, NOT_ELIGIBLE),
            SCANNING, EnumSet.of(RECIPE, NOT_ELIGIBLE),
            RECIPE, EnumSet.of(VERIFY),
            VERIFY, EnumSet.of(REVIEW, REPAIR),
            REPAIR, EnumSet.of(VERIFY, DRAFT),
            REVIEW, EnumSet.of(PR_OPENED, DRAFT));

    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }

    /** True for states where a worker is actively processing the run. */
    public boolean isActive() {
        return this != QUEUED && !isTerminal();
    }

    public boolean canTransitionTo(RunState next) {
        if (isTerminal()) {
            return false;
        }
        if (next == CANCELLED) {
            return true;
        }
        if (next == FAILED) {
            return isActive();
        }
        return FORWARD.getOrDefault(this, Set.of()).contains(next);
    }
}
