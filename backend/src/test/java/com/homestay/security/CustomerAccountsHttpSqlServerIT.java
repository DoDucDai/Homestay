package com.homestay.security;

import com.homestay.HomestayApplication;
import com.homestay.db.DBConnection;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.support.GenericApplicationContext;
import java.nio.file.*;
import java.sql.*;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** Real HTTP + SQL; email is captured in test memory and never sent externally. */
public class CustomerAccountsHttpSqlServerIT {
    static final ObjectMapper JSON=new ObjectMapper();
    static final CapturedMail MAIL=new CapturedMail();
    static String base;
    static class CapturedMail implements MailGateway {
        final Map<String,String> messages=new ConcurrentHashMap<>();
        volatile boolean enabled=true,fail=false;
        public boolean configured(){return enabled;}
        public void send(String email,String subject,String text){if(fail)throw AccountSettings.unavailable();messages.put(email,text);}
        String code(String email){var matcher=java.util.regex.Pattern.compile("[0-9]{6}").matcher(messages.get(email));if(!matcher.find())throw new AssertionError("OTP not captured");return matcher.group();}
        String token(String email){var matcher=java.util.regex.Pattern.compile("#token=([A-Za-z0-9_.-]+)").matcher(messages.get(email));if(!matcher.find())throw new AssertionError("Reset link not captured");return matcher.group(1);}
    }
    static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void pass(String message){System.out.println("PASS "+message);}
    static void execute(Statement statement,String text)throws Exception {
        boolean rows=statement.execute(text);
        while(rows || statement.getUpdateCount()!=-1){if(rows)try(var result=statement.getResultSet()){while(result.next()) {}}rows=statement.getMoreResults();}
    }
    static void sql(String text)throws Exception {try(var c=DBConnection.getConnection();var s=c.createStatement()){execute(s,text);}}
    static String value(String text)throws Exception {try(var c=DBConnection.getConnection();var s=c.createStatement();var rows=s.executeQuery(text)){require(rows.next(),"Missing SQL result");return rows.getString(1);}}
    static Map<String,Object> data(HttpResponse<String> response)throws Exception {return JSON.readValue(response.body(),Map.class);}
    static void status(HttpResponse<String> response,int expected){require(response.statusCode()==expected,"Expected HTTP "+expected+" got "+response.statusCode()+": "+response.body());}
    static class Client {
        final HttpClient http=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).followRedirects(HttpClient.Redirect.NEVER).build();
        String csrf;
        HttpResponse<String> send(String method,String path,Object body,boolean token)throws Exception {
            var request=HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(25));
            if(token)request.header("X-CSRF-TOKEN",csrf);
            if(body!=null)request.header("Content-Type","application/json");
            return http.send(request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
        }
        HttpResponse<String> get(String path)throws Exception{return send("GET",path,null,false);}
        HttpResponse<String> post(String path,Object body)throws Exception{return send("POST",path,body,true);}
        HttpResponse<String> put(String path,Object body)throws Exception{return send("PUT",path,body,true);}
        void csrf()throws Exception {var response=get("/api/auth/csrf");status(response,200);csrf=(String)data(response).get("token");}
        void login(String username,String password)throws Exception {csrf();status(post("/api/auth/login",Map.of("username",username,"password",password)),200);csrf();}
        Map<String,Object> me()throws Exception {var response=get("/api/auth/me");status(response,200);return (Map<String,Object>)data(response).get("user");}
    }
    static ServletWebServerApplicationContext start() {
        var application=new SpringApplication(HomestayApplication.class);
        application.addInitializers(context->{
            var bean=new RootBeanDefinition(CapturedMail.class);bean.setInstanceSupplier(()->MAIL);bean.setPrimary(true);
            ((GenericApplicationContext)context).registerBeanDefinition("capturedMailGateway",bean);
        });
        var context=(ServletWebServerApplicationContext)application.run("--server.port=0","--server.address=127.0.0.1","--spring.profiles.active=dev","--COOKIE_SECURE=false","--logging.level.root=WARN");
        base="http://127.0.0.1:"+context.getWebServer().getPort();return context;
    }
    public static void main(String[] args)throws Exception {
        if(args.length>0 && args[0].equals("--child")){checks();return;}
        checkMigrationRollback();
        String db="HomestayMemberTest_"+UUID.randomUUID().toString().replace("-","");
        try(var c=DBConnection.getConnection();var s=c.createStatement()) {
            s.execute("CREATE DATABASE ["+db+"]");
            try {
                s.execute("USE ["+db+"]");String schema=Files.readString(Path.of("../database/schema.sql"));
                for(String batch:schema.substring(schema.indexOf("CREATE TABLE Users")).split("(?im)^\\s*GO\\s*$"))if(!batch.isBlank())execute(s,batch);
                var process=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java.exe").toString(),"-Dfile.encoding=UTF-8","-cp",System.getProperty("java.class.path"),CustomerAccountsHttpSqlServerIT.class.getName(),"--child");
                process.environment().put("DB_NAME",db);process.inheritIO();var child=process.start();
                if(!child.waitFor(240,TimeUnit.SECONDS)){child.destroyForcibly().waitFor();throw new AssertionError("Member checks timeout");}
                require(child.exitValue()==0,"Member integration failed");
            } finally {s.execute("USE master");s.execute("ALTER DATABASE ["+db+"] SET SINGLE_USER WITH ROLLBACK IMMEDIATE");s.execute("DROP DATABASE ["+db+"]");System.out.println("Disposed isolated member database.");}
        }
    }
    static void checkMigrationRollback()throws Exception {
        String database="HomestayMemberRollback_"+UUID.randomUUID().toString().replace("-","");
        try(var connection=DBConnection.getConnection();var statement=connection.createStatement()) {
            statement.execute("CREATE DATABASE ["+database+"]");
            try {
                statement.execute("USE ["+database+"]");statement.execute("CREATE TABLE Users(user_id INT PRIMARY KEY,email NVARCHAR(254)); INSERT Users VALUES(1,'legacy@example.org')");
                boolean failed=false;
                try { com.homestay.db.AccountRegistrationMigration.migrate(connection); } catch(SQLException expected) { failed=true; }
                require(failed,"Migration should reject missing customer schema");
                try(var rows=statement.executeQuery("SELECT COL_LENGTH('dbo.Users','email_version'),COUNT(*) FROM Users")) {
                    rows.next();require(rows.getObject(1)==null && rows.getInt(2)==1,"Failed migration left partial schema or lost users");
                }
                pass("member schema failure rolls back added columns and preserves legacy rows");
            } finally { statement.execute("USE master");statement.execute("ALTER DATABASE ["+database+"] SET SINGLE_USER WITH ROLLBACK IMMEDIATE");statement.execute("DROP DATABASE ["+database+"]"); }
        }
    }
    static void cooldown()throws Exception {sql("UPDATE AccountChallenges SET created_at=DATEADD(second,-61,SYSUTCDATETIME())");}
    static Map<String,Object> registration(String username,String email){return Map.of("username",username,"email",email,"fullName","Member test","phone","0901234567","password","Member@123","role","admin","emailVerified",true);}
    static void checks()throws Exception {
        require(System.getenv("DB_NAME").matches("HomestayMemberTest_[a-f0-9]{32}"),"Unsafe member test database");
        require(!new AccountSettings(new org.springframework.core.env.StandardEnvironment()).emailVerificationEnabled(),"Demo must default to no email verification");
        System.setProperty("EMAIL_VERIFICATION_ENABLED","true");
        System.setProperty("AUTH_TOKEN_SECRET","isolated-test-key-which-is-at-least-32-characters");System.setProperty("APP_PUBLIC_URL","https://homestay-mochi-demo.vercel.app");
        try(var c=DBConnection.getConnection();var st=c.prepareStatement("INSERT Users(username,password,full_name,role) VALUES('qaadmin',?,'Admin','admin')")){st.setString(1,PasswordHashing.encoder().encode("Admin@123"));st.executeUpdate();}
        sql("INSERT Customers(full_name,cccd,phone) VALUES('Legacy guest','111111111111','0900000000'); INSERT Rooms(room_code,room_name,room_type,price,hourly_price,status) VALUES('Room 9901','Member test room','Single',300000,80000,N'Trống')");
        ServletWebServerApplicationContext context=start();
        try {
            Client a=new Client(),b=new Client(),admin=new Client();
            status(a.send("POST","/api/auth/register",registration("membera","a@example.org"),false),403);
            a.csrf();status(a.post("/api/auth/register",registration("membera","a@example.org")),200);a.csrf();
            var identity=a.me();require("customer".equals(identity.get("role")) && Boolean.FALSE.equals(identity.get("emailVerified")),"Registration trusted forged role/verification");
            int aId=((Number)identity.get("userId")).intValue();
            require(PasswordHashing.validHash(value("SELECT password FROM Users WHERE user_id="+aId)),"Registration stored weak/plaintext password");
            b.csrf();status(b.post("/api/auth/register",registration("memberb","b@example.net")),200);b.csrf();
            for(String path:List.of("/api/customers","/api/bookings","/api/payments","/pages/bookings.html","/pages/checkin.html","/pages/checkout.html"))status(a.get(path),200);
            for(String path:List.of("/api/statistics/overview","/pages/rooms.html","/pages/statistics.html","/api/me/bookings","/pages/member-bookings.html"))status(a.get(path),403);
            status(a.get("/pages/account.html"),200);status(a.get("/pages/services.html"),200);
            Client firstRegistration=new Client(),secondRegistration=new Client();firstRegistration.csrf();secondRegistration.csrf();
            var registrationPool=Executors.newFixedThreadPool(2);
            try {
                var one=registrationPool.submit(()->firstRegistration.post("/api/auth/register",registration("racerone","race@example.org")));
                var two=registrationPool.submit(()->secondRegistration.post("/api/auth/register",registration("racertwo","race@example.org")));
                var statuses=List.of(one.get().statusCode(),two.get().statusCode());require(statuses.contains(200) && statuses.contains(409),"Concurrent duplicate email registration accepted twice");
                require("1".equals(value("SELECT COUNT(*) FROM Users WHERE email='race@example.org'")),"Duplicate email rows created");
            } finally {registrationPool.shutdownNow();}
            pass("registration creates customer session, ignores forged role and uses shared operational routes; admin routes remain protected");

            LocalDate today=LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
            status(a.post("/api/auth/email-verification/request",Map.of()),200);String code=MAIL.code("a@example.org");
            status(a.post("/api/auth/email-verification/request",Map.of()),429);
            status(b.post("/api/auth/email-verification/confirm",Map.of("code",code,"userId",aId)),400);
            String wrong=code.equals("000000")?"111111":"000000";
            status(a.post("/api/auth/email-verification/confirm",Map.of("code",wrong)),400);
            require("1".equals(value("SELECT MAX(attempts) FROM AccountChallenges WHERE user_id="+aId)),"Wrong OTP attempt rolled back");
            status(a.post("/api/auth/email-verification/confirm",Map.of("code",code)),200);
            require(Boolean.TRUE.equals(a.me().get("emailVerified")),"Verification not persisted");
            status(a.post("/api/auth/email-verification/confirm",Map.of("code",code)),400);
            pass("OTP cooldown, wrong attempts, single use and account ownership enforced");

            admin.login("qaadmin","Admin@123");
            var booking=Map.<String,Object>of("roomId",1,"customerId",1,"checkinDate",today.plusDays(2).toString(),"checkoutDate",today.plusDays(3).toString());
            var created=a.post("/api/bookings",booking);status(created,200);
            int bookingId=((Number)data(created).get("bookingId")).intValue();
            status(a.put("/api/bookings/"+bookingId+"/cancel",Map.of()),200);
            pass("customer creates and cancels bookings through the shared workflow");

            cooldown();status(a.post("/api/auth/email-verification/request",Map.of()),200);String oldCode=MAIL.code("a@example.org");
            var profile=new HashMap<String,Object>(Map.of("fullName","Updated member","phone","0901234567","email","new-a@example.org","birthDate","2000-01-01","city","Hanoi","userId",999,"emailVerified",true));
            status(a.put("/api/auth/profile",profile),200);require(Boolean.FALSE.equals(a.me().get("emailVerified")),"Email edit kept verification");
            status(a.post("/api/auth/email-verification/confirm",Map.of("code",oldCode)),400);
            cooldown();status(a.post("/api/auth/email-verification/request",Map.of()),200);status(a.post("/api/auth/email-verification/confirm",Map.of("code",MAIL.code("new-a@example.org"))),200);
            status(a.put("/api/auth/profile",profile),200);require(Boolean.TRUE.equals(a.me().get("emailVerified")),"Same email profile edit revoked verification");
            profile.put("email","b@example.net");status(a.put("/api/auth/profile",profile),409);require("new-a@example.org".equals(a.me().get("email")),"Duplicate profile email partially committed");
            pass("profile changes bind verification to saved email and invalidate old codes; unchanged email preserves verification");

            Client recovery=new Client(),secondSession=new Client();recovery.csrf();secondSession.login("membera","Member@123");
            var unknown=recovery.post("/api/auth/forgot-password",Map.of("email","missing@example.org"));status(unknown,200);
            status(admin.put("/api/auth/profile",Map.of("fullName","Admin","phone","0901234567","email","qa-admin@example.org","birthDate","2000-01-01","city","QA")),200);
            var unverifiedAdmin=recovery.post("/api/auth/forgot-password",Map.of("email","qa-admin@example.org"));status(unverifiedAdmin,200);
            require(unknown.body().equals(unverifiedAdmin.body()) && !MAIL.messages.containsKey("qa-admin@example.org"),"Unverified legacy admin email may recover privileged access");
            status(admin.post("/api/auth/email-verification/request",Map.of()),200);status(admin.post("/api/auth/email-verification/confirm",Map.of("code",MAIL.code("qa-admin@example.org"))),200);
            status(recovery.post("/api/auth/forgot-password",Map.of("email","qa-admin@example.org")),200);require(MAIL.token("qa-admin@example.org")!=null,"Verified admin recovery unavailable");
            var known=recovery.post("/api/auth/forgot-password",Map.of("email","new-a@example.org"));status(known,200);require(unknown.body().equals(known.body()),"Reset message enumerates accounts");
            String token=MAIL.token("new-a@example.org");require(MAIL.messages.get("new-a@example.org").contains("https://homestay-mochi-demo.vercel.app/pages/login.html?view=reset-password#token="),"Reset origin not configured frontend");
            sql("UPDATE AccountChallenges SET expires_at=DATEADD(second,-1,SYSUTCDATETIME()) WHERE purpose='RESET'");
            status(recovery.post("/api/auth/reset-password",Map.of("token",token,"password","NewMember@123")),400);
            cooldown();status(recovery.post("/api/auth/forgot-password",Map.of("email","new-a@example.org")),200);token=MAIL.token("new-a@example.org");
            final String resetToken=token;Client otherReset=new Client();otherReset.csrf();var resetPool=Executors.newFixedThreadPool(2);
            try {
                var one=resetPool.submit(()->recovery.post("/api/auth/reset-password",Map.of("token",resetToken,"password","NewMember@123")));
                var two=resetPool.submit(()->otherReset.post("/api/auth/reset-password",Map.of("token",resetToken,"password","NewMember@123")));
                var statuses=List.of(one.get().statusCode(),two.get().statusCode());require(statuses.contains(200) && statuses.contains(400),"Reset token consumed concurrently more than once");
            } finally {resetPool.shutdownNow();}
            recovery.csrf();
            status(recovery.post("/api/auth/reset-password",Map.of("token",token,"password","OtherPassword@123")),400);
            status(a.get("/api/auth/me"),401);status(secondSession.get("/api/auth/me"),401);
            Client relogin=new Client();relogin.csrf();status(relogin.post("/api/auth/login",Map.of("username","membera","password","Member@123")),401);relogin.login("membera","NewMember@123");
            pass("generic reset responses, fixed frontend fragment links, expiry/replay checks and all-session credential revocation");

            cooldown();status(b.post("/api/auth/email-verification/request",Map.of()),200);String bCode=MAIL.code("b@example.net");String bWrong=bCode.equals("000000")?"111111":"000000";
            for(int i=0;i<5;i++)status(b.post("/api/auth/email-verification/confirm",Map.of("code",bWrong)),400);
            status(b.post("/api/auth/email-verification/confirm",Map.of("code",bCode)),400);
            cooldown();status(b.post("/api/auth/email-verification/request",Map.of()),200);bCode=MAIL.code("b@example.net");
            sql("UPDATE AccountChallenges SET expires_at=DATEADD(second,-1,SYSUTCDATETIME()) WHERE purpose='VERIFY' AND used_at IS NULL");
            status(b.post("/api/auth/email-verification/confirm",Map.of("code",bCode)),400);
            cooldown();MAIL.fail=true;status(b.post("/api/auth/email-verification/request",Map.of()),503);MAIL.fail=false;
            require("FAILED".equals(value("SELECT TOP(1) delivery_status FROM AccountChallenges WHERE user_id="+b.me().get("userId")+" ORDER BY created_at DESC")),"Delivery failure left a usable challenge");
            MAIL.enabled=false;status(b.post("/api/auth/email-verification/request",Map.of()),503);MAIL.enabled=true;
            cooldown();status(b.post("/api/auth/email-verification/request",Map.of()),200);String persistentCode=MAIL.code("b@example.net");
            context.close();context=start();b=new Client();b.login("memberb","Member@123");
            status(b.post("/api/auth/email-verification/request",Map.of()),429);
            final Client existing=b;Client parallel=new Client();parallel.login("memberb","Member@123");
            var pool=Executors.newFixedThreadPool(2);
            try {
                var one=pool.submit(()->existing.post("/api/auth/email-verification/confirm",Map.of("code",persistentCode)));
                var two=pool.submit(()->parallel.post("/api/auth/email-verification/confirm",Map.of("code",persistentCode)));
                require((one.get().statusCode()==200) != (two.get().statusCode()==200),"Concurrent OTP consumed twice");
            } finally {pool.shutdownNow();}
            pass("five-attempt lockout, expired/failed email fail closed, restart persistence and concurrent single-use OTP");
            // Exercise the same HTTP/server booking paths with verification temporarily disabled.
            System.setProperty("EMAIL_VERIFICATION_ENABLED","false");MAIL.enabled=false;
            Client demo=new Client();demo.csrf();status(demo.post("/api/auth/register",registration("demomember","demo@not-a-real-mailbox.invalid")),200);demo.csrf();
            require(Boolean.FALSE.equals(demo.me().get("emailVerified")) && Boolean.FALSE.equals(demo.me().get("emailVerificationEnabled")),"Demo mode falsely verified an email or hid its policy");
            int mailCount=MAIL.messages.size();String challengeCount=value("SELECT COUNT(*) FROM AccountChallenges");
            var disabled=demo.post("/api/auth/email-verification/request",Map.of());status(disabled,503);require("EMAIL_VERIFICATION_DISABLED".equals(data(disabled).get("code")),"Disabled verification called mail provider");
            status(demo.post("/api/auth/email-verification/confirm",Map.of("code","123456")),503);
            require(MAIL.messages.size()==mailCount && challengeCount.equals(value("SELECT COUNT(*) FROM AccountChallenges")),"Disabled verification sent email or created OTP");
            status(demo.get("/api/bookings"),200);status(demo.get("/api/customers"),200);
            status(demo.get("/api/statistics/overview"),403);
            require("customer".equals(demo.me().get("role")),"Demo registration did not use customer role");
            System.setProperty("EMAIL_VERIFICATION_ENABLED","true");
            require(Boolean.TRUE.equals(demo.me().get("emailVerificationEnabled")) && Boolean.FALSE.equals(demo.me().get("emailVerified")),"Re-enabling verification trusted the demo email");
            pass("demo email registration uses shared customer access and keeps OTP disabled without mail");
        } finally {context.close();}
    }
}
