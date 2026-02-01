package com.example.apipractice.usecase.port;

import com.example.apipractice.core.domain.Task;

import java.util.Optional;

/**
 * Taskリポジトリのポート（インターフェース）
 * Usecase層: Infrastructure層への依存を逆転（DIP）
 *
 * 配置理由: Usecaseが永続化の実装詳細に依存しないため。
 * テスト時にMockで差し替え可能。
 */
public interface TaskRepository {
    Task save(Task task);
    Optional<Task> findById(Long id);
}
