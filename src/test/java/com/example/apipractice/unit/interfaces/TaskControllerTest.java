package com.example.apipractice.unit.interfaces;

import com.example.apipractice.core.domain.Task;
import com.example.apipractice.core.domain.TaskStatus;
import com.example.apipractice.interfaces.rest.TaskController;
import com.example.apipractice.interfaces.rest.dto.CreateTaskRequest;
import com.example.apipractice.interfaces.rest.dto.TaskResponse;
import com.example.apipractice.usecase.service.TaskService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * TaskController の単体テスト
 * UT: Spring起動なし、Mockito使用
 *
 * テスト方針:
 * - ServiceをMock化
 * - HTTPリクエスト→DTO変換→Service呼び出しを検証
 * - レスポンスDTOの変換を検証
 */
@ExtendWith(MockitoExtension.class)
@Tag("unit")
class TaskControllerTest {

    @Mock
    private TaskService taskService;

    @InjectMocks
    private TaskController taskController;

    @Test
    void タスク作成APIがServiceを呼び出してレスポンスを返す() {
        // given
        CreateTaskRequest request = new CreateTaskRequest();
        request.setTitle("Test Task");
        request.setDescription("Description");

        Task task = Task.create("Test Task", "Description");
        task.setId(1L);

        when(taskService.createTask("Test Task", "Description")).thenReturn(task);

        // when
        TaskResponse response = taskController.createTask(request);

        // then
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getTitle()).isEqualTo("Test Task");
        assertThat(response.getStatus()).isEqualTo(TaskStatus.TODO);
        verify(taskService, times(1)).createTask("Test Task", "Description");
    }

    @Test
    void タスク取得APIがServiceを呼び出してレスポンスを返す() {
        // given
        Task task = Task.create("Test Task", "Description");
        task.setId(1L);

        when(taskService.getTask(1L)).thenReturn(task);

        // when
        TaskResponse response = taskController.getTask(1L);

        // then
        assertThat(response.getId()).isEqualTo(1L);
        verify(taskService, times(1)).getTask(1L);
    }

    @Test
    void タスク完了APIがServiceを呼び出して完了状態のレスポンスを返す() {
        // given
        Task task = Task.create("Test Task", "Description");
        task.setId(1L);
        task.complete();

        when(taskService.completeTask(1L)).thenReturn(task);

        // when
        TaskResponse response = taskController.completeTask(1L);

        // then
        assertThat(response.getStatus()).isEqualTo(TaskStatus.DONE);
        verify(taskService, times(1)).completeTask(1L);
    }
}
