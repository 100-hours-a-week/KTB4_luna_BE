package com.example.community.post.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class PostPageResponseDTO {
    private List<PostListResponseDTO> posts;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
}
