package dev.bootshift.run;

import static dev.bootshift.run.RunState.BASELINE;
import static dev.bootshift.run.RunState.CANCELLED;
import static dev.bootshift.run.RunState.CLONING;
import static dev.bootshift.run.RunState.DRAFT;
import static dev.bootshift.run.RunState.FAILED;
import static dev.bootshift.run.RunState.NOT_ELIGIBLE;
import static dev.bootshift.run.RunState.PR_OPENED;
import static dev.bootshift.run.RunState.QUEUED;
import static dev.bootshift.run.RunState.RECIPE;
import static dev.bootshift.run.RunState.REPAIR;
import static dev.bootshift.run.RunState.REVIEW;
import static dev.bootshift.run.RunState.SCANNING;
import static dev.bootshift.run.RunState.VERIFY;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class RunStateTest {

    @Test
    void happyPathFromReadmeIsAllowed() {
        List<RunState> path = List.of(QUEUED, CLONING, BASELINE, SCANNING, RECIPE, VERIFY, REVIEW, PR_OPENED);
        for (int i = 0; i < path.size() - 1; i++) {
            assertThat(path.get(i).canTransitionTo(path.get(i + 1)))
                    .as("%s -> %s", path.get(i), path.get(i + 1)).isTrue();
        }
    }

    @Test
    void repairLoopsBackToVerifyAndCanEndAsDraft() {
        assertThat(VERIFY.canTransitionTo(REPAIR)).isTrue();
        assertThat(REPAIR.canTransitionTo(VERIFY)).isTrue();
        assertThat(REPAIR.canTransitionTo(DRAFT)).isTrue();
        assertThat(REVIEW.canTransitionTo(DRAFT)).isTrue();
    }

    @Test
    void ineligibleProjectsStopAtBaselineOrScan() {
        assertThat(BASELINE.canTransitionTo(NOT_ELIGIBLE)).isTrue();
        assertThat(SCANNING.canTransitionTo(NOT_ELIGIBLE)).isTrue();
        assertThat(RECIPE.canTransitionTo(NOT_ELIGIBLE)).isFalse();
    }

    @Test
    void stagesCannotBeSkipped() {
        assertThat(QUEUED.canTransitionTo(BASELINE)).isFalse();
        assertThat(CLONING.canTransitionTo(RECIPE)).isFalse();
        assertThat(RECIPE.canTransitionTo(PR_OPENED)).isFalse();
        assertThat(VERIFY.canTransitionTo(PR_OPENED)).isFalse();
    }

    @Test
    void aQueuedRunCanBeCancelledButNotFailed() {
        assertThat(QUEUED.canTransitionTo(CANCELLED)).isTrue();
        assertThat(QUEUED.canTransitionTo(FAILED)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(names = {"CLONING", "BASELINE", "SCANNING", "RECIPE", "VERIFY", "REPAIR", "REVIEW"})
    void everyActiveStageCanFailOrBeCancelled(RunState state) {
        assertThat(state.isActive()).isTrue();
        assertThat(state.canTransitionTo(FAILED)).isTrue();
        assertThat(state.canTransitionTo(CANCELLED)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(names = {"PR_OPENED", "DRAFT", "NOT_ELIGIBLE", "FAILED", "CANCELLED"})
    void terminalStatesGoNowhere(RunState terminal) {
        assertThat(terminal.isTerminal()).isTrue();
        assertThat(Arrays.stream(RunState.values()).filter(terminal::canTransitionTo)).isEmpty();
    }
}
