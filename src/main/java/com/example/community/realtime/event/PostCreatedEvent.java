package com.example.community.realtime.event;

import java.time.Instant;

public record PostCreatedEvent(String eventId, Long postId, long actorUserId, Instant occurredAt) {

}
