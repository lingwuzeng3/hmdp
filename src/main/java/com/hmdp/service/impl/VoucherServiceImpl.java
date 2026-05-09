package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Voucher;
import com.hmdp.mapper.VoucherMapper;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherService;
import com.hmdp.utils.RedisConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherServiceImpl extends ServiceImpl<VoucherMapper, Voucher> implements IVoucherService {

    private final ISeckillVoucherService seckillVoucherService;
    private final StringRedisTemplate stringRedisTemplate;

    @Autowired
    public VoucherServiceImpl(
            ISeckillVoucherService seckillVoucherService,
            StringRedisTemplate stringRedisTemplate
    ) {
        this.seckillVoucherService = seckillVoucherService;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public Result queryVoucherOfShop(Long shopId) {
        // 查询优惠券信息
        List<Voucher> vouchers = getBaseMapper().queryVoucherOfShop(shopId);
        // 返回结果
        return Result.ok(vouchers);
    }

    @Override
    @Transactional
    public void addSeckillVoucher(Voucher voucher) {
        // 保存优惠券
        save(voucher);
        // 保存秒杀信息
        SeckillVoucher seckillVoucher = new SeckillVoucher();
        seckillVoucher.setVoucherId(voucher.getId());
        seckillVoucher.setStock(voucher.getStock());
        seckillVoucher.setBeginTime(voucher.getBeginTime());
        seckillVoucher.setEndTime(voucher.getEndTime());
        seckillVoucherService.save(seckillVoucher);

        Long vid = voucher.getId();
        ZoneId zone = ZoneId.systemDefault();
        long beginEpoch = voucher.getBeginTime().atZone(zone).toEpochSecond();
        long endEpoch = voucher.getEndTime().atZone(zone).toEpochSecond();

        // 覆盖 Redis 秒杀数据（同一 voucherId 重复上架时先清 owners，避免脏资格）
        stringRedisTemplate.delete(RedisConstants.SECKILL_OWNERS_KEY + vid);
        stringRedisTemplate.opsForValue().set(RedisConstants.SECKILL_STOCK_KEY + vid, voucher.getStock().toString());
        String infoKey = RedisConstants.SECKILL_INFO_KEY + vid;
        stringRedisTemplate.opsForHash().put(infoKey, "begin", Long.toString(beginEpoch));
        stringRedisTemplate.opsForHash().put(infoKey, "end", Long.toString(endEpoch));
    }
}
