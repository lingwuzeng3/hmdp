package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
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
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdWorker redisIdWorker;

    @Transactional
    @Override
    public Result robseckillVoucher(Long voucherId) throws Exception {
        //1.查询优惠券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        if(voucher.getStock() < 1){
            return Result.fail("库存不够了");
        }
        if(voucher.getEndTime().isBefore(LocalDateTime.now()) ||
                voucher.getBeginTime().isAfter(LocalDateTime.now())){
            return Result.fail("活动无效");
        }

        //2.订单持久化
        long orderId = redisIdWorker.nextId("SECKILL_VOUCHER_ORDER");
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setUserId(UserHolder.getUser().getId());
        save(voucherOrder);

        //3.更新库存
        boolean flag = seckillVoucherService.update(
                new UpdateWrapper<SeckillVoucher>()
                .eq("voucher_id", voucherId).setSql("stock = stock - 1")
        );
        if (!flag){
            throw new Exception("创建秒杀券订单失败");
        }

        return Result.ok(orderId);
    }
}
