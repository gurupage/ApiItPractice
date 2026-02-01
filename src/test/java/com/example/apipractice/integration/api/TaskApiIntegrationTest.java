package com.example.apipractice.integration.api;

import com.example.apipractice.integration.config.TestcontainersConfig;
import com.example.apipractice.interfaces.rest.dto.CreateTaskRequest;
import com.example.apipractice.interfaces.rest.dto.TaskResponse;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

/**
 * Task API の統合テスト（E2E）
 * IT: Spring起動 + Testcontainers（Oracle XE） + WireMock（外部API）
 *
 * テスト方針:
 * - Controller → Service → Repository → DB の全体フロー検証
 * - WireMockで外部通知APIをモック化
 * - 実環境に近い動作確認
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Tag("integration")
class TaskApiIntegrationTest extends TestcontainersConfig {

    @Autowired
    private TestRestTemplate restTemplate;

    private static WireMockServer notificationWireMock;
    private static WireMockServer userValidationWireMock;

    /**
     * WireMockサーバー起動（クラスごと）
     * わざとテストクラスごとに起動（教材用）
     *
     * 2つの外部APIをモック:
     * - 通知API (port 8081)
     * - ユーザー検証API (port 8082)
     */
    @BeforeAll
    static void startWireMock() {
        // 通知API用WireMock
        notificationWireMock = new WireMockServer(8081);
        notificationWireMock.start();

        // ユーザー検証API用WireMock
        userValidationWireMock = new WireMockServer(8082);
        userValidationWireMock.start();
    }

    @AfterAll
    static void stopWireMock() {
        notificationWireMock.stop();
        userValidationWireMock.stop();
    }

    @BeforeEach
    void setupWireMock() {
        notificationWireMock.resetAll();
        userValidationWireMock.resetAll();

        // 通知API のモック設定
        WireMock.configureFor("localhost", 8081);
        stubFor(post(urlEqualTo("/notifications"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"success\"}")));

        // ユーザー検証API のデフォルトモック設定（ユーザー存在）
        WireMock.configureFor("localhost", 8082);
        stubFor(get(urlMatching("/api/users/.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"userId\":\"test-user\",\"username\":\"Test User\",\"active\":true}")));
    }

    /**
     * WireMock URL を Spring プロパティに注入
     */
    @DynamicPropertySource
    static void setWireMockUrl(DynamicPropertyRegistry registry) {
        registry.add("notification.api.url", () -> "http://localhost:8081/notifications");
        registry.add("user.validation.api.url", () -> "http://localhost:8082/api/users");
    }

    @Test
    void タスク作成から完了までのE2Eフロー() {
        // 1. POST /tasks - タスク作成
        CreateTaskRequest request = new CreateTaskRequest();
        request.setUserId("user123");
        request.setTitle("E2E Test Task");
        request.setDescription("End-to-End test");

        ResponseEntity<TaskResponse> createResponse = restTemplate.postForEntity(
                "/tasks",
                request,
                TaskResponse.class
        );

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createResponse.getBody()).isNotNull();
        assertThat(createResponse.getBody().getId()).isNotNull();
        assertThat(createResponse.getBody().getTitle()).isEqualTo("E2E Test Task");
        assertThat(createResponse.getBody().getStatus().toString()).isEqualTo("TODO");

        Long taskId = createResponse.getBody().getId();

        // WireMockが呼ばれたことを確認（2つの外部API）
        WireMock.configureFor("localhost", 8082);
        verify(1, getRequestedFor(urlEqualTo("/api/users/user123")));

        WireMock.configureFor("localhost", 8081);
        verify(1, postRequestedFor(urlEqualTo("/notifications")));

        // 2. GET /tasks/{id} - タスク取得
        ResponseEntity<TaskResponse> getResponse = restTemplate.getForEntity(
                "/tasks/" + taskId,
                TaskResponse.class
        );

        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody()).isNotNull();
        assertThat(getResponse.getBody().getId()).isEqualTo(taskId);

        // 3. POST /tasks/{id}/complete - タスク完了
        ResponseEntity<TaskResponse> completeResponse = restTemplate.postForEntity(
                "/tasks/" + taskId + "/complete",
                null,
                TaskResponse.class
        );

        assertThat(completeResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(completeResponse.getBody()).isNotNull();
        assertThat(completeResponse.getBody().getStatus().toString()).isEqualTo("DONE");
    }

    @Test
    void 存在しないタスクを取得すると404が返る() {
        // when
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/tasks/9999",
                String.class
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void 完了済みタスクを完了しようとすると400が返る() {
        // given: タスク作成と完了
        CreateTaskRequest request = new CreateTaskRequest();
        request.setUserId("user456");
        request.setTitle("Test Task");
        request.setDescription("Description");

        ResponseEntity<TaskResponse> createResponse = restTemplate.postForEntity(
                "/tasks",
                request,
                TaskResponse.class
        );

        Long taskId = createResponse.getBody().getId();

        restTemplate.postForEntity("/tasks/" + taskId + "/complete", null, TaskResponse.class);

        // when: 再度完了を試みる
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/tasks/" + taskId + "/complete",
                null,
                String.class
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void POSTで作成したタスクがGETで取得できる() {
        // given
        CreateTaskRequest request = new CreateTaskRequest();
        request.setUserId("user789");
        request.setTitle("Persistence Test Task");
        request.setDescription("DB永続化の検証テスト");

        // when: POST /tasks でタスク作成
        ResponseEntity<TaskResponse> postResponse = restTemplate.postForEntity(
                "/tasks",
                request,
                TaskResponse.class
        );

        assertThat(postResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(postResponse.getBody()).isNotNull();

        Long taskId = postResponse.getBody().getId();

        // then: GET /tasks/{id} で同じタスクが取得できる
        ResponseEntity<TaskResponse> getResponse = restTemplate.getForEntity(
                "/tasks/" + taskId,
                TaskResponse.class
        );

        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody()).isNotNull();
        assertThat(getResponse.getBody().getId()).isEqualTo(taskId);
        assertThat(getResponse.getBody().getTitle()).isEqualTo("Persistence Test Task");
        assertThat(getResponse.getBody().getDescription()).isEqualTo("DB永続化の検証テスト");
        assertThat(getResponse.getBody().getStatus().toString()).isEqualTo("TODO");
        assertThat(getResponse.getBody().getCreatedAt()).isNotNull();
        assertThat(getResponse.getBody().getUpdatedAt()).isNotNull();
    }

    @Test
    void タスク完了後の状態変更がDBに正しく反映される() {
        // given: タスク作成
        CreateTaskRequest request = new CreateTaskRequest();
        request.setUserId("user-complete");
        request.setTitle("完了テストタスク");
        request.setDescription("完了状態の検証");

        ResponseEntity<TaskResponse> createResponse = restTemplate.postForEntity(
                "/tasks",
                request,
                TaskResponse.class
        );

        Long taskId = createResponse.getBody().getId();
        String initialStatus = createResponse.getBody().getStatus().toString();

        assertThat(initialStatus).isEqualTo("TODO");

        // when: POST /tasks/{id}/complete でタスク完了
        ResponseEntity<TaskResponse> completeResponse = restTemplate.postForEntity(
                "/tasks/" + taskId + "/complete",
                null,
                TaskResponse.class
        );

        // then: 完了レスポンスの検証
        assertThat(completeResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(completeResponse.getBody()).isNotNull();
        assertThat(completeResponse.getBody().getStatus().toString()).isEqualTo("DONE");

        // then: GETで再取得して完了状態が永続化されていることを確認
        ResponseEntity<TaskResponse> getResponse = restTemplate.getForEntity(
                "/tasks/" + taskId,
                TaskResponse.class
        );

        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody()).isNotNull();
        assertThat(getResponse.getBody().getId()).isEqualTo(taskId);
        assertThat(getResponse.getBody().getStatus().toString()).isEqualTo("DONE");
        assertThat(getResponse.getBody().getUpdatedAt()).isAfter(createResponse.getBody().getCreatedAt());
    }

    @Test
    void 複数のタスクを作成して個別に取得できる() {
        // given: 3つのタスクを作成
        CreateTaskRequest request1 = new CreateTaskRequest();
        request1.setUserId("user-multi-1");
        request1.setTitle("Task 1");
        request1.setDescription("First task");

        CreateTaskRequest request2 = new CreateTaskRequest();
        request2.setUserId("user-multi-2");
        request2.setTitle("Task 2");
        request2.setDescription("Second task");

        CreateTaskRequest request3 = new CreateTaskRequest();
        request3.setUserId("user-multi-3");
        request3.setTitle("Task 3");
        request3.setDescription("Third task");

        ResponseEntity<TaskResponse> response1 = restTemplate.postForEntity("/tasks", request1, TaskResponse.class);
        ResponseEntity<TaskResponse> response2 = restTemplate.postForEntity("/tasks", request2, TaskResponse.class);
        ResponseEntity<TaskResponse> response3 = restTemplate.postForEntity("/tasks", request3, TaskResponse.class);

        Long taskId1 = response1.getBody().getId();
        Long taskId2 = response2.getBody().getId();
        Long taskId3 = response3.getBody().getId();

        // when & then: 各タスクを個別に取得できる
        ResponseEntity<TaskResponse> getTask1 = restTemplate.getForEntity("/tasks/" + taskId1, TaskResponse.class);
        assertThat(getTask1.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getTask1.getBody().getTitle()).isEqualTo("Task 1");

        ResponseEntity<TaskResponse> getTask2 = restTemplate.getForEntity("/tasks/" + taskId2, TaskResponse.class);
        assertThat(getTask2.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getTask2.getBody().getTitle()).isEqualTo("Task 2");

        ResponseEntity<TaskResponse> getTask3 = restTemplate.getForEntity("/tasks/" + taskId3, TaskResponse.class);
        assertThat(getTask3.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getTask3.getBody().getTitle()).isEqualTo("Task 3");

        // IDが異なることを確認
        assertThat(taskId1).isNotEqualTo(taskId2);
        assertThat(taskId2).isNotEqualTo(taskId3);
        assertThat(taskId1).isNotEqualTo(taskId3);
    }

    @Test
    void 存在しないユーザーでタスク作成すると400が返る() {
        // given: WireMockで404を返すように設定
        WireMock.configureFor("localhost", 8082);
        stubFor(get(urlEqualTo("/api/users/unknown-user"))
                .willReturn(aResponse()
                        .withStatus(404)));

        CreateTaskRequest request = new CreateTaskRequest();
        request.setUserId("unknown-user");
        request.setTitle("Test Task");
        request.setDescription("Description");

        // when
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/tasks",
                request,
                String.class
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // ユーザー検証APIが呼ばれたことを確認
        WireMock.configureFor("localhost", 8082);
        verify(1, getRequestedFor(urlEqualTo("/api/users/unknown-user")));

        // 通知APIは呼ばれないことを確認
        WireMock.configureFor("localhost", 8081);
        verify(0, postRequestedFor(urlEqualTo("/notifications")));
    }
}
