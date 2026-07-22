package com.example.community.comment.repository;

import com.example.community.comment.entity.Comment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {
    @EntityGraph(attributePaths = {"author", "parentComment"})
    @Query("""
        select c
        from Comment c
        where c.post.postId = :postId
        order by c.createdAt desc
    """)
    List<Comment> findListByPost(@Param("postId") long postId);

    @EntityGraph(attributePaths = {"author", "post", "parentComment"})
    @Query("""
        select c
        from Comment c
        where c.post.postId = :postId and c.commentId = :commentId
    """)
    Optional<Comment> findCommentWithPost(long postId, long commentId);
}
