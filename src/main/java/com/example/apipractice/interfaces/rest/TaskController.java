package com.example.apipractice.interfaces.rest;

import com.example.apipractice.core.domain.Task;
import com.example.apipractice.interfaces.rest.dto.CreateTaskRequest;
import com.example.apipractice.interfaces.rest.dto.TaskResponse;
import com.example.apipractice.usecase.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * Task REST API Controller
 * Interface層: 外部からのHTTPリクエスト受付
 *
 * 配置理由: HTTPプロトコルはインターフェース層の責務。
 * UT対象（Serviceをモック化して高速テスト）。
 */
@RestController
@RequestMapping("/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    /**
     * POST /tasks - タスク作成
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TaskResponse createTask(@Valid @RequestBody CreateTaskRequest request) {
        Task task = taskService.createTask(
                request.getUserId(),
                request.getTitle(),
                request.getDescription()
        );
        return TaskResponse.from(task);
    }

    /**
     * GET /tasks/{id} - タスク取得
     */
    @GetMapping("/{id}")
    public TaskResponse getTask(@PathVariable Long id) {
        Task task = taskService.getTask(id);
        return TaskResponse.from(task);
    }

    /**
     * POST /tasks/{id}/complete - タスク完了
     */
    @PostMapping("/{id}/complete")
    public TaskResponse completeTask(@PathVariable Long id) {
        Task task = taskService.completeTask(id);
        return TaskResponse.from(task);
    }

    /**
     * 例外ハンドリング
     */
    @ExceptionHandler(TaskService.TaskNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNotFound(TaskService.TaskNotFoundException ex) {
        return new ErrorResponse(ex.getMessage());
    }

    @ExceptionHandler(TaskService.UserNotFoundException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleUserNotFound(TaskService.UserNotFoundException ex) {
        return new ErrorResponse(ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleBadRequest(IllegalStateException ex) {
        return new ErrorResponse(ex.getMessage());
    }

    // エラーレスポンス用DTO
    public static class ErrorResponse {
        private String message;

        public ErrorResponse(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }
}
