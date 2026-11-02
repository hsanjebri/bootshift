package dev.bootshift.fixture.securitychain;

import static org.springframework.security.config.Customizer.withDefaults;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.MvcRequestMatcher;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.servlet.handler.HandlerMappingIntrospector;

/**
 * Security config written in the Spring Security 6 style. It uses three things that Security 7
 * removes: {@code AntPathRequestMatcher}, {@code MvcRequestMatcher} with
 * {@code HandlerMappingIntrospector}, and the non-lambda {@code http.csrf()} DSL.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    @SuppressWarnings({"deprecation", "removal"})
    SecurityFilterChain filterChain(HttpSecurity http, HandlerMappingIntrospector introspector) throws Exception {
        MvcRequestMatcher.Builder mvc = new MvcRequestMatcher.Builder(introspector);

        http.authorizeHttpRequests(auth -> auth
                .requestMatchers(new AntPathRequestMatcher("/public/**")).permitAll()
                .requestMatchers(mvc.pattern("/api/admin/**")).hasRole("ADMIN")
                .requestMatchers(new AntPathRequestMatcher("/api/**")).authenticated()
                .anyRequest().denyAll())
            .httpBasic(withDefaults());

        http.csrf().disable();

        return http.build();
    }

    @Bean
    @SuppressWarnings("deprecation")
    UserDetailsService users() {
        return new InMemoryUserDetailsManager(
                User.withDefaultPasswordEncoder().username("user").password("password").roles("USER").build(),
                User.withDefaultPasswordEncoder().username("admin").password("password").roles("USER", "ADMIN").build());
    }
}
