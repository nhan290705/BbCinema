package com.example.bbcinema.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpMethod;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.*;
import org.springframework.security.web.authentication.session.*;
import org.springframework.security.web.csrf.*;
import java.util.*;

@Configuration
public class SecurityConfig {
    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }
    @Bean
    public CsrfTokenRepository csrfTokenRepository() { return new HttpSessionCsrfTokenRepository(); }
    @Bean
    public SessionAuthenticationStrategy sessionAuthenticationStrategy(CsrfTokenRepository csrf) {
        return new CompositeSessionAuthenticationStrategy(List.of(
                new ChangeSessionIdAuthenticationStrategy(), new CsrfAuthenticationStrategy(csrf)));
    }
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityContextRepository repository,
                                                   CsrfTokenRepository csrf, ObjectMapper mapper) throws Exception {
        String[] managementPaths = {"/api/provinces", "/api/provinces/**", "/api/cinemas", "/api/cinemas/**",
                "/api/auditoriums", "/api/auditoriums/**", "/api/seat-types", "/api/seat-types/**"};
        http.securityContext(c -> c.securityContextRepository(repository))
            .csrf(c -> c.csrfTokenRepository(csrf))
            .requestCache(c -> c.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**",
                    "/", "/login", "/register", "/login.html", "/register.html", "/auth.css", "/auth.js", "/auth-art.svg",
                    "/admin.html", "/admin.css", "/admin-shell.js", "/admin-dashboard.js",
                    "/cinema-management.html", "/cinema-management.css", "/cinema-management.js",
                    "/seat-layout-editor.css", "/seat-layout-editor.js",
                    "/movie-management.html", "/movies.css", "/movies.js", "/movie-media.js",
                    "/showtime-management.html", "/showtimes.css", "/showtimes.js",
                    "/api/auth/csrf", "/api/auth/register", "/api/auth/login", "/error").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/movies", "/api/movies/*", "/api/genres").permitAll()
                .requestMatchers(HttpMethod.HEAD, "/api/movies", "/api/movies/*", "/api/genres").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/showtimes", "/api/showtimes/**").permitAll()
                .requestMatchers(HttpMethod.HEAD, "/api/showtimes", "/api/showtimes/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/demo/posters/**", "/uploads/posters/**").permitAll()
                .requestMatchers(HttpMethod.HEAD, "/demo/posters/**", "/uploads/posters/**").permitAll()
                .requestMatchers(HttpMethod.GET, managementPaths).authenticated()
                .requestMatchers(HttpMethod.HEAD, managementPaths).authenticated()
                .requestMatchers(managementPaths).hasRole("ADMIN")
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/manager/**").hasRole("MANAGER")
                .requestMatchers("/api/staff/**").hasRole("STAFF")
                .requestMatchers("/api/customer/**").hasRole("CUSTOMER")
                .anyRequest().authenticated())
            .logout(logout -> logout.logoutUrl("/api/auth/logout")
                .invalidateHttpSession(true).deleteCookies("JSESSIONID")
                .logoutSuccessHandler((req, res, auth) -> res.setStatus(204)))
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((req, res, ex) -> {
                    res.setStatus(401); res.setContentType("application/json;charset=UTF-8");
                    mapper.writeValue(res.getWriter(), Map.of("message", "Vui lòng đăng nhập"));
                })
                .accessDeniedHandler((req, res, ex) -> {
                    res.setStatus(403); res.setContentType("application/json;charset=UTF-8");
                    mapper.writeValue(res.getWriter(), Map.of("message", "Không có quyền truy cập hoặc CSRF token không hợp lệ"));
                }));
        return http.build();
    }
}
