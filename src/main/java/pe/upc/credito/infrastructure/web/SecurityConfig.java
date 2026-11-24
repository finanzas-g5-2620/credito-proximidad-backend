package pe.upc.credito.infrastructure.web;

import tools.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import pe.upc.credito.infrastructure.security.ApiAccessDeniedHandler;
import pe.upc.credito.infrastructure.security.ApiAuthenticationEntryPoint;
import pe.upc.credito.infrastructure.security.JwtAuthenticationFilter;
import pe.upc.credito.infrastructure.security.TokenSessionService;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           TokenSessionService sessions,
                                           ObjectMapper objectMapper) throws Exception {
        var jwtFilter = new JwtAuthenticationFilter(sessions, objectMapper);
        var entryPoint = new ApiAuthenticationEntryPoint(objectMapper);
        var deniedHandler = new ApiAccessDeniedHandler(objectMapper);

        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(deniedHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/auth/login").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**",
                                "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/auth/me").authenticated()
                        .requestMatchers("/stores", "/stores/**").hasRole("SUPER_ADMIN")
                        .requestMatchers(HttpMethod.POST, "/customers").hasRole("STORE_ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/customers/**").hasRole("STORE_ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/customers/**").hasRole("STORE_ADMIN")
                        .requestMatchers(HttpMethod.GET, "/customers", "/customers/**")
                                .hasAnyRole("STORE_ADMIN", "CUSTOMER")
                        .requestMatchers("/sales/customers").hasRole("STORE_ADMIN")
                        .requestMatchers("/products", "/products/**", "/catalog/products")
                                .hasRole("STORE_ADMIN")
                        .requestMatchers(HttpMethod.GET, "/sales/**")
                                .hasAnyRole("STORE_ADMIN", "CUSTOMER")
                        .requestMatchers(HttpMethod.POST, "/sales/**").hasRole("STORE_ADMIN")
                        .requestMatchers(HttpMethod.GET, "/settlements/**")
                                .hasAnyRole("STORE_ADMIN", "CUSTOMER")
                        .requestMatchers(HttpMethod.POST, "/settlements/**").hasRole("STORE_ADMIN")
                        .requestMatchers(HttpMethod.GET, "/finance/**")
                                .hasAnyRole("STORE_ADMIN", "CUSTOMER")
                        .requestMatchers(HttpMethod.POST, "/finance/**")
                                .hasAnyRole("STORE_ADMIN", "CUSTOMER")
                        .requestMatchers("/analytics/**", "/__test/**")
                                .hasRole("STORE_ADMIN")
                        .anyRequest().authenticated())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of(
                "http://localhost:*",
                "http://127.0.0.1:*"));
        configuration.setAllowedMethods(List.of(
                "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "Idempotency-Key"));
        configuration.setExposedHeaders(List.of("Location"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
