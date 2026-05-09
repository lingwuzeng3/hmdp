package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.utils.RedisConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 从 Redis Stream {@link RedisConstants#STREAM_ORDER_KEY} 读取秒杀下单消息并异步落库。
 */
@Slf4j
@Component
public class SeckillOrderConsumer {

    private final StringRedisTemplate stringRedisTemplate;
    private final IVoucherOrderService voucherOrderService;
    private ExecutorService executor;

    public SeckillOrderConsumer(StringRedisTemplate stringRedisTemplate,
                                IVoucherOrderService voucherOrderService) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.voucherOrderService = voucherOrderService;
    }

    @PostConstruct
    public void start() {
        ensureConsumerGroup();
        executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "seckill-stream-consumer");
            t.setDaemon(false);
            return t;
        });
        executor.submit(this::consumeLoop);
    }

    /**
     * MKSTREAM 确保 Stream 存在；组已存在时忽略 BUSYGROUP。
     */
    private void ensureConsumerGroup() {
        try {
            stringRedisTemplate.execute((RedisCallback<Void>) conn -> {
                byte[] key = RedisConstants.STREAM_ORDER_KEY.getBytes(StandardCharsets.UTF_8);
                conn.streamCommands().xGroupCreate(key, RedisConstants.STREAM_ORDER_GROUP,
                        ReadOffset.from("0"), true);
                return null;
            });
        } catch (InvalidDataAccessApiUsageException e) {
            if (!isBusyGroup(e)) {
                log.warn("创建秒杀 Stream 消费组: {}", e.getMessage());
            }
        } catch (Exception e) {
            if (!isBusyGroup(e)) {
                log.warn("创建秒杀 Stream 消费组失败（若组已存在可忽略）: {}", e.toString());
            }
        }
    }

    private static boolean isBusyGroup(Throwable e) {
        String msg = e.getMessage();
        if (msg != null && msg.contains("BUSYGROUP")) {
            return true;
        }
        Throwable c = e.getCause();
        return c != null && isBusyGroup(c);
    }

    private void consumeLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                List<MapRecord<String, Object, Object>> records = stringRedisTemplate.opsForStream().read(
                        Consumer.from(RedisConstants.STREAM_ORDER_GROUP, RedisConstants.STREAM_ORDER_CONSUMER),
                        StreamReadOptions.empty().count(10).block(Duration.ofSeconds(5)),
                        StreamOffset.create(RedisConstants.STREAM_ORDER_KEY, ReadOffset.lastConsumed())
                );
                if (records == null || records.isEmpty()) {
                    continue;
                }
                for (MapRecord<String, Object, Object> record : records) {
                    processRecord(record);
                }
            } catch (Exception e) {
                log.error("秒杀 Stream 消费异常", e);
            }
        }
    }

    private void processRecord(MapRecord<String, Object, Object> record) {
        try {
            Map<Object, Object> raw = record.getValue();
            String voucherIdStr = Objects.toString(raw.get("voucherId"), null);
            String userIdStr = Objects.toString(raw.get("userId"), null);
            String orderIdStr = Objects.toString(raw.get("orderId"), null);
            if (voucherIdStr == null || userIdStr == null || orderIdStr == null) {
                log.warn("秒杀 Stream 消息字段不完整 recordId={} body={}", record.getId(), raw);
                stringRedisTemplate.opsForStream().acknowledge(RedisConstants.STREAM_ORDER_GROUP, record);
                return;
            }
            long voucherId = Long.parseLong(voucherIdStr);
            long userId = Long.parseLong(userIdStr);
            long orderId = Long.parseLong(orderIdStr);
            Result r = voucherOrderService.createVoucherOrder(userId, voucherId, orderId);
            if (Boolean.FALSE.equals(r.getSuccess())) {
                log.warn("异步秒杀下单失败 orderId={} userId={} voucherId={} msg={}",
                        orderId, userId, voucherId, r.getErrorMsg());
            }
            stringRedisTemplate.opsForStream().acknowledge(RedisConstants.STREAM_ORDER_GROUP, record);
        } catch (Exception e) {
            log.error("处理秒杀 Stream 消息失败 recordId={}", record.getId(), e);
        }
    }

    @PreDestroy
    public void stop() {
        if (executor != null) {
            executor.shutdownNow();
            try {
                executor.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
