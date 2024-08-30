
# 《微服务架构下的限流策略探讨》

随着微服务架构的日益普及，服务之间的依赖和调用关系愈发复杂，确保服务的稳定性成为关键课题。在实际业务中，瞬时流量激增的情况时有发生，这可能导致请求超时，甚至引发服务器过载和宕机。为保护系统自身及其上下游服务，限流措施不可或缺。限流能够迅速拒绝超过设定上限的请求，保障系统及上下游服务的稳定运行。合理的限流策略可有效应对流量激增，确保系统的可用性和性能。本文将深入探讨几种常见的限流算法，对比其优缺点，提供限流算法选择建议，并针对业务中的分布式限流提出多种解决方案。

## 一、限流概述

限流是高并发场景下，通过控制系统处理请求的速率，迅速拒绝超过设定上限的请求，以保障系统及上下游服务稳定运行的服务保护策略。

在限流技术中，需理解两个主要概念：阈值和拒绝策略。

**阈值**：指单位时间内允许的最大请求量。例如，将 QPS（每秒请求数）限制为 1000，意味着系统在 1 秒内最多接受 1000 次请求。通过设置适当的阈值，可有效控制系统负载，避免因过多请求导致系统崩溃或性能下降。

**拒绝策略**：处理超过阈值请求的方法。常见的拒绝策略包括直接拒绝和排队等待。直接拒绝策略会立即拒绝超过阈值的请求，并直接向用户返回结果；排队等待策略则将请求放入队列中，按照一定规则处理，避免瞬间拒绝大量请求。选择合适的拒绝策略可在系统稳定性和用户体验之间取得平衡，帮助系统应对突发流量激增、恶意访问或频繁请求的情况，保障系统的稳定性和可用性。

限流方案根据实施范围分为单机限流和分布式限流。其中，单机限流根据算法又可细分为固定窗口、滑动窗口、漏桶和令牌桶等四种常见类型。

## 二、为什么要限流**

限流主要是为了在高并发场景下，拒绝部分请求，避免因过载导致系统崩溃或性能下降，从而保证服务能够健康稳定地运行。具体原因如下：

**1. 防止系统过载**
 - **资源保护**：当请求量超过系统处理能力时，CPU、内存、带宽等资源会被迅速消耗，导致系统负载过高甚至崩溃。限流可以防止资源被耗尽，保护系统正常运行。
 - **服务质量**：在过载情况下，系统响应时间变长，用户体验变差。限流可以保证在高负载下仍能提供较好的服务质量。

**2. 提升系统稳定性**
 - **预防雪崩效应**：如果一个服务过载，会影响其他依赖该服务的系统组件，可能导致整个系统崩溃。限流可以阻止问题扩散，保持系统稳定性。
 - **减少故障传播**：当某个服务出现问题时，限流可以防止问题蔓延到其他服务，减少故障范围。

**3. 应对突发流量**
 - **瞬时高峰处理**：在特定时间段（如促销活动、突发事件等），请求量可能突然增加。限流可以控制请求速率，平稳地处理瞬时高峰。
 - **恶意请求防护**：限流可以防止恶意用户或攻击者通过大量请求来消耗系统资源，提高系统的安全性。

## 三、限流基本算法**

**1. 固定窗口限流**

**算法原理**：
固定窗口限流是最简单直观的限流算法，其基本原理是将时间划分为固定大小的窗口，并在每个窗口内限制请求数量或速率。具体来说，就是将请求按照时间顺序放入时间窗口中，并计算该时间窗口内的请求数量，如果请求数量超出了限制，则拒绝该请求。

**算法步骤**：
 - 将时间划分为固定大小的窗口，例如每秒一个。
 - 在每个时间窗口内，记录请求的数量。
 - 当新的请求到达时，将计数器的值加 1。
 - 如果计数器的值超过了预设的阈值（例如 3 个请求），则拒绝该请求。
 - 当时间窗口结束时，重置计数器。

**算法实现**：
```java
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
        FixedLimiter limiter = new FixedLimiter(Duration.ofSeconds(1), 3); // 每秒最多允许 3 个请求
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
```

**优点**：
算法实现非常简单，易于实现和理解。

**缺点**：
 - 请求分布不均匀：在固定窗口算法中，请求在窗口内的分布可能会不均衡，导致某些窗口内的请求量超出阈值，而其他窗口内的请求较少。
 - 应对突发流量能力有限：由于固定窗口算法的窗口大小是固定的，无法灵活调整，因此难以应对突发的流量高峰。
 - 存在明显的临界问题：在窗口结束时重置请求计数可能会导致处理请求的不公平。例如，窗口结束前的最后一秒内请求数已达上限，而窗口开始时的第一秒内请求计数为零。比如：限流阀值为每秒 5 个请求，单位时间窗口为 1 秒。如果在前 0.5 秒到 1 秒的时间内并发 5 个请求，接着在 1 秒到 1.5 秒的时间内又并发 5 个请求。虽然这两个时间段各自都没有超过限流阈值，但如果计算 0.5 秒到 1.5 秒的总请求数，则总共是 10 个请求，已经远远超过了 1 秒内不超过 5 个请求的限流标准。

**适用场景**：
固定窗口算法适合在请求速率有明确要求且流量相对稳定的场景中使用。然而，对于应对突发流量和请求分布不均匀的情况，该算法可能表现不足，此时需要考虑使用其他更灵活的限流算法。

**2. 滑动窗口限流**

**算法原理**：
滑动窗口其实也是一种通过将时间窗口划分为一个一个小的时间段来限流的方法，每个小的时间段又称之为小周期。相比于固定窗口限流，它可以根据时间滑动删除过期的小周期，以解决固定窗口临界值的问题。比如要限制 1s 内的请求数量，让时间窗口为 1s，在前面固定窗口分析中，会出现临界值问题，比如在 0.5s 和 1.5s 之间，这个 1s 时间段内的请求数就会出现大于阈值情况，那么滑动窗口就可以将 1s 窗口划分成两个小周期，每个 0.5s。每隔 0.5s，就往右滑动一个小周期，这样统计的整个窗口大小仍然是 1s，只是过期的左边的那个小周期会随着时间删除，这样整个 1s 的统计周期就是随时间滑动的。

**算法实现**：
```java
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
        // 创建一个滑动窗口限流器，窗口大小为 500 毫秒，每个小周期最多允许 2 个请求，每秒允许 4 个请求
        SlidingLimiter limiter = new SlidingLimiter(Duration.ofMillis(500), 2);
        // 发送 20 个请求，每 100 毫秒发送一个
        mockRequest(20, Duration.ofMillis(100), limiter);
        System.out.println("------------------------------------------");
    }
}
```

**优点**：
 - 精度高，可以通过调整时间窗口小周期的大小来实现不同的限流效果，小周期的时间跨度越短，精度越高。
 - 简单易实现。
 - 灵活性高，滑动窗口算法能够根据实际情况动态调整窗口大小，从而适应流量变化。这样可以更好地应对突发流量和请求分布不均匀的情况。

**缺点**：
滑动窗口算法本质上是固定窗口算法的细化版本，它能够在一定程度上提高限流的精度和实时性，但不能彻底解决请求分布不均匀的问题。该算法依赖于窗口的大小和时间间隔，特别是在突发流量过大或请求分布极度不均匀的极端情况下，仍可能导致限流不准确。因此，在实际应用中，需要引入更复杂的算法或策略来进一步优化限流效果。假设窗口以 0.5s 为小周期移动，在【0.5s，1.5s】，【1.5s，2.5s】间其实都是合理的，不会有流量超出，但是其实在【0.8s，1.8s】间就有 10 个请求了，并没有达到限流效果。因为滑动窗口本质其实是将窗口粒度更小，但是不管多小，仍然是以窗口来限制，所以总会存在流量不均导致的限流不准确问题。

**适用场景**：
滑动窗口同样也是适合流量相对稳定的场景。

**3. 漏斗限流**

**算法原理**：
漏斗限流算法的核心思想是将请求存储在一个漏斗中，漏斗以固定的速率漏出请求。如果漏斗被填满，新到达的请求将被丢弃。请求可以以不定的速率流入漏桶，而漏桶以固定的速率流出，所以漏斗算法可以将突发流量均匀地分配，确保系统在稳定的负载下运行。

**算法步骤**：
 - 设置桶的容量：定义服务器在瞬间能够接受的最大请求数。
 - 确定处理速率：设定每秒能够处理的请求数。
 - 请求处理计算：
   - 计算处理完成的请求数：
     - 已处理请求数 = (当前请求时间 − 上次请求时间) × 处理速率。
   - 更新当前请求数：
     - 最新的当前请求数 = 当前请求数 − 已处理请求数。
 - 比较和处理请求：
   - 如果当前请求数超过桶的容量，则拒绝本次请求。
   - 否则，允许请求通过，并将当前请求数增加 1。

**算法实现**：
```java
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
            long elapsed = Duration.between(lastTime, Instant.now()).toMillis();
            double seconds = elapsed / 1000.0;
            int leakyReqCount = (int) (seconds * rate);

            if (leakyReqCount > 0) {
                currentReqNum -= leakyReqCount;
                lastTime = Instant.now();
            }
            if (currentReqNum < 0) {
                currentReqNum = 0;
            }
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
```

**优点**：
 - 平滑处理请求速度：漏斗限流能够有效地平滑限制请求的处理速度，防止瞬间请求过多导致系统崩溃或出现雪崩效应。
 - 适应流量变化：可以通过控制请求的处理速度，使系统能够适应不同的流量需求，避免系统过载或资源过度闲置。
 - 灵活适应场景：通过调整桶的容量和漏出的速率，漏斗限流可以满足不同的限流需求，灵活适应各种使用场景。

**缺点**：
 - 无法动态调整流量：漏斗的漏出速率是固定的，不够灵活，无法根据实际流量情况进行动态调整。
 - 突发流量处理有限：在突发流量过大的情况下，漏斗可能很快被填满，大量请求将被拒绝，可能会导致服务质量下降。

**适用场景**：
虽然漏斗可以以平滑的速度处理请求，但是仍然不能较好地应对突发流量，所以也是适用于流量相对稳定的场景。

**4. 令牌桶限流**

**算法原理**：
令牌桶算法限流是维护一个固定容量的存放令牌的桶，令牌以固定的速率产生，并放入桶中。每个令牌代表一个请求的许可。当请求到达时，需要从令牌桶中获取一个令牌才能通过。如果令牌桶中没有足够的令牌，则请求被限制或丢弃。

**算法步骤**：
 - 桶的容量：定义桶的容量，即服务器在一瞬间最多可以接受的请求数。
 - 令牌生成速率：定义令牌的生成速率，例如每秒生成的令牌数。
 - 计算当前令牌数：
   - 每次请求时，计算桶中剩余的令牌数，也就是当前令牌数。
    
