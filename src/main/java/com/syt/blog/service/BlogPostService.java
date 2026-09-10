package com.syt.blog.service;

import com.syt.blog.entity.BlogPost;
import com.syt.blog.repository.BlogPostRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文章业务逻辑实现类
 */
@Service
public class BlogPostService {

    private final BlogPostRepository blogPostRepository;

    public BlogPostService(BlogPostRepository blogPostRepository) {
        this.blogPostRepository = blogPostRepository;
    }

    private static final int STATUS_PUBLISHED = 1;

    
    @Transactional
    public BlogPost createPost(BlogPost post) {
        return blogPostRepository.save(post);
    }

    
    public BlogPost getPostById(Long id) {
        return blogPostRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("文章不存在，ID: " + id));
    }

    
    public List<BlogPost> getPublishedPosts() {
        return blogPostRepository.findByStatus(STATUS_PUBLISHED);
    }

    
    public List<BlogPost> getPostsByCategory(Long categoryId) {
        return blogPostRepository.findByCategoryIdAndStatus(categoryId, STATUS_PUBLISHED);
    }

    
    public List<BlogPost> searchPosts(String keyword) {
        return blogPostRepository.findByTitleContainingOrderByCreatedAtDesc(keyword);
    }

    
    @Transactional
    public BlogPost updatePost(Long id, BlogPost post) {
        BlogPost existing = getPostById(id);
        if (post.getTitle() != null) existing.setTitle(post.getTitle());
        if (post.getContentMd() != null) existing.setContentMd(post.getContentMd());
        if (post.getContentHtml() != null) existing.setContentHtml(post.getContentHtml());
        if (post.getSummary() != null) existing.setSummary(post.getSummary());
        if (post.getCategoryId() != null) existing.setCategoryId(post.getCategoryId());
        if (post.getCoverImage() != null) existing.setCoverImage(post.getCoverImage());
        if (post.getStatus() != null) existing.setStatus(post.getStatus());
        if (post.getIsTop() != null) existing.setIsTop(post.getIsTop());
        if (post.getAllowComment() != null) existing.setAllowComment(post.getAllowComment());
        if (post.getReadingTime() != null) existing.setReadingTime(post.getReadingTime());
        return blogPostRepository.save(existing);
    }

    
    @Transactional
    public void deletePost(Long id) {
        BlogPost post = getPostById(id);
        // 软删除：设置删除时间而非物理删除
        post.setDeletedAt(LocalDateTime.now());
        blogPostRepository.save(post);
    }

    
    @Transactional
    public BlogPost publishPost(Long id) {
        BlogPost post = getPostById(id);
        post.setStatus(STATUS_PUBLISHED);
        return blogPostRepository.save(post);
    }

    
    @Transactional
    public void incrementViewCount(Long id) {
        BlogPost post = getPostById(id);
        post.setViewCount(post.getViewCount() + 1);
        blogPostRepository.save(post);
    }
}
