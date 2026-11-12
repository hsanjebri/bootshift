package dev.bootshift.run;

/**
 * Executes one claimed run, starting in {@link RunState#CLONING}. It must move the run to a
 * terminal state through {@link RunContext#advance}. Throwing {@link InterruptedException} means
 * the server is shutting down: the run is left as is and re-queued on the next start.
 */
public interface MigrationPipeline {

    void execute(RunContext context) throws InterruptedException;
}
