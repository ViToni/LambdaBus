/*******************************************************************************
 * Copyright (c) 2018: Victor Toni
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the Eclipse
 * Public License, v. 2.0 are satisfied:
 *     GNU General Public License, version 2 with the GNU Classpath Exception
 * which is available at
 *     https://www.gnu.org/software/classpath/license.html
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 *
 * Contributors:
 *     Victor Toni - initial implementation
 *******************************************************************************/
package org.kromo.lambdabus.impl.concurrent;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The default {@link ExecutorService} implementations provided by
 * {@link Executors} use non-daemon threads. If these {@link ExecutorService}s
 * are not shutdown explicitly they might run forever.<br>
 * This {@link ThreadPoolExecutor} uses a {@link ThreadFactory} which returns
 * only daemon threads avoiding having to wait for the threads to finish when
 * the application should quit gracefully.
 *
 * @author Victor Toni - initial implementation
 *
 */
public class DaemonThreadPoolExecutor
    extends ThreadPoolExecutor {

    /**
     * Multiplier applied to the numbers of CPUs to calculate the number of core
     * threads for the pool if it is not explicitly configured.
     */
    private static final int CORE_THREADS_PER_CPU_MULTIPLIER = 1;

    /**
     * Multiplier applied to the numbers of CPUs to calculate the maximum number of
     * threads for the pool if it is not explicitly configured.
     */
    private static final int MAX_THREADS_PER_CPU_MULTIPLIER = 4;

    /**
     * Prefix to be used for the {@link Thread}s created for the default
     * {@link ExecutorService}.
     */
    private static final String DEFAULT_THREAD_POOL_PREFIX = "λ-bus-pool";

    /**
     * Internal counter for instances using the default {@link ThreadFactory}
     * without a custom name.
     */
    private static final AtomicInteger DEFAULT_INSTANCE_COUNT = new AtomicInteger();

    /**
     * Creates a new {@code ThreadPoolExecutor} with the given initial
     * parameters.
     *
     * @param workQueue
     *            the queue to use for holding tasks before they are executed.
     *            This queue will hold only the {@code Runnable} tasks submitted
     *            by the {@code execute} method.
     * @throws NullPointerException
     *             if {@code workQueue} is {@code null}
     */
    public DaemonThreadPoolExecutor(
            final BlockingQueue<Runnable> workQueue
    ) {
        this(
                Runtime.getRuntime().availableProcessors(),
                workQueue);
    }

    /**
     * Creates a new {@code ThreadPoolExecutor} with the given initial
     * parameters.
     *
     * @param availableProcessors
     *            based on this value the {@code coreThreadCount} and
     *            {@code maxThreadCount} will be calculated
     * @param workQueue
     *            the queue to use for holding tasks before they are executed.
     *            This queue will hold only the {@code Runnable} tasks submitted
     *            by the {@code execute} method.
     * @throws IllegalArgumentException
     *             if one of the following holds:<br>
     *             {@code availableProcessors < 0}
     * @throws NullPointerException
     *             if {@code workQueue} is {@code null}
     */
    public DaemonThreadPoolExecutor(
            final int availableProcessors,
            final BlockingQueue<Runnable> workQueue
    ) {
        this(
                availableProcessors * CORE_THREADS_PER_CPU_MULTIPLIER,
                availableProcessors * MAX_THREADS_PER_CPU_MULTIPLIER,
                workQueue);
    }

    /**
     * Creates a new {@code ThreadPoolExecutor} with the given initial
     * parameters.
     *
     * @param coreThreadCount
     *            the number of threads to keep in the pool, even if they are
     *            idle
     * @param maxThreadCount
     *            the maximum number of threads allowed in the pool
     * @param workQueue
     *            the queue to use for holding tasks before they are executed.
     *            This queue will hold only the {@code Runnable} tasks submitted
     *            by the {@code execute} method.
     * @throws IllegalArgumentException
     *             if one of the following holds:<br>
     *             {@code coreThreadCount < 0}<br>
     *             {@code maxThreadCount <= 0}<br>
     *             {@code maxThreadCount < coreThreadCount}
     * @throws NullPointerException
     *             if {@code workQueue} is {@code null}
     */
    public DaemonThreadPoolExecutor(
            final int coreThreadCount,
            final int maxThreadCount,
            final BlockingQueue<Runnable> workQueue
    ) {
        this(
            coreThreadCount,
            maxThreadCount,
            10L, TimeUnit.SECONDS,
            workQueue);
    }

    /**
     * Creates a new {@code ThreadPoolExecutor} with the given initial
     * parameters.
     *
     * @param coreThreadCount
     *            the number of threads to keep in the pool, even if they are
     *            idle
     * @param maxThreadCount
     *            the maximum number of threads allowed in the pool
     * @param keepAliveTime
     *            when the number of threads is greater than the core, this is
     *            the maximum time that excess idle threads will wait for new
     *            tasks before terminating.
     * @param timeUnit
     *            the time unit for the {@code keepAliveTime} argument
     * @param workQueue
     *            the queue to use for holding tasks before they are executed.
     *            This queue will hold only the {@code Runnable} tasks submitted
     *            by the {@code execute} method.
     * @throws IllegalArgumentException
     *             if one of the following holds:<br>
     *             {@code coreThreadCount < 0}<br>
     *             {@code maxThreadCount <= 0}<br>
     *             {@code maxThreadCount < coreThreadCount}<br>
     *             {@code keepAliveTime < 0}
     * @throws NullPointerException
     *             if {@code workQueue} or {@code timeUnit} is {@code null}
     */
    public DaemonThreadPoolExecutor(
            final int coreThreadCount,
            final int maxThreadCount,
            final long keepAliveTime,
            final TimeUnit timeUnit,
            final BlockingQueue<Runnable> workQueue
    ) {
        this(
            coreThreadCount,
            maxThreadCount,
            keepAliveTime,
            timeUnit,
            workQueue,
            getDefaultThreadFactoryName());
    }

    /**
     * Creates a new {@code ThreadPoolExecutor} with the given initial
     * parameters.
     *
     * @param coreThreadCount
     *            the number of threads to keep in the pool, even if they are
     *            idle
     * @param maxThreadCount
     *            the maximum number of threads allowed in the pool
     * @param keepAliveTime
     *            when the number of threads is greater than the core, this is
     *            the maximum time that excess idle threads will wait for new
     *            tasks before terminating.
     * @param timeUnit
     *            the time unit for the {@code keepAliveTime} argument
     * @param workQueue
     *            the queue to use for holding tasks before they are executed.
     *            This queue will hold only the {@code Runnable} tasks submitted
     *            by the {@code execute} method.
     * @param threadFactoryName
     *            name to be used by the {@link ThreadFactory} as prefix when
     *            the executor creates a new thread
     * @throws IllegalArgumentException
     *             if one of the following holds:<br>
     *             {@code coreThreadCount < 0}<br>
     *             {@code maxThreadCount <= 0}<br>
     *             {@code maxThreadCount < coreThreadCount}<br>
     *             {@code keepAliveTime < 0}
     * @throws NullPointerException
     *             if {@code workQueue}, {@code timeUnit} or
     *             {@code threadFactoryName} is {@code null}
     */
    protected DaemonThreadPoolExecutor(
            final int coreThreadCount,
            final int maxThreadCount,
            final long keepAliveTime,
            final TimeUnit timeUnit,
            final BlockingQueue<Runnable> workQueue,
            final String threadFactoryName
    ) {
        super(
            coreThreadCount,
            maxThreadCount,
            keepAliveTime,
            timeUnit,
            workQueue,
            new DaemonThreadFactory(threadFactoryName));
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb
            .append(getClass().getSimpleName())
            .append('(')
            .append("core pool size:").append(getCorePoolSize())
            .append(",max pool size:").append(getMaximumPoolSize())
            .append(",active:").append(getActiveCount())
            .append(',').append(getQueue().getClass().getSimpleName())
            .append(',') .append(getThreadFactory().getClass().getSimpleName())
            .append(')');
        return sb.toString();
    }

    protected static String getDefaultThreadFactoryName() {
        return DEFAULT_THREAD_POOL_PREFIX + "-" + DEFAULT_INSTANCE_COUNT.incrementAndGet();
    }

}
