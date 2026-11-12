package dev.bootshift.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring Framework 7 API versioning, read from the second path segment: {@code /api/v1/...}.
 * Mappings declare {@code version = "1"}; unsupported versions get a 400 Problem Details response.
 */
@Configuration
class ApiVersionConfig implements WebMvcConfigurer {

    static final String V1 = "1";

    @Override
    public void configureApiVersioning(ApiVersionConfigurer configurer) {
        configurer.usePathSegment(1).addSupportedVersions(V1);
    }
}
