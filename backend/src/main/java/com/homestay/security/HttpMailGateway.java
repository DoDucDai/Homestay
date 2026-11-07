package com.homestay.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

/** HTTPS transport only; no SMTP, secret logging, or client-provided provider URLs. */
@Service
public class HttpMailGateway implements MailGateway {
    private final AccountSettings settings;
    private final ObjectMapper json;
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
    public HttpMailGateway(AccountSettings settings,ObjectMapper json) { this.settings=settings;this.json=json; }
    @Override public boolean configured() {
        return Set.of("brevo","resend").contains(settings.value("MAIL_PROVIDER","disabled"))
            && !settings.value("MAIL_API_KEY","").isBlank() && AccountPolicy.email(settings.value("MAIL_FROM",""));
    }
    @Override public void send(String email,String subject,String text) {
        if(!configured())throw AccountSettings.unavailable();
        String provider=settings.value("MAIL_PROVIDER","disabled"),from=settings.value("MAIL_FROM","");
        try {
            Map<String,Object> body=provider.equals("brevo")
                ? Map.of("sender",Map.of("email",from,"name","Homestay"),"to",List.of(Map.of("email",email)),"subject",subject,"textContent",text)
                : Map.of("from","Homestay <"+from+">","to",List.of(email),"subject",subject,"text",text);
            var request=HttpRequest.newBuilder(URI.create(provider.equals("brevo")?"https://api.brevo.com/v3/smtp/email":"https://api.resend.com/emails"))
                .timeout(Duration.ofSeconds(12)).header("Content-Type","application/json")
                .header(provider.equals("brevo")?"api-key":"Authorization",(provider.equals("brevo")?"":"Bearer ")+settings.value("MAIL_API_KEY",""))
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
            var result=client.send(request,HttpResponse.BodyHandlers.discarding());
            if(result.statusCode()<200 || result.statusCode()>=300) {
                org.slf4j.LoggerFactory.getLogger(HttpMailGateway.class).warn("Email provider {} rejected request (HTTP {})",provider,result.statusCode());
                throw AccountSettings.unavailable();
            }
        } catch(InterruptedException e) { Thread.currentThread().interrupt();throw AccountSettings.unavailable(); }
        catch(Exception e) { throw AccountSettings.unavailable(); }
    }
}
