package com.example.community.realtime.service;

import com.example.community.realtime.connection.RealtimeConnection;
import com.example.community.realtime.connection.RealtimeConnectionRegistry;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

@Service
public class RealtimeStreamService {
    private final RealtimeConnectionRegistry registry;

    public RealtimeStreamService(RealtimeConnectionRegistry registry){
        this.registry = registry;
    }

    public SseEmitter connect(long userId, SseEmitter sseEmitter) throws IOException{
        Instant connectedAt = Instant.now();
        RealtimeConnection connection = registry.register(userId, sseEmitter, connectedAt);
        String connectionId = connection.getConnectionId();
        try {
            sseEmitter.onCompletion(() -> registry.remove(connectionId, sseEmitter));
            sseEmitter.onTimeout(() -> registry.remove(connectionId, sseEmitter));
            sseEmitter.onError(error -> registry.remove(connectionId, sseEmitter));
            sseEmitter.send(SseEmitter.event()
                    .name("connected")
                    .data(Map.of(
                            "connectionId", connectionId,
                            "connectedAt", connection.getConnectedAt()
                    )));
            return sseEmitter;
        } catch(IOException | RuntimeException exception){
            registry.remove(connectionId, sseEmitter);
            throw exception;
        }
    }
}
