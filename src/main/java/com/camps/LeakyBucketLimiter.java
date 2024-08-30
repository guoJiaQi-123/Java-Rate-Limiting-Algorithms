package com.camps;

import java.util.concurrent.locks.ReentrantLock;
import java.time.Duration;
import java.time.Instant;


/**
 * 漏桶限流
 */
public class LeakyBucketLimiter {
    private int rate; // 漏桶的速率
    private int capacity; // 漏桶容量
    private int currentReqNum; // 当前桶内的请求数
    private Instant lastTime;
    private final ReentrantLock lock = new ReentrantLock();

    public LeakyBucketLimiter(int rate, int capacity) {
        this.rate = rate;
        this.capacity = capacity;
        this.currentReqNum = 0;
        this.lastTime = Instant.now();
    }

    public boolean allow() {
        lock.lock();
        try {
            // 在 Java 中，java.time.Duration.between()方法用于计算两个时间点之间的时间间隔。
            long elapsed = Duration.between(lastTime, Instant.now()).toMillis();
            double seconds = elapsed / 1000.0;
            // 已处理请求数 = (当前请求时间 − 上次请求时间) × 处理速率
            int leakyReqCount = (int) (seconds * rate);

            if (leakyReqCount > 0) {
                currentReqNum -= leakyReqCount;
                lastTime = Instant.now();
            }
            // 漏桶内的请求全部被处理了，当前请求数置为0
            if (currentReqNum < 0) {
                currentReqNum = 0;
            }
            // 当前请求数小于容量，可以加入漏桶
            if (currentReqNum < capacity) {
                currentReqNum++;
                return true;
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    public static void mockRequest(int n, long delay, LeakyBucketLimiter limiter) {
        for (int i = 0; i < n; i++) {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (limiter.allow()) {
                System.out.printf("第%d个请求通过\n", i + 1);
            } else {
                System.out.printf("第%d个请求被限流\n", i + 1);
            }
        }
    }


    public static void main(String[] args) {
        System.out.println("=================漏桶算法=================");
        LeakyBucketLimiter limiter = new LeakyBucketLimiter(4, 5);
        mockRequest(10, 50, limiter);
        System.out.println("------------------------------------------");
    }
}




