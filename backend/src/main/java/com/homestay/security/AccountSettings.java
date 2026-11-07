package com.homestay.security;

import com.homestay.api.ApiProblem;
import org.springframework.core.env.*;
import org.springframework.stereotype.Component;
import java.net.URI;

@Component
public class AccountSettings {
    private final Environment environment;
    public AccountSettings(Environment environment) { this.environment=environment; }
    // Temporarily disabled for the demo until the mail provider is ready.
    public boolean emailVerificationEnabled() { return !"false".equalsIgnoreCase(value("EMAIL_VERIFICATION_ENABLED","false")); }
    public String value(String key,String fallback) {
        String explicit=System.getenv(key);
        if(explicit==null)explicit=System.getProperty(key);
        if(explicit==null && !environment.acceptsProfiles(Profiles.of("prod")))explicit=environment.getProperty(key);
        return explicit==null || explicit.isBlank()?fallback:explicit.trim();
    }
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
    public String tokenSecret() {
        String secret=value("AUTH_TOKEN_SECRET","");
        if(secret.length()<32)throw unavailable();
        return secret;
    }
    public static ApiProblem unavailable() { return new ApiProblem(503,"EMAIL_UNAVAILABLE","Chức năng email tạm chưa sẵn sàng. Vui lòng thử lại sau hoặc liên hệ quản lý."); }
}
