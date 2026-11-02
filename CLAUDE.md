# Bootshift: rules for Claude Code

Read README.md before starting. It is the source of truth.

## Workflow
- Work on one phase at a time, in the order given in section 19.
- Before coding, give a short plan for the current phase and wait for approval.
- Commit small, working steps using Conventional Commits.
- Finish a phase only when its "done when" criteria are shown to pass.
- If you find the spec is wrong, say so and propose a README change. Do not silently diverge.

## Verify, don't assume
- Spring Boot 4, Spring AI 2 and Angular 22 are newer than much training data.
- Check current docs for dependency names, APIs and properties.
- Never invent a version, class or property. Pin exact versions in the build.

## Code standards
- Java 25, no preview features. Use records for DTOs and constructor injection.
- `@NullMarked` packages, Jackson 3 only, and package-by-feature.
- Angular: standalone components, signals, Signal Forms and `httpResource`.
- Test behavior with every change. Never delete or disable a test to get green.

## Non-negotiable
- The sandbox never receives secrets or direct internet access.
- The model never gets a shell.
- Guardrails are enforced in code.
- Bootshift never merges a PR.
- Do not bundle OpenRewrite recipes, or add hosted-service features (README section 3).
- Never commit secrets.

## Pinned versions (Phase 0, checked 2026-11-02)
See `docs/phase-0.md` for how these were chosen. Change them only with a note there.
- rewrite-maven-plugin `6.46.1`, rewrite-spring `6.37.1`
- Fixture source: Spring Boot `3.5.16`, Java 17
- Target: Spring Boot `4.1.2`
