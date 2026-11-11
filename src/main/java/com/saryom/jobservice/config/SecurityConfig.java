package com.saryom.jobservice.config;

import com.saryom.jobservice.auth.FirebaseAuthenticationFilter;
import com.saryom.jobservice.auth.ImportTokenAuthenticationFilter;
import com.saryom.jobservice.auth.RestAuthenticationEntryPoint;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Stateless security. The recruiter directory is public to read; everything
 * else requires a sign-in, except the upload endpoint, which also accepts an
 * import token. If a valid token is present on a public route it is still
 * honored, so signed-in users see recruiters' contact details.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http,
                                    FirebaseAuthenticationFilter firebaseFilter,
                                    ImportTokenAuthenticationFilter importTokenFilter,
                                    RestAuthenticationEntryPoint entryPoint) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> {})
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Authorization is enforced in the controller/service layer via
                // CurrentUser.requireUid() (→ 401), not by path matching: Spring
                // Security 7's PathPatternRequestMatcher matches bare collection
                // paths inconsistently (depends on the query string), which made
                // public browse fail in production for listing-service. The
                // filter still authenticates when a bearer token is present, and the
                // API gateway remains the primary auth boundary.
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .exceptionHandling(ex -> ex.authenticationEntryPoint(entryPoint))
                .addFilterBefore(firebaseFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(importTokenFilter, FirebaseAuthenticationFilter.class);
        return http.build();
    }

    /** Run the filter only inside the security chain, not as a servlet-registered filter. */
    @Bean
    FilterRegistrationBean<FirebaseAuthenticationFilter> disableFirebaseFilterAutoRegistration(
            FirebaseAuthenticationFilter filter) {
        FilterRegistrationBean<FirebaseAuthenticationFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    FilterRegistrationBean<ImportTokenAuthenticationFilter> disableImportTokenFilterAutoRegistration(
            ImportTokenAuthenticationFilter filter) {
        FilterRegistrationBean<ImportTokenAuthenticationFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
