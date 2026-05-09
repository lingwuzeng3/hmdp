package com.hmdp;

import com.hmdp.entity.SeckillVoucher;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.utils.RedisConstants;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

/**
 * 将 tb_seckill_voucher 库存同步到 Redis：<code>seckill:stock:{voucherId}</code> → 库存字符串。
 */
@SpringBootTest
class SeckillStockRedisSyncTest {

    @Autowired
    private ISeckillVoucherService seckillVoucherService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void syncSeckillStockFromDbToRedis() {
        List<SeckillVoucher> list = seckillVoucherService.list();
        for (SeckillVoucher sv : list) {
            if (sv.getVoucherId() == null || sv.getStock() == null) {
                continue;
            }
            String key = RedisConstants.SECKILL_STOCK_KEY + sv.getVoucherId();
            stringRedisTemplate.opsForValue().set(key, sv.getStock().toString());
        }
    }
}
