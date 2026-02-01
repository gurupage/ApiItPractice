package com.example.apipractice.usecase.port;

/**
 * ユーザー存在確認APIクライアントのポート
 * Usecase層: 外部ユーザー管理システムへの依存を抽象化
 *
 * 配置理由: 外部システムの実装詳細（HTTP通信）をUsecaseから隠蔽。
 * IT時にWireMockで差し替え可能。
 */
public interface UserValidationClient {

    /**
     * ユーザーの存在を確認
     *
     * @param userId ユーザーID
     * @return ユーザーが存在する場合true、存在しない場合false
     * @throws UserValidationException 外部API呼び出しに失敗した場合
     */
    boolean existsUser(String userId);

    /**
     * ユーザー検証例外
     */
    class UserValidationException extends RuntimeException {
        public UserValidationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
