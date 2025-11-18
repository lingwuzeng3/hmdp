package com.hmdp.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * 包名：com.hmdp.utils
 * 用户：admin
 * 日期：2025-11-05
 * 项目名称：hm-dianping
 */

@Component
public class RedisIdWorker {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    //开始时间戳-11月1日
    private static final long BEGIN_TIMESTAMP = 1761955200;

    //序列号位数
    private static final long COUNT_BITS = 32L;

    //得到分布式唯一id :timestamp + 今日自增id序列号
    public long nextId(String keyPrefix){

        //1.生成时间戳,用时间差表示
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timeStamp = nowSecond - BEGIN_TIMESTAMP;

        //2.得到序列号，date更新序列号从1开始重新自增
        //这里redis充当了一个分布式原子计数器，我们只关心INCR的返回值，不关心Redis中存储的具体数值
        String date = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        Long count = stringRedisTemplate.opsForValue().increment("id:" + keyPrefix + ":" + date);

        //3.long固定64位，前32高位存时间戳，后32低位存序列号。能保证最高位为0
        return timeStamp << COUNT_BITS | count;
    }

    public static void main(String[] args){
        LocalDateTime time = LocalDateTime.of(2025,11,1,0,0,0);
        long second = time.toEpochSecond(ZoneOffset.UTC);
        System.out.println("second = " + second);
    }
}
