package dev.bootshift.build;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.stream.Stream;

import com.github.dockerjava.api.DockerClient;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.unit.DataSize;

import dev.bootshift.sandbox.DockerSandbox;
import dev.bootshift.sandbox.Sandbox;
import dev.bootshift.sandbox.SandboxProperties;
import dev.bootshift.sandbox.SandboxTestSupport;

/**
 * Real Maven builds of the {@code simple-web} fixture in the sandbox, through the dependency
 * proxy, with a failing test and then a compiler error added. Slow and needs the dev proxy:
 *
 * <pre>
 * docker compose -f deploy/compose.dev.yaml up -d reposilite
 * docker build -t bootshift-sandbox:dev sandbox
 * BOOTSHIFT_E2E=true ./mvnw test -Dtest=BaselineEndToEndTest
 * </pre>
 */
@Tag("e2e")
@EnabledIfEnvironmentVariable(named = "BOOTSHIFT_E2E", matches = "true")
class BaselineEndToEndTest {

    static final String IMAGE = "bootshift-sandbox:dev";
    static final UUID RUN = UUID.randomUUID();

    static DockerClient docker;
    static DockerSandbox sandbox;
    static MavenBuild maven;

    @TempDir
    static Path tmp;

    @BeforeAll
    static void setUp() {
        docker = SandboxTestSupport.dockerOrSkip(IMAGE);
        SandboxProperties properties = new SandboxProperties(IMAGE, "bootshift-sandbox", "http://reposilite:8080/releases",
                DataSize.ofGigabytes(2), 2, 512, DataSize.ofMegabytes(512), Duration.ofMinutes(15), DataSize.ofMegabytes(50), null);
        sandbox = new DockerSandbox(docker, properties);
        maven = new MavenBuild(sandbox, properties);
    }

    @AfterAll
    static void tearDown() throws IOException {
        if (sandbox != null) {
            sandbox.release(RUN);
        }
        if (docker != null) {
            docker.close();
        }
    }

    @Test
    void reportsAFailingTestAndThenACompilerError() throws Exception {
        Path repo = copyFixture(tmp.resolve("simple-web"));
        Path testDir = repo.resolve("src/test/java/dev/bootshift/fixture/simpleweb");
        Files.writeString(testDir.resolve("BrokenTest.java"), """
                package dev.bootshift.fixture.simpleweb;

                import static org.junit.jupiter.api.Assertions.assertEquals;

                import org.junit.jupiter.api.Test;

                class BrokenTest {
                    @Test
                    void addsUp() {
                        assertEquals(4, 2 + 3);
                    }
                }
                """);

        BuildReport failingTest = maven.verify(RUN, BuildKind.BASELINE, repo, 17, tmp.resolve("logs/one.log"), l -> { }, () -> false);

        assertThat(failingTest.outcome()).isEqualTo(Sandbox.Outcome.EXITED);
        assertThat(failingTest.compiles()).isTrue();
        assertThat(failingTest.testsRun()).isGreaterThan(1);
        assertThat(failingTest.testsFailed()).isEqualTo(1);
        assertThat(failingTest.failures()).singleElement().satisfies(f -> {
            assertThat(f.type()).isEqualTo(FailureType.TEST);
            assertThat(f.file()).isEqualTo("dev.bootshift.fixture.simpleweb.BrokenTest");
            assertThat(f.symbol()).isEqualTo("addsUp");
            assertThat(f.message()).contains("expected: <4> but was: <5>");
        });

        Files.writeString(repo.resolve("src/main/java/dev/bootshift/fixture/simpleweb/Broken.java"), """
                package dev.bootshift.fixture.simpleweb;

                class Broken {
                    DoesNotExist field;
                }
                """);

        BuildReport compileError = maven.verify(RUN, BuildKind.BASELINE, repo, 17, tmp.resolve("logs/two.log"), l -> { }, () -> false);

        assertThat(compileError.compiles()).isFalse();
        assertThat(compileError.failures()).filteredOn(f -> f.type() == FailureType.COMPILE).singleElement().satisfies(f -> {
            assertThat(f.file()).isEqualTo("src/main/java/dev/bootshift/fixture/simpleweb/Broken.java");
            assertThat(f.line()).isEqualTo(4);
            assertThat(f.symbol()).isEqualTo("class DoesNotExist");
        });
    }

    private static Path copyFixture(Path target) throws IOException {
        Path source = Path.of("../fixtures/simple-web").toAbsolutePath().normalize();
        try (Stream<Path> files = Files.walk(source)) {
            for (Path file : (Iterable<Path>) files::iterator) {
                Path dest = target.resolve(source.relativize(file).toString());
                if (Files.isDirectory(file)) {
                    Files.createDirectories(dest);
                } else if (!file.toString().contains("target")) {
                    Files.copy(file, dest);
                }
            }
        }
        return target;
    }
}
