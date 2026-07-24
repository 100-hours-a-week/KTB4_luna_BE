package com.example.community.post.repository;

import com.example.community.post.entity.Post;
import com.example.community.post.entity.PostStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PostRepository extends JpaRepository<Post,Long> {
    @Query(value = """
        select p 
        from Post p
        join fetch p.author
        where p.status <> :status
        order by p.createdAt desc, p.postId desc
    """, countQuery = """
        select count(p)
        from Post p
        where p.status <> :status
    """)
    Page<Post> findByStatusNot(@Param("status") PostStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"author", "detail"})
    Optional<Post> findByPostId(Long postId);
}
