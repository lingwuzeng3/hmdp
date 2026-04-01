# hm-dianping（黑马点评）

基于黑马程序员 [黑马点评](https://www.bilibili.com/video/BV1NV411u7GE/?spm_id_from=333.337.search-card.all.click) 的 **Spring Boot 后端练习项目**：本地生活 / 商户点评类业务（用户、商户、优惠券、笔记、上传等）。

## 技术栈

| 类别 | 说明 |
|------|------|
| 框架 | Spring Boot 2.7、Spring Web |
| 数据 | MySQL、MyBatis-Plus |
| 缓存 / 会话 | Redis（`StringRedisTemplate`、Hash 存登录态） |
| 其他 | Hutool、Lombok、AspectJ（`@EnableAspectJAutoProxy`） |
| JDK | 17（见 `pom.xml`） |

## 功能完成情况（对照当前代码）

### 已实现

- **用户**：手机验证码（Redis）、登录（签发 token 写入 Redis）、**登出（删除 `login:token:{token}`）**、当前用户 `/me`、用户详情扩展信息 `/info/{id}`；请求头 `authorization` + `RefreshInterceptor` / `LoginInterceptor` 双拦截器。
- **商户**：按 id 查询（**逻辑过期** + 互斥锁异步重建，见 `ShopServiceImpl.queryById`）、列表筛选、新增/修改；更新店铺后会删缓存 key。
- **商户类型**：列表查询 + 写入 Redis（具体拼接/命中分支与课程实现可能存在差异，若缓存异常可优先查库逻辑排查）。
- **优惠券**：新增普通券/秒杀券、按店铺查券列表。
- **秒杀下单**：库存乐观更新（`stock > 0` 条件更新）、一人一单校验、`RedisIdWorker` 生成订单号、单机 `synchronized` 防同一用户并发（**非分布式锁**）。
- **笔记**：发布、点赞、我的笔记、热门笔记（热门列表会按 `user_id` 回填昵称/头像）。
- **上传**：笔记图片上传与删除（本地目录见 `SystemConstants.IMAGE_UPLOAD_DIR`）。

### 未实现 / 仅骨架

- `FollowController`、`BlogCommentsController`：无接口方法，仅有 `RequestMapping` 前缀；对应 `IFollowService`、`IBlogCommentsService` 为 MyBatis-Plus 默认实现，**关注、评论相关业务未接 HTTP**。
- **课程后续章节**（若视频/资料中有）：如 Feed 流、附近商户 Geo、签到等，需对照课程与 `RedisConstants` 中预留 key 自行核对是否落地。

### 实现上的注意点（非「未完成」，但需知悉）

- **热门笔记**：`tb_blog.user_id` 必须在 `tb_user` 中存在，否则回填用户时会 NPE；数据需保持一致。
- **秒杀**：`synchronized(userId.toString().intern())` 仅在单 JVM 内有效，多实例部署需改为分布式锁等方案。
- **店铺缓存**：`queryById` 当前启用的是**逻辑过期**方案；**互斥锁重建缓存**版本在 `ShopServiceImpl` 中已注释，可对照课程切换。

---

## 课程笔记

### day01

[短信登录业务](https://blog.csdn.net/qq_55926096/article/details/144827101)

- 运用了 **session** 技术，记录用户验证码等信息
- 使用 **Redis** 优化 session，解决集群分布问题
- 通过双重 **Interceptor** 进行 token 登录校验

### day02

[商户信息缓存](https://blog.csdn.net/qq_55926096/article/details/145450099)

- 学习了**缓存穿透**，并通过设置空字符串来解决
- 了解了**缓存击穿**和**缓存雪崩**，缓存雪崩可以通过分散设置不同的过期时间来防止，而缓存击穿主要有两种方式
  1. 互斥锁，用 Redis 中自带的 setnx 来进行串行等待保证数据的一致性，但影响性能。
  2. 逻辑过期，通过设置一个逻辑时间，到期后分出新线程修改，其余请求访问旧数据，不保证一致性，但性能更好。

### day03

**秒杀券抢购**

1. **全局 ID 生成器**：由时间戳差值 + 当日序列号等组成（Redis 自增），见 `RedisIdWorker`。
2. 1) **乐观锁**：允许并发查询，但在更新时检查数据是否被修改过，如果被修改则更新失败。  
   2) **悲观锁**：在查询库存时就直接锁定这条记录，直到整个扣减操作完成才释放锁。  
   3) 单机悲观锁在分布式环境失效，只对当前 JVM 有效。

## 运行提示

- 配置 MySQL、Redis 连接（`src/main/resources` 下配置文件）。
- 数据表与课程 SQL 一致时使用实体上的 `tb_*` 表名；若自行改表，需保证外键语义与代码一致（如 `tb_blog.user_id` → `tb_user.id`）。
