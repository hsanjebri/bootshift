package dev.bootshift.run;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import dev.bootshift.TestDatabase;
import dev.bootshift.repo.RepositoryName;

/** The Postgres queue, exercised directly with the worker switched off. */
@SpringBootTest(webEnvironment = WebEnvironment.NONE, properties = "bootshift.runs.worker-enabled=false")
@Import(TestDatabase.class)
@ActiveProfiles("test")
@DirtiesContext
class RunQueueTest {

    @Autowired
    private RunService runs;

    @Autowired
    private RunStateWriter writer;

    @Test
    void concurrentWorkersNeverClaimTheSameRun() throws Exception {
        List<UUID> mine = IntStream.range(0, 40)
                .mapToObj(i -> runs.start(new StartRun(new RepositoryName("hsanjebri", "queue-" + i), null), null).run().id())
                .toList();

        ConcurrentLinkedQueue<UUID> claimed = new ConcurrentLinkedQueue<>();
        CountDownLatch go = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            List<Future<?>> workers = new ArrayList<>();
            for (int w = 0; w < 8; w++) {
                workers.add(pool.submit(() -> {
                    go.await();
                    for (Optional<UUID> next = writer.claimNext(); next.isPresent(); next = writer.claimNext()) {
                        claimed.add(next.get());
                    }
                    return null;
                }));
            }
            go.countDown();
            for (Future<?> worker : workers) {
                worker.get();
            }
        }

        assertThat(claimed).doesNotHaveDuplicates();
        assertThat(claimed).containsAll(mine);
        Set<RunState> states = mine.stream().map(id -> runs.get(id).status()).collect(Collectors.toSet());
        assertThat(states).containsExactly(RunState.CLONING);
    }

    @Test
    void claimingWritesTheStateChangeEvent() {
        UUID id = runs.start(new StartRun(new RepositoryName("hsanjebri", "claim-event"), null), null).run().id();
        List<UUID> drained = new ArrayList<>();
        for (Optional<UUID> next = writer.claimNext(); next.isPresent(); next = writer.claimNext()) {
            drained.add(next.get());
        }

        assertThat(drained).contains(id);
        assertThat(runs.events(id, 0, 10)).extracting(RunEvent::type, e -> e.payload().get("to").asString())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(RunEvent.STATE_CHANGED, "QUEUED"),
                        org.assertj.core.groups.Tuple.tuple(RunEvent.STATE_CHANGED, "CLONING"));
    }

    @Test
    void cancelledRunsAreNeverClaimed() {
        UUID id = runs.start(new StartRun(new RepositoryName("hsanjebri", "cancel-before-claim"), null), null).run().id();
        runs.cancel(id);

        List<UUID> drained = new ArrayList<>();
        for (Optional<UUID> next = writer.claimNext(); next.isPresent(); next = writer.claimNext()) {
            drained.add(next.get());
        }

        assertThat(drained).doesNotContain(id);
        assertThat(runs.get(id).status()).isEqualTo(RunState.CANCELLED);
    }
}
