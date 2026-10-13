package com.syt.blog.service;

import com.syt.blog.common.BusinessException;
import com.syt.blog.common.PageResult;
import com.syt.blog.common.ResourceNotFoundException;
import com.syt.blog.DTO.Mapper.PostMapper;
import com.syt.blog.DTO.Request.PostRequest;
import com.syt.blog.DTO.Response.BlogPostDetailResponse;
import com.syt.blog.DTO.Response.PostResponse;
import com.syt.blog.jooq.tables.BlogPostTags;
import com.syt.blog.jooq.tables.records.BlogPostTagsRecord;
import com.syt.blog.jooq.tables.records.BlogPostsRecord;
import com.syt.blog.repository.PostRepository;
import com.syt.blog.util.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.log4j.Log4j;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文章业务逻辑实现类
 */
@Service
@Slf4j
public class BlogPostService {
    private final ViewCountService viewCountService;
    private final PostRepository postRepository;
    private final DSLContext dsl;

    public BlogPostService(ViewCountService viewCountService, PostRepository postRepository, DSLContext dsl) {
        this.viewCountService = viewCountService;
        this.postRepository = postRepository;
        this.dsl = dsl;
    }

    private static final int STATUS_PUBLISHED = 1;

    /**
     * 创建文章
     */
    @Transactional
    public PostResponse createPost(PostRequest postRequest) {
        // 可选字段兜底（见 PostRequest 注释）：没传则用默认值，避免插 NULL
        if (postRequest.getReadingTime() == null) {
            postRequest.setReadingTime(15);
        }
        if (postRequest.getAllowComment() == null) {
            postRequest.setAllowComment((short) 1);
        }
        BlogPostsRecord blogPostsRecord = PostMapper.INSTANCE.postRequestToBlogPostsRecord(postRequest);
        Integer id = SecurityUtils.currentUserId();
        blogPostsRecord.setUserId(id);
        log.info("Creating post for user ID: " + id);
        // MapStruct 默认用 new BlogPostsRecord() 造对象，结果 Record 没绑 Configuration，
        // 直接 record.insert() 会抛 "No JDBC Connection configured"。
        // jOOQ Record 实现了 Attachable，把 Spring 注入的 DSLContext attach 上即可。
        blogPostsRecord.attach(dsl.configuration());
        blogPostsRecord.insert();
        Integer Postid = blogPostsRecord.getId();
        log.info("Post created with ID: " + Postid);
        if (Postid == null) {
            throw new RuntimeException("创建文章失败");
        }
        BlogPostTagsRecord blogPostTagsRecord = new BlogPostTagsRecord();
        // 同上：手 new 的 Record 也要先 attach，否则第一条 tag 就 500
        blogPostTagsRecord.attach(dsl.configuration());
        blogPostTagsRecord.setPostId(Postid);
        for (Integer tagId : postRequest.getTags()){
            blogPostTagsRecord.setTagId(tagId);
            blogPostTagsRecord.insert();
        }
        PostResponse postResponse = postRepository.getPostAndCategory(Postid);
        return postResponse;
    }

    /**
     * 根据 ID 获取文章
     */
    public BlogPostDetailResponse getPostById(Integer id, HttpServletRequest request) {
        BlogPostDetailResponse postResponse = postRepository.findByIdPost(id);
        if (postResponse != null && postResponse.getId() != null) {
            // 查询返回后再计数，防刷检查全在 ViewCountService 内部
            viewCountService.recordView(postResponse.getId(), postResponse.getUserId(), request);
        }
        return postResponse;

    }

    /**
     * 审核文章
     */
    @Transactional
    public void approvePost(Integer id) {
        BlogPostsRecord blogPostsRecord = postRepository.getPostById(id);
        if (blogPostsRecord == null) {
            throw new ResourceNotFoundException("文章不存在");
        }
        if (blogPostsRecord.getReviewStatus() != 0) {
            throw new BusinessException(400, "文章已审核");
        }
        Integer currentUserId = SecurityUtils.currentUserId();
        blogPostsRecord.setReviewerId(currentUserId);
        blogPostsRecord.setStatus((short) STATUS_PUBLISHED);
        blogPostsRecord.setReviewStatus((short) STATUS_PUBLISHED);
        blogPostsRecord.setReviewedAt(LocalDateTime.now());
        blogPostsRecord.update();
    }


    public void rejectPost(Integer id) {
        BlogPostsRecord blogPostsRecord = postRepository.getPostById(id);
        if (blogPostsRecord == null) {
            throw new ResourceNotFoundException("文章不存在");
        }
        if (blogPostsRecord.getReviewStatus() == 2) {
            throw new BusinessException(400, "文章已拒绝");
        }
        Integer currentUserId = SecurityUtils.currentUserId();
        blogPostsRecord.setReviewerId(currentUserId);
        blogPostsRecord.setStatus((short) 0);
        blogPostsRecord.setReviewStatus((short) 2);
        blogPostsRecord.setReviewedAt(LocalDateTime.now());
        blogPostsRecord.update();
    }


    public int likePost(Integer postId) {
        Integer currentUserId = SecurityUtils.currentUserId();
        requireVisiblePost(postId, currentUserId);
        if (postRepository.findByLike(postId, currentUserId)) {
            throw new BusinessException(400, "文章已点赞");
        }
        postRepository.like(postId, currentUserId);
        return postRepository.countLikes(postId);
    }

    public int unlikePost(Integer postId) {
        Integer currentUserId = SecurityUtils.currentUserId();
        requireVisiblePost(postId, currentUserId);
        if (!postRepository.findByLike(postId, currentUserId)) {
            throw new BusinessException(400, "文章未点赞");
        }
        postRepository.unlike(postId, currentUserId);
        return postRepository.countLikes(postId);
    }
    /**
     * 获取已发布文章列表
     */
    public List<PostResponse> getPublishedPosts(Integer categoryId, Integer tagId, String keyword) {
        return postRepository.findPostCond(categoryId, tagId, keyword);
    }

    /**
     * 我的文章：本人全部稿件（草稿/待审核/已发布）。未登录 currentUserId 抛 401。
     */
    public List<PostResponse> getMyPosts() {
        return postRepository.findMyPosts(SecurityUtils.currentUserId());
    }

    /**
     * 获取待审核文章列表
     */
    public PageResult getPendingPosts(Integer page, Integer pageSize) {
        return postRepository.findPendingPosts(page, pageSize);
    }

    /**
     * 收藏文章
     * <p>
     * 条件：文章存在且可见（未软删、审核通过）；未登录由 SecurityConfig 拦成 401。
     * 返回**操作后**该文章的收藏总次数（以收藏明细表重算，并同步 fav_count 冗余列）。
     */
    @Transactional
    public int favoritePost(Integer postId) {
        Integer currentUserId = SecurityUtils.currentUserId();
        requireVisiblePost(postId, currentUserId);
        if (postRepository.findByFavorite(postId, currentUserId) != 0) {
            throw new BusinessException(400, "文章已收藏");
        }
        postRepository.favorite(postId, currentUserId);
        return syncFavCount(postId);
    }

    /**
     * 取消收藏文章，返回操作后该文章的收藏总次数
     */
    @Transactional
    public int unfavoritePost(Integer postId) {
        if (postRepository.getPostById(postId) == null) {
            throw new ResourceNotFoundException("文章不存在");
        }
        Integer currentUserId = SecurityUtils.currentUserId();
        if (postRepository.findByFavorite(postId, currentUserId) == 0) {
            throw new ResourceNotFoundException("未收藏该文章");
        }
        postRepository.unfavorite(postId, currentUserId);
        return syncFavCount(postId);
    }

    /** 收藏前置条件：文章存在、未软删、审核通过（与 updateViewCount 的可见性口径一致） */
    private void requireVisiblePost(Integer postId, Integer currentUserId) {
        BlogPostsRecord post = postRepository.getPostById(postId);
        if (post == null
                || post.getDeletedAt() != null
                || post.getReviewStatus() == null
                || post.getReviewStatus() != 1) {
            throw new ResourceNotFoundException("文章不存在或已下架");
        }
        if (currentUserId==null){
            throw new BusinessException(401, "请先登录");
        }
    }

    /** 以收藏明细表为准重算 blog_posts.fav_count 冗余列，返回最新总数 */
    private int syncFavCount(Integer postId) {
        int total = postRepository.countFavorites(postId);
        postRepository.updateFavCount(postId, total);
        return total;
    }
    /**
     * 更新文章
     */
    @Transactional
    public PostResponse updatePost(Integer id, PostRequest postRequest) {
        PostResponse existing = getPostById(id,null);
        if (existing == null) {
            throw new ResourceNotFoundException("文章不存在");
        }
        // 权限校验：仅文章作者可编辑（与 API 文档 403 约定一致）。
        // SecurityConfig 只保证「登录可进」，改别人的稿（含管理员改他人稿）仍会被下面这行挡掉。
        Integer currentUserId = SecurityUtils.currentUserId();
        if (existing.getUserId() == null || !existing.getUserId().equals(currentUserId)) {
            throw new BusinessException(403, "只有文章作者可以编辑");
        }
        if (postRequest.getTitle() != null) {
            existing.setTitle(postRequest.getTitle());
        }
        if (postRequest.getContentMd() != null) {
            existing.setContentMd(postRequest.getContentMd());
        }
        if (postRequest.getContentHtml() != null) {
            existing.setContentHtml(postRequest.getContentHtml());
        }
        if (postRequest.getSummary() != null) {
            existing.setSummary(postRequest.getSummary());
        }
        if (postRequest.getCoverImage() != null) {
            existing.setCoverImage(postRequest.getCoverImage());
        }
        if (postRequest.getAllowComment() != null) {
            existing.setAllowComment(postRequest.getAllowComment());
        }
        existing.setReadingTime((short) 15);
        // 重新提交审核 = review_status 归 0（待审核），approve 时才置 1。
        // 发布状态按新语义（0草稿/1已发布/2已下架）：已发布的编辑期间先下架待审，
        // 草稿保持 0——历史逻辑无条件置 2 会把草稿也标成「已下架」
        existing.setReviewStatus((short) 0);
        if (existing.getStatus() == null || existing.getStatus() != 0) {
            existing.setStatus((short) 2);
        }
        postRepository.save(id, existing, postRequest);
        return getPostById(id,null);
    }

    /**
     * 删除文章
     */
    @Transactional
    public void deletePost(Integer id) {
        PostResponse post = getPostById(id,null);
        if(post == null){
            throw new ResourceNotFoundException("文章不存在");
        }
        // 权限校验：仅文章作者可删除（与 API 文档 403 约定一致）。
        Integer currentUserId = SecurityUtils.currentUserId();
        if (post.getUserId() == null || !post.getUserId().equals(currentUserId)||!SecurityUtils.isAdmin()) {
            throw new BusinessException(403, "只有文章作者以及管理员可以删除");
        }
        // 软删除：设置删除时间而非物理删除
        post.setDeletedAt(LocalDateTime.now());
    }


    
    @Transactional
    public void incrementViewCount(Integer id) {
        postRepository.updateViewCount(id);
    }
}
