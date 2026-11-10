package com.homestay;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.*;
import java.nio.file.*;

@Configuration
// NOTE BACKEND: Cấu hình nơi phục vụ HTML/CSS/JavaScript và chuyển đường dẫn gốc về trang chủ.
public class WebConfig implements WebMvcConfigurer {
    private final String frontendDirectory;
    public WebConfig(@Value("${FRONTEND_DIRECTORY:}") String frontendDirectory) {this.frontendDirectory=frontendDirectory;}
    // NOTE BACKEND: Ưu tiên file AI ngoài ổ đĩa và thư mục FRONTEND_DIRECTORY nếu có; dùng tài nguyên trong JAR làm nguồn dự phòng.
    @Override public void addResourceHandlers(@NonNull ResourceHandlerRegistry registry) {
        Path aiDirectory=Path.of("ai-rag/web").toAbsolutePath().normalize();
        if(!Files.isDirectory(aiDirectory)) aiDirectory=Path.of("../ai-rag/web").toAbsolutePath().normalize();
        registry.addResourceHandler("/ai/**").addResourceLocations(aiDirectory.toUri().toString(),"classpath:/static/ai/");
        if(!frontendDirectory.isBlank()) {
            Path directory=Path.of(frontendDirectory);
            if(!directory.isAbsolute() || !Files.isDirectory(directory)) throw new IllegalStateException("FRONTEND_DIRECTORY must be an existing absolute directory.");
            registry.addResourceHandler("/**").addResourceLocations(directory.toUri().toString(),"classpath:/static/");
        } else registry.addResourceHandler("/**").addResourceLocations("classpath:/static/");
    }
    // NOTE BACKEND: Khi người dùng mở /, chuyển hướng sang /pages/landing.html.
    @Override public void addViewControllers(@NonNull ViewControllerRegistry registry) {registry.addRedirectViewController("/","/pages/landing.html");}
}
