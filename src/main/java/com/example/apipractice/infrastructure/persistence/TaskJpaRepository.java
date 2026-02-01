package com.example.apipractice.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA リポジトリ
 * Infrastructure層: フレームワーク機能の利用
 *
 * 配置理由: Spring Data JPAはインフラ技術。
 * 自動実装されるため、コード量最小。
 */
public interface TaskJpaRepository extends JpaRepository<TaskEntity, Long> {
}
