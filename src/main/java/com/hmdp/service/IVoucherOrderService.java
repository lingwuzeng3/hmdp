package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    Result robseckillVoucher(Long voucherId);

    Result createVoucherOrder(Long userId, Long voucherId);

    /**
     * 秒杀异步落库：orderId 须与请求路径返回给客户端的一致。
     */
    Result createVoucherOrder(Long userId, Long voucherId, Long orderId);
}
