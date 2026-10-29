package com.syt.blog.service;

import com.syt.blog.DTO.Response.UserResponse;
import com.syt.blog.common.BusinessException;
import com.syt.blog.jooq.tables.pojos.BlogUsers;
import com.syt.blog.repository.FollowsRepository;
import com.syt.blog.repository.UserRepository;
import com.syt.blog.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BlogFollowsService {
    private final FollowsRepository followsRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public String followUser(Integer followeeId) {
        Integer followerId=SecurityUtils.currentUserId();
        if (followeeId.equals(followerId)){
            throw new BusinessException(400,"不能关注自己");
        }
       isUser(followeeId);
        try {
            followsRepository.addFollows(followerId,followeeId);
        }catch (DataIntegrityViolationException e){
            throw new BusinessException(409,"已关注");
        }
        notificationService.createNotification(followeeId,followerId,"follow","关注了你","关注了你","");
        return "true";
    }


    public String deleteFollows(Integer followeeId) {
        Integer followerId=SecurityUtils.currentUserId();
        isUser(followeeId);
        // 只删除「当前用户 → 该被关注者」这一条，不能把 followerId 传错成 followeeId
        followsRepository.deleteFollows(followerId, followeeId);
        return "false";

    }

    public String getFollowStatus(Integer followeeId) {
        Integer followerId=SecurityUtils.currentUserId();
        isUser(followeeId);
        return followsRepository.getFollowStatus(followerId, followeeId) ? "true" : "false";
    }

    public List<UserResponse> getMineAll() {
        Integer followerId= SecurityUtils.currentUserId();
        List<Integer> followeeIds= followsRepository.getFollow(followerId);
        return userRepository.getFollowUser(followeeIds);
    }

    private void isUser(Integer followeeId){
        BlogUsers user= userRepository.findById(followeeId);
        if (user==null){
            throw new BusinessException(404,"用户不存在");
        }
    }
}
