package com.example.apipractice.interfaces.rest.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * タスク作成リクエストDTO
 * Interface層: 外部からの入力データ
 *
 * 配置理由: HTTPリクエストの構造。Validation含む。
 */
public class CreateTaskRequest {

    @NotBlank(message = "Title is required")
    private String title;

    private String description;

    @NotBlank(message = "User ID is required")
    private String userId;

    // Getters and Setters
    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }
}
