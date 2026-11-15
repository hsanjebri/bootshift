package dev.bootshift.build;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.xml.stream.XMLStreamException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SurefireReportsTest {

    static final String REPORT = """
            <?xml version="1.0" encoding="UTF-8"?>
            <testsuite name="dev.x.TodoControllerTest" tests="4" failures="1" errors="1" skipped="1">
              <testcase name="listsTodos" classname="dev.x.TodoControllerTest" time="0.1"/>
              <testcase name="rejectsMissingDone" classname="dev.x.TodoControllerTest" time="0.2">
                <failure message="Status expected:&lt;201&gt; but was:&lt;400&gt;" type="java.lang.AssertionError"><![CDATA[java.lang.AssertionError: Status expected:<201> but was:<400>
            \tat dev.x.TodoControllerTest.rejectsMissingDone(TodoControllerTest.java:42)]]></failure>
                <system-out>log</system-out>
              </testcase>
              <testcase name="contextLoads" classname="dev.x.TodoControllerTest" time="0.3">
                <error message="Failed to load ApplicationContext for [WebMergedContextConfiguration@5a1b2c3d testClass = dev.x.TodoControllerTest]" type="java.lang.IllegalStateException">stack</error>
              </testcase>
              <testcase name="later" classname="dev.x.TodoControllerTest" time="0">
                <skipped message="disabled"/>
              </testcase>
            </testsuite>
            """;

    @Test
    void countsTestCasesAndReadsFailures() throws Exception {
        SurefireReports.Summary summary = parse(REPORT);

        assertThat(summary.run()).isEqualTo(4);
        assertThat(summary.failed()).isEqualTo(2);
        assertThat(summary.skipped()).isEqualTo(1);
        assertThat(summary.failures()).hasSize(2);

        Failure assertion = summary.failures().get(0);
        assertThat(assertion.type()).isEqualTo(FailureType.TEST);
        assertThat(assertion.file()).isEqualTo("dev.x.TodoControllerTest");
        assertThat(assertion.symbol()).isEqualTo("rejectsMissingDone");
        assertThat(assertion.message()).isEqualTo("AssertionError: Status expected:<201> but was:<400>");

        Failure startup = summary.failures().get(1);
        assertThat(startup.type()).isEqualTo(FailureType.STARTUP);
        assertThat(startup.message()).contains("WebMergedContextConfiguration@<hash>");
    }

    @Test
    void refusesExternalEntities(@TempDir Path dir) throws Exception {
        Path secret = Files.writeString(dir.resolve("secret.txt"), "TOP-SECRET");
        String xxe = """
                <?xml version="1.0"?>
                <!DOCTYPE testsuite [<!ENTITY leak SYSTEM "%s">]>
                <testsuite><testcase name="t" classname="C"><failure message="&leak;"/></testcase></testsuite>
                """.formatted(secret.toUri());

        try {
            SurefireReports.Summary summary = parse(xxe);
            assertThat(summary.failures()).allSatisfy(f -> assertThat(f.message()).doesNotContain("TOP-SECRET"));
        } catch (XMLStreamException expected) {
            // Rejecting the document outright is just as good.
        }
    }

    @Test
    void readsReportsOfEveryModuleButNotDependencies(@TempDir Path repo) throws Exception {
        write(repo.resolve("target/surefire-reports/TEST-a.xml"), REPORT);
        write(repo.resolve("web/api/target/failsafe-reports/TEST-b.xml"), REPORT);
        write(repo.resolve("web/target/surefire-reports/not-a-report.xml"), REPORT);
        write(repo.resolve("node_modules/x/target/surefire-reports/TEST-c.xml"), REPORT);

        SurefireReports.Summary summary = SurefireReports.read(repo);

        assertThat(summary.run()).isEqualTo(8);
        assertThat(summary.failures()).extracting(Failure::module).containsOnly(".", "web/api");
    }

    @Test
    void anEmptyWorkingCopyHasNoTests(@TempDir Path repo) throws Exception {
        assertThat(SurefireReports.read(repo)).isEqualTo(SurefireReports.Summary.EMPTY);
    }

    private static SurefireReports.Summary parse(String xml) throws XMLStreamException {
        return SurefireReports.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), ".");
    }

    private static void write(Path file, String content) throws Exception {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
