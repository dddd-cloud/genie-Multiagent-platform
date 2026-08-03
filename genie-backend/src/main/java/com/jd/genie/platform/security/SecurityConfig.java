package com.jd.genie.platform.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.platform.user.dto.AuthUserResponse;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    InternalAgentAuthFilter internalAgentAuthFilter(SecurityProperties securityProperties, ObjectMapper objectMapper) {
        return new InternalAgentAuthFilter(securityProperties, objectMapper);
    }

    /** The filter belongs solely to Spring Security's chain, not the servlet container chain. */
    @Bean
    FilterRegistrationBean<InternalAgentAuthFilter> internalAgentAuthFilterRegistration(InternalAgentAuthFilter filter) {
        FilterRegistrationBean<InternalAgentAuthFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    CookieCsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieName("XSRF-TOKEN");
        repository.setHeaderName("X-XSRF-TOKEN");
        repository.setCookieCustomizer(cookie -> cookie.path("/").sameSite("Lax"));
        return repository;
    }

    @Bean
    DaoAuthenticationProvider daoAuthenticationProvider(CurrentUserDetailsService detailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(detailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AuthenticationConfiguration authenticationConfiguration,
                                            ObjectMapper objectMapper, SecurityContextRepository securityContextRepository,
                                            CookieCsrfTokenRepository csrfTokenRepository,
                                            InternalAgentAuthFilter internalAgentAuthFilter) throws Exception {
        AuthenticationManager authenticationManager = authenticationConfiguration.getAuthenticationManager();
        JsonUsernamePasswordAuthenticationFilter loginFilter = new JsonUsernamePasswordAuthenticationFilter(authenticationManager, objectMapper);
        loginFilter.setSecurityContextRepository(securityContextRepository);
        loginFilter.setSessionAuthenticationStrategy(new ChangeSessionIdAuthenticationStrategy());
        loginFilter.setAuthenticationSuccessHandler(new JsonAuthenticationSuccessHandler(objectMapper, securityContextRepository));
        loginFilter.setAuthenticationFailureHandler(new JsonAuthenticationFailureHandler(objectMapper));

        http
            .securityContext(context -> context.securityContextRepository(securityContextRepository).requireExplicitSave(true))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                .sessionFixation(fixation -> fixation.changeSessionId()))
            .csrf(csrf -> csrf.csrfTokenRepository(csrfTokenRepository)
                .ignoringRequestMatchers("/AutoAgent"))
            .exceptionHandling(errors -> errors.authenticationEntryPoint(new JsonAuthenticationEntryPoint(objectMapper))
                .accessDeniedHandler(new JsonAccessDeniedHandler(objectMapper)))
            .authorizeHttpRequests(auth -> auth
                // Initial requests are fully authenticated below. Servlet async/error redispatches
                // must not be re-authorized after an SSE response has already been committed.
                .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                .requestMatchers("/api/v1/auth/csrf", "/api/v1/auth/login", "/web/health").permitAll()
                .requestMatchers("/AutoAgent").hasAuthority(InternalAgentAuthenticationToken.AUTHORITY)
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .requestMatchers(
                    "/api/v1/auth/logout",
                    "/api/v1/users/me",
                    "/api/v1/conversations/**",
                    "/data/**",
                    "/web/api/v1/gpt/**",
                    "/api/v2/**",
                    "/web/api/v2/gpt/**"
                ).authenticated()
                .anyRequest().authenticated())
            .logout(logout -> logout.logoutUrl("/api/v1/auth/logout")
                .logoutSuccessHandler((request, response, authentication) -> JsonApiWriter.write(objectMapper, response, 200, "OK", "success", null)))
            .addFilterAt(loginFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(internalAgentAuthFilter, AuthorizationFilter.class);
        return http.build();
    }
}
