package com.homestay;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import java.io.File;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        File frontendDir = new File("../frontend");
        if (!frontendDir.exists()) {
            frontendDir = new File("frontend");
        }
        String frontendPath = "file:" + frontendDir.getAbsolutePath().replace("\\", "/") + "/";
        registry.addResourceHandler("/**")
                .addResourceLocations(frontendPath, "classpath:/static/", "classpath:/public/");
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/", "/pages/login.html");
    }
}
