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
package org.kromo.lambdabus.util;

import static java.time.Duration.ofMillis;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.junit.jupiter.api.function.Executable;
import org.slf4j.Logger;

import org.kromo.lambdabus.LambdaBus;
import org.kromo.lambdabus.test.util.MultithreadedTasks;

/**
 * Helper to assert how events are dispatched.
 * 
 * @author Victor Toni - initial implementation
 *
 */
public final class ThreadingAssertions {

    private ThreadingAssertions () {
        // no instance
    }

    private static final int ZERO = 0;
    private static final int ONE  = 1;

    /**
     * Timeout used to wait for published events to complete
     */
    protected static final int DEFAULT_TIMEOUT_MILLIS = 500;

    private static final AtomicInteger INSTANCE_COUNT = new AtomicInteger();

    /**
     * Test that all events are processed in order.<br>
     * If posting would be asynchronously a previous {@link CompletableFuture}
     * might be not complete yet.
     * 
     * @param eventCount
     *            number of events to create
     * @param lambdaBusSupplier
     *            {@link Supplier} of an {@link LambdaBus} instance
     * @param postMethodReference
     *            method reference for posting events to the created bus
     * @param logger
     *            logger of testing class (helpful to identify log entries for a
     *            given {@link LambdaBus} implementation)
     */
    public static void assertThatEventsAreProcessedInOrder(
            final int eventCount,
            final Supplier<LambdaBus> lambdaBusSupplier,
            final BiConsumer<LambdaBus, Object> postMethodReference,
            final Logger logger
    ) {
        try (final LambdaBus lb = lambdaBusSupplier.get()) {
            final CountDownLatch doneLatch = new CountDownLatch(eventCount);

            final AtomicBoolean outOfSync = new AtomicBoolean(false);
            final AtomicInteger counter = new AtomicInteger(ZERO);
            final Consumer<Integer> intConsumer = (intEvent) -> {
                if(! counter.compareAndSet(intEvent, intEvent + ONE)) {
                    outOfSync.set(true);
                }
                doneLatch.countDown();
            };

            lb.subscribe(Integer.class, intConsumer);

            for(int i = 0; i < eventCount; i++) {
                assertFalse(outOfSync.get());
                postMethodReference.accept(lb, i);
            }
            assertFalse(outOfSync.get());

            /**
             * Wait for all events to be dispatched.
             */
            final Executable result = doneLatch::await;
            assertTimeout(
                    ofMillis(DEFAULT_TIMEOUT_MILLIS),
                    result
            );
        }
    }

    /**
     * Events posted to the synchronously are directly dispatched to their
     * subscriber means in the same thread.<br>
     * This is tested by this test.<br>
     * 
     * @param eventCount
     *            number of events to create
     * @param threadCount
     *            number of parallel thread to use for posting events
     * @param lambdaBusSupplier
     *            {@link Supplier} of an {@link LambdaBus} instance
     * @param postMethodReference
     *            method reference for posting events to the created bus
     * @param testingClass
     *            test class used for naming of threads used in this test
     * @param logger
     *            logger of testing class (helpful to identify log entries for a
     *            given {@link LambdaBus} implementation)
     */
    public static void assertDisptachingIsDoneInCallerThread(
            final int eventCount,
            final int threadCount,
            final Supplier<LambdaBus> lambdaBusSupplier,
            final BiConsumer<LambdaBus, Object> postMethodReference,
            final Class<?> testingClass,
            final Logger logger
    ) {
        final int subscriberCount = 107;
        final int totalCount = eventCount * subscriberCount;

        final AtomicReference<String> errorMessageRef = new AtomicReference<>();
        final Map<String, Integer> threadHashCodes = new ConcurrentHashMap<>(threadCount);

        final CountDownLatch doneLatch = new CountDownLatch(totalCount);
        final Consumer<String> stringSubscriber = eventName -> {
            final Integer publisherThreadHashCode = threadHashCodes.get(eventName);
            final Integer currentThreadHashCode = Thread.currentThread().hashCode();
            if (!currentThreadHashCode.equals(publisherThreadHashCode)) {
                final String errorMessage = String.format(
                        "Thread hash codes do not match. Publisher (expected): %s, Consumer (actual): %s",
                        publisherThreadHashCode, currentThreadHashCode);
                errorMessageRef.compareAndSet(null, errorMessage);
            }
            doneLatch.countDown();
        };

        final AtomicInteger counter = new AtomicInteger(-1);
        try (final LambdaBus lb = lambdaBusSupplier.get()) {
            for(int i = 0; i < subscriberCount; i++) {
                lb.subscribe(String.class, stringSubscriber);
            }

            final Runnable postRunnable = () -> {
                final int index = counter.incrementAndGet();
                final String eventName = String.format("%s_event-%05d", testingClass.getSimpleName(), index);
                final Integer publisherThreadHashCode = Thread.currentThread().hashCode();
                threadHashCodes.put(eventName, publisherThreadHashCode);
                postMethodReference.accept(lb, eventName);
            };

            final String threadFactoryName = testingClass + "-pool-" + INSTANCE_COUNT.incrementAndGet();

            final MultithreadedTasks tasks = new MultithreadedTasks(threadCount, threadFactoryName);
            tasks.executeTask(eventCount, postRunnable);

            final String errorMessage = errorMessageRef.get();
            if (null != errorMessage) {
                fail(errorMessage);
            }

            /**
             * Wait for all events to be dispatched.
             */
            final Executable result = doneLatch::await;
            assertTimeout(
                    ofMillis(DEFAULT_TIMEOUT_MILLIS),
                    result
            );

            final Set<Integer> uniqueThreadNames = new HashSet<>(threadHashCodes.values());
            logger.trace("Number of threads used for dispatching: {}", uniqueThreadNames.size());
        }
    }

}
