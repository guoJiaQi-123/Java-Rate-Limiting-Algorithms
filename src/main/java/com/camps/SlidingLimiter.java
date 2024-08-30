package com.camps;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatter;
import java.util.LinkedList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


/**
 * 滑动窗口限流
 */
public class SlidingLimiter {
    private final Duration windowSize; // 窗口小周期大小
    private final int maxRequests; // 最大请求数
    private final LinkedList<LocalDateTime> requestTimeList; // 窗口小周期内的请求时间
    private final Lock requestsLock; // 请求锁
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public SlidingLimiter(Duration windowSize, int maxRequests) {
        this.windowSize = windowSize;
        this.maxRequests = maxRequests;
        this.requestTimeList = new LinkedList<>();
        this.requestsLock = new ReentrantLock();
    }

    public boolean allowRequest() {
        requestsLock.lock();
        try {
            LocalDateTime currentTime = LocalDateTime.now();
            // 移除过期的请求
            while (!requestTimeList.isEmpty() && Duration.between(requestTimeList.peek(), currentTime).compareTo(windowSize) > 0) {
                requestTimeList.poll();
            }
            // 检查请求数是否超过阈值
            if (requestTimeList.size() >= maxRequests) {
                return false;
            }
            requestTimeList.add(currentTime);
            return true;
        } finally {
            requestsLock.unlock();
        }
    }

    public static void mockRequest(int n, Duration d, SlidingLimiter limiter) {
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);

        for (int i = 0; i < n; i++) {
            int requestId = i + 1;
            executor.schedule(() -> {
                String now = LocalDateTime.now().format(formatter);
                if (limiter.allowRequest()) {
                    System.out.printf("%s 请求通过\n", now);
                } else {
                    System.out.printf("%s 请求被限流\n", now);
                }
            }, i * d.toMillis(), TimeUnit.MILLISECONDS);
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

    public static void main(String[] args) {
        System.out.println("=================滑动窗口算法=================");
        // 创建一个滑动窗口限流器，窗口大小为500毫秒，每个小周期最多允许2个请求，每秒允许4个请求
        SlidingLimiter limiter = new SlidingLimiter(Duration.ofMillis(500), 2);
        // 发送20个请求，每100毫秒发送一个
        mockRequest(20, Duration.ofMillis(100), limiter);
        System.out.println("------------------------------------------");
    }
}

