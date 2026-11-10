package com.homestay.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.homestay.api.ApiProblem;
import org.springframework.core.env.*;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import javax.net.ssl.SSLSession;

/** No SQL, application startup or external email: production gateway with an in-memory HTTP transport. */
public final class EmailDeliveryChecks {
    private static final ObjectMapper JSON=new ObjectMapper();
    private static final String FAKE_KEY="test-only-credential-never-use-for-real-email";
    private static final Map<String,String> CONFIG=Map.of(
        "MAIL_PROVIDER","brevo","MAIL_API_KEY",FAKE_KEY,"MAIL_FROM","sender@example.org",
        "MAIL_FROM_NAME","DucDai","AUTH_TOKEN_SECRET","test-only-signing-secret-at-least-32-characters",
        "APP_PUBLIC_URL","https://frontend.example.org","EMAIL_VERIFICATION_ENABLED","true");
    private static void require(boolean condition,String description) { if(!condition)throw new AssertionError(description); }
    private static AccountSettings settings(Map<String,String> values) {
        var environment=new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("email-test",new HashMap<>(values)));
        return new AccountSettings(environment,Map.of());
    }
    private static void unavailable(Runnable action) {
        try { action.run();throw new AssertionError("Failed email reported as accepted"); }
        catch(ApiProblem problem) {
            require(problem.status==503 && problem.code.equals("EMAIL_UNAVAILABLE"),"Wrong public failure status");
            require(!problem.getMessage().contains(FAKE_KEY),"Public error exposed credential");
        }
    }
    private record Response(int statusCode,String body) implements HttpResponse<String> {
        public HttpRequest request() { return null; }
        public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }
        public HttpHeaders headers() { return HttpHeaders.of(Map.of(),(a,b)->true); }
        public Optional<SSLSession> sslSession() { return Optional.empty(); }
        public URI uri() { return URI.create("https://api.brevo.com/v3/smtp/email"); }
        public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
    }
    private static String body(HttpRequest request) throws Exception {
        var result=new CompletableFuture<String>();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>() {
            final ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
            public void onNext(ByteBuffer buffer) { byte[] part=new byte[buffer.remaining()];buffer.get(part);bytes.writeBytes(part); }
            public void onError(Throwable error) { result.completeExceptionally(error); }
            public void onComplete() { result.complete(bytes.toString(StandardCharsets.UTF_8)); }
        });
        return result.get(2,TimeUnit.SECONDS);
    }
    public static void main(String[] args) throws Exception {
        if(args.length>0) { probe(args[0]);return; }
        var log=new ListAppender<ILoggingEvent>();log.start();
        var logger=(Logger)LoggerFactory.getLogger(HttpMailGateway.class);logger.addAppender(log);
        try {
            HttpRequest[] captured=new HttpRequest[1];
            var gateway=new HttpMailGateway(settings(CONFIG),JSON,request->{captured[0]=request;return new Response(201,"{\"messageId\":\"<accepted@example.org>\"}");});
            require(gateway.configured(),"Valid Brevo configuration rejected");
            gateway.send("recipient@example.org","Tiếng Việt","Mã kiểm tra trong bộ nhớ");
            var request=captured[0];var payload=JSON.readTree(body(request));
            require(request.uri().toString().equals("https://api.brevo.com/v3/smtp/email") && request.method().equals("POST"),"Wrong Brevo HTTPS destination");
            require(request.timeout().orElseThrow().equals(Duration.ofSeconds(12)),"Mail request has no bounded timeout");
            require(request.headers().firstValue("api-key").orElseThrow().equals(FAKE_KEY),"Missing Brevo authentication");
            require(payload.path("sender").path("name").asText().equals("DucDai") && payload.path("sender").path("email").asText().equals("sender@example.org"),"Wrong sender");
            require(payload.path("to").get(0).path("email").asText().equals("recipient@example.org") && payload.path("subject").asText().equals("Tiếng Việt"),"Recipient or UTF-8 content changed");
            System.out.println("PASS Brevo HTTPS payload, sender, authentication, UTF-8 and request timeout");

            for(int status:List.of(301,400,401,403,429,500,503)) {
                var rejected=new HttpMailGateway(settings(CONFIG),JSON,r->new Response(status,"{\"messageId\":\"misleading\",\"message\":\""+FAKE_KEY+"\"}"));
                unavailable(()->rejected.send("recipient@example.org","subject","text"));
            }
            for(String response:List.of("","{}","null","{\"messageId\":\"\"}","{\"messageId\":123}","invalid JSON "+FAKE_KEY)) {
                var invalid=new HttpMailGateway(settings(CONFIG),JSON,r->new Response(201,response));
                unavailable(()->invalid.send("recipient@example.org","subject","text"));
            }
            var timedOut=new HttpMailGateway(settings(CONFIG),JSON,r->{throw new HttpTimeoutException(FAKE_KEY);});
            unavailable(()->timedOut.send("recipient@example.org","subject","text"));
            var disconnected=new HttpMailGateway(settings(CONFIG),JSON,r->{throw new IOException(FAKE_KEY);});
            unavailable(()->disconnected.send("recipient@example.org","subject","text"));
            var interrupted=new HttpMailGateway(settings(CONFIG),JSON,r->{throw new InterruptedException(FAKE_KEY);});
            try {
                unavailable(()->interrupted.send("recipient@example.org","subject","text"));
                require(Thread.currentThread().isInterrupted(),"Mail lost the thread interrupt");
            } finally { Thread.interrupted(); }
            require(log.list.stream().noneMatch(e->e.getFormattedMessage().contains(FAKE_KEY) || e.getThrowableProxy()!=null),"Mail failure log exposed response/exception data");
            System.out.println("PASS rejected HTTP, invalid acceptance receipts, timeout, network failure, interrupt and secret-free errors");

            for(String missing:List.of("MAIL_PROVIDER","MAIL_API_KEY","MAIL_FROM")) {
                var values=new HashMap<>(CONFIG);values.remove(missing);
                var disabled=new HttpMailGateway(settings(values),JSON,r->{throw new AssertionError("Unconfigured mail reached HTTP");});
                require(!disabled.configured(),"Incomplete email configuration accepted");
                unavailable(()->disabled.send("recipient@example.org","subject","text"));
            }
            var resend=new HashMap<>(CONFIG);resend.put("MAIL_PROVIDER","resend");
            new HttpMailGateway(settings(resend),JSON,r->{
                require(r.uri().toString().equals("https://api.resend.com/emails"),"Resend destination changed");
                require(r.headers().firstValue("Authorization").orElseThrow().equals("Bearer "+FAKE_KEY),"Resend authentication changed");
                return new Response(200,"{\"id\":\"resend-acceptance\"}");
            }).send("recipient@example.org","subject","text");
            require(!settings(Map.of("EMAIL_VERIFICATION_ENABLED","typo")).emailVerificationEnabled(),"Invalid flag enabled verification");
            var shortSecret=new HashMap<>(CONFIG);shortSecret.put("AUTH_TOKEN_SECRET","short");unavailable(()->settings(shortSecret).tokenSecret());
            var invalidUrl=new HashMap<>(CONFIG);invalidUrl.put("APP_PUBLIC_URL","http://external.example.org");unavailable(()->settings(invalidUrl).publicUrl());
            System.out.println("PASS missing configuration blocks sending, Resend compatibility and account configuration guards");
            dotenvProcesses();
        } finally { logger.detachAppender(log);log.stop(); }
        System.out.println("Email checks passed; no real emails or business data were used.");
    }
    private static void probe(String mode) {
        var environment=new StandardEnvironment();if(mode.startsWith("prod"))environment.setActiveProfiles("prod");
        var settings=new AccountSettings(environment);
        if(mode.equals("prod-ignore-file")) {
            require(!new HttpMailGateway(settings,JSON).configured(),"Production read local .env credentials");
            unavailable(settings::tokenSecret);
        } else {
            require(settings.value("MAIL_FROM_NAME","").equals(mode.equals("prod-env")?"Environment sender":"Quoted sender"),"Wrong configuration precedence or dotenv search path");
            require(settings.tokenSecret().length()>=32,"Secret was not loaded");
            require(settings.publicUrl().equals("https://frontend.example.org"),"Wrong reset URL");
            require(new HttpMailGateway(settings,JSON).configured(),"Mail configuration was not loaded");
        }
    }
    private static void dotenvProcesses() throws Exception {
        Path root=Files.createTempDirectory(Path.of("target").toAbsolutePath(),"email-config-");
        Path backend=Files.createDirectory(root.resolve("backend"));
        var values=new HashMap<>(CONFIG);values.put("MAIL_FROM_NAME","\"Quoted sender\"");
        StringBuilder fixture=new StringBuilder();values.forEach((k,v)->fixture.append(k).append('=').append(v).append('\n'));
        Files.writeString(root.resolve(".env"),fixture);
        try {
            String classpath=Arrays.stream(System.getProperty("java.class.path").split(java.util.regex.Pattern.quote(File.pathSeparator)))
                .map(p->Path.of(p).toAbsolutePath().toString()).collect(java.util.stream.Collectors.joining(File.pathSeparator));
            for(String mode:List.of("dev-root","dev-backend","prod-ignore-file","prod-env")) {
                var child=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-cp",classpath,EmailDeliveryChecks.class.getName(),mode)
                    .directory((mode.equals("dev-backend")?backend:root).toFile()).inheritIO();
                for(String key:CONFIG.keySet())child.environment().remove(key);
                if(mode.equals("prod-env")) { child.environment().putAll(CONFIG);child.environment().put("MAIL_FROM_NAME","Environment sender"); }
                var process=child.start();
                if(!process.waitFor(20,TimeUnit.SECONDS)) { process.destroyForcibly();throw new AssertionError("Email configuration probe timed out"); }
                require(process.exitValue()==0,"Email configuration probe failed: "+mode);
            }
        } finally { Files.deleteIfExists(root.resolve(".env"));Files.deleteIfExists(backend);Files.deleteIfExists(root); }
        System.out.println("PASS root/backend dotenv loading with quotes; production ignores file and uses real environment variables");
    }
}
