package com.instituteops.security;

import com.instituteops.audit.RequestAuditFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final InstituteUserDetailsService userDetailsService;
    private final LoginSuccessHandler loginSuccessHandler;
    private final InternalApiClientAuthFilter internalApiClientAuthFilter;
    private final RequestAuditFilter requestAuditFilter;

    public SecurityConfig(
        InstituteUserDetailsService userDetailsService,
        LoginSuccessHandler loginSuccessHandler,
        InternalApiClientAuthFilter internalApiClientAuthFilter,
        RequestAuditFilter requestAuditFilter
    ) {
        this.userDetailsService = userDetailsService;
        this.loginSuccessHandler = loginSuccessHandler;
        this.internalApiClientAuthFilter = internalApiClientAuthFilter;
        this.requestAuditFilter = requestAuditFilter;
    }

    @Bean
    public DaoAuthenticationProvider daoAuthenticationProvider(org.springframework.security.crypto.password.PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    @Order(1)
    public SecurityFilterChain internalApiFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/api/internal/**")
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth.anyRequest().hasAuthority("API_INTERNAL"))
            .addFilterBefore(internalApiClientAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(requestAuditFilter, InternalApiClientAuthFilter.class)
            .httpBasic(Customizer.withDefaults())
            .formLogin(form -> form.disable());
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain webFilterChain(HttpSecurity http, DaoAuthenticationProvider daoAuthenticationProvider) throws Exception {
        http
            .authenticationProvider(daoAuthenticationProvider)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/css/**", "/error").permitAll()
                .requestMatchers("/admin/**").hasRole(RoleCode.SYSTEM_ADMIN.name())
                .requestMatchers("/registrar/**").hasAnyRole(RoleCode.SYSTEM_ADMIN.name(), RoleCode.REGISTRAR_FINANCE_CLERK.name())
                .requestMatchers("/instructor/**").hasAnyRole(RoleCode.SYSTEM_ADMIN.name(), RoleCode.INSTRUCTOR.name())
                .requestMatchers("/inventory/**").hasAnyRole(RoleCode.SYSTEM_ADMIN.name(), RoleCode.INVENTORY_MANAGER.name())
                .requestMatchers("/procurement/**").hasAnyRole(RoleCode.SYSTEM_ADMIN.name(), RoleCode.PROCUREMENT_APPROVER.name())
                .requestMatchers("/store/**").hasAnyRole(RoleCode.SYSTEM_ADMIN.name(), RoleCode.STORE_MANAGER.name())
                .requestMatchers("/api/inventory/**").hasAnyRole(RoleCode.SYSTEM_ADMIN.name(), RoleCode.INVENTORY_MANAGER.name())
                .requestMatchers("/api/store/**").hasAnyRole(RoleCode.SYSTEM_ADMIN.name(), RoleCode.STORE_MANAGER.name())
                .requestMatchers("/student/**", "/api/students/**", "/api/classes/**").hasAnyRole(
                    RoleCode.SYSTEM_ADMIN.name(),
                    RoleCode.REGISTRAR_FINANCE_CLERK.name(),
                    RoleCode.INSTRUCTOR.name(),
                    RoleCode.STUDENT.name()
                )
                .requestMatchers("/api/grades/**").hasAnyRole(
                    RoleCode.SYSTEM_ADMIN.name(),
                    RoleCode.INSTRUCTOR.name(),
                    RoleCode.REGISTRAR_FINANCE_CLERK.name()
                )
                .requestMatchers("/dashboard").authenticated()
                .anyRequest().denyAll()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .successHandler(loginSuccessHandler)
                .permitAll()
            )
            .logout(logout -> logout.logoutUrl("/logout").logoutSuccessUrl("/login?logout"))
            .addFilterAfter(requestAuditFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
