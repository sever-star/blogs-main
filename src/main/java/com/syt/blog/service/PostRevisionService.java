package com.syt.blog.service;

import com.syt.blog.DTO.Request.PostRequest;
import com.syt.blog.DTO.Response.BlogPostDetailResponse;
import com.syt.blog.DTO.Response.PostResponse;
import com.syt.blog.DTO.Response.PostRevisionResponse;
import com.syt.blog.common.BusinessException;
import com.syt.blog.common.Result;
import com.syt.blog.jooq.tables.pojos.BlogUsers;
import com.syt.blog.jooq.tables.records.BlogPostsRecord;
import com.syt.blog.jooq.tables.records.BlogUsersRecord;
import com.syt.blog.repository.PostRepository;
import com.syt.blog.repository.PostRevisionRepository;
import com.syt.blog.repository.UserRepository;
import com.syt.blog.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PostRevisionService {
    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final PostRevisionRepository postRevisionRepository;
    private final BlogPostService postService;

    public List<PostRevisionResponse> getRevision(Long postId){
        Integer currentUserId = SecurityUtils.currentUserId();
        BlogPostsRecord post= postRepository.getPostById(Math.toIntExact(postId));
        if(post==null){
            throw new BusinessException(404,"文章不存在");
        }
        if (!post.getUserId().equals(currentUserId)) {
            throw new BusinessException(403,"仅文章作者可操作");
        }
        List<PostRevisionResponse> list=postRevisionRepository.findRevisions(postId,currentUserId);
        return list;
    }

    public PostRevisionResponse findRevision(Long postId, Long revId) {
        Integer currentUserId = SecurityUtils.currentUserId();
        PostRevisionResponse response= postRevisionRepository.findOneRevision(postId,revId);
        if(response==null){
            throw new BusinessException(404,"文章不存在");
        }
        if (!response.getEditedBy().equals(currentUserId)) {
            throw new BusinessException(403, "仅文章作者可操作");
        }
        return response;
    }

    public PostResponse rollbackPost(Long id, Long revId) {
        Integer currentUserId = SecurityUtils.currentUserId();
        BlogPostDetailResponse post = postRepository.findByIdPost(Math.toIntExact(id));
        if (post == null) throw new BusinessException(404, "文章不存在");
        if (!post.getUserId().equals(currentUserId)) throw new BusinessException(403, "仅文章作者可操作");

        PostRequest revision = postRevisionRepository.addRevision(post, revId, currentUserId); // 先存档当前状态
        Short status = (post.getStatus() != null && post.getStatus() == 1) ? (short) 2 : (short) 0;
        postRevisionRepository.applyRevisionToPost(Math.toIntExact(id), revision, status, (short) 0);

        return postRepository.findByIdPost(Math.toIntExact(id));
    }

}
