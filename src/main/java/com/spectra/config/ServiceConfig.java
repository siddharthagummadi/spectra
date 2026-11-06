package com.spectra.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.spectra.repository.UserRepository;

@Configuration
public class ServiceConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(Customizer.withDefaults())
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/api/health", "/api/users/register", "/actuator/**").permitAll()

                    .requestMatchers(HttpMethod.GET, "/api/services", "/api/services/**").hasAnyRole("ADMIN", "USER")
                    .requestMatchers(HttpMethod.POST, "/api/services", "/api/services/**").hasAnyRole("ADMIN", "USER")
                    .requestMatchers(HttpMethod.PUT, "/api/services/**").hasAnyRole("ADMIN", "USER")
                    .requestMatchers(HttpMethod.PATCH, "/api/services/**").hasAnyRole("ADMIN", "USER")
                    .requestMatchers(HttpMethod.DELETE, "/api/services/**").hasRole("ADMIN")

                    .requestMatchers(HttpMethod.GET, "/api/alert-rules", "/api/alert-rules/**").hasAnyRole("ADMIN", "USER")
                    .requestMatchers(HttpMethod.POST, "/api/alert-rules", "/api/alert-rules/**").hasAnyRole("ADMIN", "USER")
                    .requestMatchers(HttpMethod.PUT, "/api/alert-rules/**").hasAnyRole("ADMIN", "USER")
                    .requestMatchers(HttpMethod.PATCH, "/api/alert-rules/**").hasAnyRole("ADMIN", "USER")
                    .requestMatchers(HttpMethod.DELETE, "/api/alert-rules/**").hasAnyRole("ADMIN", "USER")

                    .requestMatchers(HttpMethod.GET, "/api/metrics", "/api/metrics/**").hasAnyRole("ADMIN", "USER")
                    .requestMatchers(HttpMethod.POST, "/api/metrics", "/api/metrics/**").hasAnyRole("ADMIN", "USER")

                    .requestMatchers("/api/alerts", "/api/alerts/**").hasAnyRole("ADMIN", "USER")

                    .requestMatchers("/api/anomalies", "/api/anomalies/**").hasAnyRole("ADMIN", "USER")

                    .requestMatchers("/api/ai", "/api/ai/**").hasAnyRole("ADMIN", "USER")

                    .requestMatchers("/api/incidents", "/api/incidents/**").hasAnyRole("ADMIN", "USER")

                    .requestMatchers(HttpMethod.GET, "/api/users/me").hasAnyRole("ADMIN", "USER")
                    .requestMatchers(HttpMethod.PATCH, "/api/users/*/role").hasRole("ADMIN")

                    .anyRequest().authenticated()
            )
            .formLogin(form -> form.disable())
            .httpBasic(Customizer.withDefaults());

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:5173", "http://localhost:3000"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public UserDetailsService userDetailsService(UserRepository userRepository) {
        return username -> userRepository.findByEmail(username)
                .map(user -> org.springframework.security.core.userdetails.User
                        .withUsername(user.getEmail())
                        .password(user.getPassword())
                        .roles(user.getRole().name())
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + username));
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    @Profile("!test")
    public org.springframework.boot.ApplicationRunner databaseMigrationInitializer(org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
        return args -> {
            jdbcTemplate.execute("ALTER TABLE monitored_services ADD COLUMN IF NOT EXISTS user_id BIGINT");
            jdbcTemplate.execute(
                "DO $$ BEGIN "
                + "IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_monitored_services_user') THEN "
                + "  ALTER TABLE monitored_services ADD CONSTRAINT fk_monitored_services_user FOREIGN KEY (user_id) REFERENCES users(id); "
                + "END IF; "
                + "END $$;"
            );
            jdbcTemplate.execute(
                "UPDATE monitored_services "
                + "SET user_id = (SELECT id FROM users WHERE email = 'operator@spectra.local') "
                + "WHERE user_id IS NULL AND EXISTS (SELECT 1 FROM users WHERE email = 'operator@spectra.local')"
            );
            jdbcTemplate.execute(
                "UPDATE monitored_services "
                + "SET user_id = (SELECT MIN(id) FROM users) "
                + "WHERE user_id IS NULL"
            );
            jdbcTemplate.execute("ALTER TABLE monitored_services DROP CONSTRAINT IF EXISTS uk64ac0rx6a93ujsmg854rl8h9i");
            jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_services_user_name ON monitored_services (user_id, name)");
        };
    }
}
