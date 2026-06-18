package com.hotel_booking.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SecurityFilterTest {

    @Autowired
    private SecurityFilter securityFilter;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private SecurityFilterChain securityFilterChain;

    @Autowired
    @Qualifier("corsConfig")
    private WebMvcConfigurer corsConfigurer;

    @Test
    void securityBeansAreCreatedFromConfiguration() {
        assertThat(securityFilter).isNotNull();
        assertThat(passwordEncoder).isInstanceOf(BCryptPasswordEncoder.class);
        assertThat(authenticationManager).isNotNull();
        assertThat(securityFilterChain).isNotNull();
    }

    @Test
    void corsConfigurerAllowsAngularLocalhostForAllPathsMethodsAndHeaders() throws Exception {
        CorsRegistry registry = new CorsRegistry();

        corsConfigurer.addCorsMappings(registry);

        Method getCorsConfigurations = CorsRegistry.class.getDeclaredMethod("getCorsConfigurations");
        getCorsConfigurations.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, org.springframework.web.cors.CorsConfiguration> configurations =
                (Map<String, org.springframework.web.cors.CorsConfiguration>) getCorsConfigurations.invoke(registry);

        assertThat(configurations).containsKey("/**");
        org.springframework.web.cors.CorsConfiguration configuration = configurations.get("/**");
        assertThat(configuration.getAllowedOrigins()).containsExactly("http://localhost:4200");
        assertThat(configuration.getAllowedMethods()).containsExactly("*");
        assertThat(configuration.getAllowedHeaders()).containsExactly("*");
    }
}
