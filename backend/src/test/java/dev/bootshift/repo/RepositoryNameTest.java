package dev.bootshift.repo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RepositoryNameTest {

    @Test
    void parsesOwnerAndName() {
        RepositoryName name = RepositoryName.parse("hsanjebri/bootshift");

        assertThat(name.owner()).isEqualTo("hsanjebri");
        assertThat(name.name()).isEqualTo("bootshift");
        assertThat(name).hasToString("hsanjebri/bootshift");
    }

    @Test
    void acceptsDotsUnderscoresAndHyphensInName() {
        assertThat(RepositoryName.parse("spring-projects/spring-petclinic.v2_x")).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "no-slash", "owner/", "/name", "a/b/c", "owner/..", "owner/.", "-owner/name",
            "owner/na me", "owner/name;rm", "https://github.com/a/b", "own_er/name"})
    void rejectsAnythingThatIsNotAPlainOwnerSlashName(String input) {
        assertThatIllegalArgumentException().isThrownBy(() -> RepositoryName.parse(input));
    }
}
