package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.script.SeckillLuaExecutor;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.MessageConstants;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.time.ZoneId;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    private final ISeckillVoucherService seckillVoucherService;
    private final RedisIdWorker redisIdWorker;
    private final SeckillLuaExecutor seckillLuaExecutor;
    private final StringRedisTemplate stringRedisTemplate;

    public VoucherOrderServiceImpl(ISeckillVoucherService seckillVoucherService,
                                   RedisIdWorker redisIdWorker,
                                   SeckillLuaExecutor seckillLuaExecutor,
                                   StringRedisTemplate stringRedisTemplate) {
        this.seckillVoucherService = seckillVoucherService;
        this.redisIdWorker = redisIdWorker;
        this.seckillLuaExecutor = seckillLuaExecutor;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public Result robseckillVoucher(Long voucherId) {
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        if (voucher == null) {
            return Result.fail("优惠券不存在");
        }
        if (voucher.getBeginTime() == null || voucher.getEndTime() == null) {
            return Result.fail(MessageConstants.VOUCHER_OUT_OF_TIME);
        }
        ensureSeckillRedisPresent(voucherId, voucher);

        Long userId = UserHolder.getUser().getId();
        long orderId = redisIdWorker.nextId("SECKILL_VOUCHER_ORDER");
        Long code = seckillLuaExecutor.tryReserve(voucherId, userId, orderId);
        if (code == null) {
            return Result.fail(MessageConstants.SECKILL_NOT_READY);
        }
        return switch (code.intValue()) {
            case 1 -> Result.ok(orderId);
            case -1 -> Result.fail(MessageConstants.VOUCHER_OUT_OF_TIME);
            case -2 -> {
                String ownersKey = RedisConstants.SECKILL_OWNERS_KEY + voucherId;
                Boolean member = stringRedisTemplate.opsForSet().isMember(ownersKey, userId.toString());
                Boolean ownersExists = stringRedisTemplate.hasKey(ownersKey);
                log.warn(
                        "Lua=-2 用户已抢购(一人一单 Set): voucherId={} userId={} ownersKey={} keyExists={} redisIsMember={}",
                        voucherId, userId, ownersKey, ownersExists, member);
                yield Result.fail(MessageConstants.REPEAT_BUY);
            }
            case -3 -> Result.fail(MessageConstants.STOCK_IS_NOT_ENOUGH);
            case -4 -> Result.fail(MessageConstants.SECKILL_NOT_READY);
            default -> Result.fail(MessageConstants.SECKILL_NOT_READY);
        };
    }

    @Override
    public Result createVoucherOrder(Long userId, Long voucherId) {
        long orderId = redisIdWorker.nextId("SECKILL_VOUCHER_ORDER");
        IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
        return proxy.createVoucherOrder(userId, voucherId, orderId);
    }

    @Override
    @Transactional
    public Result createVoucherOrder(Long userId, Long voucherId, Long orderId) {
        int cnt = count(new QueryWrapper<VoucherOrder>().eq("user_id", userId).eq("voucher_id", voucherId));
        if (cnt > 0) {
            return Result.fail(MessageConstants.REPEAT_BUY);
        }

        boolean flag = seckillVoucherService.update(
                new UpdateWrapper<SeckillVoucher>()
                        .eq("voucher_id", voucherId)
                        .gt("stock", 0)
                        .setSql("stock = stock - 1")
        );
        if (!flag) {
            return Result.fail(MessageConstants.STOCK_IS_NOT_ENOUGH);
        }

        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setUserId(userId);
        flag = save(voucherOrder);
        if (!flag) {
            return Result.fail(MessageConstants.SAVE_VOUCHER_FAIL);
        }

        return Result.ok(orderId);
    }

    /**
     * Lua 依赖 seckill:stock 与 seckill:info(begin/end)。仅同步库存或 Redis 被清空时会缺 info/stock，
     * 此处按库表补齐缺失项；已存在的库存键不覆盖，避免覆盖 Lua 已扣减的计数。
     */
    private void ensureSeckillRedisPresent(Long voucherId, SeckillVoucher sv) {
        String stockKey = RedisConstants.SECKILL_STOCK_KEY + voucherId;
        String infoKey = RedisConstants.SECKILL_INFO_KEY + voucherId;
        ZoneId zone = ZoneId.systemDefault();
        long beginEpoch = sv.getBeginTime().atZone(zone).toEpochSecond();
        long endEpoch = sv.getEndTime().atZone(zone).toEpochSecond();

        Boolean hasStock = stringRedisTemplate.hasKey(stockKey);
        if (Boolean.FALSE.equals(hasStock) && sv.getStock() != null) {
            stringRedisTemplate.opsForValue().set(stockKey, sv.getStock().toString());
        }
        Object begin = stringRedisTemplate.opsForHash().get(infoKey, "begin");
        Object end = stringRedisTemplate.opsForHash().get(infoKey, "end");
        if (begin == null || end == null) {
            stringRedisTemplate.opsForHash().put(infoKey, "begin", Long.toString(beginEpoch));
            stringRedisTemplate.opsForHash().put(infoKey, "end", Long.toString(endEpoch));
        }
    }
}
