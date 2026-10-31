package com.homestay.security;

import com.homestay.db.DBConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

/** Real SQL Server checks in a disposable database, never the configured database. */
public class ValidationHttpSqlServerIT {

    private static int passed;

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && "--isolated-child".equals(args[0])) {
            String database = System.getenv("DB_NAME");
            if (database == null || !database.matches("HomestayFix03Test_[a-f0-9]{32}")) {
                throw new IllegalStateException("Refusing tests outside a disposable database");
            }
            try (Connection conn = DBConnection.getConnection()) {
                if (!database.equals(conn.getCatalog())) throw new IllegalStateException("Wrong database");
            }
            runChecks();
            return;
        }
        String database = "HomestayFix03Test_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection bootstrap = DBConnection.getConnection(); Statement stmt = bootstrap.createStatement()) {
            boolean created = false;
            try {
                stmt.execute("CREATE DATABASE [" + database + "]");
                created = true;
                stmt.execute("USE [" + database + "]");
                String schema = Files.readString(Path.of("../database/schema.sql"));
                // Keep table definitions, skip CREATE DATABASE / USE of the real database.
                schema = schema.substring(schema.indexOf("CREATE TABLE Users"));
                schema = schema.lines().filter(line -> !line.contains("password_scheme") && !line.contains("auth_version") && !line.contains("document_type")).reduce("", (a,b) -> a+b+"\n");
                for (String batch : schema.split("(?im)^\\s*GO\\s*$")) {
                    if (!batch.isBlank()) stmt.execute(batch);
                }
                ProcessBuilder child = new ProcessBuilder(
                        Path.of(System.getProperty("java.home"), "bin", "java.exe").toString(),
                        "-cp", System.getProperty("java.class.path"),
                        ValidationHttpSqlServerIT.class.getName(), "--isolated-child");
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
                    System.out.println("Disposed isolated FIX-03 test database.");
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

        sql("INSERT INTO Customers(full_name,cccd,phone,email,address) VALUES(N'Legacy customer','OLD 01',' 123','old bad',N'Original')");
        String documentMigration=Files.readString(Path.of("../database/migrations/005_customer_document_type.sql"));
        sql(documentMigration);sql(documentMigration);
        require("LEGACY".equals(value("SELECT document_type FROM Customers WHERE cccd='OLD 01'")),"Legacy type was guessed");
        require(" 123".equals(value("SELECT phone FROM Customers WHERE cccd='OLD 01'")),"Legacy phone was silently changed");
        pass("document migration repeatable and preserves ambiguous legacy documents/phone");
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
            for(String path:List.of("/api/rooms","/api/services")) status(staff.write("POST",path,Map.of()),403);
            for(String path:List.of("/api/rooms/1","/api/services/1")) status(staff.write("PUT",path,Map.of()),403);
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
            runValidationChecks(loginNew);
            runCustomerHistoryChecks(loginNew);
            runReportChecks(loginNew);
        }
        System.out.println("SQL Server + HTTP FIX-03: "+passed+" groups passed.");
    }

    @SuppressWarnings("unchecked")
    private static void runCustomerHistoryChecks(Client admin) throws Exception {
        Client staff=new Client();status(staff.login("staff",STAFF_PASSWORD),200);
        status(staff.write("POST","/api/customers",Map.of("fullName","History O'Neil","documentType","PASSPORT","cccd","HX123456","phone","0987654321")),200);
        int fresh=Integer.parseInt(value("SELECT MAX(customer_id) FROM Customers"));
        for(String term:List.of("History O'Neil","HX123456","0987654321")) {
            var response=staff.get("/api/customers?search="+java.net.URLEncoder.encode(term,java.nio.charset.StandardCharsets.UTF_8));status(response,200);
            List<Map<String,Object>> list=JSON.readValue(response.body(),List.class);
            require(list.size()==1 && ((Number)list.get(0).get("customerId")).intValue()==fresh,"Customer search mismatch");
        }
        for(String term:List.of("%","' OR 1=1 --")) {
            var response=staff.get("/api/customers?search="+java.net.URLEncoder.encode(term,java.nio.charset.StandardCharsets.UTF_8));status(response,200);
            require(JSON.readValue(response.body(),List.class).isEmpty(),"Search is not literal");
        }
        error(staff.get("/api/customers?search="+"x".repeat(101)),400);
        pass("FE-04 server search matches name/document/phone and treats SQL-looking input literally");
        var empty=staff.get("/api/customers/"+fresh+"/history");status(empty,200);
        require(((List<?>)data(empty).get("bookings")).isEmpty() && "0".equals(data(empty).get("totalPaid")),"New customer history is not empty zero");
        var emptyList=staff.get("/api/customers/"+fresh+"/bookings");status(emptyList,200);require(JSON.readValue(emptyList.body(),List.class).isEmpty(),"Customer bookings must return an array");
        error(staff.get("/api/customers/999999/bookings"),404);
        error(staff.get("/api/customers/999999/history"),404);error(staff.get("/api/customers/0/history"),400);
        error(new Client().get("/api/customers/"+fresh+"/history"),401);
        pass("FE-04 empty history is valid, missing customer is 404, invalid ID 400 and anonymous request 401");

        int paidBooking=Integer.parseInt(value("SELECT TOP 1 booking_id FROM Payments WHERE payment_status=N'Đã thanh toán' ORDER BY payment_id"));
        int owner=Integer.parseInt(value("SELECT customer_id FROM Bookings WHERE booking_id="+paidBooking));
        int room=Integer.parseInt(value("SELECT room_id FROM Bookings WHERE booking_id="+paidBooking));
        int service=Integer.parseInt(value("SELECT TOP 1 service_id FROM Services ORDER BY service_id"));
        sql("INSERT INTO Booking_Services(booking_id,service_id,quantity,service_name_snapshot,unit_price_snapshot,total_price) VALUES("
                +paidBooking+","+service+",2,N'Fixture water',10000,20000),("+paidBooking+","+service+",1,N'Fixture rental',100000,100000)");
        sql("INSERT INTO Bookings(customer_id,room_id,checkin_date,checkout_date,status,price_per_night) VALUES("
                +owner+","+room+",'2035-01-01','2035-01-03','Confirmed',300000),("+owner+","+room+",'2035-02-01','2035-02-03','Cancelled',300000)");
        int cancelled=Integer.parseInt(value("SELECT MAX(booking_id) FROM Bookings"));
        sql("INSERT INTO Payments(booking_id,room_total,service_total,grand_total,payment_status) VALUES("+cancelled+",98765,0,98765,N'Chưa thanh toán')");
        var response=staff.get("/api/customers/"+owner+"/history");status(response,200);var history=data(response);
        List<Map<String,Object>> rows=(List<Map<String,Object>>)history.get("bookings");
        require(rows.size()==Integer.parseInt(value("SELECT COUNT(*) FROM Bookings WHERE customer_id="+owner)),"Service join duplicated bookings");
        var bookingList=staff.get("/api/customers/"+owner+"/bookings");status(bookingList,200);require(JSON.readValue(bookingList.body(),List.class).size()==rows.size(),"Bookings/history endpoints disagree");
        String expected=value("SELECT CONVERT(varchar(50),CAST(SUM(p.grand_total) AS decimal(38,0))) FROM Payments p JOIN Bookings b ON b.booking_id=p.booking_id WHERE b.customer_id="+owner+" AND p.payment_status=N'Đã thanh toán'");
        require(expected.equals(history.get("totalPaid")),"History total includes unpaid or duplicated services");
        Map<String,Object> paid=rows.stream().filter(b -> ((Number)b.get("bookingId")).intValue()==paidBooking).findFirst().orElseThrow();
        require(paid.get("paymentId")!=null && paid.get("paidAt")!=null && ((Number)paid.get("nights")).intValue()>0,"Paid history lacks invoice or nights");
        require(rows.stream().filter(b -> ((Number)b.get("bookingId")).intValue()==cancelled).allMatch(b -> b.get("paymentId")==null && ((Number)b.get("paidTotal")).intValue()==0),"Unpaid payment counted");
        pass("FE-04 multiple services do not duplicate bookings or totals; unpaid/cancelled amounts excluded");
        sql("UPDATE Rooms SET price=price+1 WHERE room_id="+room+"; UPDATE Services SET unit_price=unit_price+1 WHERE service_id="+service);
        require(expected.equals(data(admin.get("/api/customers/"+owner+"/history")).get("totalPaid")),"Catalog edits changed customer spending");
        status(staff.get("/api/payments/"+paid.get("paymentId")),200);
        require("0".equals(data(staff.get("/api/customers/"+fresh+"/history")).get("totalPaid")),"History leaks another customer payments");
        error(admin.write("DELETE","/api/customers/"+owner,null),409);error(staff.write("DELETE","/api/customers/"+owner,null),403);
        pass("FE-04 payment details remain frozen/scoped and customer deletion respects history and staff role");
        sql("EXEC sp_rename 'dbo.Bookings','Bookings_HistoryOffline'");
        try { error(admin.get("/api/customers/"+owner+"/history"),503); }
        finally { sql("EXEC sp_rename 'dbo.Bookings_HistoryOffline','Bookings'"); }
        pass("FE-04 history query failure is 503 rather than an empty successful history");
    }

    @SuppressWarnings("unchecked")
    private static void runReportChecks(Client admin) throws Exception {
        int customer=Integer.parseInt(value("SELECT TOP 1 customer_id FROM Customers ORDER BY customer_id"));
        int room=Integer.parseInt(value("SELECT TOP 1 room_id FROM Rooms ORDER BY room_id"));
        String[] dates={"2031-01-15","2031-02-15","2030-01-15","2031-01-20"};
        int[] amounts={100,200,700,9999};
        for(int i=0;i<dates.length;i++) {
            sql("INSERT INTO Bookings(customer_id,room_id,checkin_date,checkout_date,status,price_per_night,created_at) VALUES("
                    +customer+","+room+",'"+dates[i]+"',DATEADD(day,1,'"+dates[i]+"'),'CheckedOut',"+amounts[i]+",'"+dates[i]+"')");
            int booking=Integer.parseInt(value("SELECT MAX(booking_id) FROM Bookings"));
            sql("INSERT INTO Payments(booking_id,room_total,service_total,grand_total,payment_status,paid_at) VALUES("
                    +booking+","+amounts[i]+",0,"+amounts[i]+",N'"+(i==3?"Chưa thanh toán":"Đã thanh toán")+"','"+dates[i]+"')");
        }
        var yearly=admin.get("/api/statistics/yearly?year=2031");status(yearly,200);
        List<Number> months=JSON.readValue(yearly.body(),List.class);
        require(months.size()==12 && months.get(0).intValue()==100 && months.get(1).intValue()==200,"Yearly data has wrong month/status/year");
        require(months.stream().mapToLong(Number::longValue).sum()==300,"Yearly total mismatch");
        var empty=admin.get("/api/statistics/yearly?year=2040");status(empty,200);
        require(((List<Number>)JSON.readValue(empty.body(),List.class)).stream().allMatch(n->n.intValue()==0),"Empty year is not zero filled");
        pass("REPORT-01 yearly returns exactly 12 months, excludes unpaid/other-year amounts and zero-fills empty months");
        long sum=0;
        for(int month=1;month<=12;month++) {
            var response=admin.get("/api/statistics/revenue?month="+month+"&year=2031");status(response,200);
            List<Map<String,Object>> list=JSON.readValue(response.body(),List.class);
            long monthly=list.stream().mapToLong(p->((Number)p.get("grandTotal")).longValue()).sum();sum+=monthly;
            require(monthly==months.get(month-1).longValue(),"Monthly invoices and yearly chart disagree");
        }
        require(sum==300,"Invoice reconciliation total mismatch");
        pass("REPORT-01 all 12 monthly invoice totals reconcile with yearly revenue");
        int payment=Integer.parseInt(value("SELECT TOP 1 payment_id FROM Payments WHERE invoice_snapshot IS NOT NULL ORDER BY payment_id"));
        String name=value("SELECT JSON_VALUE(invoice_snapshot,'$.customerName') FROM Payments WHERE payment_id="+payment);
        String date=value("SELECT CONVERT(varchar(10),paid_at,23) FROM Payments WHERE payment_id="+payment);
        int owner=Integer.parseInt(value("SELECT b.customer_id FROM Bookings b JOIN Payments p ON p.booking_id=b.booking_id WHERE p.payment_id="+payment));
        sql("UPDATE Customers SET full_name=N'Renamed after payment' WHERE customer_id="+owner);
        var report=admin.get("/api/statistics/revenue?month="+Integer.parseInt(date.substring(5,7))+"&year="+date.substring(0,4));status(report,200);
        List<Map<String,Object>> rows=JSON.readValue(report.body(),List.class);
        require(rows.stream().anyMatch(p->((Number)p.get("paymentId")).intValue()==payment && Objects.equals(p.get("customerName"),name)),"Paid report changed historical customer name");
        var overview=admin.get("/api/statistics/overview");status(overview,200);Map<String,Object> stats=data(overview);
        require(((Number)stats.get("totalRooms")).intValue()==((Number)stats.get("availableRooms")).intValue()+((Number)stats.get("bookedRooms")).intValue()+((Number)stats.get("occupiedRooms")).intValue()+((Number)stats.get("maintenanceRooms")).intValue(),"Room KPI sum mismatch");
        pass("REPORT-01 snapshot names stay frozen and room KPI totals reconcile");
        for(String year:List.of("0","10000","1.5","x")) error(admin.get("/api/statistics/yearly?year="+year),400);
        Client staff=new Client();status(staff.login("staff",STAFF_PASSWORD),200);error(staff.get("/api/statistics/yearly?year=2031"),403);
        sql("EXEC sp_rename 'dbo.Payments','Payments_ReportOffline'");
        try {error(admin.get("/api/statistics/yearly?year=2031"),503);}
        finally {sql("EXEC sp_rename 'dbo.Payments_ReportOffline','Payments'");}
        pass("REPORT-01 invalid years, staff access and database outages retain 400/403/503 semantics");
    }

    private static void error(java.net.http.HttpResponse<String> response,int expected) throws Exception {
        status(response,expected);Map<String,Object> body=data(response);
        require(Boolean.FALSE.equals(body.get("success")) && body.get("code") instanceof String
                && body.get("message") instanceof String,"Inconsistent error JSON");
        String output=response.body();
        for(String forbidden:List.of("SQLServerException","SELECT ","INSERT INTO","stackTrace",".java:","Violation of")) {
            require(!output.contains(forbidden),"Internal detail leaked into response");
        }
    }
    private static Map<String,Object> customerBody(String document,String type,String phone) {
        return Map.of("fullName","O'Neil <img src=x onerror=alert(1)>","cccd",document,"documentType",type,"phone",phone,"email","guest@example.test","address","Address");
    }
    private static void runValidationChecks(Client admin) throws Exception {
        for(String path:List.of("/api/rooms/999999","/api/customers/999999","/api/services/999999","/api/payments/999999")) error(admin.get(path),404);
        for(String path:List.of("/api/rooms/-1","/api/customers/abc","/api/services/1.5","/api/payments/0")) error(admin.get(path),400);
        error(admin.write("PUT","/api/bookings/999999/cancel",null),404);
        error(admin.write("PUT","/api/checkin/999999",null),404);
        error(admin.write("DELETE","/api/bookings/services/999999",null),404);
        pass("missing resources are 404 and invalid path IDs are 400 with consistent sanitized JSON");

        Map<String,Object> validRoom=Map.of("roomCode","SEC1","roomName","Room","roomType","Single","price",300000);
        error(admin.write("POST","/api/rooms",validRoom),409);
        error(admin.write("POST","/api/customers",customerBody("000000000001","CCCD","0900000000")),409);
        error(admin.write("POST","/api/services",Map.of("serviceName","Water","unitPrice",1)),409);
        int paidBooking=Integer.parseInt(value("SELECT MAX(booking_id) FROM Bookings"));
        error(admin.write("PUT","/api/checkin/"+paidBooking,null),409);
        error(admin.write("POST","/api/checkout/"+paidBooking,Map.of()),409);
        pass("duplicate catalog/documents and invalid booking transitions use 409");

        var passport=admin.write("POST","/api/customers",customerBody("AB123456","PASSPORT","0912345678"));status(passport,200);
        require(Boolean.TRUE.equals(data(passport).get("success")),"Valid passport rejected");
        int passportId=Integer.parseInt(value("SELECT MAX(customer_id) FROM Customers"));
        var detail=admin.get("/api/customers/"+passportId);status(detail,200);
        require("PASSPORT".equals(data(detail).get("documentType")),"Document type missing");
        require("O'Neil <img src=x onerror=alert(1)>".equals(data(detail).get("fullName")),"Valid text was corrupted or stripped");
        require(data(admin.get("/api/customers/"+passportId)).get("validationWarnings") instanceof List,"Missing legacy warning metadata");
        pass("passport and quoted/HTML-looking names round-trip as JSON text without changing success shape");

        for(Map<String,Object> bad:List.of(customerBody("12345678901","CCCD","0912345678"),customerBody("A-12345","PASSPORT","0912345678"),
                customerBody("AC123456","UNKNOWN","0912345678"),customerBody("AC123456","PASSPORT","1912345678"),customerBody("AC123456","PASSPORT","091234567"))) {
            error(admin.write("POST","/api/customers",bad),400);
        }
        Map<String,Object> badEmail=new HashMap<>(customerBody("AC123456","PASSPORT","0912345678"));badEmail.put("email","not an email");error(admin.write("POST","/api/customers",badEmail),400);
        Map<String,Object> tooLong=new HashMap<>(customerBody("AC123456","PASSPORT","0912345678"));tooLong.put("fullName","x".repeat(101));error(admin.write("POST","/api/customers",tooLong),400);
        tooLong.put("fullName",Map.of("text","wrong type"));error(admin.write("POST","/api/customers",tooLong),400);
        pass("CCCD/passport/phone/email/length/type validation enforced at the server");

        int legacyId=Integer.parseInt(value("SELECT customer_id FROM Customers WHERE cccd='OLD 01'"));
        var legacy=data(admin.get("/api/customers/"+legacyId));
        require(((List<?>)legacy.get("validationWarnings")).size()>=2,"Legacy problems not exposed for review");
        status(admin.write("PUT","/api/customers/"+legacyId,Map.of("fullName","Legacy updated","cccd","OLD 01","documentType","LEGACY","phone"," 123","email","old bad","address","Changed")),200);
        require(" 123".equals(value("SELECT phone FROM Customers WHERE customer_id="+legacyId)),"Unchanged legacy phone altered");
        error(admin.write("PUT","/api/customers/"+legacyId,Map.of("fullName","Legacy updated","phone","987")),400);
        status(admin.write("PUT","/api/customers/"+legacyId,customerBody("ZX987654","PASSPORT","0901234567")),200);
        require(((List<?>)data(admin.get("/api/customers/"+legacyId)).get("validationWarnings")).isEmpty(),"Corrected legacy record still flagged");
        pass("legacy values remain editable unchanged, changed invalid values rejected and explicit correction clears warnings");

        for(Object price:List.of(-1,1.5,"abc",true,1000000000000L)) {
            Map<String,Object> body=new HashMap<>(validRoom);body.put("roomCode","BADPRICE");body.put("price",price);error(admin.write("POST","/api/rooms",body),400);
            error(admin.write("POST","/api/services",Map.of("serviceName","Bad","unitPrice",price)),400);
        }
        error(admin.write("POST","/api/rooms",Map.of("roomCode","BAD","roomName","Bad","roomType","Alien","price",1)),400);
        error(admin.write("PUT","/api/rooms/1",Map.of("roomName","Bad","roomType","Single","price",1,"status","Alien")),400);
        error(admin.write("POST","/api/services",Map.of("serviceName","Bad","unitPrice",1,"status","Alien")),400);
        error(admin.get("/api/bookings?status=Alien"),400);
        pass("money rejects negatives/fractions/invalid types/overflow and enums are validated");

        String today=java.time.LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toString();
        String tomorrow=java.time.LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(1).toString();
        int roomId=Integer.parseInt(value("SELECT MAX(room_id) FROM Rooms"));
        Map<String,Object> booking=new HashMap<>(Map.of("customerId",passportId,"roomId",roomId,"checkinDate",today,"checkoutDate",tomorrow));
        booking.put("note","x".repeat(501));error(admin.write("POST","/api/bookings",booking),400);booking.remove("note");
        booking.put("customerId",1.5);error(admin.write("POST","/api/bookings",booking),400);booking.put("customerId",passportId);
        booking.put("checkinDate","2026-02-30");error(admin.write("POST","/api/bookings",booking),400);
        error(admin.write("POST","/api/bookings/999999/services",Map.of("serviceId",1,"quantity",1.5)),400);
        error(admin.write("POST","/api/checkout/"+paidBooking,Map.of("paymentMethod","Crypto")),400);
        for(String query:List.of("month=0&year=2026","month=13&year=2026","month=x&year=2026","month=1&year=10000","month=1&year=0")) error(admin.get("/api/statistics/revenue?"+query),400);
        pass("booking IDs/dates/notes, service quantity, payment method and report periods are validated");

        var malformed=admin.http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(base+"/api/customers"))
                .header("Content-Type","application/json").header("X-CSRF-TOKEN",admin.token)
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString("{")).build(),java.net.http.HttpResponse.BodyHandlers.ofString());
        error(malformed,400);
        var unsupported=admin.http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(base+"/api/customers"))
                .header("Content-Type","text/plain").header("X-CSRF-TOKEN",admin.token)
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString("raw")).build(),java.net.http.HttpResponse.BodyHandlers.ofString());
        error(unsupported,415);
        pass("malformed JSON and unsupported media types have safe 400/415 JSON responses");

        int paymentId=Integer.parseInt(value("SELECT MAX(payment_id) FROM Payments"));
        String frozen=value("SELECT invoice_snapshot FROM Payments WHERE payment_id="+paymentId);
        sql("UPDATE Payments SET invoice_snapshot='[1]' WHERE payment_id="+paymentId);
        try { error(admin.get("/api/payments/"+paymentId),500); }
        finally {
            try(Connection conn=DBConnection.getConnection();PreparedStatement stmt=conn.prepareStatement("UPDATE Payments SET invoice_snapshot=? WHERE payment_id=?")) {
                stmt.setString(1,frozen);stmt.setInt(2,paymentId);stmt.executeUpdate();
            }
        }
        pass("unexpected invoice decoding error is 500 without returning stored JSON or stack trace");

        sql("EXEC sp_rename 'dbo.Customers','Customers_Fix03Offline'");
        try {
            error(admin.get("/api/customers"),503);error(admin.get("/api/customers/"+passportId),503);
            error(admin.get("/api/bookings"),503);error(admin.get("/api/statistics/overview"),503);
        } finally { sql("EXEC sp_rename 'dbo.Customers_Fix03Offline','Customers'"); }
        sql("EXEC sp_rename 'dbo.Services','Services_Fix03Offline'");
        try { error(admin.get("/api/services"),503); } finally { sql("EXEC sp_rename 'dbo.Services_Fix03Offline','Services'"); }
        sql("EXEC sp_rename 'dbo.Rooms','Rooms_Fix03Offline'");
        try {
            error(admin.get("/api/rooms"),503);error(admin.get("/api/rooms/"+roomId),503);
            error(admin.get("/api/bookings/available-rooms?checkin="+today+"&checkout="+tomorrow),503);
        } finally { sql("EXEC sp_rename 'dbo.Rooms_Fix03Offline','Rooms'"); }
        status(admin.get("/api/customers"),200);status(admin.get("/api/rooms"),200);
        pass("database query failures return 503 instead of empty lists, partial overview or false not-found");
    }
}
