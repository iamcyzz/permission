package com.mmall.service;

import com.google.common.base.Joiner;
import com.mmall.beans.CacheKeyConstants;
import com.mmall.util.JsonMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import redis.clients.jedis.ShardedJedis;

import javax.annotation.Resource;

@Service
@Slf4j
//这个是定义一个Cache
public class SysCacheService {

    //导入redis的连接池
    @Resource(name = "redisPool")
    private RedisPool redisPool;


    //保存cacha的方法
    //参数：保存的value,过期时间，指定的前缀
    public void saveCache(String toSavedValue, int timeoutSeconds, CacheKeyConstants prefix) {
        saveCache(toSavedValue, timeoutSeconds, prefix, null);
    }


    //进行保存操作的时候，需要传很多的值，我们再增加一个方法,为了适应redis的api封装的，可以传入多个key值
    public void saveCache(String toSavedValue, int timeoutSeconds, CacheKeyConstants prefix, String... keys) {
        //如果传入要保持的值是空，直接就返回了,不进行操作
        if (toSavedValue == null) {
            return;
        }
        //否则就需要拿到redis的连接
        //定义一个对象，设置为空
        ShardedJedis shardedJedis = null;
        try {
            //然后尝试拿到redis的连接
            //然后就要生成缓存key,生成出我们定义key
            String cacheKey = generateCacheKey(prefix, keys);
            //拿到redis连接,生成一个实例
            shardedJedis = redisPool.instance();
            //拿到实例方法进行保存：传入自定义key，超时时间，保存的值
            shardedJedis.setex(cacheKey, timeoutSeconds, toSavedValue);
        } catch (Exception e) {
            log.error("save cache exception, prefix:{}, keys:{}", prefix.name(), JsonMapper.obj2String(keys), e);
        } finally {
            //关闭连接redis资源
            redisPool.safeClose(shardedJedis);
        }
    }

    //从redis里面获取到key的值，返回来的值是string，然后再由调用的人后期再决定如何处理使用这个得到的string
    public String getFromCache(CacheKeyConstants prefix, String... keys) {
        //定义一个连接为空
        ShardedJedis shardedJedis = null;
        //然后就要生成缓存key,生成出我们定义key,这样才能从redis拿到我们存的值
        String cacheKey = generateCacheKey(prefix, keys);
        try {
            //拿到redis连接,生成一个实例
            shardedJedis = redisPool.instance();
            //拿到实例方法进行查询：传入自定义key
            String value = shardedJedis.get(cacheKey);
            //获取到value值传回
            return value;
        } catch (Exception e) {
            log.error("get from cache exception, prefix:{}, keys:{}", prefix.name(), JsonMapper.obj2String(keys), e);
            return null;
        } finally {
            //无论成功失败，都是需要关闭的连接
            redisPool.safeClose(shardedJedis);
        }
    }

    //上面每次对redis进行保存数据的时候，都需要生成一个对应的缓存的key名称
    //传入的参数是:自定义前缀和对应的key
    //新key的生成规则就是自定义前缀+redis的key
    private String generateCacheKey(CacheKeyConstants prefix, String... keys) {
        String key = prefix.name();
        //假如redis传入进来的key不为空，我们就给他拼凑后返回回去
        if (keys != null && keys.length > 0) {
            key += "_" + Joiner.on("_").join(keys);
        }
        //返回拼凑好的key
        return key;
    }
}
