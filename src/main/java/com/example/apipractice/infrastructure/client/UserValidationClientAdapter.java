package com.example.apipractice.infrastructure.client;

import com.example.apipractice.usecase.port.UserValidationClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

/**
 * ユーザー存在確認APIクライアントの実装
 * Infrastructure層: HTTP通信の技術詳細
 *
 * 配置理由: RestTemplateを使った外部API呼び出しはインフラ技術。
 * IT時にWireMockで外部APIをモック化できる。
 *
 * 外部API仕様:
 * - GET /api/users/{userId}
 * - 200 OK: ユーザー存在
 * - 404 Not Found: ユーザー不在
 * - その他: API呼び出しエラー
 */
@Component
public class UserValidationClientAdapter implements UserValidationClient {

    private final RestTemplate restTemplate;
    private final String userApiUrl;

    public UserValidationClientAdapter(
            RestTemplate restTemplate,
            @Value("${user.validation.api.url:http://localhost:8082/api/users}") String userApiUrl) {
        this.restTemplate = restTemplate;
        this.userApiUrl = userApiUrl;
    }

    @Override
    public boolean existsUser(String userId) {
        try {
            String url = userApiUrl + "/" + userId;
            ResponseEntity<UserResponse> response = restTemplate.getForEntity(url, UserResponse.class);

            // 200 OK: ユーザー存在
            return response.getStatusCode() == HttpStatus.OK && response.getBody() != null;

        } catch (HttpClientErrorException.NotFound e) {
            // 404 Not Found: ユーザー不在
            return false;

        } catch (Exception e) {
            // その他のエラー: 外部API障害
            throw new UserValidationException(
                    "Failed to validate user: " + userId,
                    e
            );
        }
    }

    /**
     * ユーザー情報レスポンス（外部APIのレスポンス形式）
     */
    static class UserResponse {
        private String userId;
        private String username;
        private boolean active;

        // Getters and Setters
        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public boolean isActive() {
            return active;
        }

        public void setActive(boolean active) {
            this.active = active;
        }
    }
}
