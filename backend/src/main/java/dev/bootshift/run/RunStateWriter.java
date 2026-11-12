package dev.bootshift.run;

import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes a status change and its {@code STATE_CHANGED} event in one transaction, so a reader can
 * never see a new status without the matching event (or the other way round).
 */
@Component
class RunStateWriter {

    private final RunStore store;
    private final RunEventLog events;

    RunStateWriter(RunStore store, RunEventLog events) {
        this.store = store;
        this.events = events;
    }

    @Transactional
    public boolean move(UUID id, RunState from, RunState to, @Nullable String reason) {
        if (!store.transition(id, from, to, reason)) {
            return false;
        }
        events.append(id, RunEvent.STATE_CHANGED, RunService.stateChange(from, to));
        return true;
    }

    @Transactional
    public Optional<UUID> claimNext() {
        Optional<UUID> claimed = store.claimNext();
        claimed.ifPresent(id -> events.append(id, RunEvent.STATE_CHANGED, RunService.stateChange(RunState.QUEUED, RunState.CLONING)));
        return claimed;
    }
}
