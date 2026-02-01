package com.example.apipractice.integration.infrastructure;

import com.example.apipractice.core.domain.Task;
import com.example.apipractice.core.domain.TaskStatus;
import com.example.apipractice.integration.config.TestcontainersConfig;
import com.example.apipractice.usecase.port.TaskRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * TaskRepository の統合テスト
 * IT: Spring起動 + Testcontainers（Oracle XE）
 *
 * テスト方針:
 * - 実DBでCRUD動作を検証
 * - Flyway Migrationの動作確認
 * - ドメインモデル⇔Entity変換の正確性を検証
 */
@SpringBootTest
@ActiveProfiles("test")
@Tag("integration")
@Transactional  // テスト後にロールバック
class TaskRepositoryIntegrationTest extends TestcontainersConfig {

    @Autowired
    private TaskRepository taskRepository;

    @Test
    void タスクを保存して取得できる() {
        // given
        Task task = Task.create("Integration Test Task", "This is a test");

        // when
        Task saved = taskRepository.save(task);

        // then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getTitle()).isEqualTo("Integration Test Task");
        assertThat(saved.getStatus()).isEqualTo(TaskStatus.TODO);

        // DBから取得
        Optional<Task> found = taskRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getTitle()).isEqualTo("Integration Test Task");
    }

    @Test
    void タスクを更新できる() {
        // given
        Task task = Task.create("Original Title", "Description");
        Task saved = taskRepository.save(task);

        // when
        saved.complete();  // ステータス変更
        Task updated = taskRepository.save(saved);

        // then
        assertThat(updated.getStatus()).isEqualTo(TaskStatus.DONE);

        // DBから再取得して確認
        Optional<Task> found = taskRepository.findById(updated.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo(TaskStatus.DONE);
    }

    @Test
    void 存在しないIDで検索すると空のOptionalが返る() {
        // when
        Optional<Task> found = taskRepository.findById(9999L);

        // then
        assertThat(found).isEmpty();
    }
}
