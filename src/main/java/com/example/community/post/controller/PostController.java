package com.example.community.post.controller;

import com.example.community.global.ApiResponse;
import com.example.community.global.exceptions.InvalidInputException;
import com.example.community.post.dto.*;
import com.example.community.post.service.PostService;
import com.example.community.user.entity.UserRole;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

@RestController()
@RequestMapping("/api/posts")
public class PostController {
    private final PostService postService;
    public PostController(PostService postService) {
        this.postService = postService;
    }
    @PostMapping()
    public ResponseEntity<ApiResponse<PostResponseDTO>> postUpload(Authentication authentication, @Valid @RequestBody PostRequestDTO requestDTO){
        Long userId = getLoginUserId(authentication);
        PostResponseDTO responseDTO = postService.upload(userId, requestDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>("post_create_success", responseDTO));
    }
    @GetMapping()
    public ResponseEntity<ApiResponse<PostPageResponseDTO>> getPostList(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page
    ){
        if (page < 0) throw new InvalidInputException();
        PostPageResponseDTO responseDTO = postService.getPostList(page);
        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse<>("posts_loading_success", responseDTO));
    }
    @GetMapping("/{postId}")
    public ResponseEntity<ApiResponse<PostDetailResponseDTO>> getPostDetail(Authentication authentication, @PathVariable("postId") Long postId){
        Long userId = getLoginUserId(authentication);
        UserRole role = getLoginUserRole(authentication);
        PostDetailResponseDTO responseDTO= postService.getPostDetail(userId, role, postId);
        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse<>("post_loading_success", responseDTO));
    }
    @PatchMapping("/{postId}")
    public ResponseEntity<ApiResponse<PostDetailResponseDTO>> modifyPost(Authentication authentication, @PathVariable("postId") Long postId, @Valid @RequestBody PostRequestDTO requestDTO){
        Long userId = getLoginUserId(authentication);
        PostDetailResponseDTO responseDTO = postService.modifyPost(userId, postId, requestDTO);
        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse<>("post_modify_success", responseDTO));
    }
    @DeleteMapping("/{postId}")
    public ResponseEntity<ApiResponse<String>> deletePost(Authentication authentication, @PathVariable("postId") Long postId){
        Long userId = getLoginUserId(authentication);
        postService.deletePost(userId, postId);
        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse<>("post_delete_success", null));
    }
    @PostMapping("/{postId}/likes")
    public ResponseEntity<ApiResponse<LikeResponseDTO>> likePost(Authentication authentication, @PathVariable("postId") Long postId){
        Long userId = getLoginUserId(authentication);
        LikeResponseDTO responseDTO = postService.likePost(userId, postId);
        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse<>("post_like_success", responseDTO));
    }
    @DeleteMapping("/{postId}/likes")
    public ResponseEntity<ApiResponse<LikeResponseDTO>> unlikePost(Authentication authentication, @PathVariable("postId") Long postId){
        Long userId = getLoginUserId(authentication);
        LikeResponseDTO responseDTO = postService.unlikePost(userId, postId);
        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse<>("post_unlike_success", responseDTO));
    }
    @PostMapping("/{postId}/report")
    public ResponseEntity<ApiResponse<ReportResponseDTO>> reportPost(Authentication authentication, @PathVariable("postId") Long postId, @Valid @RequestBody ReportRequestDTO requestDTO){
        Long userId = getLoginUserId(authentication);
        ReportResponseDTO responseDTO = postService.reportPost(userId, postId, requestDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>("post_report_success", responseDTO));
    }

    private Long getLoginUserId(Authentication authentication){
        return Long.valueOf(authentication.getName());
    }

    private UserRole getLoginUserRole(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority ->
                        authority.equals(UserRole.ROLE_ADMIN.name())
                                || authority.equals(UserRole.ROLE_USER.name())
                )
                .map(UserRole::valueOf)
                .findFirst()
                .orElse(UserRole.ROLE_USER);
    }
}
