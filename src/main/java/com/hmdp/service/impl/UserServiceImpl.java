package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;


/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 发送验证码ServiceImpl
     *
     * @param phone
     * @return
     */
    @Override
    public Result sendCode(String phone) {
        //先判断 电话号码的正确性 false 为有效号码
        if(RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("无效电话号码");
        }

        //随机生成验证码，存入会话
        String code = RandomUtil.randomNumbers(6);
        stringRedisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY + phone,
                code,RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);
        log.info("phone:{},验证码为:{}",phone,code);

        return Result.ok();
    }

    /**
     * 用户登录
     *
     * @param loginForm
     * @return
     */
    @Override
    public Result login(LoginFormDTO loginForm) {

        //1.检查电话是否合格
        String phone = loginForm.getPhone();
        if(RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("无效电话号码");
        }

        //2.校验验证码
        String in_code = loginForm.getCode();
        String true_code = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY+phone);
        if(true_code == null || !true_code.equals(in_code)){
            return Result.fail("无效验证码");
        }

        //3.查看数据库是否有该用户数据
        User user = query().eq("phone", phone).one();
        if(user == null){
            //没有则创建用户,并存入数据库
            user = new User()
                .setPhone(phone)
                .setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10))
                .setCreateTime(LocalDateTime.now());
            save(user);
        }

        //4.将User对象转为HashMap存储
        UserDTO userDTO = new UserDTO();
        BeanUtil.copyProperties(user, userDTO);

        Map<String, Object> userMap = new HashMap<>();
        userMap.put("id", ""+userDTO.getId());
        userMap.put("nickName", userDTO.getNickName());
        userMap.put("icon", userDTO.getIcon());

        String token =  UUID.randomUUID().toString(true);
        String tokenKey = RedisConstants.LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey,userMap);
        stringRedisTemplate.expire(tokenKey,RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
        return Result.ok(token);

    }

}
