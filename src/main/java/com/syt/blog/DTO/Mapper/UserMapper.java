package com.syt.blog.DTO.Mapper;

import com.syt.blog.DTO.Request.UserRequest;
import com.syt.blog.DTO.Response.UserResponse;
import com.syt.blog.jooq.tables.pojos.BlogUsers;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper
public interface UserMapper {
    UserMapper INSTANCE = Mappers.getMapper(UserMapper.class);
    @Mapping(target = "username",ignore = true)
    @Mapping(target = "password",ignore = true)
    @Mapping(target = "email",ignore = true)
    @Mapping(target = "status",ignore = true)
    @Mapping(target = "lastLoginAt",ignore = true)
    @Mapping(target = "createdAt",ignore = true)
    @Mapping(target = "updatedAt",ignore = true)
    BlogUsers userDTOToBlogUsers(UserRequest userRequest);

    UserResponse BlogUsersToUserResponse (BlogUsers blogUsers);

    @Mapping(target = "nickname",ignore = true)
    @Mapping(target = "email",ignore = true)
    @Mapping(target = "avatar",ignore = true)
    @Mapping(target = "bio",ignore = true)
    @Mapping(target = "status",ignore = true)
    @Mapping(target = "lastLoginAt",ignore = true)
    @Mapping(target = "website",ignore = true)
    @Mapping(target = "github",ignore = true)
    @Mapping(target = "weibo",ignore = true)
    @Mapping(target = "createdAt",ignore = true)
    @Mapping(target = "updatedAt",ignore = true)
    BlogUsers Login(UserRequest userRequest);

}
