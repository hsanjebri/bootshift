package dev.bootshift.build;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.OptionalInt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class JavaVersionsTest {

    @ParameterizedTest
    @CsvSource({"8, 17", "11, 17", "17, 17", "18, 21", "21, 21", "24, 25", "25, 25"})
    void picksTheSmallestJdkThatIsNewEnough(int declared, int jdk) {
        assertThat(JavaVersions.select(OptionalInt.of(declared)).jdk()).isEqualTo(jdk);
    }

    @Test
    void refusesJavaNewerThanTheSandbox() {
        assertThatThrownBy(() -> JavaVersions.select(OptionalInt.of(26)))
                .isInstanceOf(JavaVersions.UnsupportedJava.class)
                .hasMessageContaining("Java 26");
    }

    @Test
    void readsTheFixturePom() throws Exception {
        JavaVersions.Selection selection = JavaVersions.select(Path.of("../fixtures/simple-web"));

        assertThat(selection.declared()).hasValue(17);
        assertThat(selection.jdk()).isEqualTo(17);
    }

    @Test
    void prefersReleaseAndResolvesReferences(@TempDir Path repo) throws Exception {
        Files.writeString(repo.resolve("pom.xml"), """
                <project>
                  <properties>
                    <jdk>21</jdk>
                    <java.version>17</java.version>
                    <maven.compiler.release>${jdk}</maven.compiler.release>
                  </properties>
                  <profiles><profile><properties><java.version>25</java.version></properties></profile></profiles>
                </project>
                """);

        assertThat(JavaVersions.select(repo)).isEqualTo(new JavaVersions.Selection(OptionalInt.of(21), 21));
    }

    @Test
    void understandsOldStyleVersions() {
        assertThat(JavaVersions.parse("1.8")).hasValue(8);
        assertThat(JavaVersions.parse("21.0.2")).hasValue(21);
        assertThat(JavaVersions.parse("${unresolved}")).isEmpty();
    }

    @Test
    void defaultsTo17WithoutAPom(@TempDir Path repo) throws Exception {
        assertThat(JavaVersions.select(repo)).isEqualTo(new JavaVersions.Selection(OptionalInt.empty(), 17));
    }
}
