package com.homestay.security;

import com.homestay.db.DBConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

/** Real SQL Server checks in a disposable database, never the configured database. */
public class SecurityHttpSqlServerIT {

    private static int passed;

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && "--isolated-child".equals(args[0])) {
            String database = System.getenv("DB_NAME");
            if (database == null || !database.matches("HomestaySec01Test_[a-f0-9]{32}")) {
                throw new IllegalStateException("Refusing tests outside a disposable database");
            }
            try (Connection conn = DBConnection.getConnection()) {
                if (!database.equals(conn.getCatalog())) throw new IllegalStateException("Wrong database");
            }
            runChecks();
            return;
        }
        String database = "HomestaySec01Test_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection bootstrap = DBConnection.getConnection(); Statement stmt = bootstrap.createStatement()) {
            boolean created = false;
            try {
                stmt.execute("CREATE DATABASE [" + database + "]");
                created = true;
                stmt.execute("USE [" + database + "]");
                String schema = Files.readString(Path.of("../database/schema.sql"));
                // Keep table definitions, skip CREATE DATABASE / USE of the real database.
                schema = schema.substring(schema.indexOf("CREATE TABLE Users"));
                schema = schema.lines().filter(line -> !line.contains("password_scheme") && !line.contains("auth_version")).reduce("", (a,b) -> a+b+"\n");
                for (String batch : schema.split("(?im)^\\s*GO\\s*$")) {
                    if (!batch.isBlank()) stmt.execute(batch);
                }
                ProcessBuilder child = new ProcessBuilder(
                        Path.of(System.getProperty("java.home"), "bin", "java.exe").toString(),
                        "-cp", System.getProperty("java.class.path"),
                        SecurityHttpSqlServerIT.class.getName(), "--isolated-child");
                child.environment().put("DB_NAME", database);
                child.inheritIO();
                Process process = child.start();
                if (!process.waitFor(300, TimeUnit.SECONDS)) {
                    process.destroyForcibly().waitFor();
                    throw new AssertionError("SQL Server tests timed out");
                }
                if (process.exitValue() != 0) throw new AssertionError("SQL Server tests failed");
            } finally {
                if (created) {
                    stmt.execute("USE [master]");
                    stmt.execute("ALTER DATABASE [" + database + "] SET SINGLE_USER WITH ROLLBACK IMMEDIATE");
                    stmt.execute("DROP DATABASE [" + database + "]");
                    System.out.println("Disposed isolated SEC-01 test database.");
                }
            }
        }
    }

    private static void sql(String sql) throws SQLException {
        try (Connection conn = DBConnection.getConnection(); Statement stmt = conn.createStatement()) {
            boolean resultSet = stmt.execute(sql);
            // Drain every result so a THROW following a diagnostic SELECT is observed.
            while (resultSet || stmt.getUpdateCount() != -1) {
                if (resultSet) {
                    try (ResultSet rs = stmt.getResultSet()) {
                        while (rs.next()) { /* consume diagnostic rows */ }
                    }
                }
                resultSet = stmt.getMoreResults();
            }
        }
    }

    private static String value(String sql) throws SQLException {
        try (Connection conn = DBConnection.getConnection(); Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (!rs.next()) throw new AssertionError("Missing query result");
            return rs.getString(1);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static boolean success(Map<String, Object> response) {
        return Boolean.TRUE.equals(response.get("success"));
    }

    private static void pass(String name) {
        passed++;
        System.out.println("PASS " + name);
    }

    private static List<Map<String, Object>> concurrent(Supplier<Map<String, Object>> a,
                                                       Supplier<Map<String, Object>> b) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Map<String, Object>> one = executor.submit(() -> { start.await(); return a.get(); });
            Future<Map<String, Object>> two = executor.submit(() -> { start.await(); return b.get(); });
            start.countDown();
            return List.of(one.get(20, TimeUnit.SECONDS), two.get(20, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }



    private static final com.fasterxml.jackson.databind.ObjectMapper JSON=new com.fasterxml.jackson.databind.ObjectMapper();
    private static String base;
    private static final String ADMIN_PASSWORD=" admin password with spaces ";
    private static final String STAFF_PASSWORD="Staff Password 123!";
    private static class Client {
        final java.net.CookieManager cookies=new java.net.CookieManager(null,java.net.CookiePolicy.ACCEPT_ALL);
        final java.net.http.HttpClient http=java.net.http.HttpClient.newBuilder().cookieHandler(cookies)
                .followRedirects(java.net.http.HttpClient.Redirect.NEVER).build();
        String token;
        java.net.http.HttpResponse<String> send(String method,String path,Object body,Map<String,String> headers) throws Exception {
            var request=java.net.http.HttpRequest.newBuilder(java.net.URI.create(base+path)).timeout(java.time.Duration.ofSeconds(15));
            headers.forEach(request::header);
            var publisher=body==null ? java.net.http.HttpRequest.BodyPublishers.noBody()
                    : java.net.http.HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body));
            if(body!=null) request.header("Content-Type","application/json");
            return http.send(request.method(method,publisher).build(),java.net.http.HttpResponse.BodyHandlers.ofString());
        }
        java.net.http.HttpResponse<String> get(String path) throws Exception { return send("GET",path,null,Map.of()); }
        java.net.http.HttpResponse<String> write(String method,String path,Object body) throws Exception {
            return send(method,path,body,Map.of("X-CSRF-TOKEN",token));
        }
        java.net.http.HttpResponse<String> csrf() throws Exception {
            var response=get("/api/auth/csrf");status(response,200);
            token=(String)data(response).get("token");require(token!=null,"Missing CSRF token");return response;
        }
        java.net.http.HttpResponse<String> login(String username,String password) throws Exception {
            csrf();var response=write("POST","/api/auth/login",Map.of("username",username,"password",password,"role","admin","userId",1));
            if(response.statusCode()==200) csrf();return response;
        }
        String session() {
            return cookies.getCookieStore().getCookies().stream().filter(c -> "HOMESTAY_SESSION".equals(c.getName()))
                    .map(java.net.HttpCookie::getValue).findFirst().orElse(null);
        }
    }
    @SuppressWarnings("unchecked")
    private static Map<String,Object> data(java.net.http.HttpResponse<String> response) throws Exception {
        return JSON.readValue(response.body(),Map.class);
    }
    private static void status(java.net.http.HttpResponse<String> response,int expected) {
        require(response.statusCode()==expected,"HTTP expected "+expected+" but got "+response.statusCode()+": "+response.body());
    }
    @SuppressWarnings("unchecked")
    private static Map<String,Object> user(java.net.http.HttpResponse<String> response) throws Exception {
        return (Map<String,Object>)data(response).get("user");
    }
    private static void expire(org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext app,Client client) throws Exception {
        var tomcat=((org.springframework.boot.web.embedded.tomcat.TomcatWebServer)app.getWebServer()).getTomcat();
        for(var container:tomcat.getHost().findChildren()) {
            if(container instanceof org.apache.catalina.Context context) {
                var session=context.getManager().findSession(client.session());
                if(session!=null) { session.expire();return; }
            }
        }
        throw new AssertionError("Cannot locate test session");
    }
    private static void runChecks() throws Exception {
        try(var conn=DBConnection.getConnection();var stmt=conn.prepareStatement(
                "INSERT INTO Users(username,password,full_name,role) VALUES(?,?,N'Admin test','admin'),(?,?,N'Staff test','staff')")) {
            stmt.setString(1,"admin");stmt.setString(2,ADMIN_PASSWORD);stmt.setString(3,"staff");stmt.setString(4,STAFF_PASSWORD);stmt.executeUpdate();
        }
        String migration=Files.readString(Path.of("../database/migrations/004_account_password_metadata.sql"));
        sql(migration);sql(migration);
        require("legacy".equals(value("SELECT password_scheme FROM Users WHERE username='admin'")),"Old password not marked legacy");
        boolean blocked=false;
        try { new SecurityConfig().checkAccountMigration().run((org.springframework.boot.ApplicationArguments)null); }
        catch(IllegalStateException expected) { blocked=true; }
        require(blocked,"Startup accepted unconverted plaintext");
        pass("SQL 004 repeatable and startup guard rejects unconverted accounts");

        sql("INSERT INTO Users(username,password,password_scheme,full_name,role) VALUES('bad','not-a-hash','unknown',N'Bad','staff')");
        boolean rollback=false;
        try(Connection conn=DBConnection.getConnection()) { PasswordMigration.migrate(conn); }
        catch(SQLException expected) { rollback=true; }
        require(rollback && ADMIN_PASSWORD.equals(value("SELECT password FROM Users WHERE username='admin'")),"Migration did not roll back");
        sql("DELETE FROM Users WHERE username='bad'");
        try(Connection conn=DBConnection.getConnection()) {
            require(PasswordMigration.migrate(conn)==2,"Expected two migrated accounts");
            String hash=value("SELECT password FROM Users WHERE username='admin'");
            require(PasswordHashing.validHash(hash) && PasswordHashing.encoder().matches(ADMIN_PASSWORD,hash),"Password not preserved");
            require(PasswordMigration.migrate(conn)==0,"Second migration rewrote hashes");
            require(hash.equals(value("SELECT password FROM Users WHERE username='admin'")),"Hash changed on rerun");
        }
        new SecurityConfig().checkAccountMigration().run((org.springframework.boot.ApplicationArguments)null);
        pass("password migration atomic, salted, preserves spaces and is repeatable without plaintext login");

        boolean wildcardRejected=false;
        try { new SecurityConfig().corsConfiguration("*"); } catch(IllegalArgumentException expected) { wildcardRejected=true; }
        require(wildcardRejected,"Credentialed wildcard origin accepted");

        class MutableClock extends java.time.Clock {
            long now=0;
            public java.time.ZoneId getZone() { return java.time.ZoneOffset.UTC; }
            public java.time.Clock withZone(java.time.ZoneId zone) { return this; }
            public java.time.Instant instant() { return java.time.Instant.ofEpochMilli(now); }
        }
        MutableClock time=new MutableClock();LoginThrottle attempts=new LoginThrottle(time);
        for(int i=0;i<30;i++) require(attempts.allow("shared-ip","account"+i),"IP limit reached too early");
        require(!attempts.allow("shared-ip","another"),"IP limit bypassed");
        LoginThrottle accountAttempts=new LoginThrottle(time);
        long accepted=java.util.stream.IntStream.range(0,12).parallel().filter(i -> accountAttempts.allow("ip"+i,"same-account")).count();
        require(accepted==5,"Concurrent account limit bypassed");
        time.now=15*60*1000L+1;
        require(attempts.allow("shared-ip","new-account") && accountAttempts.allow("ip-new","same-account"),"Throttle window never expires");
        pass("IP and concurrent account rate limits expire correctly after the cooldown");

        var application=new org.springframework.boot.SpringApplication(com.homestay.HomestayApplication.class);
        try(var context=(org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext)application.run(
                "--server.port=0","--server.address=127.0.0.1","--spring.main.banner-mode=off","--logging.level.root=ERROR",
                "--auth.allowed-origins=http://localhost:5500","--server.servlet.session.cookie.secure=false")) {
            base="http://localhost:"+context.getWebServer().getPort();
            Client anonymous=new Client();
            status(anonymous.get("/pages/login.html"),200);status(anonymous.get("/js/api.js"),200);
            status(anonymous.get("/api/rooms"),401);status(anonymous.get("/api/auth/me"),401);
            status(anonymous.send("POST","/api/bookings",Map.of(),Map.of()),401);
            status(anonymous.get("/pages/rooms.html"),302);
            pass("public login/assets work and anonymous protected API/page access is blocked");

            status(anonymous.send("POST","/api/auth/login",Map.of("username","admin","password",ADMIN_PASSWORD),Map.of()),403);
            Client admin=new Client();
            var tokenResponse=admin.csrf();String originalSession=admin.session(),oldToken=admin.token;
            String cookie=tokenResponse.headers().allValues("Set-Cookie").toString().toLowerCase(Locale.ROOT);
            require(cookie.contains("httponly") && cookie.contains("samesite=lax"),"Cookie flags missing");
            var login=admin.write("POST","/api/auth/login",Map.of("username","admin","password",ADMIN_PASSWORD));status(login,200);
            require(!Objects.equals(originalSession,admin.session()),"Session ID not rotated at login");
            require(!login.body().contains("pbkdf2") && !login.body().contains("\"password\""),"Password hash leaked");
            status(admin.send("POST","/api/rooms",Map.of(),Map.of("X-CSRF-TOKEN",oldToken)),403);
            admin.csrf();status(admin.get("/api/auth/me"),200);
            pass("login CSRF protected, session ID and token rotate, cookie HttpOnly/SameSite and profile excludes hash");

            status(admin.send("POST","/api/rooms",Map.of(),Map.of()),403);
            status(admin.send("POST","/api/rooms",Map.of(),Map.of("X-CSRF-TOKEN","wrong")),403);
            var room=admin.write("POST","/api/rooms",Map.of("roomCode","SEC1","roomName","Security room","roomType","Single","price",300000));
            status(room,200);require(Boolean.TRUE.equals(data(room).get("success")),"Admin cannot create room");
            status(admin.get("/api/statistics/overview"),200);
            pass("missing/wrong CSRF rejected while admin can mutate with valid token and read statistics");

            Client unknown=new Client(),wrong=new Client();
            var unknownResult=unknown.login("missing-account","wrong-password");var wrongResult=wrong.login("admin","wrong-password");
            status(unknownResult,401);status(wrongResult,401);
            require(Objects.equals(data(unknownResult).get("message"),data(wrongResult).get("message")),"Account enumeration messages differ");
            status(new Client().login("x".repeat(51),"wrong-password"),401);
            Client rateLimited=new Client();rateLimited.csrf();
            for(int i=0;i<5;i++) status(rateLimited.write("POST","/api/auth/login",Map.of("username","limited-account","password","wrong")),401);
            var limited=rateLimited.write("POST","/api/auth/login",Map.of("username","limited-account","password","wrong"));
            status(limited,429);require(limited.headers().firstValue("Retry-After").isPresent(),"Missing throttle hint");
            pass("generic credential errors and bounded login attempt limit enforced over HTTP");

            Client staff=new Client();var staffLogin=staff.login("staff",STAFF_PASSWORD);status(staffLogin,200);
            require("staff".equals(user(staffLogin).get("role")),"Forged login role trusted");
            status(staff.get("/api/rooms"),200);status(staff.get("/api/services"),200);status(staff.get("/api/customers"),200);
            status(staff.get("/api/statistics/overview"),403);
            status(staff.get("/pages/statistics.html"),403);
            status(staff.get("/pages/rooms.html"),403);
            status(staff.get("/pages/customers.html?workspace=admin"),403);
            status(staff.get("/pages/customers.html"),200);
            status(staff.get("/pages/account.html"),200);
            for(String path:List.of("/api/rooms","/api/services")) status(staff.write("POST",path,Map.of()),403);
            for(String path:List.of("/api/rooms/1","/api/services/1")) status(staff.write("PUT",path,Map.of()),403);
            status(staff.write("PUT","/api/rooms/1/status",Map.of("status","Bảo trì")),403);
            status(staff.get("/api/rooms?type=Single&status=Tr%E1%BB%91ng&search=P"),200);
            status(staff.get("/api/payments"),200);
            for(String path:List.of("/api/rooms/1","/api/services/1","/api/customers/1")) status(staff.write("DELETE",path,null),403);
            status(staff.send("PUT","/api/rooms/1",Map.of("role","admin","userId",1),Map.of("X-CSRF-TOKEN",staff.token,"X-Role","admin")),403);
            pass("staff reads allowed but admin statistics/catalog writes/customer deletion and forged role are denied");

            var customer=staff.write("POST","/api/customers",Map.of("fullName","Test guest","cccd","000000000001","phone","0900000000"));
            status(customer,200);require(Boolean.TRUE.equals(data(customer).get("success")),"Staff customer create failed");
            int customerId=Integer.parseInt(value("SELECT MAX(customer_id) FROM Customers"));
            int roomId=Integer.parseInt(value("SELECT MAX(room_id) FROM Rooms"));
            sql("INSERT INTO Services(service_name,unit_price,status) VALUES(N'Water',10000,N'Hoạt động')");
            int serviceId=Integer.parseInt(value("SELECT MAX(service_id) FROM Services"));
            java.time.LocalDate today=java.time.LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
            var booking=staff.write("POST","/api/bookings",Map.of("customerId",customerId,"roomId",roomId,"checkinDate",today.toString(),"checkoutDate",today.plusDays(1).toString()));
            status(booking,200);require(Boolean.TRUE.equals(data(booking).get("success")),"Staff booking failed");
            int bookingId=((Number)data(booking).get("bookingId")).intValue();
            status(staff.write("PUT","/api/checkin/"+bookingId,null),200);
            var service=staff.write("POST","/api/bookings/"+bookingId+"/services",Map.of("serviceId",serviceId,"quantity",2));status(service,200);
            require(Boolean.TRUE.equals(data(service).get("success")),"Staff service add failed");
            int lineId=((Number)data(service).get("bookingServiceId")).intValue();
            var removal=staff.write("DELETE","/api/bookings/services/"+lineId,null);status(removal,200);
            require(Boolean.TRUE.equals(data(removal).get("success")),"Staff service delete failed");
            var payment=staff.write("POST","/api/checkout/"+bookingId,Map.of());status(payment,200);
            require(Boolean.TRUE.equals(data(payment).get("success")),"Staff checkout failed");
            status(staff.get("/api/payments/"+data(payment).get("paymentId")),200);
            status(staff.get("/api/payments?bookingId="+bookingId),200);
            pass("staff customer/booking/check-in/service add-delete/checkout/invoice flow works through protected HTTP APIs");

            Client cors=new Client();
            var allowed=cors.send("OPTIONS","/api/rooms",null,Map.of("Origin","http://localhost:5500","Access-Control-Request-Method","GET","Access-Control-Request-Headers","X-CSRF-TOKEN"));
            status(allowed,200);require("http://localhost:5500".equals(allowed.headers().firstValue("Access-Control-Allow-Origin").orElse("")),"Origin not explicit");
            require("true".equals(allowed.headers().firstValue("Access-Control-Allow-Credentials").orElse("")),"Credentials not permitted for allowed origin");
            status(cors.send("OPTIONS","/api/rooms",null,Map.of("Origin","https://evil.example","Access-Control-Request-Method","GET")),403);
            pass("explicit credentialed dev CORS allowed and unknown/wildcard origins rejected");

            Client expires=new Client();status(expires.login("staff",STAFF_PASSWORD),200);expire(context,expires);
            status(expires.get("/api/auth/me"),401);
            Client forged=new Client();status(forged.send("GET","/api/rooms",null,Map.of("Cookie","HOMESTAY_SESSION=fake-session")),401);
            String oldCookie=staff.session();status(staff.write("POST","/api/auth/logout",null),200);
            status(staff.get("/api/auth/me"),401);
            Client replay=new Client();status(replay.send("GET","/api/rooms",null,Map.of("Cookie","HOMESTAY_SESSION="+oldCookie)),401);
            pass("server-expired, forged, logged-out and replayed sessions cannot access protected APIs");

            Client peer=new Client();status(peer.login("admin",ADMIN_PASSWORD),200);
            var wrongOld=admin.write("PUT","/api/auth/change-password",Map.of("oldPassword","wrong old","newPassword","Valid New Secret 123!","userId",2));status(wrongOld,400);
            status(admin.write("PUT","/api/auth/change-password",Map.of("oldPassword",ADMIN_PASSWORD,"newPassword","short")),400);
            String newPassword="  New Admin Secret 123!  ";
            status(admin.write("PUT","/api/auth/change-password",Map.of("oldPassword",ADMIN_PASSWORD,"newPassword",newPassword,"userId",2)),200);
            status(admin.get("/api/auth/me"),401);status(peer.get("/api/rooms"),401);
            require(PasswordHashing.encoder().matches(STAFF_PASSWORD,value("SELECT password FROM Users WHERE username='staff'")),"Password changed for client-supplied userId");
            Client loginNew=new Client();status(loginNew.login("admin",ADMIN_PASSWORD),401);
            status(loginNew.login("admin",newPassword.trim()),401);status(loginNew.login("admin",newPassword),200);
            require(PasswordHashing.encoder().matches(newPassword,value("SELECT password FROM Users WHERE username='admin'")),"New password not hashed");
            pass("password policy/old password checked, userId ignored, spaces preserved, old/all current sessions revoked");

            Client changedRole=new Client();status(changedRole.login("staff",STAFF_PASSWORD),200);
            sql("UPDATE Users SET role='admin' WHERE username='staff'");status(changedRole.get("/api/rooms"),401);
            sql("UPDATE Users SET role='staff' WHERE username='staff'");
            pass("server role changes invalidate existing authority snapshots");
        }
        System.out.println("SQL Server + HTTP SEC-01: "+passed+" groups passed.");
    }
}
