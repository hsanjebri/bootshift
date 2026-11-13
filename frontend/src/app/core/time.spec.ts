import { elapsed, formatDuration, relativeTime } from './time';

describe('formatDuration', () => {
  it('formats seconds, minutes and hours', () => {
    expect(formatDuration(4_200)).toBe('4s');
    expect(formatDuration(125_000)).toBe('2m 05s');
    expect(formatDuration(3_780_000)).toBe('1h 03m');
  });

  it('shows a dash for missing or negative values', () => {
    expect(formatDuration(null)).toBe('–');
    expect(formatDuration(-1)).toBe('–');
  });
});

describe('elapsed', () => {
  const start = '2026-11-13T10:00:00Z';

  it('measures to the end time when there is one', () => {
    expect(elapsed(start, '2026-11-13T10:00:09Z', 0)).toBe(9_000);
  });

  it('measures to now while still running', () => {
    expect(elapsed(start, null, Date.parse('2026-11-13T10:01:00Z'))).toBe(60_000);
  });

  it('is null before the run started', () => {
    expect(elapsed(null, null, 0)).toBeNull();
  });
});

describe('relativeTime', () => {
  const now = Date.parse('2026-11-13T12:00:00Z');

  it('rounds to a readable unit', () => {
    expect(relativeTime('2026-11-13T11:59:50Z', now)).toBe('just now');
    expect(relativeTime('2026-11-13T11:55:00Z', now)).toBe('5 min ago');
    expect(relativeTime('2026-11-13T09:00:00Z', now)).toBe('3 h ago');
    expect(relativeTime('2026-11-11T12:00:00Z', now)).toBe('2 d ago');
  });
});
