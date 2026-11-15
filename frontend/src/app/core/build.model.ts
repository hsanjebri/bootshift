/** Mirrors dev.bootshift.build.BuildResultStore.StoredBuild, from GET /api/v1/runs/{id}/builds. */
export interface Build {
  id: string;
  kind: 'BASELINE' | 'RECIPE' | 'REPAIR' | 'FINAL';
  iteration: number;
  outcome: 'EXITED' | 'TIMED_OUT' | 'CANCELLED';
  exitCode: number | null;
  jdk: number;
  testsRun: number;
  testsFailed: number;
  testsSkipped: number;
  durationMs: number;
  logTruncated: boolean;
  createdAt: string;
  failures: BuildFailure[];
}

export type FailureType = 'COMPILE' | 'TEST' | 'DEPENDENCY' | 'STARTUP' | 'OTHER';

export interface BuildFailure {
  fingerprint: string;
  type: FailureType;
  module: string;
  file: string | null;
  line: number | null;
  symbol: string | null;
  message: string;
  isNew: boolean;
}

export const BUILD_LABELS: Record<Build['kind'], string> = {
  BASELINE: 'Baseline',
  RECIPE: 'After recipes',
  REPAIR: 'After repair',
  FINAL: 'Final',
};

export const FAILURE_LABELS: Record<FailureType, string> = {
  COMPILE: 'Compile',
  TEST: 'Test',
  DEPENDENCY: 'Dependency',
  STARTUP: 'Startup',
  OTHER: 'Build',
};

/** Compile and dependency problems first: they decide whether a run can continue. */
const ORDER: FailureType[] = ['DEPENDENCY', 'COMPILE', 'STARTUP', 'TEST', 'OTHER'];

export function sortFailures(failures: readonly BuildFailure[]): BuildFailure[] {
  return [...failures].sort((a, b) => ORDER.indexOf(a.type) - ORDER.indexOf(b.type));
}

/** Where a failure is: {@code A.java:12}, {@code a.FooTest › method}, or the failing artifact. */
export function locate(failure: BuildFailure): string {
  if (failure.type === 'TEST' || failure.type === 'STARTUP') {
    const cls = failure.file?.split('.').pop() ?? '';
    return failure.symbol ? `${cls} › ${failure.symbol}` : cls;
  }
  if (failure.file) {
    const name = failure.file.split('/').pop();
    return failure.line ? `${name}:${failure.line}` : (name ?? failure.file);
  }
  return failure.symbol ?? (failure.module === '.' ? 'project' : failure.module);
}

/** One line for the activity log, from a BUILD_FINISHED event payload. */
export function describeBuildEvent(payload: Record<string, unknown>): string {
  const kind = BUILD_LABELS[payload['kind'] as Build['kind']] ?? 'Build';
  const seconds = Math.round(Number(payload['durationMs'] ?? 0) / 1000);
  const where = `JDK ${payload['jdk']}, ${seconds}s`;
  if (payload['outcome'] === 'TIMED_OUT') return `${kind} build timed out (${where})`;
  if (payload['outcome'] === 'CANCELLED') return `${kind} build cancelled (${where})`;
  const compile = Number(payload['compileErrors'] ?? 0);
  const tests = `${payload['testsRun']} tests, ${payload['testsFailed']} failing, ${payload['testsSkipped']} skipped`;
  return compile > 0
    ? `${kind} build: ${compile} compiler error${compile === 1 ? '' : 's'} (${where})`
    : `${kind} build: ${tests} (${where})`;
}
