package com.hx.mcpsidecar.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MyMvcConfig implements WebMvcConfigurer {
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")
                    .allowedOriginPatterns("*")      // 用 pattern 支持通配符
                    .allowedMethods("*")
                    .allowedHeaders("*")
                    .allowCredentials(true)         // 允许传凭证
                    .maxAge(3600);
            }
        };
    }
}
