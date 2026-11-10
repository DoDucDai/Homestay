package com.homestay.security;

import com.homestay.db.DBConnection;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

/** Real HTTP/SQL account email workflows; reuses the captured-mail fixture, never calls Brevo. */
public final class EmailFlowsHttpSqlServerIT extends CustomerAccountsHttpSqlServerIT {
    private static final String DATABASE_PREFIX="HomestayEmailTest_";
    public static void main(String[] args) throws Exception {
        if(args.length>0 && args[0].equals("--child")) { checks();return; }
        String database=DATABASE_PREFIX+UUID.randomUUID().toString().replace("-","");
        require(database.matches(DATABASE_PREFIX+"[a-f0-9]{32}"),"Unsafe isolated database name");
        try(var connection=DBConnection.getConnection();var statement=connection.createStatement()) {
            statement.execute("CREATE DATABASE ["+database+"]");
            try {
                statement.execute("USE ["+database+"]");
                String schema=Files.readString(Path.of("../database/schema.sql"));
                for(String batch:schema.substring(schema.indexOf("CREATE TABLE Users")).split("(?im)^\\s*GO\\s*$"))
                    if(!batch.isBlank())execute(statement,batch);
                var child=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),
                    "-Dfile.encoding=UTF-8","-cp",System.getProperty("java.class.path"),EmailFlowsHttpSqlServerIT.class.getName(),"--child");
                child.environment().put("DB_NAME",database);
                child.environment().put("MAIL_PROVIDER","disabled");
                child.environment().put("MAIL_API_KEY","unused-test-only");
                for(String key:List.of("EMAIL_VERIFICATION_ENABLED","AUTH_TOKEN_SECRET","APP_PUBLIC_URL"))child.environment().remove(key);
                child.inheritIO();var process=child.start();
                if(!process.waitFor(240,TimeUnit.SECONDS)) { process.destroyForcibly().waitFor();throw new AssertionError("Email integration timed out"); }
                require(process.exitValue()==0,"Email integration failed");
            } finally {
                statement.execute("USE master");
                statement.execute("ALTER DATABASE ["+database+"] SET SINGLE_USER WITH ROLLBACK IMMEDIATE");
                statement.execute("DROP DATABASE ["+database+"]");
                System.out.println("Disposed isolated email database.");
            }
        }
    }
    private static int userId(Client client) throws Exception { return ((Number)client.me().get("userId")).intValue(); }
    private static String latestDelivery(int user,String purpose) throws Exception {
        return value("SELECT TOP(1) delivery_status FROM AccountChallenges WHERE user_id="+user+" AND purpose='"+purpose+"' AND used_at IS NULL ORDER BY created_at DESC");
    }
    static void checks() throws Exception {
        require(Objects.toString(System.getenv("DB_NAME"),"").matches(DATABASE_PREFIX+"[a-f0-9]{32}"),"Refusing email checks outside isolated database");
        require("disabled".equals(System.getenv("MAIL_PROVIDER")),"External provider must be disabled for these checks");
        System.setProperty("EMAIL_VERIFICATION_ENABLED","true");
        System.setProperty("AUTH_TOKEN_SECRET","isolated-email-test-signing-secret-at-least-32-characters");
        System.setProperty("APP_PUBLIC_URL","https://frontend.example.org");
        var context=start();
        try {
            Client a=new Client(),b=new Client(),recovery=new Client();
            a.csrf();status(a.post("/api/auth/register",registration("emailtesta","email-a@example.org")),200);a.csrf();
            b.csrf();status(b.post("/api/auth/register",registration("emailtestb","email-b@example.org")),200);b.csrf();
            int aId=userId(a),bId=userId(b);
            require("customer".equals(a.me().get("role")) && Boolean.FALSE.equals(a.me().get("emailVerified")),"Registration trusted client verification/role");
            status(a.send("POST","/api/auth/email-verification/request",Map.of(),false),403);
            status(a.post("/api/auth/email-verification/request",Map.of("email","forged@example.org")),200);
            String code=MAIL.code("email-a@example.org");
            require(!MAIL.messages.containsKey("forged@example.org"),"Client changed OTP destination");
            require("SENT".equals(latestDelivery(aId,"VERIFY")),"Accepted OTP not marked SENT");
            require(!value("SELECT TOP(1) secret_hash FROM AccountChallenges WHERE user_id="+aId).equals(code),"Stored plaintext OTP");
            status(a.post("/api/auth/email-verification/request",Map.of()),429);
            status(b.post("/api/auth/email-verification/confirm",Map.of("code",code,"userId",aId)),400);
            String wrong=code.equals("000000")?"111111":"000000";
            status(a.post("/api/auth/email-verification/confirm",Map.of("code",wrong)),400);
            require("1".equals(value("SELECT MAX(attempts) FROM AccountChallenges WHERE user_id="+aId)),"Wrong OTP attempt was not persisted");
            status(a.post("/api/auth/email-verification/confirm",Map.of("code",code)),200);
            require(Boolean.TRUE.equals(a.me().get("emailVerified")),"Correct OTP did not verify saved email");
            status(a.post("/api/auth/email-verification/confirm",Map.of("code",code)),400);
            pass("email HTTP: registration, CSRF, saved recipient, SENT status, cooldown, ownership, wrong attempt and single-use OTP");

            cooldown();status(a.post("/api/auth/email-verification/request",Map.of()),200);String oldCode=MAIL.code("email-a@example.org");
            var profile=new HashMap<String,Object>(Map.of("fullName","Email test","phone","0901234567","email","email-new@example.org","birthDate","2000-01-01","city","Hanoi","emailVerified",true,"userId",999));
            status(a.put("/api/auth/profile",profile),200);
            require(Boolean.FALSE.equals(a.me().get("emailVerified")),"Changed email retained verification");
            status(a.post("/api/auth/email-verification/confirm",Map.of("code",oldCode)),400);
            cooldown();status(a.post("/api/auth/email-verification/request",Map.of()),200);
            status(a.post("/api/auth/email-verification/confirm",Map.of("code",MAIL.code("email-new@example.org"))),200);
            status(a.put("/api/auth/profile",profile),200);require(Boolean.TRUE.equals(a.me().get("emailVerified")),"Unchanged email lost verification");
            profile.put("email","email-b@example.org");status(a.put("/api/auth/profile",profile),409);
            require("email-new@example.org".equals(a.me().get("email")),"Rejected duplicate email was partially saved");
            pass("email HTTP: changing email revokes old OTP, unchanged email preserves verification, duplicate email rolls back");

            recovery.csrf();int count=MAIL.messages.size();
            var unknown=recovery.post("/api/auth/forgot-password",Map.of("email","missing@example.org"));status(unknown,200);
            var unverified=recovery.post("/api/auth/forgot-password",Map.of("email","email-b@example.org"));status(unverified,200);
            require(unknown.body().equals(unverified.body()) && MAIL.messages.size()==count,"Recovery exposed account existence or emailed an unverified account");
            status(recovery.post("/api/auth/forgot-password",Map.of("email","email-new@example.org")),200);
            String token=MAIL.token("email-new@example.org");
            require(MAIL.messages.get("email-new@example.org").contains("https://frontend.example.org/pages/login.html?view=reset-password#token="),"Reset link has wrong public origin or token location");
            require("SENT".equals(latestDelivery(aId,"RESET")),"Accepted reset email not marked SENT");
            sql("UPDATE AccountChallenges SET expires_at=DATEADD(second,-1,SYSUTCDATETIME()) WHERE purpose='RESET'");
            status(recovery.post("/api/auth/reset-password",Map.of("token",token,"password","NewMember@123")),400);
            cooldown();status(recovery.post("/api/auth/forgot-password",Map.of("email","email-new@example.org")),200);
            String resetToken=MAIL.token("email-new@example.org");
            Client secondSession=new Client(),parallelReset=new Client();secondSession.login("emailtesta","Member@123");parallelReset.csrf();
            var resetPool=Executors.newFixedThreadPool(2);
            try {
                var one=resetPool.submit(()->recovery.post("/api/auth/reset-password",Map.of("token",resetToken,"password","NewMember@123")));
                var two=resetPool.submit(()->parallelReset.post("/api/auth/reset-password",Map.of("token",resetToken,"password","NewMember@123")));
                var statuses=List.of(one.get().statusCode(),two.get().statusCode());
                require(statuses.contains(200) && statuses.contains(400),"Reset token consumed twice concurrently");
            } finally { resetPool.shutdownNow(); }
            recovery.csrf();status(recovery.post("/api/auth/reset-password",Map.of("token",resetToken,"password","OtherMember@123")),400);
            status(a.get("/api/auth/me"),401);status(secondSession.get("/api/auth/me"),401);
            Client relogin=new Client();relogin.csrf();status(relogin.post("/api/auth/login",Map.of("username","emailtesta","password","Member@123")),401);relogin.login("emailtesta","NewMember@123");
            pass("email HTTP: private recovery response, trusted reset URL, expired/replayed/concurrent reset tokens and all-session revocation");

            cooldown();MAIL.fail=true;
            try {
                var failedReset=recovery.post("/api/auth/forgot-password",Map.of("email","email-new@example.org"));status(failedReset,200);
                require(failedReset.body().equals(unknown.body()) && "FAILED".equals(latestDelivery(aId,"RESET")),"Failed recovery falsely reported delivery or exposed account existence");
                status(b.post("/api/auth/email-verification/request",Map.of()),503);
                require("FAILED".equals(latestDelivery(bId,"VERIFY")),"Failed OTP left a usable delivery status");
            } finally { MAIL.fail=false; }
            String challengeCount=value("SELECT COUNT(*) FROM AccountChallenges");MAIL.enabled=false;
            try { status(b.post("/api/auth/email-verification/request",Map.of()),503); }
            finally { MAIL.enabled=true; }
            require(challengeCount.equals(value("SELECT COUNT(*) FROM AccountChallenges")),"Unconfigured gateway created OTP");
            pass("email HTTP: provider failure returns OTP 503, records FAILED, keeps recovery private and blocks unconfigured sends");

            cooldown();status(b.post("/api/auth/email-verification/request",Map.of()),200);String bCode=MAIL.code("email-b@example.org");
            String bWrong=bCode.equals("000000")?"111111":"000000";
            for(int i=0;i<5;i++)status(b.post("/api/auth/email-verification/confirm",Map.of("code",bWrong)),400);
            status(b.post("/api/auth/email-verification/confirm",Map.of("code",bCode)),400);
            cooldown();status(b.post("/api/auth/email-verification/request",Map.of()),200);String expired=MAIL.code("email-b@example.org");
            sql("UPDATE AccountChallenges SET expires_at=DATEADD(second,-1,SYSUTCDATETIME()) WHERE user_id="+bId+" AND purpose='VERIFY' AND used_at IS NULL");
            status(b.post("/api/auth/email-verification/confirm",Map.of("code",expired)),400);
            pass("email HTTP: five incorrect attempts lock OTP, expired OTP cannot verify email");

            cooldown();status(b.post("/api/auth/email-verification/request",Map.of()),200);String persistent=MAIL.code("email-b@example.org");
            context.close();context=start();Client afterRestart=new Client(),parallelConfirm=new Client();
            afterRestart.login("emailtestb","Member@123");parallelConfirm.login("emailtestb","Member@123");
            status(afterRestart.post("/api/auth/email-verification/request",Map.of()),429);
            var verificationPool=Executors.newFixedThreadPool(2);
            try {
                var one=verificationPool.submit(()->afterRestart.post("/api/auth/email-verification/confirm",Map.of("code",persistent)));
                var two=verificationPool.submit(()->parallelConfirm.post("/api/auth/email-verification/confirm",Map.of("code",persistent)));
                var statuses=List.of(one.get().statusCode(),two.get().statusCode());
                require(statuses.contains(200) && statuses.contains(400),"OTP consumed twice concurrently");
            } finally { verificationPool.shutdownNow(); }
            pass("email HTTP: cooldown survives server restart and concurrent OTP can be consumed only once");

            System.setProperty("EMAIL_VERIFICATION_ENABLED","false");
            int messageCount=MAIL.messages.size();challengeCount=value("SELECT COUNT(*) FROM AccountChallenges");
            status(afterRestart.post("/api/auth/email-verification/request",Map.of()),503);
            status(afterRestart.post("/api/auth/email-verification/confirm",Map.of("code",persistent)),503);
            require(Boolean.FALSE.equals(afterRestart.me().get("emailVerificationEnabled")),"Profile did not publish disabled flag");
            require(MAIL.messages.size()==messageCount && challengeCount.equals(value("SELECT COUNT(*) FROM AccountChallenges")),"Disabled feature sent email or created challenge");
            System.setProperty("EMAIL_VERIFICATION_ENABLED","true");

            try(var connection=DBConnection.getConnection();var insert=connection.prepareStatement("INSERT Users(username,password,password_scheme,full_name,role,email) VALUES('emailadmin',?,?,'Email admin','admin','email-admin@example.org')")) {
                insert.setString(1,PasswordHashing.encoder().encode("Admin@123"));insert.setString(2,PasswordHashing.SCHEME);insert.executeUpdate();
            }
            Client admin=new Client();admin.login("emailadmin","Admin@123");
            recovery.csrf(); // Server restart invalidated the anonymous client's previous session/CSRF token.
            var unverifiedAdmin=recovery.post("/api/auth/forgot-password",Map.of("email","email-admin@example.org"));status(unverifiedAdmin,200);
            require(unknown.body().equals(unverifiedAdmin.body()) && !MAIL.messages.containsKey("email-admin@example.org"),"Unverified admin recovery allowed");
            status(admin.post("/api/auth/email-verification/request",Map.of()),200);
            status(admin.post("/api/auth/email-verification/confirm",Map.of("code",MAIL.code("email-admin@example.org"))),200);
            status(recovery.post("/api/auth/forgot-password",Map.of("email","email-admin@example.org")),200);
            require(MAIL.token("email-admin@example.org")!=null,"Verified admin recovery unavailable");
            pass("email HTTP: disabled verification creates no email/challenge; admin recovery also requires verified email");
            System.out.println("Email HTTP/SQL checks passed using captured mail and an isolated database only.");
        } finally { context.close(); }
    }
}
