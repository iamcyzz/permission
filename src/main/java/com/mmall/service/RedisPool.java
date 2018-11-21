package com.mmall.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import redis.clients.jedis.ShardedJedis;
import redis.clients.jedis.ShardedJedisPool;

import javax.annotation.Resource;

@Service("redisPool")
@Slf4j
//有了这个类，就相当可以对redis进行操作处理了
public class RedisPool {

    //这是被我们spring管理的一个单例的service
    @Resource(name = "shardedJedisPool")
    private ShardedJedisPool shardedJedisPool;

    //返回的是这个服务的单例实例，保证只起一个客户端
    public ShardedJedis instance() {
        return shardedJedisPool.getResource();
    }

    //拿到了客户端后，我们这里还要提供一个安全关闭客户端的方法
    //传入的是对应的连接，然后去关闭
    //用代码连接redis的时候，使用完了一定要关闭
    public void safeClose(ShardedJedis shardedJedis) {
        try {
           if (shardedJedis != null) {
               shardedJedis.close();
           }
        } catch (Exception e) {
            //假如无法关闭，那么就提示出现异常了
            //我们数据库查询sql不管是不是出现异常，都会关闭的，只是不用自己手动写代码关闭，因为连接池的框架代码已经自动有关闭
            //但是redis要手动写关闭,不关闭就会不断占用redis的资源，达到上限后就不能再连接了，就会内存泄露，再也连不上redis，出现超时异常
            log.error("return redis resource exception", e);
        }
    }
}
