package com.example.apipractice.core.domain;

import java.time.LocalDateTime;

/**
 * Taskドメインモデル
 * Core層: 依存なし、純粋なビジネスルール
 *
 * 配置理由: ドメインの中核概念。どの層にも依存しない。
 */
public class Task {
    private Long id;
    private String title;
    private String description;
    private TaskStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ファクトリメソッド：新規作成
    public static Task create(String title, String description) {
        Task task = new Task();
        task.title = title;
        task.description = description;
        task.status = TaskStatus.TODO;
        task.createdAt = LocalDateTime.now();
        task.updatedAt = LocalDateTime.now();
        return task;
    }

    // ビジネスロジック: 完了処理
    public void complete() {
        if (!canComplete()) {
            throw new IllegalStateException("Task is already completed");
        }
        this.status = TaskStatus.DONE;
        this.updatedAt = LocalDateTime.now();
    }

    // ビジネスルール: 完了可能か判定
    public boolean canComplete() {
        return this.status != TaskStatus.DONE;
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

    // Infrastructure層からの再構築用（DBから復元時のみ使用）
    public void setId(Long id) {
        this.id = id;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
