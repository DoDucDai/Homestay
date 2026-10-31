package com.homestay.security;

import com.homestay.db.DBConnection;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.*;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.*;
import org.springframework.security.web.authentication.session.*;
import org.springframework.security.web.context.*;
import org.springframework.security.web.csrf.*;
import org.springframework.web.cors.*;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.sql.*;
import java.util.*;

@Configuration
@DependsOn("databaseSettings")
public class SecurityConfig {
    @Bean public PasswordEncoder passwordEncoder() { return PasswordHashing.encoder(); }
    @Bean public SecurityContextRepository contextRepository() { return new HttpSessionSecurityContextRepository(); }
    @Bean public HttpSessionCsrfTokenRepository csrfRepository() { return new HttpSessionCsrfTokenRepository(); }
    @Bean public SessionAuthenticationStrategy sessionStrategy(HttpSessionCsrfTokenRepository csrf) {
        return new CompositeSessionAuthenticationStrategy(List.of(new ChangeSessionIdAuthenticationStrategy(),new CsrfAuthenticationStrategy(csrf)));
    }
    @Bean public AuthenticationManager authenticationManager(UserAccounts accounts,PasswordEncoder encoder) {
        DaoAuthenticationProvider provider=new DaoAuthenticationProvider();
        provider.setUserDetailsService(accounts); provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }
    public ApplicationRunner checkAccountMigration() {return checkAccountMigration(new org.springframework.core.env.StandardEnvironment());}
    @Bean public ApplicationRunner checkAccountMigration(org.springframework.core.env.Environment environment) {
        return args -> {
            try(Connection conn=DBConnection.getConnection();PreparedStatement stmt=conn.prepareStatement(
                    "SELECT password,password_scheme FROM Users");ResultSet rs=stmt.executeQuery()) {
                while(rs.next()) {
                    if (!PasswordHashing.SCHEME.equals(rs.getString("password_scheme")) || !PasswordHashing.validHash(rs.getString("password"))) {
                        throw new IllegalStateException("Account password migration required. Stop writes and run scripts/migrate-passwords.ps1 after SQL migration 004.");
                    }
                    if(environment.acceptsProfiles(org.springframework.core.env.Profiles.of("prod")) && PasswordHashing.encoder().matches("123456",rs.getString("password"))) {
                        throw new IllegalStateException("Production contains demo credentials. Change demo passwords in a private dev setup before enabling prod.");
                    }
                }
            }
        };
    }
    @Bean public CorsConfigurationSource corsConfiguration(@Value("${auth.allowed-origins:}") String origins) {
        CorsConfiguration config=new CorsConfiguration();
        List<String> allowed=Arrays.stream(origins.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        if (allowed.stream().anyMatch(s -> s.contains("*"))) throw new IllegalArgumentException("AUTH_ALLOWED_ORIGINS must contain explicit origins");
        config.setAllowedOrigins(allowed); config.setAllowCredentials(true);
        config.setAllowedMethods(List.of("GET","POST","PUT","DELETE","OPTIONS"));
        config.setAllowedHeaders(List.of("Content-Type","X-CSRF-TOKEN")); config.setMaxAge(600L);
        UrlBasedCorsConfigurationSource source=new UrlBasedCorsConfigurationSource();source.registerCorsConfiguration("/**",config);return source;
    }
    @Bean public SecurityFilterChain security(HttpSecurity http,SecurityContextRepository repository,
            HttpSessionCsrfTokenRepository csrf,UserAccounts accounts,
            @org.springframework.beans.factory.annotation.Qualifier("corsConfiguration") CorsConfigurationSource cors) throws Exception {
        http.formLogin(AbstractHttpConfigurer::disable).httpBasic(AbstractHttpConfigurer::disable).logout(AbstractHttpConfigurer::disable)
            .cors(c -> c.configurationSource(cors))
            .csrf(c -> c.csrfTokenRepository(csrf))
            .securityContext(c -> c.securityContextRepository(repository).requireExplicitSave(true))
            .requestCache(c -> c.disable())
            .exceptionHandling(c -> c.authenticationEntryPoint((request,response,error) -> {
                if (request.getServletPath().startsWith("/api/")) SecurityJson.error(response,401,"UNAUTHENTICATED","Vui lòng đăng nhập lại!");
                else response.sendRedirect(request.getContextPath()+"/pages/login.html");
            }).accessDeniedHandler((request,response,error) -> {
                boolean authenticated=SecurityContextHolder.getContext().getAuthentication()!=null
                        && !(SecurityContextHolder.getContext().getAuthentication() instanceof AnonymousAuthenticationToken);
                boolean csrfError=error instanceof CsrfException;
                boolean publicLogin="/api/auth/login".equals(request.getServletPath());
                if (!authenticated && !publicLogin) SecurityJson.error(response,401,"UNAUTHENTICATED","Vui lòng đăng nhập lại!");
                else SecurityJson.error(response,403,csrfError?"CSRF_INVALID":"FORBIDDEN",
                        csrfError?"Phiên xác nhận không hợp lệ. Vui lòng tải lại trang!":"Bạn không có quyền thực hiện thao tác này!");
            }))
            .authorizeHttpRequests(c -> c
                .requestMatchers("/","/pages/login.html","/css/**","/js/**","/favicon.ico","/error").permitAll()
                .requestMatchers(HttpMethod.GET,"/api/auth/csrf").permitAll()
                .requestMatchers(HttpMethod.POST,"/api/auth/login").permitAll()
                .requestMatchers("/api/auth/me","/api/auth/logout","/api/auth/change-password").hasAnyRole("ADMIN","STAFF")
                .requestMatchers("/api/statistics/**","/pages/statistics.html","/pages/rooms.html").hasRole("ADMIN")
                .requestMatchers(request -> "/pages/customers.html".equals(request.getServletPath())
                        && "admin".equals(request.getParameter("workspace"))).hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST,"/api/rooms","/api/services").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT,"/api/rooms/**","/api/services/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE,"/api/rooms/**","/api/services/**","/api/customers/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET,"/api/rooms","/api/rooms/**","/api/services","/api/services/**","/api/customers","/api/customers/**",
                    "/api/bookings","/api/bookings/**","/api/checkout/preview/**","/api/payments","/api/payments/**").hasAnyRole("ADMIN","STAFF")
                .requestMatchers(HttpMethod.POST,"/api/customers","/api/bookings","/api/bookings/*/services","/api/checkout/*").hasAnyRole("ADMIN","STAFF")
                .requestMatchers(HttpMethod.PUT,"/api/customers/*","/api/bookings/*/cancel","/api/checkin/*").hasAnyRole("ADMIN","STAFF")
                .requestMatchers(HttpMethod.DELETE,"/api/bookings/services/*").hasAnyRole("ADMIN","STAFF")
                .requestMatchers("/pages/**").hasAnyRole("ADMIN","STAFF").anyRequest().denyAll());
        http.addFilterAfter(new OncePerRequestFilter() {
            @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
                    throws ServletException,IOException {
                var auth=SecurityContextHolder.getContext().getAuthentication();
                if (auth!=null && auth.getPrincipal() instanceof AppUser user) {
                    try {
                        if (!accounts.sessionCurrent(user)) {
                            if (request.getSession(false)!=null) request.getSession(false).invalidate();
                            SecurityContextHolder.clearContext();
                            SecurityJson.error(response,401,"UNAUTHENTICATED","Thông tin đăng nhập đã thay đổi. Vui lòng đăng nhập lại!");return;
                        }
                    } catch(SQLException e) {
                        SecurityJson.error(response,503,"AUTH_UNAVAILABLE","Không thể kiểm tra phiên đăng nhập. Vui lòng thử lại!");return;
                    }
                }
                chain.doFilter(request,response);
            }
        },SecurityContextHolderFilter.class);
        return http.build();
    }
}
