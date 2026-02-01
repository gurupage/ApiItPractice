package com.example.apipractice.usecase.service;

import com.example.apipractice.core.domain.Task;
import com.example.apipractice.usecase.port.NotificationClient;
import com.example.apipractice.usecase.port.TaskRepository;
import com.example.apipractice.usecase.port.UserValidationClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Taskのユースケース実装
 * Usecase層: ビジネスロジックのオーケストレーション
 *
 * 配置理由: ドメインモデルとポートを使ってユースケースを実現。
 * UT対象（依存を全てMock化して高速テスト）。
 */
@Service
@Transactional
public class TaskService {

    private final TaskRepository taskRepository;
    private final NotificationClient notificationClient;
    private final UserValidationClient userValidationClient;

    public TaskService(
            TaskRepository taskRepository,
            NotificationClient notificationClient,
            UserValidationClient userValidationClient) {
        this.taskRepository = taskRepository;
        this.notificationClient = notificationClient;
        this.userValidationClient = userValidationClient;
    }

    /**
     * タスク作成
     * ビジネスルール:
     * - タスク作成前にユーザーの存在を確認
     * - 作成後に外部通知を送信
     */
    public Task createTask(String userId, String title, String description) {
        // 1. ユーザー存在確認（外部API）
        if (!userValidationClient.existsUser(userId)) {
            throw new UserNotFoundException("User not found: userId=" + userId);
        }

        // 2. ドメインモデル生成
        Task task = Task.create(title, description);

        // 3. 永続化
        Task savedTask = taskRepository.save(task);

        // 4. 外部通知（例: Slack, メール等）
        notificationClient.notifyTaskCreated(savedTask.getId(), savedTask.getTitle());

        return savedTask;
    }

    /**
     * タスク取得
     */
    @Transactional(readOnly = true)
    public Task getTask(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException("Task not found: id=" + id));
    }

    /**
     * タスク完了
     * ビジネスルール: ドメインモデルの完了ロジックを使用
     */
    public Task completeTask(Long id) {
        Task task = getTask(id);

        // ドメインロジック呼び出し（完了可否判定含む）
        task.complete();

        return taskRepository.save(task);
    }

    /**
     * カスタム例外
     */
    public static class TaskNotFoundException extends RuntimeException {
        public TaskNotFoundException(String message) {
            super(message);
        }
    }

    /**
     * ユーザー不在例外
     */
    public static class UserNotFoundException extends RuntimeException {
        public UserNotFoundException(String message) {
            super(message);
        }
    }
}
