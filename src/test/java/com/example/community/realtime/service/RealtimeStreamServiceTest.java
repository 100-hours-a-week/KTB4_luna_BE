package com.example.community.realtime.service;

import com.example.community.realtime.connection.RealtimeConnection;
import com.example.community.realtime.connection.RealtimeConnectionRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RealtimeStreamServiceTest {

    RealtimeConnectionRegistry registry;
    SseEmitter emitter;
    Instant connectedAt;
    RealtimeStreamService service;

    @BeforeEach
    void setUp() {
        registry = mock(RealtimeConnectionRegistry.class);
        emitter = mock(SseEmitter.class);
        connectedAt = Instant.parse("2026-07-30T10:00:00Z");
        RealtimeConnection connection =
                new RealtimeConnection("connection-1", 1L, emitter, connectedAt);
        when(registry.register(eq(1L), same(emitter), any(Instant.class))).thenReturn(connection);
        service = new RealtimeStreamService(registry);
    }

    @Test
    @DisplayName("연결을 등록하고 connected 이벤트를 전송한다")
    void connectsAndSendsConnectedEvent() throws Exception {
        SseEmitter result = service.connect(1L, emitter);

        assertThat(result).isSameAs(emitter);
        verify(registry).register(eq(1L), same(emitter), any(Instant.class));

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
        assertThat(eventParts).contains(Map.of(
                "connectionId", "connection-1",
                "connectedAt", connectedAt
        ));
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
}
