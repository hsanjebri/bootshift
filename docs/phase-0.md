# Phase 0: prove the idea

Date: 2026-11-02. Goal (README §19): fixtures reproducibly reach known failures after the
OpenRewrite recipes, and the plan is updated with what we learned.

## Pinned versions

| Thing | Version | How it was chosen |
|---|---|---|
| `rewrite-maven-plugin` | 6.46.1 | Pair listed in `rewrite-recipe-bom` 3.37.0 |
| `rewrite-spring` | 6.37.1 | Patch release on the 3.37.0 pair (6.37.0) |
| Fixture source | Spring Boot 3.5.16, Java 17 | Last 3.5.x on Maven Central |
| Target | Spring Boot 4.1.2 | Latest 4.1.x; 4.2.0-RC1 is not stable |
| Maven | 3.9.16 | Docker image `maven:3.9.16-eclipse-temurin-17` |

`rewrite-recipe-bom` 3.38.0 is the newest BOM, but it points at `rewrite-spring` 6.38.0 and
plugin 6.47.0, which return 404 on Maven Central. Don't trust the newest BOM blindly: check that
the versions it lists actually exist before pinning them.

## How to reproduce

```bash
scripts/phase0-run.sh <fixture>            # Maven inside a throwaway Docker container (default)
MVN=/path/to/mvn scripts/phase0-run.sh <fixture> --local   # local Maven; trusted fixtures only
```

Each run copies the fixture to `work/<fixture>` with one git commit per stage. It writes logs,
Surefire summaries and the recipe diff to `evals/results/phase-0/<fixture>/`.

The runs below used `--local` (Maven 3.9.16 on JDK 23), because Docker was not running yet.
The fixtures compile with `release 17`.

## Results

| Fixture | Baseline | After recipes | What is left |
|---|---|---|---|
| `simple-web` | compiles, 5 tests, 0 failing | compiles, 5 tests, **1 failing** | Jackson 3 behavior change |
| `rest-clients` | compiles, 5 tests, 0 failing | compiles, 5 tests, **3 errors** | Missing `WebClient.Builder` bean |
| `security-chain` | compiles, 7 tests, 0 failing | **does not compile** (6 errors, 1 file) | Security 7 matcher removals |

**Reproducibility:** I ran `simple-web` and `rest-clients` twice. The recipe diffs were the same
apart from git `index` lines, and the verify results were the same. `security-chain` has only one
complete run with the final script; the first attempt exposed the `run` vs `runNoFork` bug below.

### What the recipes handled

| Change | Fixture |
|---|---|
| Parent 3.5.16 → 4.1.2 (via 4.0.x) | all |
| `spring-boot-starter-web` → `spring-boot-starter-webmvc` | all |
| Added `spring-boot-starter-webmvc-test` | simple-web, security-chain |
| `@WebMvcTest` / `@AutoConfigureMockMvc` → `org.springframework.boot.webmvc.test.autoconfigure` | simple-web, security-chain |
| `@MockBean` → `@MockitoBean` | simple-web |
| `RestTemplateBuilder` → `org.springframework.boot.restclient`, added `spring-boot-starter-restclient` | rest-clients |
| `TestRestTemplate` → `org.springframework.boot.resttestclient`, added `@AutoConfigureTestRestTemplate` and `spring-boot-resttestclient` | rest-clients |
| `@RestClientTest` → `org.springframework.boot.restclient.test.autoconfigure`, added `spring-boot-starter-restclient-test` | rest-clients |
| `fromHttpUrl` → `fromUriString` | rest-clients |
| `getRawStatusCode()` → `getStatusCode().value()` | rest-clients |
| `http.csrf().disable()` → `http.csrf(csrf -> csrf.disable())` | security-chain |

### What the recipes left for the agent

1. **`simple-web`: `TodoControllerTest.createsTodo` returns 400 instead of 201.**
   `HttpMessageNotReadableException: Cannot map null into type boolean`. Jackson 3 enables
   `FAIL_ON_NULL_FOR_PRIMITIVES` by default, so a request body that omits `done` is now rejected.
   This changes behavior for API clients, not just the tests. The tempting fix is to add
   `"done":false` to the test request, and that fix is wrong: it hides the break. The right fix is
   `spring.jackson.deserialization.fail-on-null-for-primitives=false`, or a deliberate API change
   listed under "Review by hand". This is a good case for the `tempting-shortcut` style of eval.
2. **`rest-clients`: all 3 `@SpringBootTest` tests fail to start the context.**
   `No qualifying bean of type WebClient$Builder`. The recipes kept `spring-boot-starter-webflux`,
   which no longer auto-configures `WebClient.Builder` in Boot 4. The fix is to add
   `spring-boot-starter-webclient`, which is playbook entry `webclient-builder-bean-missing`.
3. **`security-chain`: 6 compile errors in `SecurityConfig.java`.**
   `AntPathRequestMatcher` and `MvcRequestMatcher` are gone in Spring Security 7.0.8, and the recipe
   does not rewrite them. The fix is `PathPatternRequestMatcher.pathPattern(...)` or plain string
   patterns, and dropping the `HandlerMappingIntrospector` parameter.

These are the three failure classes the agent must fix in Phase 4: a behavior change, a missing
dependency or bean, and an API removal.

## Spec corrections (proposed and applied to README)

1. **There is no `UpgradeSpringBoot_4_1` umbrella recipe** in rewrite-spring 6.37.1. I checked by
   listing `META-INF/rewrite/*.yml` in the jar. The Boot 4 recipes are `UpgradeSpringBoot_4_0`,
   `SpringBootProperties_4_0`, `SpringBootProperties_4_1` and some smaller ones.
   `recipe/bootshift-boot-4-1.yml` defines `dev.bootshift.UpgradeSpringBoot_4_1`, which chains the
   4.1 properties recipe with pinned version bumps. It only references recipes; nothing is bundled.
2. **Use `rewrite:runNoFork`, not `rewrite:run`.** `run` forks the Maven lifecycle and compiles the
   project first. After the 4.0 step, `security-chain` no longer compiled, so the 4.1 step failed
   before any recipe ran. `runNoFork` parses the sources itself and works on code that doesn't compile.
3. **The playbook seed had three inaccurate entries**: `UNPROCESSABLE_ENTITY` (deprecated, not
   removed), Mongo properties (only some move) and Spock (Boot dropped its integration). See
   `playbook/boot-3-to-4.yaml` for all entries with status and sources.
4. **README §6, "Green is not the same as safe"** now lists the Jackson 3 null-for-primitives change.

## Other findings

- **`UpgradeSpringBoot_4_0` resolves `4.0.x` at run time** (latest 4.0 patch). Our 4.1 step
  immediately pins 4.1.2, so the end state is reproducible, but the intermediate commit can vary
  as 4.0 patches ship. Record the resolved versions from the datatables in every run report.
- **Spring Framework under Boot 4.1.2 is 7.0.10**, Spring Security is 7.0.8 and Jackson databind
  is 3.1.7. I checked the playbook API facts against these jars with `javap`.
- **Name check:** `github.com/hsanjebri/bootshift` is free. Note that
  `github.com/KrishnaAnnavaram/bootshift` (created 2026-10) describes a very similar Spring Boot
  migration harness. Consider this before launch (search ranking, confusion).
- **Windows checkouts** need `core.autocrlf=false` in work copies, or recipe diffs pick up
  line-ending noise. The script sets it.

## Still open in Phase 0

- [ ] **Raw model test (step 7):** give one failure from the list above, with the file excerpts and
      the matching playbook entry, to a model through a plain prompt, and check whether its patch
      makes the build green. Blocked on an API key. Best candidate: `security-chain`, because it is
      a compile error with a clear correct answer.
- [ ] **Run every fixture once in Docker mode** to confirm the container path matches `--local`.
- [ ] Check the domain name.
