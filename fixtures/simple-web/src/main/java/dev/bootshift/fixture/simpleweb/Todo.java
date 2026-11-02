package dev.bootshift.fixture.simpleweb;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record Todo(Long id, @NotBlank @Size(max = 80) String title, boolean done) {
}
