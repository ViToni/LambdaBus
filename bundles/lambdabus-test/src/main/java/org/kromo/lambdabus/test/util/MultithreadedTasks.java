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
 *     Victor Toni - initial API and implementation
 *******************************************************************************/
package org.kromo.lambdabus.test.util;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Phaser;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Helper class to execute a task multiple times and in parallel. It is supposed to start all
 * threads at the same time.
 *
 * @author Victor Toni - initial API and implementation
 */
public class MultithreadedTasks {

    public enum ExecutionPolicy {
        /**
         * Don't wait for other threads to pickup tasks just do as much work as
         * possible.
         */
        THROUGHPUT,

        /**
         * Let all threads pickup a tasks, wait until complete, repeat.
         */
        PARALLELISM
    }

    /**
     * By default, use as many threads as possible to achieve as much parallelism as possible.
     */
    private static final ExecutionPolicy DEFAULT_POLICY = ExecutionPolicy.PARALLELISM;

    /**
     * Counter used as part of the thread name prefix so each call of
     * {@code executeTask(...)} uses a unique prefix.
     */
    private static final AtomicInteger executionCounter = new AtomicInteger();

    /**
     * Maximum number of threads to use for task execution.<br>
     * (If a task is to be repeated {@code N} times and {@code N < maxThreads}
     * at most N threads will be created.)
     */
    private final int maxThreads;

    /**
     * Prefix for the internally used {@link ThreadFactory}.
     */
    private final String threadNamePrefix;

    /**
     * Create a configured instance.
     *
     * @param maxThreads
     *            max number of threads used to execute tasks
     */
    public MultithreadedTasks(
            final int maxThreads
    ) {
        this(maxThreads, MultithreadedTasks.class.getSimpleName());
    }

    /**
     * Create a configured instance.
     *
     * @param maxThreads
     *            max number of threads used to execute tasks
     * @param threadNamePrefix
     *            prefix used by internal {@link ThreadFactory} to create
     *            {@link Thread} names
     */
    public MultithreadedTasks(
            final int maxThreads,
            final String threadNamePrefix
    ) {
        if (maxThreads < 1) {
            throw new IllegalArgumentException("'maxThreads' must not be less than ONE");
        }
        this.maxThreads = maxThreads;

        this.threadNamePrefix = Objects.requireNonNull(threadNamePrefix, "'threadNamePrefix' must not be");
    }

    /**
     * Execute given task {@code N} times.
     *
     * @param nTimes
     *            number of times to execute task
     * @param task
     *            {@link Runnable} to be executed {@code N} times
     * @return number of threads used to execute the task
     */
    public int executeTask(
            final int nTimes,
            final Runnable task
    ) {
        return executeTask(nTimes, task, DEFAULT_POLICY);
    }

    /**
     * Execute given task {@code N} times.
     *
     * @param nTimes
     *            number of times to execute task
     * @param task
     *            {@link Runnable} to be executed {@code N} times
     * @param executionPolicy
     *            how should the tasks be processed
     * @return number of threads used to execute the task
     */
    public int executeTask(
            final int nTimes,
            final Runnable task,
            final ExecutionPolicy executionPolicy
    ) {
        if (nTimes < 1) {
            throw new IllegalArgumentException("'nTimes' must not be less than ONE");
        }
        Objects.requireNonNull(task, "'task' must not be null");

        final int executionCount = executionCounter.incrementAndGet();

        // don't need more threads than tasks
        final int numberOfThreads = Math.min(nTimes, maxThreads);

         // Synchronization of thread start and iterations
        final Phaser phaser = new Phaser();

        // Synchronization aid for waiting on end of all threads
        final CountDownLatch endLatch = new CountDownLatch(numberOfThreads);

        final AtomicInteger counter = new AtomicInteger();

        // reference to get exception from inside the threads
        final AtomicReference<Exception> exceptionRef = new AtomicReference<>();

        // register current thread because we have to set up things
        phaser.register();

        final Runnable runnable = () -> {
            phaser.register();
            try {
                // wait for all threads to have been created and started
                phaser.arriveAndAwaitAdvance();
                while (
                        null == exceptionRef.get() &&
                        counter.incrementAndGet() <= nTimes
                ) {
                    try {
                        task.run();

                        if (executionPolicy == ExecutionPolicy.PARALLELISM) {
                            /*
                             * Sync at this point to use as many threads as possible.
                             * Otherwise, the "first" few threads might process all the tasks.
                             */
                            phaser.arriveAndAwaitAdvance();
                        }
                    } catch (final Exception e) {
                        // just catch the first exception (if any) of all tasks
                        exceptionRef.compareAndSet(null, e);
                    }
                }
            } finally {
                // finally done in this thread
                try {
                    phaser.arriveAndDeregister();
                } finally {
                    endLatch.countDown();
                }
            }
        };

        final ThreadFactory threadFactory = createThreadFactory(
                threadNamePrefix,
                executionCount);

        // create threads and start them
        for (int i = 0; i < numberOfThreads; i++) {
            threadFactory.newThread(runnable).start();
        }

        // everything is set up let the waiting worker threads start at once
        phaser.arriveAndDeregister();

        try {
            // wait for all threads to end
            endLatch.await();
        } catch (final InterruptedException e) {
            // just catch this exception if it's the first exception
            exceptionRef.compareAndSet(null, e);

            // restore interrupted state
            Thread.currentThread().interrupt();
        }

        final Exception exception = exceptionRef.get();
        if (null != exception) {
            // re-throw exception if any
            sneakyThrow(exception);
        }

        return numberOfThreads;
    }

    /**
     * Compiler infers {@code <E>} to a RuntimeException. Now we can throw everything!
     *
     * @param <E>
     *            type of exception to throw
     * @param e
     *            exception to throw
     * @throws E
     *             given exception
     */
    @SuppressWarnings("unchecked")
    private static <E extends Throwable> void sneakyThrow(
            final Throwable e
    ) throws E {
        throw (E) e;
    }

    private static ThreadFactory createThreadFactory(
            final String threadFactoryPrefix,
            final int executionCount
    ) {
        final String threadNamePrefix = threadFactoryPrefix + "-execution-" + executionCount;

        return new SimpleThreadFactory(threadNamePrefix);
    }

}
