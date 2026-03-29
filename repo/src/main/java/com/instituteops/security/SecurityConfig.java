package com.instituteops.security;

import com.instituteops.audit.RequestAuditFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
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
    public SecurityFilterChain securityFilterChain(HttpSecurity http, DaoAuthenticationProvider daoAuthenticationProvider) throws Exception {
        http
            .authenticationProvider(daoAuthenticationProvider)
            .httpBasic(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/login", "/css/**", "/error").permitAll()
                .requestMatchers("/api/internal/**").hasAuthority("API_INTERNAL")
                .requestMatchers("/admin/**").hasRole(RoleCode.SYSTEM_ADMIN.name())
                .requestMatchers("/registrar/**").hasAnyRole(RoleCode.SYSTEM_ADMIN.name(), RoleCode.REGISTRAR_FINANCE_CLERK.name())
                .requestMatchers("/instructor/**").hasAnyRole(RoleCode.SYSTEM_ADMIN.name(), RoleCode.INSTRUCTOR.name())
                .requestMatchers("/inventory/**").hasAnyRole(RoleCode.SYSTEM_ADMIN.name(), RoleCode.INVENTORY_MANAGER.name())
                .requestMatchers("/procurement/**").hasAnyRole(RoleCode.SYSTEM_ADMIN.name(), RoleCode.PROCUREMENT_APPROVER.name())
                .requestMatchers("/store/student/**", "/store/student").hasAnyRole(
                    RoleCode.SYSTEM_ADMIN.name(),
                    RoleCode.STORE_MANAGER.name(),
                    RoleCode.STUDENT.name()
                )
                .requestMatchers("/store/**").hasAnyRole(RoleCode.SYSTEM_ADMIN.name(), RoleCode.STORE_MANAGER.name())
                .requestMatchers("/api/inventory/**").hasAnyRole(RoleCode.SYSTEM_ADMIN.name(), RoleCode.INVENTORY_MANAGER.name())
                .requestMatchers("/api/procurement/**").hasAnyRole(RoleCode.SYSTEM_ADMIN.name(), RoleCode.PROCUREMENT_APPROVER.name())
                .requestMatchers("/api/store/**").hasAnyRole(
                    RoleCode.SYSTEM_ADMIN.name(),
                    RoleCode.STORE_MANAGER.name(),
                    RoleCode.STUDENT.name()
                )
                .requestMatchers("/admin/recommender/**").hasRole(RoleCode.SYSTEM_ADMIN.name())
                .requestMatchers("/api/recommender/train/**", "/api/recommender/incremental/**", "/api/recommender/rollback/**").hasRole(
                    RoleCode.SYSTEM_ADMIN.name()
                )
                .requestMatchers("/api/recommender/**").hasAnyRole(
                    RoleCode.SYSTEM_ADMIN.name(),
                    RoleCode.STUDENT.name(),
                    RoleCode.STORE_MANAGER.name(),
                    RoleCode.INSTRUCTOR.name(),
                    RoleCode.REGISTRAR_FINANCE_CLERK.name()
                )
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
            .addFilterBefore(internalApiClientAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(requestAuditFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
