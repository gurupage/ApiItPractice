package com.example.apipractice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * HTTPクライアント設定
 * 外部API呼び出し用のRestTemplateを提供
 */
@Configuration
public class HttpClientConfig {

    /**
     * RestTemplateのBean定義
     * 外部HTTP APIクライアント（NotificationClient、UserValidationClient）で使用
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
