package com.homestay.security;

import com.homestay.api.ApiProblem;
import org.springframework.core.env.*;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import io.github.cdimascio.dotenv.Dotenv;
import java.net.URI;
import java.nio.file.*;
import java.util.*;

@Component
// NOTE BACKEND: Đọc cấu hình email, URL frontend và secret dùng ký mã; kiểm tra trước khi sử dụng để không gửi liên kết sai.
public class AccountSettings {
    private final Environment environment;
    private final Map<String,String> localValues;
    @Autowired
    public AccountSettings(Environment environment) { this(environment,readLocalValues(environment)); }
    AccountSettings(Environment environment,Map<String,String> localValues) {
        this.environment=environment;this.localValues=Map.copyOf(localValues);
    }
    // Chỉ giá trị true bật xác thực; thiếu cấu hình hoặc gõ sai không tự bật gửi mã.
    public boolean emailVerificationEnabled() { return "true".equalsIgnoreCase(value("EMAIL_VERIFICATION_ENABLED","false")); }
    // Dùng lại dotenv-java sẵn có; hỗ trợ chạy từ root hoặc backend, không xuất secret sang system properties.
    private static Map<String,String> readLocalValues(Environment environment) {
        if(environment.acceptsProfiles(Profiles.of("prod")))return Map.of();
        for(Path directory:List.of(Path.of("."),Path.of(".."),Path.of("backend"))) {
            if(!Files.isRegularFile(directory.resolve(".env")))continue;
            try {
                var dotenv=Dotenv.configure().directory(directory.toString()).load();
                Map<String,String> values=new HashMap<>();
                for(String key:List.of("MAIL_PROVIDER","MAIL_API_KEY","MAIL_FROM","MAIL_FROM_NAME","EMAIL_VERIFICATION_ENABLED","AUTH_TOKEN_SECRET","APP_PUBLIC_URL")) {
                    String value=dotenv.get(key);if(value!=null)values.put(key,value);
                }
                return values;
            } catch(RuntimeException error) {
                // DotenvException có thể chứa dòng dữ liệu lỗi; không đưa exception/secret vào log.
                throw new IllegalStateException("Cannot read local email configuration.");
            }
        }
        return Map.of();
    }
    // NOTE BACKEND: Ưu tiên biến môi trường rồi system property; ngoài prod mới lấy thêm cấu hình Spring, sau đó dùng giá trị mặc định.
    public String value(String key,String fallback) {
        String explicit=System.getenv(key);
        if(explicit==null)explicit=System.getProperty(key);
        if(explicit==null && !environment.acceptsProfiles(Profiles.of("prod")))explicit=environment.getProperty(java.util.Objects.requireNonNull(key));
        if(explicit==null && !environment.acceptsProfiles(Profiles.of("prod")))explicit=localValues.get(key);
        return explicit==null || explicit.isBlank()?fallback:explicit.trim();
    }
    // NOTE BACKEND: Kiểm tra URL gốc cho liên kết đặt lại mật khẩu; prod cần HTTPS, dev chỉ cho HTTP ở localhost/127.0.0.1.
    public String publicUrl() {
        String url=value("APP_PUBLIC_URL",environment.acceptsProfiles(Profiles.of("prod"))?"":"http://localhost:"+environment.getProperty("server.port","8080"));
        try {
            URI uri=URI.create(url);
            if(uri.getHost()==null || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null
                    || !(uri.getPath().isEmpty() || uri.getPath().equals("/"))
                    || !(uri.getScheme().equals("https") || !environment.acceptsProfiles(Profiles.of("prod")) && uri.getScheme().equals("http") && java.util.Set.of("localhost","127.0.0.1").contains(uri.getHost())))throw new IllegalArgumentException();
            return url.replaceAll("/+$","");
        } catch(Exception e) { throw unavailable(); }
    }
    // NOTE BACKEND: Yêu cầu secret ít nhất 32 ký tự trước khi ký hoặc kiểm tra mã, không sinh secret tạm thay cho cấu hình thiếu.
    public String tokenSecret() {
        String secret=value("AUTH_TOKEN_SECRET","");
        if(secret.length()<32)throw unavailable();
        return secret;
    }
    // NOTE BACKEND: Tạo lỗi 503 dùng chung khi chức năng email chưa đủ cấu hình hoặc dịch vụ không sẵn sàng.
    public static ApiProblem unavailable() { return new ApiProblem(503,"EMAIL_UNAVAILABLE","Chức năng email tạm chưa sẵn sàng. Vui lòng thử lại sau hoặc liên hệ quản lý."); }
}
