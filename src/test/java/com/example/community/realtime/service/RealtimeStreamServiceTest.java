package com.example.community.realtime.service;

import com.example.community.CommunityApplication;
import com.example.community.global.auth.AuthValidator;
import com.example.community.global.exceptions.ContentNotFoundException;
import com.example.community.global.exceptions.ForbiddenException;
import com.example.community.global.exceptions.InvalidInputException;
import com.example.community.realtime.connection.RealtimeConnection;
import com.example.community.realtime.connection.RealtimeConnectionRegistry;
import com.example.community.realtime.connection.RealtimeInterestType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RealtimeStreamServiceTest {

    RealtimeConnectionRegistry registry;
    SseEmitter emitter;
    RealtimeConnection connection;
    RealtimeStreamService service;
    AuthValidator authValidator;

    @BeforeEach
    void setUp() {
        registry = mock(RealtimeConnectionRegistry.class);
        emitter = mock(SseEmitter.class);
        authValidator = mock(AuthValidator.class);
        connection = new RealtimeConnection("connection-1", 1L, emitter);
        when(registry.register(eq(1L), same(emitter))).thenReturn(connection);
        service = new RealtimeStreamService(registry, authValidator);
    }

    @Test
    @DisplayName("연결을 등록하고 connected 이벤트를 전송한다")
    void connectsAndSendsConnectedEvent() throws Exception {
        SseEmitter result = service.connect(1L, emitter);

        assertThat(result).isSameAs(emitter);
        verify(registry).register(eq(1L), same(emitter));

        ArgumentCaptor<SseEmitter.SseEventBuilder> eventCaptor =
                ArgumentCaptor.forClass(SseEmitter.SseEventBuilder.class);
        verify(emitter).send(eventCaptor.capture());

        List<Object> eventParts = eventCaptor.getValue().build().stream()
                .map(SseEmitter.DataWithMediaType::getData)
                .toList();
        assertThat(eventParts.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .anyMatch(part -> part.contains("event:connected"))).isTrue();
        assertThat(eventParts).contains(Map.of("connectionId", "connection-1"));
    }

    @Test
    @DisplayName("연결 종료 callback은 registry에서 연결을 제거한다")
    void cleanupCallbacksRemoveRegisteredConnection() throws Exception {
        service.connect(1L, emitter);

        ArgumentCaptor<Runnable> completionCaptor = ArgumentCaptor.forClass(Runnable.class);
        ArgumentCaptor<Runnable> timeoutCaptor = ArgumentCaptor.forClass(Runnable.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<Throwable>> errorCaptor =
                ArgumentCaptor.forClass(Consumer.class);
        verify(emitter).onCompletion(completionCaptor.capture());
        verify(emitter).onTimeout(timeoutCaptor.capture());
        verify(emitter).onError(errorCaptor.capture());

        completionCaptor.getValue().run();
        timeoutCaptor.getValue().run();
        errorCaptor.getValue().accept(new RuntimeException("connection failed"));

        verify(registry, times(3)).remove("connection-1", emitter);
    }

    @Test
    @DisplayName("connected 이벤트 전송 실패 시 등록된 연결을 제거한다")
    void sendFailureRemovesRegisteredConnection() throws Exception {
        doThrow(new IOException("send failed"))
                .when(emitter)
                .send(any(SseEmitter.SseEventBuilder.class));

        assertThatThrownBy(() -> service.connect(1L, emitter))
                .isInstanceOf(IOException.class)
                .hasMessage("send failed");

        verify(registry).remove("connection-1", emitter);
    }

    @Test
    @DisplayName("heartbeat는 25초마다 공통 scheduler로 실행된다")
    void heartbeatUsesCommonScheduler() throws Exception {
        Scheduled scheduled = RealtimeStreamService.class
                .getDeclaredMethod("sendHeartbeat")
                .getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.fixedDelay()).isEqualTo(25_000L);
        assertThat(CommunityApplication.class.isAnnotationPresent(EnableScheduling.class)).isTrue();
    }

    @Test
    @DisplayName("heartbeat 전송 실패 연결들만 제거하고 나머지 연결은 계속 전송한다")
    void heartbeatFailureRemovesOnlyFailedConnection() throws Exception {
        SseEmitter ioFailedEmitter = mock(SseEmitter.class);
        SseEmitter runtimeFailedEmitter = mock(SseEmitter.class);
        SseEmitter activeEmitter = mock(SseEmitter.class);
        RealtimeConnection ioFailedConnection =
                new RealtimeConnection("io-failed", 1L, ioFailedEmitter);
        RealtimeConnection runtimeFailedConnection =
                new RealtimeConnection("runtime-failed", 2L, runtimeFailedEmitter);
        RealtimeConnection activeConnection =
                new RealtimeConnection("active-connection", 3L, activeEmitter);
        when(registry.findAll()).thenReturn(List.of(
                ioFailedConnection,
                runtimeFailedConnection,
                activeConnection
        ));
        doThrow(new IOException("heartbeat failed"))
                .when(ioFailedEmitter)
                .send(any(SseEmitter.SseEventBuilder.class));
        doThrow(new IllegalStateException("emitter completed"))
                .when(runtimeFailedEmitter)
                .send(any(SseEmitter.SseEventBuilder.class));

        service.sendHeartbeat();

        verify(registry).remove("io-failed", ioFailedEmitter);
        verify(registry).remove("runtime-failed", runtimeFailedEmitter);
        verify(registry, never()).remove("active-connection", activeEmitter);

        ArgumentCaptor<SseEmitter.SseEventBuilder> eventCaptor =
                ArgumentCaptor.forClass(SseEmitter.SseEventBuilder.class);
        verify(activeEmitter).send(eventCaptor.capture());
        assertThat(eventCaptor.getValue().build().stream()
                .map(SseEmitter.DataWithMediaType::getData)
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .anyMatch(part -> part.contains(":heartbeat"))).isTrue();
    }

    @Test
    @DisplayName("연결 소유자는 더 큰 revision으로 관심 상태를 변경한다")
    void ownerUpdatesInterestWithNewerRevision() {
        when(registry.findById("connection-1")).thenReturn(Optional.of(connection));

        boolean updated = service.updateInterest(
                1L,
                "connection-1",
                RealtimeInterestType.POST_DETAIL,
                10L,
                1L
        );

        assertThat(updated).isTrue();
        assertThat(connection.getInterestType()).isEqualTo(RealtimeInterestType.POST_DETAIL);
        assertThat(connection.getPostId()).isEqualTo(10L);
        assertThat(connection.getInterestRevision()).isEqualTo(1L);
    }

    @Test
    @DisplayName("같거나 작은 revision은 기존 관심 상태를 변경하지 않는다")
    void staleRevisionDoesNotChangeInterest() {
        connection.updateInterestIfNewer(RealtimeInterestType.POST_DETAIL, 10L, 2L);
        when(registry.findById("connection-1")).thenReturn(Optional.of(connection));

        boolean updated = service.updateInterest(
                1L,
                "connection-1",
                RealtimeInterestType.POST_LIST,
                null,
                2L
        );

        assertThat(updated).isFalse();
        assertThat(connection.getInterestType()).isEqualTo(RealtimeInterestType.POST_DETAIL);
        assertThat(connection.getPostId()).isEqualTo(10L);
        assertThat(connection.getInterestRevision()).isEqualTo(2L);
    }

    @Test
    @DisplayName("POST_DETAIL은 양수 postId가 필요하다")
    void postDetailRequiresPositivePostId() {
        when(registry.findById("connection-1")).thenReturn(Optional.of(connection));

        assertThatThrownBy(() -> service.updateInterest(
                1L,
                "connection-1",
                RealtimeInterestType.POST_DETAIL,
                null,
                1L
        )).isInstanceOf(InvalidInputException.class);

        assertThatThrownBy(() -> service.updateInterest(
                1L,
                "connection-1",
                RealtimeInterestType.POST_DETAIL,
                0L,
                1L
        )).isInstanceOf(InvalidInputException.class);

        assertThat(connection.getInterestType()).isEqualTo(RealtimeInterestType.NONE);
        assertThat(connection.getInterestRevision()).isZero();
    }

    @Test
    @DisplayName("POST_DETAIL이 아닌 관심 상태는 postId를 저장하지 않는다")
    void nonPostDetailInterestClearsPostId() {
        connection.updateInterestIfNewer(RealtimeInterestType.POST_DETAIL, 10L, 1L);
        when(registry.findById("connection-1")).thenReturn(Optional.of(connection));

        boolean updated = service.updateInterest(
                1L,
                "connection-1",
                RealtimeInterestType.POST_LIST,
                99L,
                2L
        );

        assertThat(updated).isTrue();
        assertThat(connection.getInterestType()).isEqualTo(RealtimeInterestType.POST_LIST);
        assertThat(connection.getPostId()).isNull();
    }

    @Test
    @DisplayName("존재하지 않는 연결은 404 예외로 처리한다")
    void missingConnectionIsNotFound() {
        when(registry.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateInterest(
                1L,
                "missing",
                RealtimeInterestType.POST_LIST,
                null,
                1L
        )).isInstanceOf(ContentNotFoundException.class);
    }

    @Test
    @DisplayName("다른 사용자의 연결은 변경할 수 없다")
    void cannotUpdateAnotherUsersConnection() {
        when(registry.findById("connection-1")).thenReturn(Optional.of(connection));
        doThrow(new ForbiddenException())
                .when(authValidator)
                .validateOwner(2L, 1L);

        assertThatThrownBy(() -> service.updateInterest(
                2L,
                "connection-1",
                RealtimeInterestType.POST_LIST,
                null,
                1L
        )).isInstanceOf(ForbiddenException.class);

        assertThat(connection.getInterestType()).isEqualTo(RealtimeInterestType.NONE);
        assertThat(connection.getInterestRevision()).isZero();
    }
}
