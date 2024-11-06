package com.waterworks.mlqsdummydatagenerator.infra.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * The `SecurityConfig` class is a Spring configuration class responsible for configuring security
 * settings for a web application. It is annotated with `@Configuration` and `@EnableWebSecurity`,
 * indicating its role in defining security configurations.
 *
 * @author Edgar Thomson
 * @version 1.0
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  /**
   * Configures a security filter chain for the application.
   *
   * @param http The `HttpSecurity` object used for configuring security settings.
   * @return A configured `SecurityFilterChain` for handling security in the application.
   */
  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .authorizeHttpRequests(auth ->
            auth.requestMatchers("/actuator/**")
                .permitAll()
                .requestMatchers("/api/v1/**")
                .authenticated())
        .httpBasic(Customizer.withDefaults());
    return http.build();
  }
}
