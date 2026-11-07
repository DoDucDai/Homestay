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
// NOTE BACKEND: Cấu hình đăng nhập bằng cookie, CSRF, CORS và phân quyền admin/customer trước khi yêu cầu đi vào controller.
public class SecurityConfig {
    // NOTE BACKEND: Đăng ký bộ băm mật khẩu chung thành bean để các thành phần Spring dùng cùng thuật toán.
    @Bean public PasswordEncoder passwordEncoder() { return PasswordHashing.encoder(); }
    // NOTE BACKEND: Lưu ngữ cảnh người dùng đã xác thực vào HTTP session phía server.
    @Bean public SecurityContextRepository contextRepository() { return new HttpSessionSecurityContextRepository(); }
    // NOTE BACKEND: Gắn mã CSRF với session; các yêu cầu ghi phải gửi lại mã hợp lệ.
    @Bean public HttpSessionCsrfTokenRepository csrfRepository() { return new HttpSessionCsrfTokenRepository(); }
    // NOTE BACKEND: Đổi session ID và làm mới CSRF khi xác thực thành công để phiên trước đăng nhập không được giữ nguyên.
    @Bean public SessionAuthenticationStrategy sessionStrategy(HttpSessionCsrfTokenRepository csrf) {
        return new CompositeSessionAuthenticationStrategy(List.of(new ChangeSessionIdAuthenticationStrategy(),new CsrfAuthenticationStrategy(csrf)));
    }
    // NOTE BACKEND: Ghép nguồn tài khoản UserAccounts với PasswordEncoder để Spring kiểm tra tên đăng nhập và mật khẩu.
    @Bean public AuthenticationManager authenticationManager(UserAccounts accounts,PasswordEncoder encoder) {
        DaoAuthenticationProvider provider=new DaoAuthenticationProvider();
        provider.setUserDetailsService(accounts); provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }
    // NOTE BACKEND: Kiểm tra tài khoản đã dùng chuỗi băm được hỗ trợ; khi prod, từ chối mật khẩu demo trước khi cho hệ thống hoạt động.
    public ApplicationRunner checkAccountMigration() {return checkAccountMigration(new org.springframework.core.env.StandardEnvironment());}
    // NOTE BACKEND: Kiểm tra tài khoản đã dùng chuỗi băm được hỗ trợ; khi prod, từ chối mật khẩu demo trước khi cho hệ thống hoạt động.
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
    // NOTE BACKEND: Chỉ chấp nhận danh sách origin cụ thể khi dùng cookie xuyên origin; không cho ký tự đại diện *.
    @Bean public CorsConfigurationSource corsConfiguration(@Value("${auth.allowed-origins:}") String origins) {
        CorsConfiguration config=new CorsConfiguration();
        List<String> allowed=Arrays.stream(origins.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        if (allowed.stream().anyMatch(s -> s.contains("*"))) throw new IllegalArgumentException("AUTH_ALLOWED_ORIGINS must contain explicit origins");
        config.setAllowedOrigins(allowed); config.setAllowCredentials(true);
        config.setAllowedMethods(List.of("GET","POST","PUT","DELETE","OPTIONS"));
        config.setAllowedHeaders(List.of("Content-Type","X-CSRF-TOKEN")); config.setMaxAge(600L);
        UrlBasedCorsConfigurationSource source=new UrlBasedCorsConfigurationSource();source.registerCorsConfiguration("/**",config);return source;
    }
    // NOTE BACKEND: Lắp chuỗi bộ lọc và quy tắc đường dẫn: trang công khai, API đăng nhập, API chung và thao tác chỉ dành cho admin.
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
                else {
                    String target=request.getRequestURI()+(request.getQueryString()==null?"":"?"+request.getQueryString());
                    response.sendRedirect(request.getContextPath()+"/pages/login.html?next="+
                        java.net.URLEncoder.encode(target,java.nio.charset.StandardCharsets.UTF_8));
                }
            }).accessDeniedHandler((request,response,error) -> {
                boolean authenticated=SecurityContextHolder.getContext().getAuthentication()!=null
                        && !(SecurityContextHolder.getContext().getAuthentication() instanceof AnonymousAuthenticationToken);
                boolean csrfError=error instanceof CsrfException;
                boolean publicLogin=java.util.Set.of("/api/auth/login","/api/auth/register","/api/auth/forgot-password","/api/auth/reset-password").contains(request.getServletPath());
                if (!authenticated && !publicLogin) SecurityJson.error(response,401,"UNAUTHENTICATED","Vui lòng đăng nhập lại!");
                else SecurityJson.error(response,403,csrfError?"CSRF_INVALID":"FORBIDDEN",
                        csrfError?"Phiên xác nhận không hợp lệ. Vui lòng tải lại trang!":"Bạn không có quyền thực hiện thao tác này!");
            }))
            // NOTE BACKEND: Quy tắc được xét theo thứ tự từ trên xuống; đường dẫn cụ thể đặt trước mẫu rộng /pages/**.
            // Các đường dẫn member cũ bị chặn; thống kê/quản lý phòng chỉ cho admin; thao tác lưu trú dùng admin/customer.
            .authorizeHttpRequests(c -> c
                .requestMatchers("/api/me/**","/pages/member-bookings.html").denyAll()
                .requestMatchers(HttpMethod.GET,"/api/health").permitAll()
                .requestMatchers("/","/pages/landing.html","/pages/explore.html","/pages/login.html","/pages/policies.html","/pages/support.html","/pages/assistant.html","/css/**","/js/**","/ai/**","/favicon.ico","/error").permitAll()
                .requestMatchers("/api/assistant/documents","/api/assistant/support/reply").hasRole("ADMIN")
                .requestMatchers("/api/assistant/**").hasAnyRole("ADMIN","CUSTOMER")
                .requestMatchers(HttpMethod.GET,"/api/public/rooms","/api/public/rooms/*","/api/public/properties").permitAll()
                .requestMatchers(HttpMethod.GET,"/api/auth/csrf").permitAll()
                .requestMatchers(HttpMethod.POST,"/api/auth/login","/api/auth/register","/api/auth/forgot-password","/api/auth/reset-password").permitAll()
                .requestMatchers("/api/auth/profile","/api/auth/me","/api/auth/logout","/api/auth/change-password","/api/auth/email-verification/**").hasAnyRole("ADMIN","CUSTOMER")
                .requestMatchers("/pages/account.html","/pages/services.html").hasAnyRole("ADMIN","CUSTOMER")
                .requestMatchers(HttpMethod.GET,"/api/services","/api/services/*").hasAnyRole("ADMIN","CUSTOMER")
                .requestMatchers(HttpMethod.GET,"/api/bookings/available-rooms").hasAnyRole("ADMIN","CUSTOMER")
                .requestMatchers("/api/statistics/**","/pages/statistics.html","/pages/rooms.html").hasRole("ADMIN")
                .requestMatchers(request -> "/pages/customers.html".equals(request.getServletPath())
                        && "admin".equals(request.getParameter("workspace"))).hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST,"/api/services").hasAnyRole("ADMIN","CUSTOMER")
                .requestMatchers(HttpMethod.PUT,"/api/services/*").hasAnyRole("ADMIN","CUSTOMER")
                .requestMatchers(HttpMethod.DELETE,"/api/services/*").hasAnyRole("ADMIN","CUSTOMER")
                .requestMatchers(HttpMethod.POST,"/api/rooms").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT,"/api/rooms/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE,"/api/rooms/**","/api/customers/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET,"/api/rooms","/api/rooms/**","/api/services","/api/services/**","/api/customers","/api/customers/**",
                    "/api/bookings","/api/bookings/**","/api/checkout/preview/**","/api/payments","/api/payments/**").hasAnyRole("ADMIN","CUSTOMER")
                .requestMatchers(HttpMethod.POST,"/api/customers","/api/bookings","/api/bookings/*/services","/api/checkout/*").hasAnyRole("ADMIN","CUSTOMER")
                .requestMatchers(HttpMethod.PUT,"/api/customers/*","/api/bookings/*/cancel","/api/checkin/*").hasAnyRole("ADMIN","CUSTOMER")
                .requestMatchers(HttpMethod.DELETE,"/api/bookings/services/*").hasAnyRole("ADMIN","CUSTOMER")
                .requestMatchers("/pages/**").hasAnyRole("ADMIN","CUSTOMER").anyRequest().denyAll());
        // NOTE BACKEND: Mỗi yêu cầu đã đăng nhập được kiểm tra lại phiên bản/quyền trong database.
        // Nếu đã đổi quyền hoặc mật khẩu thì hủy session; lỗi truy cập database trả 503 thay vì cho qua.
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
