package com.civictrack.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Server-side authorization: URL rules live HERE, so hiding a button in HTML is never the only protection.
 * (The services add a second layer of checks.)
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/login", "/register", "/error", "/access-denied",
                                 "/css/**", "/js/**", "/images/**").permitAll()
                .requestMatchers("/citizen/**").hasRole("CITIZEN")
                .requestMatchers("/authority/**").hasRole("AUTHORITY")
                .requestMatchers("/worker/**").hasRole("FIELD_WORKER")
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/dashboard", true)
                .failureUrl("/login?error")
                .permitAll())
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .permitAll())
            .exceptionHandling(ex -> ex.accessDeniedPage("/access-denied"))
            .sessionManagement(session -> session.sessionFixation(fix -> fix.migrateSession()));
        // CSRF protection stays ON (default). Thymeleaf forms add the token automatically.
        return http.build();
    }
}
