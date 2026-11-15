package dev.bootshift.build;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class MavenLogTest {

    @Test
    void readsCompilerErrorsWithSymbolsAndModules() {
        List<String> log = List.of(
                "[INFO] -------------------------------------------------------------",
                "[ERROR] COMPILATION ERROR : ",
                "[INFO] -------------------------------------------------------------",
                "[ERROR] /workspace/api/src/main/java/dev/x/SecurityConfig.java:[8,58] package org.springframework.security.web.util.matcher does not exist",
                "[ERROR] /workspace/api/src/main/java/dev/x/SecurityConfig.java:[21,40] cannot find symbol",
                "[ERROR]   symbol:   class AntPathRequestMatcher",
                "[ERROR]   location: class dev.x.SecurityConfig",
                "[INFO] 2 errors ",
                // Maven repeats the errors in its summary.
                "[ERROR] Failed to execute goal org.apache.maven.plugins:maven-compiler-plugin:3.14.1:compile (default-compile) on project api: Compilation failure: Compilation failure: ",
                "[ERROR] /workspace/api/src/main/java/dev/x/SecurityConfig.java:[8,58] package org.springframework.security.web.util.matcher does not exist",
                "[ERROR] /workspace/api/src/main/java/dev/x/SecurityConfig.java:[21,40] cannot find symbol",
                "[ERROR]   symbol:   class AntPathRequestMatcher",
                "[ERROR]   location: class dev.x.SecurityConfig");

        List<Failure> failures = MavenLog.parse(log, 1);

        assertThat(failures).hasSize(2).allSatisfy(f -> {
            assertThat(f.type()).isEqualTo(FailureType.COMPILE);
            assertThat(f.module()).isEqualTo("api");
            assertThat(f.file()).isEqualTo("api/src/main/java/dev/x/SecurityConfig.java");
        });
        assertThat(failures.get(0).line()).isEqualTo(8);
        assertThat(failures.get(0).symbol()).isNull();
        assertThat(failures.get(1).symbol()).isEqualTo("class AntPathRequestMatcher");
        assertThat(failures.get(1).message()).isEqualTo("cannot find symbol");
    }

    @Test
    void rootModuleFilesBelongToDot() {
        List<Failure> failures = MavenLog.parse(List.of("[ERROR] /workspace/src/main/java/A.java:[3,1] ';' expected"), 1);

        assertThat(failures).singleElement().satisfies(f -> {
            assertThat(f.module()).isEqualTo(".");
            assertThat(f.file()).isEqualTo("src/main/java/A.java");
        });
    }

    @Test
    void readsDependencyResolutionFailures() {
        List<Failure> failures = MavenLog.parse(List.of(
                "[ERROR] Failed to execute goal on project shop: Could not resolve dependencies for project dev.x:shop:jar:1.0: "
                        + "Could not find artifact com.acme:internal-lib:jar:2.3 in bootshift-proxy (http://reposilite:8080/releases)"), 1);

        assertThat(failures).singleElement().satisfies(f -> {
            assertThat(f.type()).isEqualTo(FailureType.DEPENDENCY);
            assertThat(f.symbol()).isEqualTo("dev.x:shop:jar:1.0");
            assertThat(f.message()).startsWith("Failed to execute goal: Could not resolve dependencies");
        });
    }

    @Test
    void reportsAnUnrecognizedFailureAsOther() {
        List<Failure> failures = MavenLog.parse(List.of(
                "[ERROR] Failed to execute goal com.diffplug.spotless:spotless-maven-plugin:2.44.0:check (default) on project shop: "
                        + "The following files had format violations: -> [Help 1]"), 1);

        assertThat(failures).singleElement().satisfies(f -> {
            assertThat(f.type()).isEqualTo(FailureType.OTHER);
            assertThat(f.symbol()).isEqualTo("com.diffplug.spotless:spotless-maven-plugin:2.44.0:check");
        });
    }

    @Test
    void aFailedBuildWithoutAnyErrorLineStillReportsSomething() {
        assertThat(MavenLog.parse(List.of(), 137)).singleElement()
                .satisfies(f -> assertThat(f.message()).isEqualTo("Build failed with exit code 137"));
        assertThat(MavenLog.parse(List.of(), 0)).isEmpty();
    }

    @Test
    void sameErrorOnTwoLinesOfAFileIsTwoFailures() {
        List<Failure> failures = MavenLog.parse(List.of(
                "[ERROR] /workspace/src/main/java/A.java:[3,1] cannot find symbol",
                "[ERROR]   symbol:   class Foo",
                "[ERROR] /workspace/src/main/java/A.java:[9,1] cannot find symbol",
                "[ERROR]   symbol:   class Foo"), 1);

        assertThat(failures).hasSize(2);
        assertThat(failures.get(0).fingerprint()).isEqualTo(failures.get(1).fingerprint());
    }
}
