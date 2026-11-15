package dev.bootshift.build;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FingerprintsTest {

    @Test
    void ignoresDetailsThatChangeBetweenRuns() {
        String first = Fingerprints.normalize("Connection refused to localhost:54123 after 1503 ms at 2026-11-15T10:00:01.123Z by Foo@1a2b3c4d");
        String second = Fingerprints.normalize("Connection refused to localhost:61002 after 87 ms at 2026-11-16T09:12:44Z by Foo@99ffee01");

        assertThat(first).isEqualTo(second)
                .isEqualTo("Connection refused to localhost:<port> after <duration> at <time> by Foo@<hash>");
    }

    @Test
    void keepsOnlyTheFirstLineAndDropsSandboxPaths() {
        assertThat(Fingerprints.normalize("  /workspace/src/main/java/A.java:[12,5] boom\n\tat x.y.Z(Z.java:3)"))
                .isEqualTo("src/main/java/A.java boom");
    }

    @Test
    void sameProblemOnADifferentLineHasTheSameFingerprint() {
        Failure before = Failure.of(FailureType.COMPILE, ".", "src/main/java/A.java", 12, "class Foo", "cannot find symbol");
        Failure after = Failure.of(FailureType.COMPILE, ".", "src/main/java/A.java", 40, "class Foo", "cannot find symbol");

        assertThat(before.fingerprint()).isEqualTo(after.fingerprint()).hasSize(16);
    }

    @Test
    void differentSymbolsOrFilesHaveDifferentFingerprints() {
        String base = Fingerprints.of(FailureType.COMPILE, ".", "A.java", "class Foo", "cannot find symbol");

        assertThat(Fingerprints.of(FailureType.COMPILE, ".", "A.java", "class Bar", "cannot find symbol")).isNotEqualTo(base);
        assertThat(Fingerprints.of(FailureType.COMPILE, ".", "B.java", "class Foo", "cannot find symbol")).isNotEqualTo(base);
        assertThat(Fingerprints.of(FailureType.TEST, ".", "A.java", "class Foo", "cannot find symbol")).isNotEqualTo(base);
    }

    @Test
    void capsLongMessages() {
        assertThat(Fingerprints.normalize("x".repeat(1000))).hasSize(Fingerprints.MAX_MESSAGE);
        assertThat(Fingerprints.normalize(null)).isEqualTo("(no message)");
    }
}
