package com.syt.blog.DTO.Mapper;

import com.syt.blog.DTO.Request.LoginRequest;
import com.syt.blog.DTO.Request.RegisterRequest;
import com.syt.blog.DTO.Request.UpdateProfileRequest;
import com.syt.blog.DTO.Response.UserResponse;
import com.syt.blog.jooq.tables.pojos.BlogUsers;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.factory.Mappers;

/**
 * 用户相关的 DTO ↔ 实体映射
 * <p>
 * 三个入口方法都使用 {@code @BeanMapping(ignoreByDefault = true)}：
 * 默认一个字段都不映射，只有显式写出来的才会被赋值。
 * 这样即使 blog_users 将来加了新列，也不会因为「名字撞上了」而被动变成可写字段。
 */
@Mapper
public interface UserMapper {
    UserMapper INSTANCE = Mappers.getMapper(UserMapper.class);

    /**
     * 登录：只取用户名与密码，其余字段保持 null，不会写库
     */
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "username", source = "username")
    @Mapping(target = "password", source = "password")
    BlogUsers login(LoginRequest loginRequest);

    /**
     * 注册：用户名、密码、邮箱在库中均为 NOT NULL，必须由请求带入；
     * id / status / role / 时间戳交给数据库默认值。
     */
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "username", source = "username")
    @Mapping(target = "password", source = "password")
    @Mapping(target = "email", source = "email")
    @Mapping(target = "nickname", source = "nickname")
    @Mapping(target = "avatar", source = "avatar")
    @Mapping(target = "bio", source = "bio")
    BlogUsers register(RegisterRequest registerRequest);

    /**
     * 更新资料：只映射本人可改的展示类字段。
     * username / email / password / status / role 不在 {@link UpdateProfileRequest} 中，
     * 因此天然不会被映射。
     */
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "nickname", source = "nickname")
    @Mapping(target = "avatar", source = "avatar")
    @Mapping(target = "bio", source = "bio")
    @Mapping(target = "website", source = "website")
    @Mapping(target = "github", source = "github")
    @Mapping(target = "weibo", source = "weibo")
    BlogUsers updateProfile(UpdateProfileRequest updateProfileRequest);

    UserResponse BlogUsersToUserResponse(BlogUsers blogUsers);
}
