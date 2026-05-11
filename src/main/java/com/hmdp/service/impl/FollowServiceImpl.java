package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */

@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Override
    public Result follow(Long followUserId, Boolean isFollow) {

        Long userId = UserHolder.getUser().getId();
        if (followUserId.equals(userId)) {
            return Result.fail("不能关注自己");
        }
        if (BooleanUtil.isTrue(isFollow)) {
            long existed = lambdaQuery()
                    .eq(Follow::getUserId, userId)
                    .eq(Follow::getFollowUserId, followUserId)
                    .count();
            if (existed == 0) {
                Follow follow = new Follow();
                follow.setUserId(userId);
                follow.setFollowUserId(followUserId);
                follow.setCreateTime(LocalDateTime.now());
                save(follow);
            }
        } else {
            remove(new LambdaQueryWrapper<Follow>()
                    .eq(Follow::getUserId, userId)
                    .eq(Follow::getFollowUserId, followUserId));
        }
        return Result.ok();
    }

    @Override
    public Result isFollow(Long followUserId) {
        Long userId = UserHolder.getUser().getId();
        long count = lambdaQuery()
                .eq(Follow::getUserId, userId)
                .eq(Follow::getFollowUserId, followUserId)
                .count();
        return Result.ok(count > 0);
    }
}
