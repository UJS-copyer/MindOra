package com.mindora.rag.api.web;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * SSE 流式任务执行器的包装类型。
 *
 * <p><b>为什么包一层</b>：应用上下文里已有按<b>类型</b>注入的 {@code Executor}
 * （knowledge 模块的 sync/async 执行器），若再注册一个 Executor 类型的 Bean，那些未加限定的
 * 注入点会立刻出现「找到 2 个候选」的歧义。包成独立类型即与它们互不干扰。
 *
 * <p>它同时承担线程池的生命周期（{@link DisposableBean}），因为 ThreadPoolTaskExecutor
 * 实例不再直接作为 Bean 注册。
 */
public record RagStreamTaskExecutor(ThreadPoolTaskExecutor executor) implements DisposableBean {

    /** 提交任务；容量已满时抛 {@code TaskRejectedException}，由控制器转成 server_busy 事件。 */
    public void execute(Runnable task) {
        executor.execute(task);
    }

    @Override
    public void destroy() {
        executor.shutdown();
    }
}
