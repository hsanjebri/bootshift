# Phase 2: sandbox and build

**Goal (README section 19):** container runner, limits, proxy network, JDK selection, log capture,
Surefire and compiler parsing, fingerprints, and the baseline stage.

**Done when:** a real baseline run reports correct results, and tests prove the sandbox cannot reach
the internet or see secrets.

## What was built

| Piece | Where |
|---|---|
| Sandbox image: Temurin 17, 21, 25 and Maven 3.9.16, uid 1000 | `sandbox/Dockerfile` |
| Maven settings that send every repository to the proxy | `sandbox/settings.xml` |
| Reposilite 3.6.6 proxying Maven Central on an internal network | `deploy/compose.dev.yaml`, `deploy/reposilite/` |
| Container runner with README section 13 settings | `backend/.../sandbox/DockerSandbox.java` |
| JDK selection, Maven log and Surefire parsing, fingerprints | `backend/.../build/` |
| Clone with a repository-scoped, read-only token | `backend/.../git/GitClone.java`, `GitHubApp.cloneToken` |
| Real baseline stage (`bootshift.pipeline.mode=real`) | `backend/.../pipeline/RealMigrationPipeline.java` |
| `build_result` and `failure` tables | `V3__build_results.sql` |
| `GET /api/v1/runs/{id}/builds` and the baseline card on the run page | `RunController`, `run-detail-page` |

## Evidence

**Sandbox isolation** (`DockerSandboxTest`, runs in CI against the real image):

- TCP to 1.1.1.1:443, 8.8.8.8:53 and a GitHub address is blocked, and DNS for `github.com` fails.
- The environment contains only the variables Bootshift sets plus the image defaults; nothing that
  looks like a token, key, password or database setting.
- No Docker socket is mounted.
- uid and gid are 1000, effective capabilities are zero, and the root filesystem is read-only.
- cgroup limits match the configuration (memory, processes, CPU quota), on cgroup v1 and v2.
- Builds are killed on timeout and on cancel; their containers are removed.
- Logs are capped at the configured size.
- A network that is not internal is refused before any container starts.

**A real baseline** (`spring-projects/spring-petclinic` at `500158f`, 2026-11-15, real mode,
no GitHub App):

| | Maven's own summary | Bootshift |
|---|---|---|
| Tests run | 79 | 79 |
| Failing | 0 | 0 |
| Skipped | 2 (Testcontainers tests, disabled without Docker) | 2 |
| JDK | | 17 (pom declares 17) |
| Duration | 11:41 min | 704 s, with a cold proxy cache |

The working copy, the run's Maven cache volume and the container were gone afterwards.

**Failure parsing on real builds** (`BaselineEndToEndTest`, opt-in with `BOOTSHIFT_E2E=true`):
the `simple-web` fixture with a failing test added reports exactly one `TEST` failure with the right
class, method and message; with a compiler error added, exactly one `COMPILE` failure with the
right file, line and missing symbol.

## Findings

- **javac detail lines have no prefix in one section.** Maven prints each compiler error twice.
  In the compiler's own section the `symbol:` and `location:` lines have no `[ERROR]` prefix, so
  the first copy lost its symbol and the error was counted twice. The parser now keeps indented
  lines that directly follow an error. Only a real build showed this.
- **Java ignores `$HOME`.** `user.home` comes from `/etc/passwd`, so the sandbox user's home was the
  read-only `/home/ubuntu` and petclinic's `git-commit-id` plugin logged errors writing JGit
  config. The image now sets that user's home to `/tmp`.
- **Docker Desktop on WSL 2 uses cgroup v1**, while GitHub Actions uses v2. The limits test reads
  both layouts.
- **A long-lived Docker wait request ended early** through docker-java's zerodep transport, which
  looked like the build had exited. The runner polls the container state each second instead.
- **docker-java brings Jackson 2** (`jackson-databind` 2.21) as a transitive dependency. Bootshift's
  own code still uses Jackson 3 only, and an ArchUnit rule enforces that.
- **The sandbox network must be internal.** Bootshift checks this before every build, so a
  misconfigured install fails closed instead of giving builds internet access.

## Limits and open items

- Only Maven Central is proxied. Projects that need other repositories (Spring milestones, a
  company Nexus) fail dependency resolution and stop as `NOT_ELIGIBLE`. Adding upstreams is a
  Reposilite configuration change.
- Tests that need Docker cannot run in the sandbox. They show up as failures or skips; Phase 3
  reports them as "Not verified" (README section 13).
- The server talks to Docker directly. The socket proxy (README section 16) belongs to the
  production deployment in Phase 10.
- Clones have no size limit yet, and log retention is not configurable yet.
- After the baseline, the stages are still simulated and labelled `[simulated]`.
