package com.example.apipractice.infrastructure.persistence;

import com.example.apipractice.core.domain.Task;
import com.example.apipractice.usecase.port.TaskRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * TaskRepositoryポートの実装（Adapter）
 * Infrastructure層: ドメインモデル ⇔ JPAエンティティ の変換
 *
 * 配置理由: Usecaseが定義したポート（インターフェース）を実装。
 * DIP（依存性逆転の原則）を実現。
 */
@Repository
public class TaskRepositoryAdapter implements TaskRepository {

    private final TaskJpaRepository jpaRepository;

    public TaskRepositoryAdapter(TaskJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Task save(Task task) {
        TaskEntity entity = toEntity(task);
        TaskEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<Task> findById(Long id) {
        return jpaRepository.findById(id)
                .map(this::toDomain);
    }

    // ドメインモデル → JPAエンティティ
    private TaskEntity toEntity(Task task) {
        TaskEntity entity = new TaskEntity(
                task.getTitle(),
                task.getDescription(),
                task.getStatus(),
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
        if (task.getId() != null) {
            entity.setId(task.getId());
        }
        return entity;
    }

    // JPAエンティティ → ドメインモデル
    private Task toDomain(TaskEntity entity) {
        Task task = Task.create(entity.getTitle(), entity.getDescription());
        task.setId(entity.getId());
        task.setStatus(entity.getStatus());
        task.setCreatedAt(entity.getCreatedAt());
        task.setUpdatedAt(entity.getUpdatedAt());
        return task;
    }
}
