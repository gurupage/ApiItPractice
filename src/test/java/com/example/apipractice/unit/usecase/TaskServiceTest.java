package com.example.apipractice.unit.usecase;

import com.example.apipractice.core.domain.Task;
import com.example.apipractice.core.domain.TaskStatus;
import com.example.apipractice.usecase.port.NotificationClient;
import com.example.apipractice.usecase.port.TaskRepository;
import com.example.apipractice.usecase.port.UserValidationClient;
import com.example.apipractice.usecase.service.TaskService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * TaskService の単体テスト
 * UT: Spring起動なし、Mockito使用
 *
 * テスト方針:
 * - 全依存（Repository, NotificationClient, UserValidationClient）をMock化
 * - ビジネスロジックの分岐を検証
 * - 高速実行（数百ms）
 */
@ExtendWith(MockitoExtension.class)
@Tag("unit")
class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private NotificationClient notificationClient;

    @Mock
    private UserValidationClient userValidationClient;

    @InjectMocks
    private TaskService taskService;

    @Test
    void タスク作成時にユーザー検証とリポジトリ保存と通知が呼ばれる() {
        // given
        String userId = "user123";
        Task task = Task.create("Test Task", "Description");
        task.setId(1L);

        when(userValidationClient.existsUser(userId)).thenReturn(true);
        when(taskRepository.save(any(Task.class))).thenReturn(task);

        // when
        Task result = taskService.createTask(userId, "Test Task", "Description");

        // then
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getTitle()).isEqualTo("Test Task");
        verify(userValidationClient, times(1)).existsUser(userId);
        verify(taskRepository, times(1)).save(any(Task.class));
        verify(notificationClient, times(1)).notifyTaskCreated(1L, "Test Task");
    }

    @Test
    void タスク取得時に存在しない場合は例外が発生する() {
        // given
        when(taskRepository.findById(999L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> taskService.getTask(999L))
                .isInstanceOf(TaskService.TaskNotFoundException.class)
                .hasMessageContaining("Task not found: id=999");
    }

    @Test
    void タスク完了時にステータスがDONEに変更される() {
        // given
        Task task = Task.create("Test Task", "Description");
        task.setId(1L);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        Task result = taskService.completeTask(1L);

        // then
        assertThat(result.getStatus()).isEqualTo(TaskStatus.DONE);
        verify(taskRepository, times(1)).save(task);
    }

    @Test
    void 完了済みタスクを完了しようとすると例外が発生する() {
        // given
        Task task = Task.create("Test Task", "Description");
        task.setId(1L);
        task.complete();  // 既に完了状態にする
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));

        // when & then
        assertThatThrownBy(() -> taskService.completeTask(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already completed");
    }

    @Test
    void 存在しないIDでタスク完了を試みると例外が発生する() {
        // given
        Long nonExistentId = 999L;
        when(taskRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> taskService.completeTask(nonExistentId))
                .isInstanceOf(TaskService.TaskNotFoundException.class)
                .hasMessageContaining("Task not found: id=999");

        // リポジトリのsaveは呼ばれないことを確認
        verify(taskRepository, never()).save(any(Task.class));
    }

    @Test
    void タスク作成時にタイトルと説明が正しく保存される() {
        // given
        String userId = "user456";
        String expectedTitle = "新規タスク";
        String expectedDescription = "タスクの詳細説明";
        Task savedTask = Task.create(expectedTitle, expectedDescription);
        savedTask.setId(10L);

        when(userValidationClient.existsUser(userId)).thenReturn(true);
        when(taskRepository.save(any(Task.class))).thenReturn(savedTask);

        // when
        Task result = taskService.createTask(userId, expectedTitle, expectedDescription);

        // then
        assertThat(result.getTitle()).isEqualTo(expectedTitle);
        assertThat(result.getDescription()).isEqualTo(expectedDescription);
        assertThat(result.getStatus()).isEqualTo(TaskStatus.TODO);
        assertThat(result.getId()).isEqualTo(10L);

        // Repositoryが1回だけ呼ばれたことを確認
        verify(taskRepository, times(1)).save(any(Task.class));
    }

    @Test
    void 存在しないユーザーでタスク作成を試みると例外が発生する() {
        // given
        String nonExistentUserId = "unknown-user";
        when(userValidationClient.existsUser(nonExistentUserId)).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> taskService.createTask(nonExistentUserId, "Title", "Description"))
                .isInstanceOf(TaskService.UserNotFoundException.class)
                .hasMessageContaining("User not found: userId=unknown-user");

        // ユーザー検証で失敗したので、保存と通知は呼ばれない
        verify(userValidationClient, times(1)).existsUser(nonExistentUserId);
        verify(taskRepository, never()).save(any(Task.class));
        verify(notificationClient, never()).notifyTaskCreated(any(), any());
    }

    @Test
    void ユーザー検証API障害時に例外が発生する() {
        // given
        String userId = "user789";
        when(userValidationClient.existsUser(userId))
                .thenThrow(new UserValidationClient.UserValidationException(
                        "API error",
                        new RuntimeException("Connection timeout")
                ));

        // when & then
        assertThatThrownBy(() -> taskService.createTask(userId, "Title", "Description"))
                .isInstanceOf(UserValidationClient.UserValidationException.class)
                .hasMessageContaining("API error");

        // API障害で失敗したので、保存と通知は呼ばれない
        verify(taskRepository, never()).save(any(Task.class));
        verify(notificationClient, never()).notifyTaskCreated(any(), any());
    }

    @Test
    void タスク取得時にIDが一致するタスクが返される() {
        // given
        Long taskId = 5L;
        Task expectedTask = Task.create("既存タスク", "説明");
        expectedTask.setId(taskId);

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(expectedTask));

        // when
        Task result = taskService.getTask(taskId);

        // then
        assertThat(result).isEqualTo(expectedTask);
        assertThat(result.getId()).isEqualTo(taskId);
        assertThat(result.getTitle()).isEqualTo("既存タスク");

        verify(taskRepository, times(1)).findById(taskId);
    }

    @Test
    void タスク完了時にリポジトリが呼ばれる() {
        // given
        Task task = Task.create("進行中タスク", "説明");
        task.setId(3L);

        when(taskRepository.findById(3L)).thenReturn(Optional.of(task));
        when(taskRepository.save(task)).thenReturn(task);

        // when
        taskService.completeTask(3L);

        // then
        verify(taskRepository, times(1)).findById(3L);
        verify(taskRepository, times(1)).save(task);
    }
}
