package com.example.apipractice.interfaces.rest.dto;

import com.example.apipractice.core.domain.Task;
import com.example.apipractice.core.domain.TaskStatus;

import java.time.LocalDateTime;

/**
 * タスクレスポンスDTO
 * Interface層: 外部への出力データ
 *
 * 配置理由: HTTPレスポンスの構造。ドメインモデルをそのまま返さない。
 */
public class TaskResponse {

    private Long id;
    private String title;
    private String description;
    private TaskStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ドメインモデルからDTOへの変換
    public static TaskResponse from(Task task) {
        TaskResponse response = new TaskResponse();
        response.id = task.getId();
        response.title = task.getTitle();
        response.description = task.getDescription();
        response.status = task.getStatus();
        response.createdAt = task.getCreatedAt();
        response.updatedAt = task.getUpdatedAt();
        return response;
    }

    // Getters
    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
