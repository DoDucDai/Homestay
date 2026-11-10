package com.homestay.security;

import com.homestay.db.DBConnection;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.*;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
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
        DaoAuthenticationProvider provider=new DaoAuthenticationProvider(accounts);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }
    // NOTE BACKEND: Kiểm tra tài khoản đã dùng chuỗi băm được hỗ trợ; khi prod, từ chối mật khẩu demo trước khi cho hệ thống hoạt động.
    public ApplicationRunner checkAccountMigration() {return checkAccountMigration(new org.springframework.core.env.StandardEnvironment());}
    // NOTE BACKEND: Kiểm tra tài khoản đã dùng chuỗi băm được hỗ trợ; khi prod, từ chối mật khẩu demo trước khi cho hệ thống hoạt động.
    @Bean public ApplicationRunner checkAccountMigration(org.springframework.core.env.Environment environment) {
        return _ -> {
            AccountSettings settings=new AccountSettings(environment);
            boolean demoEnabled="true".equalsIgnoreCase(settings.value("DEMO_ACCOUNTS_ENABLED","false"));
            String demoPassword=demoEnabled?settings.value("DEMO_ACCOUNT_PASSWORD",""):"";
            if(demoEnabled && !"123456".equals(demoPassword) && !AccountPolicy.password(demoPassword)) {
                throw new IllegalStateException("DEMO_ACCOUNT_PASSWORD must be configured before enabling demo accounts.");
            }
            try(Connection conn=DBConnection.getConnection()) {
                boolean autoCommit=conn.getAutoCommit();conn.setAutoCommit(false);
                try {
                    if(demoEnabled)createDemoAccounts(conn,demoPassword);
                    try(PreparedStatement stmt=conn.prepareStatement("SELECT username,role,password,password_scheme FROM Users");ResultSet rs=stmt.executeQuery()) {
                        while(rs.next()) {
                            String hash=rs.getString("password");
                            if (!PasswordHashing.SCHEME.equals(rs.getString("password_scheme")) || !PasswordHashing.validHash(hash)) {
                                throw new IllegalStateException("Account password migration required. Stop writes and run scripts/migrate-passwords.ps1 after SQL migration 004.");
                            }
                            boolean allowedDemo=demoEnabled && demoRole(rs.getString("username")).equals(rs.getString("role"))
                                && PasswordHashing.encoder().matches(demoPassword,hash);
                            if(environment.acceptsProfiles(org.springframework.core.env.Profiles.of("prod"))
                                    && PasswordHashing.encoder().matches("123456",hash) && !allowedDemo) {
                                throw new IllegalStateException("Production contains unapproved demo credentials. Only explicitly enabled named demo accounts may use the demo password.");
                            }
                        }
                    }
                    conn.commit();
                } catch(Exception error) { conn.rollback();throw error; }
                finally {conn.setAutoCommit(autoCommit);}
            }
        };
    }
    private static String demoRole(String username) {
        if("admindemo".equalsIgnoreCase(username))return "admin";
        if("khachdemo".equalsIgnoreCase(username))return "customer";
        return "";
    }
    // Explicitly opted-in demo accounts only. Never reset, elevate or relink an existing account.
    private static void createDemoAccounts(Connection connection,String password) throws SQLException {
        for(String username:List.of("admindemo","khachdemo")) {
            try(var query=connection.prepareStatement("SELECT role FROM Users WITH(UPDLOCK,HOLDLOCK) WHERE username=?")) {
                query.setString(1,username);
                try(var rows=query.executeQuery()) {
                    if(rows.next()) {
                        if(!demoRole(username).equalsIgnoreCase(rows.getString("role")))
                            throw new SQLException("Demo account name already belongs to a different role; no account was changed.");
                        continue;
                    }
                }
            }
            try(var insert=connection.prepareStatement("INSERT Users(username,password,password_scheme,full_name,role) VALUES(?,?,?,?,?)")) {
                insert.setString(1,username);insert.setString(2,PasswordHashing.encoder().encode(password));
                insert.setString(3,PasswordHashing.SCHEME);insert.setString(4,username.equals("admindemo")?"Quản trị demo":"Khách demo");
                insert.setString(5,demoRole(username));insert.executeUpdate();
            }
        }
    }
    // NOTE BACKEND: Chỉ chấp nhận danh sách origin cụ thể khi dùng cookie xuyên origin; không cho ký tự đại diện *.
    @Bean public CorsConfigurationSource corsConfiguration(@Value("${auth.allowed-origins:}") String origins) {
        CorsConfiguration config=new CorsConfiguration();
        List<String> allowed=Arrays.stream(origins.split(",")).map(s -> s.trim()).filter(s -> !s.isEmpty()).toList();
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
        http.formLogin(c -> c.disable()).httpBasic(c -> c.disable()).logout(c -> c.disable())
            .cors(c -> c.configurationSource(cors))
            .csrf(c -> c.csrfTokenRepository(csrf))
            .securityContext(c -> c.securityContextRepository(repository).requireExplicitSave(true))
            .requestCache(c -> c.disable())
            .exceptionHandling(c -> c.authenticationEntryPoint((request,response,_) -> {
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
            // API vận hành chỉ dành cho admin. Khách dùng API riêng có kiểm tra quyền sở hữu ở từng truy vấn.
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
                .requestMatchers("/api/customer/**").hasRole("CUSTOMER")
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/pages/reserve.html","/pages/my-bookings.html","/pages/my-invoices.html").hasRole("CUSTOMER")
                .requestMatchers("/pages/account.html","/pages/services.html").hasAnyRole("ADMIN","CUSTOMER")
                .requestMatchers("/api/rooms","/api/rooms/**","/api/customers","/api/customers/**",
                    "/api/services","/api/services/**","/api/bookings","/api/bookings/**","/api/checkin/**",
                    "/api/checkout/**","/api/payments","/api/payments/**","/api/statistics/**").hasRole("ADMIN")
                .requestMatchers("/pages/customers.html","/pages/bookings.html","/pages/checkin.html","/pages/checkout.html",
                    "/pages/statistics.html","/pages/rooms.html","/pages/rag-documents.html","/pages/payment-review.html").hasRole("ADMIN")
                .requestMatchers("/pages/**").hasAnyRole("ADMIN","CUSTOMER").anyRequest().denyAll());
        // NOTE BACKEND: Mỗi yêu cầu đã đăng nhập được kiểm tra lại phiên bản/quyền trong database.
        // Nếu đã đổi quyền hoặc mật khẩu thì hủy session; lỗi truy cập database trả 503 thay vì cho qua.
        http.addFilterAfter(new OncePerRequestFilter() {
            @Override protected void doFilterInternal(@NonNull HttpServletRequest request,@NonNull HttpServletResponse response,@NonNull FilterChain chain)
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
