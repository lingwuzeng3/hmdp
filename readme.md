## [黑马点评](https://github.com/lingwuzeng3/hmdp.git)

### day01
短信登录业务  
- 运用了**session**技术，记录用户验证码登信息
- 使用**Redis**优化session，解决集群分布问题
- 通过双重**Interceptor**进行登录校验
