package com.example.community.realtime.event;

public record CommentCreatedEvent(String eventId, Long postId, Long commentId, Long actorUserId) {

}
