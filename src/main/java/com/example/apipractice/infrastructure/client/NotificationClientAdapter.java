package com.example.apipractice.infrastructure.client;

import com.example.apipractice.usecase.port.NotificationClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * 外部通知APIクライアントの実装
 * Infrastructure層: HTTP通信の技術詳細
 *
 * 配置理由: RestTemplateを使った外部API呼び出しはインフラ技術。
 * IT時にWireMockで外部APIをモック化できる。
 */
@Component
public class NotificationClientAdapter implements NotificationClient {

    private final RestTemplate restTemplate;
    private final String notificationApiUrl;

    public NotificationClientAdapter(
            RestTemplate restTemplate,
            @Value("${notification.api.url:http://localhost:8081/notifications}") String notificationApiUrl) {
        this.restTemplate = restTemplate;
        this.notificationApiUrl = notificationApiUrl;
    }

    @Override
    public void notifyTaskCreated(Long taskId, String title) {
        Map<String, Object> payload = Map.of(
                "taskId", taskId,
                "title", title,
                "event", "TASK_CREATED"
        );

        try {
            restTemplate.postForEntity(notificationApiUrl, payload, Void.class);
        } catch (Exception e) {
            // 教材用: 外部API失敗は警告のみ（タスク作成は成功させる）
            System.err.println("Failed to send notification: " + e.getMessage());
        }
    }
}
