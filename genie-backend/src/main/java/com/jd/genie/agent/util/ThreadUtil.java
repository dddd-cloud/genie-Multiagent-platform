package com.jd.genie.agent.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;

import java.util.concurrent.*;

@Slf4j
public class ThreadUtil {
    private static ThreadPoolExecutor executor = null;

    private ThreadUtil() {
    }

    public static synchronized void initPool(int poolSize) {
        if (executor == null) {
            ThreadFactory threadFactory = (new BasicThreadFactory.Builder()).namingPattern("exe-pool-%d").daemon(true).build();
            // Silently dropping a task leaves the caller's SSE stream hanging forever with no error,
            // so surface saturation to the submitting thread instead.
            RejectedExecutionHandler handler = (r, pool) -> {
                log.error("Agent thread pool saturated, rejecting task: active={} poolSize={} completed={}",
                        pool.getActiveCount(), pool.getPoolSize(), pool.getCompletedTaskCount());
                throw new RejectedExecutionException("Agent thread pool saturated");
            };
            int maxPoolSize = Math.max(poolSize, 1000);
            executor = new ThreadPoolExecutor(poolSize, maxPoolSize, 60000L, TimeUnit.MILLISECONDS, new SynchronousQueue(), threadFactory, handler);
        }

    }

    public static void execute(Runnable runnable) {
        if (executor == null) {
            initPool(100);
        }

        executor.execute(runnable);
    }

    public static CountDownLatch getCountDownLatch(int count) {
        return new CountDownLatch(count);
    }

    public static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (Exception var2) {
        }
    }

    public static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException var3) {
        }
    }


}
