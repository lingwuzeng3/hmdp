package com.hmdp.script;

import com.hmdp.utils.RedisConstants;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 秒杀 Lua：活动时间、一人一单（Set）、库存 DECR 原子预占。
 */
@Component
public class SeckillLuaExecutor {

    private static final DefaultRedisScript<Long> SCRIPT = new DefaultRedisScript<>();

    static {
        SCRIPT.setResultType(Long.class);
        Resource res = new ClassPathResource("lua/seckill_order.lua");
        try {
            SCRIPT.setScriptText(StreamUtils.copyToString(res.getInputStream(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load lua/seckill_order.lua", e);
        }
    }

    private final StringRedisTemplate stringRedisTemplate;

    public SeckillLuaExecutor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 预占资格成功后向 {@link RedisConstants#STREAM_ORDER_KEY} 写入一条下单消息。
     *
     * @param orderId 须由调用方预生成并与返回给客户端的订单号一致
     * @return 1 成功；-1 不在活动时间；-2 重复抢购；-3 库存不足；-4 Redis 数据缺失
     */
    public Long tryReserve(Long voucherId, Long userId, Long orderId) {
        String stockKey = RedisConstants.SECKILL_STOCK_KEY + voucherId;
        String ownersKey = RedisConstants.SECKILL_OWNERS_KEY + voucherId;
        String infoKey = RedisConstants.SECKILL_INFO_KEY + voucherId;
        long now = LocalDateTime.now().atZone(ZoneId.systemDefault()).toEpochSecond();
        List<String> keys = List.of(stockKey, ownersKey, infoKey, RedisConstants.STREAM_ORDER_KEY);
        return stringRedisTemplate.execute(SCRIPT, keys,
                String.valueOf(userId),
                String.valueOf(now),
                String.valueOf(voucherId),
                String.valueOf(orderId));
    }
}
