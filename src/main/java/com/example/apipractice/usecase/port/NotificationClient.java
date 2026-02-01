package com.example.apipractice.usecase.port;

/**
 * 外部通知APIクライアントのポート
 * Usecase層: 外部APIへの依存を抽象化
 *
 * 配置理由: 外部システムの実装詳細（HTTP通信）をUsecaseから隠蔽。
 * IT時にWireMockで差し替え可能。
 */
public interface NotificationClient {
    /**
     * タスク作成通知を送信
     * @param taskId 作成されたタスクID
     * @param title タスクタイトル
     */
    void notifyTaskCreated(Long taskId, String title);
}
