package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;

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

    /**
     * 发送验证码ServiceImpl
     * @param phone
     * @param session
     * @return
     */
    @Override
    public Result sendCode(String phone, HttpSession session) {
        //先判断 电话号码的正确性 false 为有效号码
        if(RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("无效电话号码");
        }

        //随机生成验证码，存入会话
        String code = RandomUtil.randomNumbers(6);
        session.setAttribute("code",code);
        log.info("验证码为:{}",code);

        return Result.ok();
    }

    /**
     * 用户登录
     * @param loginForm
     * @param session
     * @return
     */
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {

        //1.检查电话是否合格
        String phone = loginForm.getPhone();
        if(RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("无效电话号码");
        }

        //2.校验验证码
        String in_code = loginForm.getCode();
        String true_code = session.getAttribute("code").toString();
        if(RegexUtils.isCodeInvalid(in_code) || !true_code.equals(in_code)){
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

        //4.把用户简单存入会话
        UserDTO userDTO = new UserDTO();
        BeanUtils.copyProperties(user,userDTO);
        session.setAttribute("user",userDTO);

        return Result.ok();

    }

}
