package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


/**
 * 包名：com.hmdp.utils
 * 用户：admin
 * 日期：2025-10-17
 * 项目名称：hm-dianping
 */
public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (UserHolder.getUser() == null) {
            // ThreadLocal中没有用户信息，拦截请求
            response.setStatus(401);
            return false;
        }
        // 放行请求
        return true;
    }

}
