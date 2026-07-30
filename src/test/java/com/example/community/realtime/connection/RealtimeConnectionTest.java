package com.example.community.realtime.connection;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

public class RealtimeConnectionTest {
    @Test
    void connection_init_test(){
        SseEmitter emitter = new SseEmitter();
        Instant connectedAt = Instant.parse("2026-07-30T10:00:00Z");

        RealtimeConnection connection = new RealtimeConnection("connection-1", 1L, emitter, connectedAt);

        assertThat(connection.getConnectionId()).isEqualTo("connection-1");
        assertThat(connection.getUserId()).isEqualTo(1L);
        assertThat(connection.getEmitter()).isSameAs(emitter);
        assertThat(connection.getConnectedAt()).isEqualTo(connectedAt);
        assertThat(connection.getInterestType()).isEqualTo(RealtimeInterestType.NONE);
        assertThat(connection.getPostId()).isNull();
        assertThat(connection.getInterestRevision()).isZero();
    }
    @Test
    @DisplayName("더 큰 revision만 관심 상태에 반영한다")
    void updatesInterestOnlyWhenRevisionIsNewer() {
        RealtimeConnection connection = new RealtimeConnection(
                "connection-1",
                1L,
                new SseEmitter(),
                Instant.now()
        );

        assertThat(connection.updateInterestIfNewer(
                RealtimeInterestType.POST_DETAIL, 10L, 2L
        )).isTrue();

        assertThat(connection.updateInterestIfNewer(
                RealtimeInterestType.POST_LIST, null, 2L
        )).isFalse();

        assertThat(connection.getInterestType()).isEqualTo(RealtimeInterestType.POST_DETAIL);
        assertThat(connection.getPostId()).isEqualTo(10L);
        assertThat(connection.getInterestRevision()).isEqualTo(2L);
    }
}
