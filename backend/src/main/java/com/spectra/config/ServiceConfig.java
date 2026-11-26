package com.spectra.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.spectra.repository.AgentCredentialRepository;
import com.spectra.repository.EnterpriseAgentRepository;
import com.spectra.repository.EnterpriseRepository;
import com.spectra.repository.UserRepository;
import com.spectra.security.AgentAuthenticationFilter;

@Configuration
public class ServiceConfig {

    @Value("${spectra.cors.allowed-origins:http://localhost:5173,http://localhost:3000}")
    private String allowedOrigins;

    /**
     * Isolated authentication chain strictly for standalone Enterprise Monitoring Agents.
     * Human User/Admin Basic credentials are not accepted on /api/agent/**.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain agentSecurityFilterChain(
            HttpSecurity http,
            AgentCredentialRepository credentialRepository,
            EnterpriseAgentRepository agentRepository,
            EnterpriseRepository enterpriseRepository) throws Exception {
        AgentAuthenticationFilter agentFilter = new AgentAuthenticationFilter(
                credentialRepository, agentRepository, enterpriseRepository);

        http
            .securityMatcher("/api/agent/**")
            .cors(Customizer.withDefaults())
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers(HttpMethod.POST, "/api/agent/enroll").permitAll()
                    .anyRequest().hasRole("ENTERPRISE_AGENT")
            )
            .addFilterBefore(agentFilter, UsernamePasswordAuthenticationFilter.class)
            .httpBasic(basic -> basic.disable())
            .formLogin(form -> form.disable());

        return http.build();
    }

    /**
     * Human User and Platform Admin security chain for /api/** and /actuator/**.
     * Platform Admin has read-only access to enterprise operational endpoints and
     * write access strictly to platform-level governance (/api/admin/**, integrations approval, audit logs).
     */
    @Bean
    @Order(2)
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(Customizer.withDefaults())
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                    // Public Endpoints
                    .requestMatchers("/api/health", "/actuator/health", "/api/users/register").permitAll()

                    // Actuator Admin Endpoints
                    .requestMatchers("/actuator/**").hasRole("ADMIN")

                    // Platform Admin Enterprise & Agent Governance
                    .requestMatchers("/api/admin", "/api/admin/**").hasRole("ADMIN")

                    // Enterprise User Agent Visibility
                    .requestMatchers(HttpMethod.GET, "/api/enterprise/agents", "/api/enterprise/agents/**")
                        .hasAnyRole("OPERATIONS", "USER")

                    // Telemetry Ingestion Endpoints (Strictly Enterprise Users; Admin Read-Only)
                    .requestMatchers("/api/telemetry", "/api/telemetry/**").hasAnyRole("OPERATIONS", "USER")

                    // Monitored Services: Read by ADMIN/OPERATIONS/USER; Write strictly OPERATIONS/USER
                    .requestMatchers(HttpMethod.GET, "/api/services", "/api/services/**")
                        .hasAnyRole("ADMIN", "OPERATIONS", "USER")
                    .requestMatchers(HttpMethod.POST, "/api/services", "/api/services/**")
                        .hasAnyRole("OPERATIONS", "USER")
                    .requestMatchers(HttpMethod.PUT, "/api/services/**").hasAnyRole("OPERATIONS", "USER")
                    .requestMatchers(HttpMethod.PATCH, "/api/services/**").hasAnyRole("OPERATIONS", "USER")
                    .requestMatchers(HttpMethod.DELETE, "/api/services/**").hasAnyRole("OPERATIONS", "USER")

                    // Alert Rules: Read by all roles; Write strictly OPERATIONS/USER
                    .requestMatchers(HttpMethod.GET, "/api/alert-rules", "/api/alert-rules/**")
                        .hasAnyRole("ADMIN", "OPERATIONS", "USER")
                    .requestMatchers(HttpMethod.POST, "/api/alert-rules", "/api/alert-rules/**")
                        .hasAnyRole("OPERATIONS", "USER")
                    .requestMatchers(HttpMethod.PUT, "/api/alert-rules/**").hasAnyRole("OPERATIONS", "USER")
                    .requestMatchers(HttpMethod.PATCH, "/api/alert-rules/**").hasAnyRole("OPERATIONS", "USER")
                    .requestMatchers(HttpMethod.DELETE, "/api/alert-rules/**").hasAnyRole("OPERATIONS", "USER")

                    // Metrics: Read by all roles; Manual submit strictly OPERATIONS/USER
                    .requestMatchers(HttpMethod.GET, "/api/metrics", "/api/metrics/**")
                        .hasAnyRole("ADMIN", "OPERATIONS", "USER")
                    .requestMatchers(HttpMethod.POST, "/api/metrics", "/api/metrics/**")
                        .hasAnyRole("OPERATIONS", "USER")

                    // Alert Events: Read by all roles; Acknowledge strictly OPERATIONS/USER
                    .requestMatchers(HttpMethod.GET, "/api/alerts", "/api/alerts/**")
                        .hasAnyRole("ADMIN", "OPERATIONS", "USER")
                    .requestMatchers(HttpMethod.PATCH, "/api/alerts/**").hasAnyRole("OPERATIONS", "USER")

                    // Anomaly Events: Read by all roles; Acknowledge strictly OPERATIONS/USER
                    .requestMatchers(HttpMethod.GET, "/api/anomalies", "/api/anomalies/**")
                        .hasAnyRole("ADMIN", "OPERATIONS", "USER")
                    .requestMatchers(HttpMethod.PATCH, "/api/anomalies/**").hasAnyRole("OPERATIONS", "USER")

                    // AI Diagnostics & Analytics
                    .requestMatchers(HttpMethod.GET, "/api/ai", "/api/ai/**")
                        .hasAnyRole("ADMIN", "OPERATIONS", "USER")
                    .requestMatchers(HttpMethod.POST, "/api/ai/rca", "/api/ai/forecast", "/api/ai/runbooks", "/api/ai/copilot")
                        .hasAnyRole("ADMIN", "OPERATIONS", "USER")
                    .requestMatchers(HttpMethod.POST, "/api/ai/copilot/config").hasRole("ADMIN")

                    // Incidents: Read by all roles; Create/Modify/Resolve/Notes/Trigger AI strictly OPERATIONS/USER
                    .requestMatchers(HttpMethod.GET, "/api/incidents", "/api/incidents/**")
                        .hasAnyRole("ADMIN", "OPERATIONS", "USER")
                    .requestMatchers(HttpMethod.POST, "/api/incidents", "/api/incidents/**")
                        .hasAnyRole("OPERATIONS", "USER")
                    .requestMatchers(HttpMethod.PATCH, "/api/incidents/**").hasAnyRole("OPERATIONS", "USER")
                    .requestMatchers(HttpMethod.PUT, "/api/incidents/**").hasAnyRole("OPERATIONS", "USER")
                    .requestMatchers(HttpMethod.DELETE, "/api/incidents/**").hasAnyRole("OPERATIONS", "USER")

                    // Ingestion: Read by all roles; Write/Delete strictly OPERATIONS/USER
                    .requestMatchers(HttpMethod.GET, "/api/ingestion", "/api/ingestion/**")
                        .hasAnyRole("ADMIN", "OPERATIONS", "USER")
                    .requestMatchers(HttpMethod.POST, "/api/ingestion", "/api/ingestion/**")
                        .hasAnyRole("OPERATIONS", "USER")
                    .requestMatchers(HttpMethod.DELETE, "/api/ingestion/**").hasAnyRole("OPERATIONS", "USER")

                    // Enterprise Integrations: Status changes strictly ADMIN; requests by OPERATIONS/USER; view by all
                    .requestMatchers(HttpMethod.GET, "/api/integrations", "/api/integrations/**")
                        .hasAnyRole("ADMIN", "OPERATIONS", "USER")
                    .requestMatchers(HttpMethod.POST, "/api/integrations/request").hasAnyRole("OPERATIONS", "USER")
                    .requestMatchers(HttpMethod.PATCH, "/api/integrations/*/status").hasRole("ADMIN")

                    // Evaluation & Benchmark
                    .requestMatchers(HttpMethod.GET, "/api/evaluation", "/api/evaluation/**")
                        .hasAnyRole("ADMIN", "OPERATIONS", "USER")
                    .requestMatchers(HttpMethod.POST, "/api/evaluation", "/api/evaluation/**")
                        .hasAnyRole("OPERATIONS", "USER")

                    // In-App Notifications
                    .requestMatchers("/api/notifications", "/api/notifications/**")
                        .hasAnyRole("ADMIN", "OPERATIONS", "USER")

                    // Audit Logs: Strictly ADMIN
                    .requestMatchers("/api/audit-logs", "/api/audit-logs/**").hasRole("ADMIN")

                    // Demo Profile Endpoints
                    .requestMatchers("/api/demo", "/api/demo/**").hasAnyRole("ADMIN", "OPERATIONS", "USER")

                    // User Management: Profile by any authenticated role, Role Escalation strictly ADMIN
                    .requestMatchers(HttpMethod.GET, "/api/users/me").hasAnyRole("ADMIN", "OPERATIONS", "USER")
                    .requestMatchers(HttpMethod.PATCH, "/api/users/*/role").hasRole("ADMIN")

                    // Any Other Request Requires Authentication
                    .anyRequest().authenticated()
            )
            .formLogin(form -> form.disable())
            .httpBasic(Customizer.withDefaults());

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
        configuration.setAllowedOriginPatterns(origins);
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
}
