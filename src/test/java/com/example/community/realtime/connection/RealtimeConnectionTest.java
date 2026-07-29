package com.example.community.realtime.connection;

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
}
