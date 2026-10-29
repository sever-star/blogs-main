package com.syt.blog.controller;

import com.syt.blog.DTO.Response.UserResponse;
import com.syt.blog.common.Result;
import com.syt.blog.service.BlogFollowsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/follows")
@RequiredArgsConstructor
public class BlogFollowsController {
    private final BlogFollowsService blogFollowsService;

    /**
     * 关注用户
     */
    @PostMapping("/{userId}")
    public Result<Map<String,String>> followUser(@PathVariable("userId") Integer followeeId) {
        Map<String,String> result=new HashMap<>();
        String status= blogFollowsService.followUser(followeeId);
        result.put("followed",status);
        return Result.success(result);
    }

    /**
     * 取消关注
     */
    @DeleteMapping("/{userId}")
    public Result<Map<String,String>> deleteFollows(@PathVariable("userId") Integer followeeId) {
        Map<String,String> result=new HashMap<>();
        String status=blogFollowsService.deleteFollows(followeeId);
        result.put("followed",status);
        return Result.success(result);
    }

    /**
     * 获取关注状态
     */
    @GetMapping("/{userId}/status")
    public Result<Map<String,String>> getFollowStatus(@PathVariable("userId") Integer followeeId) {
        Map<String,String> result=new HashMap<>();
        String status=blogFollowsService.getFollowStatus(followeeId);
        result.put("followed",status);
        return Result.success(result);
    }

    /**
     * 获取关注列表
     */
    @GetMapping("/mine")
    public Result<List<UserResponse>> getMineAll(){
        List<UserResponse> result=blogFollowsService.getMineAll();
        return Result.success(result);
    }



}
