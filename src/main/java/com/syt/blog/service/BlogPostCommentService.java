package com.syt.blog.service;

import com.syt.blog.DTO.Mapper.CommentMapper;
import com.syt.blog.DTO.Response.CommentsResponse;
import com.syt.blog.jooq.tables.pojos.BlogComments;
import com.syt.blog.jooq.tables.pojos.BlogUsers;
import com.syt.blog.jooq.tables.records.BlogCommentsRecord;
import com.syt.blog.jooq.tables.records.BlogPostsRecord;
import com.syt.blog.repository.PostCommentRepository;
import com.syt.blog.repository.PostRepository;
import com.syt.blog.repository.UserRepository;
import com.syt.blog.util.SecurityUtils;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;


    @Service
    @RequiredArgsConstructor
    public class BlogPostCommentService {
        private final PostCommentRepository postCommentRepository;
        private final PostRepository postRepository;
        private final UserRepository userRepository;


        public List<CommentsResponse> listComments(Integer postId) {
            BlogPostsRecord post = postRepository.getPostById(postId);
            if (post==null) {
                throw new IllegalArgumentException("文章不存在");
            }
            List<BlogCommentsRecord> comments = postCommentRepository.findApprovedByPostId(postId);
            //list.of()快速创建不可变空列表
            if (comments==null) {return List.of();}

            Integer userId= SecurityUtils.currentUser()!=null?SecurityUtils.currentUserId():null;
            //获取评论id转为list列表
            List<Long> ids=comments.stream().map(BlogCommentsRecord::getId).toList();

            Map<Long,Integer> likeCountMap = postCommentRepository.countLikesByCommentIds(ids);

            Set<Long> likedIds =userId==null?Set.of():postCommentRepository.findLikedCommentIds(ids,userId);
            //linkedHashMap保持插入顺序
            Map<Long,CommentsResponse> toMap=new LinkedHashMap<>();
            /**获取顶级评论
             * sorted:按创建时间倒序排序,默认为升序，reversed反转为倒序
             * */
            comments.stream()
                    .filter(r->r.getParentId()==null||r.getParentId()==0L)
                    .sorted(Comparator.comparing(BlogCommentsRecord::getCreatedAt).reversed())
                    .forEach(r->toMap.put(r.getId(),toVo(r,likeCountMap,likedIds)));
            comments.stream()
                    .filter(r->r.getParentId()!=null&&r.getParentId()!=0L)
                    .sorted(Comparator.comparing(BlogCommentsRecord::getCreatedAt))
                    .forEach(r->{
                        CommentsResponse parent=toMap.get(r.getParentId());
                        //bug修复:在tovo方法中设置replies为list.of(),导致数据无法添加
                        if (parent!=null) {
                            parent.getReplies().add(toVo(r,likeCountMap,likedIds));
                        }
                    });
            return new ArrayList<>(toMap.values());
        }

        private CommentsResponse toVo(BlogCommentsRecord comment, Map<Long,Integer> likeCountMap, Set<Long> likedIds) {
            CommentsResponse vo=new CommentsResponse();
            vo.setId(comment.getId());
            vo.setArticleId(comment.getPostId());
            vo.setUserId(comment.getUserId());
            vo.setParentId(comment.getParentId());
            vo.setReplyToId(comment.getReplyToId());
            vo.setNickname(comment.getNickname());
            vo.setAvatar(comment.getAvatar());
            vo.setContent(comment.getContent());
            //获取点赞数
            vo.setLikeCount(likeCountMap.getOrDefault(comment.getId(),0));
            vo.setIsAdmin(comment.getIsAdmin());
            vo.setCreatedAt(comment.getCreatedAt());
            vo.setReplies(new ArrayList<>());
            //判断是否点赞该评论
            vo.setLiked(likedIds.contains(comment.getId()));
            return vo;
        }

        public CommentsResponse createComment(Integer postId, BlogComments comment) {
            BlogPostsRecord post = postRepository.getPostById(postId);
            if (post==null) {
                throw new IllegalArgumentException("文章不存在");
            }
            if(post.getAllowComment()==0){
                throw new IllegalArgumentException("文章已关闭评论");
            }
            Integer userId= SecurityUtils.currentUserId();
            BlogUsers user=userRepository.findById(userId);
            comment.setNickname(user.getNickname());
            comment.setAvatar(user.getAvatar());
            comment.setContent(comment.getContent());
            comment.setUserId(userId);
            comment.setPostId(postId);
            if (comment.getParentId()==null){
                comment.setParentId(0L);
            }
            if (comment.getReplyToId()==null){
                comment.setReplyToId(0L);
            }
            Integer id=post.getUserId();
            if (userId .equals(id)) {
                comment.setIsAdmin((short) 1);
            } else {
                comment.setIsAdmin((short) 0);
            };
            postCommentRepository.save(comment);
            return CommentMapper.INSTANCE.BlogCommentsToCommentsResponse(comment);
        }

        public void deleteComment(Integer commentId, Integer postId) {
            Integer userId= SecurityUtils.currentUserId();
            BlogUsers user = userRepository.findById(userId);
            BlogPostsRecord post = postRepository.getPostById(postId);
            if (post==null) {
                throw new IllegalArgumentException("文章不存在");
            }
            BlogCommentsRecord comment = postCommentRepository.getCommentById(commentId);

            if (comment==null) {
                throw new IllegalArgumentException("评论不存在");
            }
            if(!comment.getPostId().equals(postId)){
                throw new IllegalArgumentException("评论所属文章与请求文章不一致");
            }
            if (!comment.getUserId().equals(userId)&&!user.getRole().equals("ADMIN")&&!post.getUserId().equals(userId)) {
                throw new IllegalArgumentException("您没有权限删除该评论");
            }
            postCommentRepository.deleteComments(commentId);

        }

        public Integer likeComment(Integer commentId, @NotNull Integer postId) {
            Integer userId= SecurityUtils.currentUserId();
            BlogCommentsRecord comment = postCommentRepository.getCommentById(commentId);
            if (comment==null) {
                throw new IllegalArgumentException("评论不存在");
            }
            Integer likeCount= postCommentRepository.saveLike(Long.valueOf(commentId),userId);
            return likeCount;
        }

        public Integer cancelLikeComment(Integer commentId, Integer postId) {
            Integer userId= SecurityUtils.currentUserId();
            BlogCommentsRecord comment = postCommentRepository.getCommentById(commentId);
            if (comment==null) {
                throw new IllegalArgumentException("评论不存在");
            }
            Integer likeCount= postCommentRepository.deleteLike(Long.valueOf(commentId),userId);
            return likeCount;
        }
    }


