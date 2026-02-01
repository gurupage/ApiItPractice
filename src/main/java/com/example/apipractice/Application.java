package com.example.apipractice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

/**
 * Spring Boot アプリケーションエントリポイント
 */
@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    /**
     * RestTemplate Bean定義
     * NotificationClientAdapterで使用
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
