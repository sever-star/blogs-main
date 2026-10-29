package com.syt.blog.service;

import com.syt.blog.common.BusinessException;
import com.syt.blog.common.PageResult;
import com.syt.blog.common.ResourceNotFoundException;
import com.syt.blog.DTO.Mapper.PostMapper;
import com.syt.blog.DTO.Request.PostRequest;
import com.syt.blog.DTO.Response.BlogPostDetailResponse;
import com.syt.blog.DTO.Response.PostResponse;
import com.syt.blog.jooq.tables.records.BlogPostsRecord;
import com.syt.blog.repository.PostRepository;
import com.syt.blog.util.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
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
    private final NotificationService notificationService;

    public BlogPostService(ViewCountService viewCountService, PostRepository postRepository, NotificationService notificationService) {
        this.viewCountService = viewCountService;
        this.postRepository = postRepository;
        this.notificationService = notificationService;
    }

    private static final int STATUS_PUBLISHED = 1;

    /**
     * 创建文章
     */
    @Transactional
    public PostResponse createPost(PostRequest postRequest) {
        BlogPostsRecord blogPostsRecord = PostMapper.INSTANCE.postRequestToBlogPostsRecord(postRequest);
        Integer userId = SecurityUtils.currentUserId();
        blogPostsRecord.setUserId(userId);
        log.info("Creating post for user ID: {}", userId);
        // 插入与标签关联的持久化细节下沉到 repository，service 不再持有 DSLContext
        Integer postId = postRepository.insertPost(blogPostsRecord);
        log.info("Post created with ID: {}", postId);
        if (postId == null) {
            throw new RuntimeException("创建文章失败");
        }
        postRepository.saveTags(postId, postRequest.getTags());
        return postRepository.getPostAndCategory(postId);
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
        postRepository.updateReview(id, currentUserId, (short) STATUS_PUBLISHED, (short) STATUS_PUBLISHED, LocalDateTime.now());
        try{
            notificationService.createNotification(currentUserId, blogPostsRecord.getUserId(), "system", "文章已通过审核", "文章《"+blogPostsRecord.getTitle()+"》已通过审核", "/article/" + id);
        }catch (Exception e){
            log.warn("审核通过通知生成失败 postId={} actor={}", id, currentUserId, e);
        }
    }


    public void rejectPost(Integer id, String reason) {
        BlogPostsRecord blogPostsRecord = postRepository.getPostById(id);
        if (blogPostsRecord == null) {
            throw new ResourceNotFoundException("文章不存在");
        }
        if (blogPostsRecord.getReviewStatus() == 2) {
            throw new BusinessException(400, "文章已拒绝");
        }
        Integer currentUserId = SecurityUtils.currentUserId();
        postRepository.updateReview(id, currentUserId, (short) 0, (short) 2, LocalDateTime.now());
        try{
            notificationService.createNotification(currentUserId, blogPostsRecord.getUserId(), "system", "文章已被驳回", "文章《"+blogPostsRecord.getTitle()+"》已被驳回，原因："+reason, "/article/" + id);
        }catch (Exception e){
            log.warn("审核拒绝通知生成失败 postId={} actor={}", id, currentUserId, e);
        }
    }


    public int likePost(Integer postId) {
        Integer currentUserId = SecurityUtils.currentUserId();
        requireVisiblePost(postId, currentUserId);
        BlogPostsRecord post = postRepository.getPostById(postId);
        if (postRepository.findByLike(postId, currentUserId)) {
            throw new BusinessException(400, "文章已点赞");
        }
        postRepository.like(postId, currentUserId);
        // 通知文章作者（防自扰在 createNotification 内）；type 用前端约定的 like（非 post_like），
        // 通知失败不影响点赞主流程
        try {
            notificationService.createNotification(post.getUserId(), currentUserId, "like", "点赞了你的文章", "点赞了你的文章", "/article/" + postId);
        } catch (Exception e) {
            log.warn("点赞通知生成失败 postId={} actor={}", postId, currentUserId, e);
        }
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
        BlogPostsRecord post = postRepository.getPostById(postId);
        if (postRepository.findByFavorite(postId, currentUserId) != 0) {
            throw new BusinessException(400, "文章已收藏");
        }
        postRepository.favorite(postId, currentUserId);
        // type 用前端约定的 favorite（非 post_favorite）；通知失败不影响收藏主流程
        try {
            notificationService.createNotification(post.getUserId(), currentUserId, "favorite", "收藏了你的文章", "收藏了你的文章", "/article/" + postId);
        } catch (Exception e) {
            log.warn("收藏通知生成失败 postId={} actor={}", postId, currentUserId, e);
        }
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

    public List<PostResponse> getTopPosts(Integer limit) {
        return postRepository.getTopPosts(limit);
    }

    public void offlinePost(Integer id) {
        Integer currentUserId = SecurityUtils.currentUserId();
        BlogPostsRecord post = postRepository.getPostById(id);
        // requireVisiblePost：存在、未软删、审核通过；但它不看 status，
        // 已下架（status=2）的文章会校验通过被重复下架，需补 status 判断
        requireVisiblePost(id, currentUserId);
        if(!SecurityUtils.isAdmin()&&!currentUserId.equals(post.getUserId())){
            throw new BusinessException(403, "只有文章作者以及管理员可以下架");
        }
        if (post.getStatus() == null || post.getStatus() != 1) {
            throw new BusinessException(400, "仅已发布的文章可以下架");
        }
        postRepository.offPost(id);
        // 管理员下架他人文章时通知作者（作者自己下架 actor==receiver，防自扰自动跳过）
        try {
            notificationService.createNotification(post.getUserId(),currentUserId,"system","文章已下架","下架了你的文章《" + post.getTitle() + "》","/article/"+id);
        } catch (Exception e) {
            log.warn("下架通知生成失败 postId={} actor={}", id, currentUserId, e);
        }

    }

    public List<PostResponse> getLikePost() {
        Integer userId= SecurityUtils.currentUserId();
        if (userId==null){
            throw new BusinessException(401, "请先登录");
        }
        return postRepository.getLikePost(userId);
    }

    public List<PostResponse> getFavoritedPosts() {
        Integer userId= SecurityUtils.currentUserId();
        if (userId==null){
            throw new BusinessException(401, "请先登录");
        }
        return postRepository.getFavoritedPosts(userId);
    }
}
