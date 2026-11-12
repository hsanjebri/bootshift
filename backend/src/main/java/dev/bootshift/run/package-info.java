/**
 * Run lifecycle: state machine, Postgres-backed queue, worker, event log and the pipeline
 * interface. Must not depend on {@code dev.bootshift.web}.
 */
@NullMarked
package dev.bootshift.run;

import org.jspecify.annotations.NullMarked;
