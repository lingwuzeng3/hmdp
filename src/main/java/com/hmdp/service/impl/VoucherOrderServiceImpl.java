package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.MessageConstants;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

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

    private final ISeckillVoucherService seckillVoucherService;
    private final RedisIdWorker redisIdWorker;
    private final RedissonClient redissonClient;

    @Autowired
    public VoucherOrderServiceImpl(ISeckillVoucherService seckillVoucherService,
                                   RedisIdWorker redisIdWorker,
                                   RedissonClient redissonClient) {
        this.seckillVoucherService = seckillVoucherService;
        this.redisIdWorker = redisIdWorker;
        this.redissonClient = redissonClient;
    }

    @Override
    public Result robseckillVoucher(Long voucherId) {
        //1.查询优惠券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        if(voucher.getStock() < 1){
            return Result.fail(MessageConstants.STOCK_IS_NOT_ENOUGH);
        }
        if(voucher.getEndTime().isBefore(LocalDateTime.now()) ||
                voucher.getBeginTime().isAfter(LocalDateTime.now())){
            return Result.fail(MessageConstants.VOUCHER_OUT_OF_TIME);
        }

        //创建订单,用代理来保证嵌套事务的正常运行
        Long userId = UserHolder.getUser().getId();

        // 使用voucherId作为锁的key，而不是userId，确保对同一张券的互斥访问
        String lockKey = "lock:order:" + voucherId;
        RLock lock = redissonClient.getLock(lockKey);
        
        // 尝试获取锁，设置等待时间和租约时间
        boolean isLock = false;
        try {
            // 等待最多1秒，锁自动释放时间10秒
            isLock = lock.tryLock(1, 10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.fail("获取锁失败");
        }
        
        if(!isLock){
            return Result.fail(MessageConstants.REPEAT_BUY);
        }
        
        try{
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(userId,voucherId);
        } finally {
            // 只有当前线程持有锁时才释放
            if(lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }

    }

    @Transactional
    public Result createVoucherOrder(Long userId,Long voucherId) {
        //2.判断是否第一单，一个用户同种券只能抢一张

        int cnt = count(new QueryWrapper<VoucherOrder>().eq("user_id",userId).eq("voucher_id",voucherId));
        if(cnt > 0){
            return Result.fail(MessageConstants.REPEAT_BUY);
        }

        //3.更新库存
        boolean flag = seckillVoucherService.update(
                new UpdateWrapper<SeckillVoucher>()
                        .eq("voucher_id", voucherId).gt("stock",0).setSql("stock = stock - 1")
        );
        if (!flag){
            return Result.fail(MessageConstants.STOCK_IS_NOT_ENOUGH);
        }

        //4.订单持久化
        long orderId = redisIdWorker.nextId("SECKILL_VOUCHER_ORDER");
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setUserId(userId);
        flag = save(voucherOrder);
        if (!flag){
            return Result.fail(MessageConstants.SAVE_VOUCHER_FAIL);
        }

        return Result.ok(orderId);
    }
}
