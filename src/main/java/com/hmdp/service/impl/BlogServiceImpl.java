package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    private final IUserService userService;

    private final StringRedisTemplate stringRedisTemplate;

    @Autowired
    public BlogServiceImpl(UserServiceImpl userService,
                           StringRedisTemplate stringRedisTemplate
    ) {
        this.userService = userService;
        this.stringRedisTemplate = stringRedisTemplate;
    }
    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog ->{
            this.queryBlogUser(blog);
            this.isBlogLiked(blog);
        });
        return Result.ok(records);
    }

    @Override
    public Result queryBlogById(Long id) {
        //1.查询
        Blog blog = getById(id);
        if (blog == null){
            return Result.fail("笔记不存在");
        }

        //2.查询blog有关的用户
        queryBlogUser(blog);
        //3.查询blog的点赞数量
        isBlogLiked(blog);
        return Result.ok( blog);
    }

    private void isBlogLiked(Blog blog) {
        //1.获取登录用户
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            blog.setIsLike(false);
            return;
        }
        Long userId = user.getId();
        //2.判断用户是否已经点赞
        String key = RedisConstants.BLOG_LIKED_KEY + blog.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        blog.setIsLike(BooleanUtil.isTrue(score != null));
    }

    @Override
    public Result likeBlog(Long id) {
        //1.获取登录用户
        Long userId = UserHolder.getUser().getId();
        Blog blog = getById(id);
        if (blog == null) {
            return Result.fail("笔记不存在");
        }
        //2.判断用户是否已经点赞
        String key = RedisConstants.BLOG_LIKED_KEY + id;
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        if (score != null){
            //3.1 存在，则取消点赞
            //3.1.1 数据库点赞数-1
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id",id).gt("liked", 0).update();
            //3.1.2 redis集合remove
            if(isSuccess){
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            }
        }else {
            //3.2 不存在，则点赞
            //3.2.1 数据库点赞数+1
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id",id).update();
            //3.2.2 redis集合add
            if(isSuccess){
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            }
        }
        return Result.ok();
    }

    @Override
    public Result queryBlogLikes(Long id) {
        String key = RedisConstants.BLOG_LIKED_KEY + id;
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if (top5 == null || top5.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        List<Long> ids = top5.stream().
                map(Long::valueOf).
                collect(Collectors.toList());
        String idStr = StrUtil.join(",", ids);

        List<UserDTO> users = userService.query()
                .in("id", ids)
                .last("ORDER BY FIELD(id," + idStr + ")")
                .list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(users);
    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setIcon(user.getIcon());
        blog.setName(user.getNickName());
    }

}


