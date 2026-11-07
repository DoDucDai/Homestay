package com.homestay.security;

import com.homestay.api.*;
import org.springframework.stereotype.Service;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.sql.*;
import java.util.*;

@Service
public class AccountEmailActions {
    private final MailGateway mail;
    private final AccountSettings settings;
    private final SecureRandom random=new SecureRandom();
    private record Challenge(String id,String email,String secret) {}
    public AccountEmailActions(MailGateway mail,AccountSettings settings) { this.mail=mail;this.settings=settings; }
    private void ready() { settings.tokenSecret();settings.publicUrl();if(!mail.configured())throw AccountSettings.unavailable(); }
    private String digest(String purpose,String id,String secret) {
        try {
            var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(settings.tokenSecret().getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((purpose+":"+id.toLowerCase(Locale.ROOT)+":"+secret).getBytes(StandardCharsets.UTF_8)));
        } catch(GeneralSecurityException e) { throw new IllegalStateException(e); }
    }
    private static boolean same(String a,String b) { return MessageDigest.isEqual(a.getBytes(StandardCharsets.US_ASCII),b.getBytes(StandardCharsets.US_ASCII)); }
    private Challenge create(Connection connection,int userId,String purpose,String secret,int minutes,Integer expectedVersion) throws SQLException {
        String id=UUID.randomUUID().toString(),email;
        int emailVersion,authVersion;
        try(var statement=connection.prepareStatement("SELECT email,email_version,auth_version FROM Users WITH(UPDLOCK,HOLDLOCK) WHERE user_id=?")) {
            statement.setInt(1,userId);
            try(var rows=statement.executeQuery()) {
                if(!rows.next())throw ApiErrors.notFound("Không tìm thấy tài khoản.");
                email=Objects.toString(rows.getString(1),"");emailVersion=rows.getInt(2);authVersion=rows.getInt(3);
                if(expectedVersion!=null && authVersion!=expectedVersion)throw new ApiProblem(401,"UNAUTHENTICATED","Vui lòng đăng nhập lại.");
            }
        }
        if(!AccountPolicy.email(email))throw ApiErrors.invalid("Cập nhật email hợp lệ trong thông tin cá nhân trước.");
        email=AccountStore.normalize(email);
        try(var statement=connection.prepareStatement("SELECT COUNT(*) FROM Users WHERE LOWER(LTRIM(RTRIM(email)))=?")) {
            statement.setString(1,email);try(var rows=statement.executeQuery()) { rows.next();if(rows.getInt(1)!=1)throw new ApiProblem(409,"AMBIGUOUS_EMAIL","Email này đang gắn với nhiều tài khoản cũ. Vui lòng đổi email hoặc liên hệ quản lý."); }
        }
        try(var statement=connection.prepareStatement("SELECT COUNT(*) FROM AccountChallenges WHERE user_id=? AND purpose=? AND created_at>DATEADD(second,-60,SYSUTCDATETIME())")) {
            statement.setInt(1,userId);statement.setString(2,purpose);
            try(var rows=statement.executeQuery()) { rows.next();if(rows.getInt(1)>0)throw new ApiProblem(429,"RESEND_COOLDOWN","Vui lòng chờ 60 giây trước khi gửi lại."); }
        }
        try(var statement=connection.prepareStatement("UPDATE AccountChallenges SET used_at=SYSUTCDATETIME() WHERE user_id=? AND purpose=? AND used_at IS NULL; INSERT AccountChallenges(challenge_id,user_id,purpose,email,email_version,auth_version,secret_hash,expires_at) VALUES(?,?,?,?,?,?,?,DATEADD(minute,?,SYSUTCDATETIME()))")) {
            statement.setInt(1,userId);statement.setString(2,purpose);statement.setString(3,id);statement.setInt(4,userId);statement.setString(5,purpose);statement.setString(6,email);statement.setInt(7,emailVersion);statement.setInt(8,authVersion);statement.setString(9,digest(purpose,id,secret));statement.setInt(10,minutes);statement.executeUpdate();
        }
        return new Challenge(id,email,secret);
    }
    private void deliver(Challenge challenge,String subject,String text) {
        String status="FAILED";
        try { mail.send(challenge.email(),subject,text);status="SENT"; }
        finally {
            String outcome=status;
            AccountStore.transaction(connection->{
                try(var statement=connection.prepareStatement("UPDATE AccountChallenges SET delivery_status=? WHERE challenge_id=?")) {
                    statement.setString(1,outcome);statement.setString(2,challenge.id());statement.executeUpdate();return null;
                }
            });
        }
    }
    public Map<String,Object> requestVerification(AppUser user,String ip) {
        requireVerificationEnabled();
        ready();AccountStore.limit("verify-user:"+user.userId(),5,600);AccountStore.limit("verify-ip:"+ip,30,3600);
        String code=String.format(Locale.ROOT,"%06d",random.nextInt(1000000));
        Challenge challenge=AccountStore.transaction(connection->create(connection,user.userId(),"VERIFY",code,10,user.authVersion()));
        deliver(challenge,"Homestay — Mã xác thực email", "Mã xác thực email của bạn: "+code+"\nMã hết hạn sau 10 phút và chỉ dùng một lần. Không chia sẻ mã.\nNếu bạn không yêu cầu, hãy bỏ qua thư này.");
        return Map.of("success",true,"message","Đã gửi mã xác thực. Kiểm tra hộp thư và thư rác.","expiresIn",600,"retryAfter",60);
    }
    public Map<String,Object> confirmVerification(AppUser user,String code,String ip) {
        requireVerificationEnabled();
        settings.tokenSecret();AccountStore.limit("verify-attempt:"+user.userId()+":"+ip,20,600);
        if(!code.matches("[0-9]{6}"))throw ApiErrors.invalid("Mã xác thực gồm đúng 6 chữ số.");
        boolean valid=AccountStore.transaction(connection->{
            try(var lock=connection.prepareStatement("SELECT auth_version FROM Users WITH(UPDLOCK,HOLDLOCK) WHERE user_id=?")) {
                lock.setInt(1,user.userId());try(var rows=lock.executeQuery()) { if(!rows.next() || rows.getInt(1)!=user.authVersion())return false; }
            }
            try(var statement=connection.prepareStatement("SELECT TOP(1) c.challenge_id,c.secret_hash FROM AccountChallenges c WITH(UPDLOCK,HOLDLOCK) JOIN Users u ON u.user_id=c.user_id WHERE c.user_id=? AND c.purpose='VERIFY' AND c.used_at IS NULL AND c.delivery_status='SENT' AND c.attempts<5 AND c.expires_at>SYSUTCDATETIME() AND c.email_version=u.email_version AND c.auth_version=u.auth_version AND c.email=LOWER(LTRIM(RTRIM(u.email))) ORDER BY c.created_at DESC")) {
                statement.setInt(1,user.userId());
                String id,expected;
                try(var rows=statement.executeQuery()) { if(!rows.next())return false;id=rows.getString(1);expected=rows.getString(2); }
                boolean correct=same(expected,digest("VERIFY",id,code));
                try(var update=connection.prepareStatement("UPDATE AccountChallenges SET attempts=attempts+1,used_at=CASE WHEN ?=1 OR attempts>=4 THEN SYSUTCDATETIME() ELSE NULL END WHERE challenge_id=?")) {
                    update.setBoolean(1,correct);update.setString(2,id);update.executeUpdate();
                }
                if(correct)try(var update=connection.prepareStatement("UPDATE Users SET email_verified_at=SYSUTCDATETIME() WHERE user_id=?")) { update.setInt(1,user.userId());update.executeUpdate(); }
                return correct;
            }
        });
        if(!valid)throw new ApiProblem(400,"INVALID_VERIFICATION_CODE","Mã không đúng, đã dùng hoặc hết hạn. Vui lòng kiểm tra hoặc gửi mã mới.");
        return Map.of("success",true,"message","Email đã được xác thực.");
    }
    private void requireVerificationEnabled() {
        if(!settings.emailVerificationEnabled())throw new ApiProblem(503,"EMAIL_VERIFICATION_DISABLED","Xác thực email đang tạm tắt. Bạn có thể đặt phòng mà không cần mã xác thực.");
    }
    public Map<String,Object> forgotPassword(String email,String ip) {
        if(!AccountPolicy.email(email))throw ApiErrors.invalid("Nhập email đăng ký hợp lệ.");
        ready();email=AccountStore.normalize(email);
        AccountStore.limit("reset-ip:"+ip,20,900);AccountStore.limit("reset-email:"+email,5,900);
        final String target=email;
        Integer userId=AccountStore.transaction(connection->{
            try(var statement=connection.prepareStatement("SELECT MIN(user_id) FROM Users WHERE LOWER(LTRIM(RTRIM(email)))=? AND role IN('admin','customer') AND email_verified_at IS NOT NULL AND password_scheme=? HAVING COUNT(*)=1")) {
                statement.setString(1,target);statement.setString(2,PasswordHashing.SCHEME);
                try(var rows=statement.executeQuery()) { return rows.next()?rows.getInt(1):null; }
            }
        });
        if(userId!=null) {
            byte[] bytes=new byte[32];random.nextBytes(bytes);String secret=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            try {
                Challenge challenge=AccountStore.transaction(connection->create(connection,userId,"RESET",secret,15,null));
                String link=settings.publicUrl()+"/pages/login.html?view=reset-password#token="+challenge.id()+"."+secret;
                deliver(challenge,"Homestay — Đặt lại mật khẩu","Bạn đã yêu cầu đặt lại mật khẩu. Mở liên kết sau trong 15 phút:\n"+link+"\nLiên kết chỉ dùng một lần. Nếu không phải bạn yêu cầu, hãy bỏ qua thư này.");
            } catch(ApiProblem problem) {
                // The public response must not reveal account existence or provider delivery details.
                if(!Set.of("RESEND_COOLDOWN","EMAIL_UNAVAILABLE","AMBIGUOUS_EMAIL").contains(problem.code))throw problem;
            }
        }
        return Map.of("success",true,"message","Nếu email có tài khoản phù hợp, hướng dẫn đặt lại mật khẩu sẽ được gửi. Vui lòng kiểm tra cả thư rác.");
    }
    public Map<String,Object> resetPassword(String token,String password) {
        settings.tokenSecret();
        if(!AccountPolicy.password(password) || !token.matches("[0-9a-fA-F-]{36}\\.[A-Za-z0-9_-]{43}"))throw invalidReset();
        String[] parts=token.split("\\.");
        try { UUID.fromString(parts[0]); } catch(IllegalArgumentException e) { throw invalidReset(); }
        String newHash=PasswordHashing.encoder().encode(password);
        boolean valid=AccountStore.transaction(connection->{
            int userId;
            try(var statement=connection.prepareStatement("SELECT user_id FROM AccountChallenges WHERE challenge_id=? AND purpose='RESET'")) {
                statement.setString(1,parts[0]);try(var rows=statement.executeQuery()) { if(!rows.next())return false;userId=rows.getInt(1); }
            }
            try(var statement=connection.prepareStatement("SELECT user_id FROM Users WITH(UPDLOCK,HOLDLOCK) WHERE user_id=?")) {
                statement.setInt(1,userId);try(var rows=statement.executeQuery()) { if(!rows.next())return false; }
            }
            try(var statement=connection.prepareStatement("SELECT c.secret_hash FROM AccountChallenges c WITH(UPDLOCK,HOLDLOCK) JOIN Users u ON c.user_id=u.user_id WHERE c.challenge_id=? AND c.purpose='RESET' AND c.used_at IS NULL AND c.delivery_status='SENT' AND c.expires_at>SYSUTCDATETIME() AND c.email_version=u.email_version AND c.auth_version=u.auth_version AND c.email=LOWER(LTRIM(RTRIM(u.email))) AND u.role IN('admin','customer') AND u.email_verified_at IS NOT NULL")) {
                statement.setString(1,parts[0]);try(var rows=statement.executeQuery()) { if(!rows.next() || !same(rows.getString(1),digest("RESET",parts[0],parts[1])))return false; }
            }
            try(var statement=connection.prepareStatement("UPDATE Users SET password=?,password_scheme=?,auth_version=auth_version+1 WHERE user_id=?; UPDATE AccountChallenges SET used_at=SYSUTCDATETIME() WHERE user_id=? AND used_at IS NULL")) {
                statement.setString(1,newHash);statement.setString(2,PasswordHashing.SCHEME);statement.setInt(3,userId);statement.setInt(4,userId);statement.executeUpdate();
            }
            return true;
        });
        if(!valid)throw invalidReset();
        return Map.of("success",true,"message","Đã đặt lại mật khẩu. Vui lòng đăng nhập bằng mật khẩu mới.");
    }
    private static ApiProblem invalidReset() { return new ApiProblem(400,"INVALID_RESET_TOKEN","Liên kết không hợp lệ, đã dùng hoặc hết hạn; mật khẩu mới cần đúng chính sách. Vui lòng yêu cầu lại nếu cần."); }
}
