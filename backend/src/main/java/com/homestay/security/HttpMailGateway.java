package com.homestay.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import java.io.IOException;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

/** HTTPS transport only; no SMTP, secret logging, or client-provided provider URLs. */
@Service
// NOTE BACKEND: Gửi email qua API HTTPS của Brevo/Resend; khóa API chỉ đọc ở backend và không ghi vào thông báo gửi về frontend.
public class HttpMailGateway implements MailGateway {
    private final AccountSettings settings;
    private final ObjectMapper json;
    private static final HttpClient CLIENT=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4))
        .followRedirects(HttpClient.Redirect.NEVER).build();
    @FunctionalInterface
    interface Transport { HttpResponse<String> send(HttpRequest request) throws IOException,InterruptedException; }
    private final Transport transport;
    @Autowired
    public HttpMailGateway(AccountSettings settings,ObjectMapper json) {
        this(settings,json,request->CLIENT.send(request,HttpResponse.BodyHandlers.ofString()));
    }
    HttpMailGateway(AccountSettings settings,ObjectMapper json,Transport transport) {
        this.settings=settings;this.json=json;this.transport=transport;
    }
    // NOTE BACKEND: Kiểm tra nhà cung cấp được hỗ trợ, khóa API không rỗng và địa chỉ người gửi đúng định dạng.
    @Override public boolean configured() {
        return Set.of("brevo","resend").contains(provider())
            && !settings.value("MAIL_API_KEY","").isBlank() && AccountPolicy.email(settings.value("MAIL_FROM",""));
    }
    private String provider() { return settings.value("MAIL_PROVIDER","disabled").toLowerCase(Locale.ROOT); }
    // NOTE BACKEND: Tạo JSON và header tương ứng nhà cung cấp, gửi HTTP có thời hạn chờ; lỗi mạng hoặc provider từ chối sẽ thành lỗi email tạm chưa sẵn sàng.
    @Override public void send(String email,String subject,String text) {
        if(!configured())throw AccountSettings.unavailable();
        String provider=provider(),from=settings.value("MAIL_FROM","");
        String name=settings.value("MAIL_FROM_NAME","Homestay");
        try {
            Map<String,Object> body=provider.equals("brevo")
                ? Map.of("sender",Map.of("email",from,"name",name),"to",List.of(Map.of("email",email)),"subject",subject,"textContent",text)
                : Map.of("from",name+" <"+from+">","to",List.of(email),"subject",subject,"text",text);
            var request=HttpRequest.newBuilder(URI.create(provider.equals("brevo")?"https://api.brevo.com/v3/smtp/email":"https://api.resend.com/emails"))
                .timeout(Duration.ofSeconds(12)).header("Content-Type","application/json")
                .header(provider.equals("brevo")?"api-key":"Authorization",(provider.equals("brevo")?"":"Bearer ")+settings.value("MAIL_API_KEY",""))
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
            var result=transport.send(request);
            if(result.statusCode()<200 || result.statusCode()>=300) {
                org.slf4j.LoggerFactory.getLogger(HttpMailGateway.class).warn("Email provider {} rejected request (HTTP {})",provider,result.statusCode());
                throw AccountSettings.unavailable();
            }
            // HTTP 2xx chưa đủ: provider phải trả ID xác nhận đã nhận thư, không phải đã vào Inbox.
            var receipt=json.readTree(result.body());
            var messageId=receipt==null?null:receipt.get(provider.equals("brevo")?"messageId":"id");
            if(messageId==null || !messageId.isTextual() || messageId.asText().isBlank()) {
                org.slf4j.LoggerFactory.getLogger(HttpMailGateway.class).warn("Email provider {} returned no acceptance ID",provider);
                throw AccountSettings.unavailable();
            }
        } catch(InterruptedException e) { Thread.currentThread().interrupt();throw AccountSettings.unavailable(); }
        catch(IOException | IllegalArgumentException e) {
            org.slf4j.LoggerFactory.getLogger(HttpMailGateway.class).warn("Email provider {} transport or response failed",provider);
            throw AccountSettings.unavailable();
        }
    }
}
