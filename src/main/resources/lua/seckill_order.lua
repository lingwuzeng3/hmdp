-- KEYS[1]=stock KEYS[2]=owners KEYS[3]=info KEYS[4]=stream.orders
-- ARGV[1]=userId ARGV[2]=now_epoch_seconds ARGV[3]=voucherId ARGV[4]=orderId
local ownersKey = KEYS[2]
local infoKey = KEYS[3]
local streamKey = KEYS[4]
local userId = ARGV[1]
local now = tonumber(ARGV[2])
local voucherId = ARGV[3]
local orderId = ARGV[4]

local beginStr = redis.call('HGET', infoKey, 'begin')
local endStr = redis.call('HGET', infoKey, 'end')
if beginStr == false or endStr == false then
    return -4
end
local beginSec = tonumber(beginStr)
local endSec = tonumber(endStr)
if now < beginSec or now > endSec then
    return -1
end
if redis.call('SISMEMBER', ownersKey, userId) == 1 then
    return -2
end
local stockVal = redis.call('GET', KEYS[1])
if stockVal == false then
    return -4
end
local stockNum = tonumber(stockVal)
if stockNum == nil or stockNum < 1 then
    return -3
end
redis.call('DECR', KEYS[1])
local added = redis.call('SADD', ownersKey, userId)
if added == 0 then
    redis.call('INCR', KEYS[1])
    return -2
end
redis.call('XADD', streamKey, '*', 'voucherId', voucherId, 'userId', userId, 'orderId', orderId)
return 1
