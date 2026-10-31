package com.homestay;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;
import java.nio.file.*;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final String frontendDirectory;
    public WebConfig(@Value("${FRONTEND_DIRECTORY:}") String frontendDirectory) {this.frontendDirectory=frontendDirectory;}
    @Override public void addResourceHandlers(ResourceHandlerRegistry registry) {
        if(!frontendDirectory.isBlank()) {
            Path directory=Path.of(frontendDirectory);
            if(!directory.isAbsolute() || !Files.isDirectory(directory)) throw new IllegalStateException("FRONTEND_DIRECTORY must be an existing absolute directory.");
            registry.addResourceHandler("/**").addResourceLocations(directory.toUri().toString(),"classpath:/static/");
        } else registry.addResourceHandler("/**").addResourceLocations("classpath:/static/");
    }
    @Override public void addViewControllers(ViewControllerRegistry registry) {registry.addRedirectViewController("/","/pages/login.html");}
}
