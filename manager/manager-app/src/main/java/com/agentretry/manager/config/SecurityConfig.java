/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.config;

import com.agentretry.manager.security.AgentApiKeyFilter;
import com.agentretry.manager.security.IngestRateLimitFilter;
import com.agentretry.manager.security.JwtAuthFilter;
import com.agentretry.manager.sso.DatabaseClientRegistrationRepository;
import com.agentretry.manager.sso.DatabaseRelyingPartyRegistrationRepository;
import com.agentretry.manager.sso.SsoAuthenticationSuccessHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.RequestMatcher;

import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** graphql-ws upgrades with GET; auth runs in connection_init, not the HTTP handshake. */
    static final RequestMatcher GRAPHQL_WEBSOCKET_UPGRADE = request ->
            "/graphql".equals(request.getRequestURI())
                    && "GET".equalsIgnoreCase(request.getMethod())
                    && "websocket".equalsIgnoreCase(request.getHeader("Upgrade"));

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AgentApiKeyFilter agentApiKeyFilter,
            IngestRateLimitFilter ingestRateLimitFilter,
            JwtAuthFilter jwtAuthFilter,
            DatabaseClientRegistrationRepository clientRegistrationRepository,
            DatabaseRelyingPartyRegistrationRepository relyingPartyRegistrationRepository,
            SsoAuthenticationSuccessHandler ssoAuthenticationSuccessHandler,
            ManagerSecurityProperties securityProperties
    ) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers("/api/v1/auth/**").permitAll();
                    auth.requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll();
                    auth.requestMatchers("/saml2/**", "/login/saml2/**").permitAll();
                    auth.requestMatchers(GRAPHQL_WEBSOCKET_UPGRADE).permitAll();
                    if (securityProperties.isGraphiqlEnabled()) {
                        auth.requestMatchers("/graphiql", "/vendor/**").permitAll();
                    }
                    if (!securityProperties.isRequireGraphqlAuth()) {
                        auth.requestMatchers(HttpMethod.GET, "/graphql").permitAll();
                    }
                    auth.requestMatchers("/api/v1/ingest/**").hasRole("AGENT");
                    auth.requestMatchers("/api/v1/otel/**").hasRole("AGENT");
                    auth.requestMatchers("/api/v1/admin/**").hasRole("SECADMIN");
                    auth.requestMatchers("/api/v1/contracts/**").hasAnyRole("ADMIN", "SECADMIN");
                    auth.requestMatchers("/api/v1/reports/**").hasAnyRole("UI", "ADMIN", "SECADMIN");
                    auth.requestMatchers("/api/v1/chat/**").hasAnyRole("UI", "ADMIN", "SECADMIN");
                    auth.requestMatchers(HttpMethod.GET, "/api/v1/tasks/*/replay")
                            .hasAnyRole("UI", "ADMIN", "SECADMIN");
                    auth.requestMatchers("/graphql").hasAnyRole("UI", "ADMIN", "SECADMIN");
                    auth.anyRequest().authenticated();
                })
                .oauth2Login(oauth2 -> oauth2
                        .clientRegistrationRepository(clientRegistrationRepository)
                        .successHandler(ssoAuthenticationSuccessHandler)
                )
                .saml2Login(saml2 -> saml2
                        .relyingPartyRegistrationRepository(relyingPartyRegistrationRepository)
                        .successHandler(ssoAuthenticationSuccessHandler)
                )
                .addFilterBefore(ingestRateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(agentApiKeyFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, authException) -> {
                    if (request.getRequestURI().startsWith("/graphql")
                            || request.getRequestURI().startsWith("/api/")) {
                        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
                        return;
                    }
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
                }));

        return http.build();
    }
}
