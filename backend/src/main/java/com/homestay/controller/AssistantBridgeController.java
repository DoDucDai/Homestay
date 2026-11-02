package com.homestay.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Map;

/** Transport only. All AI, retrieval and support logic lives in ai-rag/. */
@RestController
@RequestMapping("/api/assistant")
public class AssistantBridgeController {
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    @RequestMapping(value={"/chat","/support","/support/reply","/documents"},method={RequestMethod.GET,RequestMethod.POST})
    public ResponseEntity<?> forward(HttpServletRequest request,@RequestBody(required=false) String body) {
        if(body!=null && body.length()>40000) return ResponseEntity.badRequest().body(Map.of("success",false,"message","Yêu cầu quá lớn."));
        String route=request.getServletPath().substring("/api/assistant".length());
        var builder=HttpRequest.newBuilder(URI.create("http://127.0.0.1:8091"+route)).timeout(Duration.ofSeconds(55))
            .header("Content-Type","application/json");
        for(String header:new String[]{"Cookie","X-CSRF-TOKEN"}) {
            String value=request.getHeader(header);if(value!=null)builder.header(header,value);
        }
        builder.method(request.getMethod(),body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body));
        try {
            var response=client.send(builder.build(),HttpResponse.BodyHandlers.ofString());
            return ResponseEntity.status(response.statusCode()).header("Content-Type","application/json; charset=utf-8")
                .header("Cache-Control","no-store").body(response.body());
        } catch(Exception error) {
            if(error instanceof InterruptedException)Thread.currentThread().interrupt();
            return ResponseEntity.status(503).body(Map.of("success",false,"message","Dịch vụ AI/Support chưa sẵn sàng. Vui lòng thử lại sau."));
        }
    }
}
