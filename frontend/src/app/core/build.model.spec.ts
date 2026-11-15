import { BuildFailure, describeBuildEvent, locate, sortFailures } from './build.model';

function failure(overrides: Partial<BuildFailure>): BuildFailure {
  return {
    fingerprint: 'f',
    type: 'TEST',
    module: '.',
    file: null,
    line: null,
    symbol: null,
    message: 'boom',
    isNew: false,
    ...overrides,
  };
}

describe('build model', () => {
  it('describes a finished build for the activity log', () => {
    expect(
      describeBuildEvent({ kind: 'BASELINE', outcome: 'EXITED', jdk: 17, durationMs: 95_400, testsRun: 40, testsFailed: 2, testsSkipped: 1, compileErrors: 0 }),
    ).toBe('Baseline build: 40 tests, 2 failing, 1 skipped (JDK 17, 95s)');
    expect(describeBuildEvent({ kind: 'BASELINE', outcome: 'EXITED', jdk: 21, durationMs: 3000, compileErrors: 1 })).toBe(
      'Baseline build: 1 compiler error (JDK 21, 3s)',
    );
    expect(describeBuildEvent({ kind: 'BASELINE', outcome: 'TIMED_OUT', jdk: 17, durationMs: 1_200_000 })).toBe(
      'Baseline build timed out (JDK 17, 1200s)',
    );
  });

  it('locates failures by file, test or artifact', () => {
    expect(locate(failure({ type: 'COMPILE', file: 'api/src/main/java/a/B.java', line: 12 }))).toBe('B.java:12');
    expect(locate(failure({ type: 'TEST', file: 'dev.x.TodoTest', symbol: 'rejects()' }))).toBe('TodoTest › rejects()');
    expect(locate(failure({ type: 'DEPENDENCY', symbol: 'com.acme:lib:1.0' }))).toBe('com.acme:lib:1.0');
    expect(locate(failure({ type: 'OTHER' }))).toBe('project');
  });

  it('puts the problems that block a run first', () => {
    const sorted = sortFailures([failure({ type: 'TEST' }), failure({ type: 'COMPILE' }), failure({ type: 'DEPENDENCY' })]);
    expect(sorted.map((f) => f.type)).toEqual(['DEPENDENCY', 'COMPILE', 'TEST']);
  });
});
