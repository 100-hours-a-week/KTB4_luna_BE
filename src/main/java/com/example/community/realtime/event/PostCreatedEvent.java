package com.example.community.realtime.event;

public record PostCreatedEvent(String eventId, Long postId, long actorUserId) {

}
