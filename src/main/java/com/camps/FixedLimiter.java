package com.camps;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


/**
 * 固定窗口限流
 */
public class FixedLimiter {
    private static final String FORMAT_TIME = "yyyy-MM-dd HH:mm:ss";
    private final long windowSize; // 窗口大小，单位为毫秒
    private final int maxRequests; // 最大请求数
    private int requests; // 当前窗口内的请求数
    private LocalDateTime lastReset; // 上次窗口重置时间
    private final Lock resetMutex; // 重置锁

    public FixedLimiter(Duration windowSize, int maxRequests) {
        this.windowSize = windowSize.toMillis();
        this.maxRequests = maxRequests;
        this.requests = 0;
        this.lastReset = LocalDateTime.now();
        this.resetMutex = new ReentrantLock();
    }

    public boolean allowRequest() {
        resetMutex.lock();
        try {
            LocalDateTime now = LocalDateTime.now();
            // 检查是否需要重置窗口
            if (Duration.between(lastReset, now).toMillis() >= windowSize) {
                requests = 0;
                lastReset = now;
            }
            // 检查请求数是否超过阈值
            if (requests >= maxRequests) {
                return false; // 限流
            }
            requests++;
            return true;
        } finally {
            resetMutex.unlock();
        }
    }

    public static void main(String[] args) {
        System.out.println(System.currentTimeMillis() / 1000);
        FixedLimiter limiter = new FixedLimiter(Duration.ofSeconds(1), 3); // 每秒最多允许3个请求
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(FORMAT_TIME);
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);

        Runnable task = () -> {
            String now = LocalDateTime.now().format(formatter);
            if (limiter.allowRequest()) {
                System.out.println(now + " 请求通过");
            } else {
                System.out.println(now + " 请求被限流");
            }
        };

        for (int i = 0; i < 20; i++) {
            executor.schedule(task, i * 100, TimeUnit.MILLISECONDS);
        }

        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }
}

