package com.hmdp.utils;

/**
 * 包名：com.hmdp.utils
 * 用户：admin
 * 日期：2025-10-18
 * 项目名称：hm-dianping
 */
public class MessageConstants {
    public static final String SHOP_NOT_EXIST = "店铺信息不存在";
    public static final String UNUSE_CODE = "无效验证码";
    public static final String UNUSE_PHONE = "无效电话号码";
    public static final String REPEAT_BUY = "用户已抢购";
    public static final String STOCK_IS_NOT_ENOUGH = "库存不够了";
    public static final String SAVE_VOUCHER_FAIL = "秒杀券扣减失败";
    public static final String VOUCHER_OUT_OF_TIME = "活动无效";
    public static final String LOCK_FAIL = "获取锁失败";
    /** Redis 缺少秒杀缓存或未预热（库存/info） */
    public static final String SECKILL_NOT_READY = "秒杀未就绪";
}
