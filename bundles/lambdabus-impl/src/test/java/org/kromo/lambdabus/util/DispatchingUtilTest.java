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
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Phaser;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.kromo.lambdabus.test.util.SimpleThreadFactory;
import org.kromo.lambdabus.test.util.SpyableLogger;

/**
 * Test cases for dispatching of events by {@link DispatchingUtil}.
 * 
 * @author Victor Toni - initial implementation
 *
 */
public class DispatchingUtilTest {

    private static final int ZERO = 0;
    private static final int ONE = 1;

    private static final int DEFAULT_EVENT_COUNT = 617;
    private static final int DEFAULT_SUBSCRIBER_COUNT = 17;

    private static final int BLOCKING_WAIT_TIME_IN_MS = 250;

    private static final int MAX_WAIT_TIME_IN_MS = 4 * BLOCKING_WAIT_TIME_IN_MS;

    private static final int START_THREAD_COUNT = 3 * DEFAULT_SUBSCRIBER_COUNT;

    private static final Random rnd = new SecureRandom();

    //##########################################################################
    // Test cases for {@link DispatchingUtil#dispatchEventSafely(Object, Consumer)}
    //##########################################################################

    @Nested
    public static class dispatchEventSafelyTest {

        private final Logger logger = LoggerFactory.getLogger(getClass());

        @Test
        public void dispatchEventSafely() {
            final int eventCount = DEFAULT_EVENT_COUNT;

            final AtomicInteger counter = new AtomicInteger(ZERO);
            final AtomicReference<Object> objectRef = new AtomicReference<>();

            final Set<Integer> threadHashCodes = ConcurrentHashMap.newKeySet();

            final Consumer<Object> eventConsumer = (dispatchedEvent) -> {
                counter.incrementAndGet();
                objectRef.set(dispatchedEvent);
                threadHashCodes.add(Thread.currentThread().hashCode());
            };

            for (int i = 0; i < eventCount; i++) {
                final Object event = new Object();
                DispatchingUtil.dispatchEventSafely(event, eventConsumer);

                assertEquals(i + ONE, counter.get(), "Event was NOT dipatched to consumer");
                assertEquals(event, objectRef.get(), "Wrong event was dipatched to consumer");
            }

            final Integer thisThreadHashCode = Thread.currentThread().hashCode();

            // threads didn't change from event to event
            assertEquals(ONE, threadHashCodes.size());
            assertEquals(thisThreadHashCode, threadHashCodes.iterator().next());
        }

        @Test
        public void dispatchEventSafelyOnThrowable() {
            final Throwable throwable = new Throwable("Test throwable");

            assertDispatchEventSafelyThrows(throwable);
        }

        @Test
        public void dispatchEventSafelyOnError() {
            final Throwable throwable = new Error("Test error");

            assertDispatchEventSafelyThrows(throwable);
        }

        @Test
        public void dispatchEventSafelyOnException() {
            final Throwable throwable = new Exception("Test exception");

            assertDispatchEventSafelyDoesNotThrow(throwable);
        }

        @Test
        public void dispatchEventSafelyOnRuntimeException() {
            final Throwable throwable = new RuntimeException("Test runtime exception");

            assertDispatchEventSafelyDoesNotThrow(throwable);
        }

        //##########################################################################
        // Helper methods
        //##########################################################################

        private void assertDispatchEventSafelyThrows(final Throwable throwable) {
            final Object event = new Object();

            assertThrows(
                    throwable.getClass(),
                    () -> dispatchEventSafely(event, throwable)
            );

            final Logger spyLogger = Mockito.spy(new SpyableLogger(logger));
            assertThrows(
                    throwable.getClass(),
                    () -> dispatchEventSafely(event, throwable, spyLogger)
            );
            verifyNoMoreInteractions(spyLogger);
        }

        private void assertDispatchEventSafelyDoesNotThrow(final Throwable throwable) {
            final Object event = new Object();

            assertDoesNotThrow(
                    () -> dispatchEventSafely(event, throwable)
            );

            final Logger spyLogger = Mockito.spy(new SpyableLogger(logger));
            assertDoesNotThrow(
                    () -> dispatchEventSafely(event, throwable, spyLogger)
            );
            verify(spyLogger, times(ONE))
                .warn(
                        anyString(),
                        any(event.getClass()),
                        eq(throwable)
                );
        }

        private <T> void dispatchEventSafely(final T event, final Throwable throwable) {
            dispatchEventSafely(event, throwable, null);
        }

        private <T> void dispatchEventSafely(final T event, final Throwable throwable, final Logger logger) {
            assertNotNull(event, "Event to dispatch was NULL");
            assertNotNull(throwable, "Exception to be raised was NULL");

            final Consumer<Object> eventConsumer = (obj) -> sneakyThrow(throwable);

            if (null != logger) {
                DispatchingUtil.dispatchEventSafely(event, eventConsumer, logger);
            } else {
                DispatchingUtil.dispatchEventSafely(event, eventConsumer);
            }
        }
    }

    //##########################################################################
    // Test cases for {@link DispatchingUtil#dispatchEventToSubscriber(Object, Collection)}
    //##########################################################################

    @Nested
    public static class dispatchEventToSubscriberTest {

        private final Logger logger = LoggerFactory.getLogger(getClass());

        @Test
        public void dispatchEventToSubscriberWithCustomLogger() throws InterruptedException {
            final boolean useDefaultLogger = false;
            final boolean testExceptionsToo = false;
            dispatchEventToSubscriber(useDefaultLogger, testExceptionsToo);
        }

        @Test
        public void dispatchEventToSubscriberWithDefaultLogger() throws InterruptedException {
            final boolean useDefaultLogger = true;
            final boolean testExceptionsToo = false;
            dispatchEventToSubscriber(useDefaultLogger, testExceptionsToo);
        }

        @Test
        public void dispatchEventToSubscriberWithCustomLoggerWithExceptions() throws InterruptedException {
            final boolean useDefaultLogger = false;
            final boolean testExceptionsToo = true;
            dispatchEventToSubscriber(useDefaultLogger, testExceptionsToo);
        }

        @Test
        public void dispatchEventToSubscriberWithDefaultLoggerWithExceptions() throws InterruptedException {
            final boolean useDefaultLogger = true;
            final boolean testExceptionsToo = true;
            dispatchEventToSubscriber(useDefaultLogger, testExceptionsToo);
        }

        //##########################################################################
        // Helper methods
        //##########################################################################

        private void dispatchEventToSubscriber(
                final boolean useDefaultLogger,
                final boolean testExceptionsToo
        ) throws InterruptedException {
            final int eventCount = DEFAULT_EVENT_COUNT;
            final int evenEventCount = eventCount / 2 + eventCount % 2;
            final int oddEventCount = eventCount / 2 ;
            final int subscriberCount = DEFAULT_SUBSCRIBER_COUNT;
            final int exceptionThrowingSubscriberCount =
                    testExceptionsToo ? subscriberCount / 2 : ZERO;

            final int totalEventCount     = subscriberCount * eventCount ;
            final int expectedEvenCount   = subscriberCount * evenEventCount;
            final int expectedOddCount    = subscriberCount * oddEventCount;
            final int totalExceptionCount = exceptionThrowingSubscriberCount * eventCount;

            final AtomicInteger evenCounter = new AtomicInteger(ZERO);
            final AtomicInteger oddCounter = new AtomicInteger(ZERO);

            final AtomicInteger handledCounter = new AtomicInteger(ZERO);
            final AtomicInteger exceptionCounter = new AtomicInteger(ZERO);

            final Set<Integer> threadHashCodes = ConcurrentHashMap.newKeySet();

            final Consumer<String> eventConsumer = (dispatchedEvent) -> {
                final int n = Integer.valueOf(dispatchedEvent);
                final boolean even = isEven(n);
                threadHashCodes.add(Thread.currentThread().hashCode());

                if (even) {
                    evenCounter.incrementAndGet();
                } else {
                    oddCounter.incrementAndGet();
                }
                handledCounter.incrementAndGet();
            };

            final Consumer<String> exceptionalConsumer = (dispatchedEvent) -> {
                // reuse logic from "regular" consumer
                eventConsumer.accept(dispatchedEvent);

                exceptionCounter.incrementAndGet();

                // now behave badly and throw an exception
                throw new RuntimeException("Oops, this consumer throws an exception");
            };

            final List<Consumer<String>> eventSubscriberCollection = new ArrayList<>();
            for(int i = 0; i < subscriberCount; i++) {
                final boolean odd = isOdd(i);
                if (testExceptionsToo && odd) {
                    eventSubscriberCollection.add(exceptionalConsumer);
                } else {
                    eventSubscriberCollection.add(eventConsumer);
                }
            }

            for(int loopIndex = 0; loopIndex < eventCount; loopIndex++) {
                // create unique events
                final String event = String.format("%03d", loopIndex);
                dispatchEventAndAssert(
                        event,
                        eventSubscriberCollection,
                        loopIndex,
                        handledCounter,
                        threadHashCodes,
                        useDefaultLogger,
                        exceptionThrowingSubscriberCount, RuntimeException.class);
            }

            assertEquals(expectedEvenCount, evenCounter.get());
            assertEquals(expectedOddCount, oddCounter.get());
            assertEquals(totalExceptionCount, exceptionCounter.get());
            assertEquals(totalEventCount, handledCounter.get());
        }

        private <T> void dispatchEventAndAssert(
                final T event,
                final Collection<Consumer<T>> eventSubscriberCollection,
                final int loopIndex,
                final AtomicInteger handledCount,
                final Set<Integer> threadHashCodes,
                final boolean useDefaultLogger,
                final int exceptionThrowingSubscriberCount,
                final Class<? extends Exception> exceptionClass
        ) throws InterruptedException {
            final Logger spyLogger = useDefaultLogger ? null : Mockito.spy(new SpyableLogger(logger)) ;

            dispatchEventAndAssert(
                    event,
                    eventSubscriberCollection,
                    loopIndex,
                    handledCount,
                    threadHashCodes,
                    useDefaultLogger,
                    spyLogger);

            if (!useDefaultLogger) {
                if (ZERO == exceptionThrowingSubscriberCount) {
                    verifyNoMoreInteractions(spyLogger);
                } else {
                    verify(spyLogger, timeout(MAX_WAIT_TIME_IN_MS).times(exceptionThrowingSubscriberCount))
                        .warn(
                                anyString(),
                                eq(event),
                                isA(exceptionClass)
                        );
                }
            }
        }

        private <T> void dispatchEventAndAssert(
                final T event,
                final Collection<Consumer<T>> eventSubscriberCollection,
                final int loopIndex,
                final AtomicInteger handledCounter,
                final Set<Integer> threadHashCodes,
                final boolean useDefaultLogger,
                final Logger logger
        ) {
            if (useDefaultLogger) {
                DispatchingUtil.dispatchEventToSubscriber(event, eventSubscriberCollection);
            } else {
                DispatchingUtil.dispatchEventToSubscriber(event, eventSubscriberCollection, logger);
            }

            // dispatching occurred only in one thread
            assertEquals(ONE, threadHashCodes.size());

            final int handledCount = (loopIndex + ONE) * eventSubscriberCollection.size();

            // the event was dispatched to all subscriber
            assertEquals(handledCount, handledCounter.get());
        }
    }

    //##########################################################################
    // Test cases for {@link DispatchingUtil#dispatchEventToSubscriberThreadedPerEvent(Object, Collection, CompletableFuture, java.util.concurrent.Executor)}
    //##########################################################################

    @Nested
    public static class dispatchEventToSubscriberThreadedPerEventTest {

        private final Logger logger = LoggerFactory.getLogger(getClass());

        private ExecutorService executorService;
        private ThreadFactory threadFactory;

        @BeforeEach
        public void beforeEachTest(final TestInfo testInfo) {
            final String threadNamePrefix = testInfo.getDisplayName();
            threadFactory = new SimpleThreadFactory(threadNamePrefix);
        }

        @AfterEach
        public void afterEachTest() {
            if (null != executorService) {
                executorService.shutdownNow();
                executorService = null;
            }
            if (null != threadFactory) {
                threadFactory = null;
            }
        }

        @Test
        public void dispatchEventToSubscriber_RejectedExecutionException() throws InterruptedException {
            final int threadPoolSize = ONE;
            final Logger spyLogger = spy(new SpyableLogger(logger)) ;

            executorService = new ThreadPoolExecutor(threadPoolSize, threadPoolSize,
                    0L, TimeUnit.MILLISECONDS,
                    new SynchronousQueue<Runnable>(), // required so that tasks don't queue up but are rejected
                    threadFactory);

            final AtomicInteger startedCounter = new AtomicInteger(ZERO);
            final AtomicInteger handledCounter = new AtomicInteger(ZERO);

            final Phaser phaser = new Phaser(threadPoolSize);

            // register ourself so that we can control advance
            phaser.register();

            /*
             * This consumer will block for some time. Since we are using an
             * Executor with a single thread and a SynchronousQueue no other
             * task can be queued/executed until it completes.
             */
            final Consumer<String> blockingConsumer = (dispatchedEvent) -> {
                startedCounter.incrementAndGet();
                phaser.arriveAndAwaitAdvance();     // used to check how may consumer were started

                phaser.arriveAndAwaitAdvance();     // used to check rejections

                handledCounter.incrementAndGet();

                phaser.arriveAndAwaitAdvance();     // used to check if completed
            };

            final List<Consumer<String>> eventSubscriberCollection = Collections.singletonList(blockingConsumer);

            final String firstEvent = String.format("%03d", ZERO);
            DispatchingUtil.dispatchEventToSubscriberThreadedPerEvent(
                    firstEvent,
                    eventSubscriberCollection,
                    executorService,
                    spyLogger);

            final Supplier<String> arrivedPartiesSupplier =
                    () -> "Arrived parties value: " + phaser.getArrivedParties();

            final Executable arriveAndAwaitAdvance = phaser::arriveAndAwaitAdvance;

            // wait for dispatching of the first event to have started
            assertTimeoutPreemptively(
                    ofMillis(MAX_WAIT_TIME_IN_MS),
                    arriveAndAwaitAdvance,
                    arrivedPartiesSupplier);

            // check that the first task started but did not complete yet
            assertEquals(ONE, startedCounter.get());
            assertEquals(ZERO, handledCounter.get());

            verifyNoMoreInteractions(spyLogger);

            final String secondEvent = String.format("%03d", ONE);
            DispatchingUtil.dispatchEventToSubscriberThreadedPerEvent(
                    secondEvent,
                    eventSubscriberCollection,
                    executorService,
                    spyLogger);

            // assert that handler for first event is still blocking
            assertEquals(ONE, startedCounter.get());
            assertEquals(ZERO, handledCounter.get());

            final ArgumentCaptor<String> strCaptor = ArgumentCaptor.forClass(String.class);
            final ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
            verify(spyLogger, times(ONE))
                .error(
                        anyString(),
                        strCaptor.capture(),
                        exceptionCaptor.capture()
                );

            assertEquals(secondEvent, strCaptor.getValue());

            assertNotNull(exceptionCaptor.getValue());
            assertEquals(RejectedExecutionException.class, exceptionCaptor.getValue().getClass());

            // check that the first task did not complete yet
            assertEquals(ONE, startedCounter.get());
            assertEquals(ZERO, handledCounter.get());

            // release the blocking phaser
            assertTimeoutPreemptively(
                    ofMillis(MAX_WAIT_TIME_IN_MS),
                    arriveAndAwaitAdvance,
                    arrivedPartiesSupplier);

            // wait for the first event to have completed
            assertTimeoutPreemptively(
                    ofMillis(MAX_WAIT_TIME_IN_MS),
                    arriveAndAwaitAdvance,
                    arrivedPartiesSupplier);

            // check that the first event was fully dispatched
            assertEquals(ONE, startedCounter.get());
            assertEquals(ONE, handledCounter.get());
        }

        @Test
        public void dispatchEventToSubscriberWithCustomLogger() throws InterruptedException, ExecutionException, TimeoutException {
            final boolean useDefaultLogger = false;
            final boolean testExceptionToo = false;
            dispatchEventToSubscriber(useDefaultLogger, testExceptionToo);
        }

        @Test
        public void dispatchEventToSubscriberWithDefaultLogger() throws InterruptedException, ExecutionException, TimeoutException {
            final boolean useDefaultLogger = true;
            final boolean testExceptionToo = false;
            dispatchEventToSubscriber(useDefaultLogger, testExceptionToo);
        }

        @Test
        public void dispatchEventToSubscriberWithCustomLoggerWithExceptions() throws InterruptedException, ExecutionException, TimeoutException {
            final boolean useDefaultLogger = false;
            final boolean testExceptionToo = true;
            dispatchEventToSubscriber(useDefaultLogger, testExceptionToo);
        }

        @Test
        public void dispatchEventToSubscriberWithDefaultLoggerWithExceptions() throws InterruptedException, ExecutionException, TimeoutException {
            final boolean useDefaultLogger = true;
            final boolean testExceptionToo = true;
            dispatchEventToSubscriber(useDefaultLogger, testExceptionToo);
        }

        //##########################################################################
        // Helper methods
        //##########################################################################

        private void dispatchEventToSubscriber(
                final boolean useDefaultLogger,
                final boolean testExceptionToo
        ) throws InterruptedException, ExecutionException, TimeoutException {
            final int eventCount = DEFAULT_EVENT_COUNT;
            final int evenEventCount = eventCount / 2 + eventCount % 2;
            final int oddEventCount = eventCount / 2 ;
            final int subscriberCount = DEFAULT_SUBSCRIBER_COUNT;
            final int exceptionThrowingSubscriberCount = subscriberCount / 2;

            executorService = Executors.newFixedThreadPool(subscriberCount, threadFactory);

            final int totalEventCount     = subscriberCount * eventCount ;
            final int expectedEvenCount   = subscriberCount * evenEventCount;
            final int expectedOddCount    = subscriberCount * oddEventCount;
            final int totalExceptionCount = exceptionThrowingSubscriberCount * eventCount;

            final AtomicInteger evenCounter = new AtomicInteger(ZERO);
            final AtomicInteger oddCounter = new AtomicInteger(ZERO);
            final AtomicInteger handledCounter = new AtomicInteger(ZERO);
            final AtomicInteger exceptionCounter = new AtomicInteger(ZERO);

            final Map<TestEvent, Set<Integer>> threadHashCodesMap = new ConcurrentHashMap<>();

            final Consumer<TestEvent> eventConsumer = (dispatchedEvent) -> {
                final Set<Integer> threadHashCodes =
                        threadHashCodesMap.computeIfAbsent(dispatchedEvent, (event) -> ConcurrentHashMap.newKeySet());
                threadHashCodes.add(Thread.currentThread().hashCode());

                randomDelay(subscriberCount);

                final int n = Integer.valueOf(dispatchedEvent.id);
                final boolean even = isEven(n);
                if (even) {
                    evenCounter.incrementAndGet();
                } else {
                    oddCounter.incrementAndGet();
                }
                handledCounter.incrementAndGet();

                dispatchedEvent.dispatchedLatch.countDown();
            };

            final Consumer<TestEvent> exceptionalConsumer = (dispatchedEvent) -> {
                // reuse logic from "regular" consumer
                eventConsumer.accept(dispatchedEvent);

                try {
                    // now behave badly and throw an exception
                    throw new RuntimeException("Oops, this consumer throws an exception");
                } finally {
                    exceptionCounter.incrementAndGet();
                }
            };

            final List<Consumer<TestEvent>> eventSubscriberCollection = new ArrayList<>();
            for(int i = 0; i < subscriberCount; i++) {
                final boolean odd = isOdd(i);
                if (testExceptionToo && odd) {
                    eventSubscriberCollection.add(exceptionalConsumer);
                } else {
                    eventSubscriberCollection.add(eventConsumer);
                }
            }

            for(int loopIndex = 0; loopIndex < eventCount; loopIndex++) {
                // create unique events
                final String id = String.format("%03d", loopIndex);
                final CountDownLatch dispatchedLatch = new CountDownLatch(subscriberCount);
                final TestEvent event = new TestEvent(id, dispatchedLatch);
                dispatchEventAndAssert(
                        event, eventSubscriberCollection,
                        loopIndex,
                        handledCounter, exceptionCounter,
                        threadHashCodesMap,
                        useDefaultLogger,
                        testExceptionToo, RuntimeException.class, exceptionThrowingSubscriberCount,
                        executorService);
            }

            assertEquals(expectedEvenCount, evenCounter.get());
            assertEquals(expectedOddCount, oddCounter.get());
            if (testExceptionToo) {
                assertEquals(totalExceptionCount, exceptionCounter.get());
            }
            assertEquals(totalEventCount, handledCounter.get());

            assertThreadHandling(threadHashCodesMap);
        }

        private <T extends TestEvent> void dispatchEventAndAssert(
                final T event,
                final Collection<Consumer<T>> eventSubscriberCollection,
                final int loopIndex,
                final AtomicInteger handledCounter,
                final AtomicInteger exceptionCounter,
                final Map<T, Set<Integer>> threadHashCodesMap,
                final boolean useDefaultLogger,
                final boolean testExceptionToo,
                final Class<? extends Exception> exceptionClass,
                final int exceptionThrowingSubscriberCount,
                final Executor executor
        ) throws ExecutionException, InterruptedException, TimeoutException {
            final Logger spyLogger = Mockito.spy(new SpyableLogger(logger));
            if (useDefaultLogger) {
                DispatchingUtil.dispatchEventToSubscriberThreadedPerEvent(
                        event,
                        eventSubscriberCollection,
                        executor);
            } else {
                DispatchingUtil.dispatchEventToSubscriberThreadedPerEvent(
                        event,
                        eventSubscriberCollection,
                        executor,
                        spyLogger);
            }

            final int handledCount = (loopIndex + ONE) * eventSubscriberCollection.size();

            // wait for dispatching of the event to have completed
            assertTimeoutPreemptively(
                    ofMillis(MAX_WAIT_TIME_IN_MS),
                    (Executable) event.dispatchedLatch::await,
                    "event.dispatchedLatch value: " + event.dispatchedLatch.getCount());

            // the event was dispatched to all subscriber
            assertEquals(handledCount, handledCounter.get());

            if (testExceptionToo) {
                final int exceptionCount = (loopIndex + ONE) * exceptionThrowingSubscriberCount;
                // the exceptions were raised as expected
                assertEquals(exceptionCount, exceptionCounter.get());
            }

            if(!useDefaultLogger) {
                if (testExceptionToo) {
                    verify(spyLogger, times(exceptionThrowingSubscriberCount))
                        .warn(anyString(), eq(event), isA(exceptionClass));
                } else {
                    verifyNoMoreInteractions(spyLogger);
                }
            }

            assertThreadHandling(event, threadHashCodesMap);
        }

        private <T> void assertThreadHandling(
                final T event,
                final Map<T, Set<Integer>> threadHashCodesMap
        ) {
            final int thisThreadHashCode = Thread.currentThread().hashCode();
            assertThreadHandling(thisThreadHashCode, event, threadHashCodesMap);
        }

        private <T> void assertThreadHandling(final Map<T, Set<Integer>> threadHashCodesMap) {
            final int thisThreadHashCode = Thread.currentThread().hashCode();
            for(final Map.Entry<T, Set<Integer>> threadHashCodesEntry : threadHashCodesMap.entrySet()) {
                final T event = threadHashCodesEntry.getKey();

                assertThreadHandling(thisThreadHashCode, event, threadHashCodesMap);
            }
        }
        private <T> void assertThreadHandling(
                final int thisThreadHashCode,
                final T event,
                final Map<T, Set<Integer>> threadHashCodesMap
        ) {
            final Set<Integer> threadHashCodes = threadHashCodesMap.get(event);

            assertNotNull(threadHashCodes);

            // dispatching occurred only in one thread per event
            assertEquals(ONE, threadHashCodes.size());

            // dispatching occurred in thread different to this one
            assertFalse(threadHashCodes.contains(thisThreadHashCode));
        }

    }

    //##########################################################################
    // Test cases for {@link DispatchingUtil#dispatchEventToSubscriberThreadedPerSubscriber(Object, Collection, CompletableFuture, java.util.concurrent.Executor)}
    //##########################################################################

    @Nested
    public static class dispatchEventToSubscriberThreadedPerSubscriberTest {

        private final Logger logger = LoggerFactory.getLogger(getClass());

        private ExecutorService executorService;
        private ThreadFactory threadFactory;

        @BeforeEach
        public void beforeEachTest(final TestInfo testInfo) {
            final String threadNamePrefix = testInfo.getDisplayName();
            threadFactory = new SimpleThreadFactory(threadNamePrefix);
        }

        @AfterEach
        public void afterEachTest() {
            if (null != executorService) {
                executorService.shutdownNow();
                executorService = null;
            }
            if (null != threadFactory) {
                threadFactory = null;
            }
        }

        @Test
        public void dispatchEventToSubscriber_RejectedExecutionException() throws InterruptedException {
            final Logger spyLogger = spy(new SpyableLogger(logger)) ;

            final int subscriberCount = DEFAULT_SUBSCRIBER_COUNT;
            final int threadPoolSize = subscriberCount / 2;
            final int rejectedSubscriberCount = subscriberCount - threadPoolSize;

            final ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(
                    threadPoolSize, threadPoolSize,
                    0L, TimeUnit.MILLISECONDS,
                    new SynchronousQueue<Runnable>(), // required so that tasks don't queue up but are rejected
                    threadFactory);
            threadPoolExecutor.allowCoreThreadTimeOut(false);
            threadPoolExecutor.prestartAllCoreThreads();

            assertEquals(threadPoolSize, threadPoolExecutor.getPoolSize());

            executorService = threadPoolExecutor;

            final AtomicInteger startedCounter = new AtomicInteger(ZERO);
            final AtomicInteger handledCounter = new AtomicInteger(ZERO);

            final Phaser phaser = new Phaser(threadPoolSize);

            // register ourself so that we can control advance
            phaser.register();

            /*
             * This consumer will block for some time. Since we are using an
             * Executor with a fixed thread count and a SynchronousQueue no other
             * tasks can be queued/executed until it completes.
             */
            final Consumer<String> blockingConsumer = (dispatchedEvent) -> {
                startedCounter.incrementAndGet();
                phaser.arriveAndAwaitAdvance();     // used to check how may consumer were started

                phaser.arriveAndAwaitAdvance();     // used to check rejections

                handledCounter.incrementAndGet();

                phaser.arriveAndAwaitAdvance();     // used to check if completed
            };

            final List<Consumer<String>> eventSubscriberCollection = new ArrayList<>();
            for (int i = 0; i < subscriberCount; i++) {
                eventSubscriberCollection.add(blockingConsumer);
            }

            final String event = String.format("%03d", ZERO);
            DispatchingUtil.dispatchEventToSubscriberThreadedPerSubscriber(
                    event,
                    eventSubscriberCollection,
                    executorService,
                    spyLogger);

            final Supplier<String> arrivedPartiesSupplier =
                    () -> "Arrived parties value: " + phaser.getArrivedParties();

            final Executable arriveAndAwaitAdvance = phaser::arriveAndAwaitAdvance;

            // wait for dispatching of the first event to have started
            assertTimeoutPreemptively(
                    ofMillis(MAX_WAIT_TIME_IN_MS),
                    arriveAndAwaitAdvance,
                    arrivedPartiesSupplier);

            // check that the first tasks started but did not complete yet
            assertEquals(threadPoolSize, startedCounter.get());
            assertEquals(ZERO, handledCounter.get());

            final ArgumentCaptor<String> strCaptor = ArgumentCaptor.forClass(String.class);
            final ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
            verify(spyLogger, times(rejectedSubscriberCount))
                .error(
                        anyString(),
                        strCaptor.capture(),
                        exceptionCaptor.capture()
                );

            for (int i = 0; i < rejectedSubscriberCount; i++) {
                assertEquals(event, strCaptor.getAllValues().get(i));

                final Exception exception = exceptionCaptor.getAllValues().get(i);
                assertNotNull(exception);
                assertEquals(RejectedExecutionException.class, exception.getClass());
            }

            // release the blocking phase
            assertTimeoutPreemptively(
                    ofMillis(MAX_WAIT_TIME_IN_MS),
                    arriveAndAwaitAdvance,
                    arrivedPartiesSupplier);

            // wait for the first event to have completed
            assertTimeoutPreemptively(
                    ofMillis(MAX_WAIT_TIME_IN_MS),
                    arriveAndAwaitAdvance,
                    arrivedPartiesSupplier);

            // check that only the accepted tasks by the executor were started and completed
            assertEquals(threadPoolSize, startedCounter.get());
            assertEquals(threadPoolSize, handledCounter.get());
        }

        @Test
        public void dispatchEventToSubscriberWithCustomLogger() throws InterruptedException, ExecutionException, TimeoutException {
            final boolean useDefaultLogger = false;
            final boolean testExceptionToo = false;
            dispatchEventToSubscriber(useDefaultLogger, testExceptionToo);
        }

        @Test
        public void dispatchEventToSubscriberWithDefaultLogger() throws InterruptedException, ExecutionException, TimeoutException {
            final boolean useDefaultLogger = true;
            final boolean testExceptionToo = false;
            dispatchEventToSubscriber(useDefaultLogger, testExceptionToo);
        }

        @Test
        public void dispatchEventToSubscriberWithCustomLoggerWithExceptions() throws InterruptedException, ExecutionException, TimeoutException {
            final boolean useDefaultLogger = false;
            final boolean testExceptionToo = true;
            dispatchEventToSubscriber(useDefaultLogger, testExceptionToo);
        }

        @Test
        public void dispatchEventToSubscriberWithDefaultLoggerWithExceptions() throws InterruptedException, ExecutionException, TimeoutException {
            final boolean useDefaultLogger = true;
            final boolean testExceptionToo = true;
            dispatchEventToSubscriber(useDefaultLogger, testExceptionToo);
        }

        //##########################################################################
        // Helper methods
        //##########################################################################

        private void dispatchEventToSubscriber(
                final boolean useDefaultLogger,
                final boolean testExceptionToo
        ) throws InterruptedException, ExecutionException, TimeoutException {
            final int eventCount = DEFAULT_EVENT_COUNT;
            final int evenEventCount = eventCount / 2 + eventCount % 2;
            final int oddEventCount = eventCount / 2 ;
            final int subscriberCount = DEFAULT_SUBSCRIBER_COUNT;
            final int exceptionThrowingSubscriberCount = subscriberCount / 2;

            final ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(
                    START_THREAD_COUNT, Integer.MAX_VALUE,
                    60L, TimeUnit.SECONDS,
                    new LinkedBlockingQueue<Runnable>(),
                    threadFactory);
            threadPoolExecutor.prestartAllCoreThreads();

            executorService = threadPoolExecutor;

            final int totalEventCount     = subscriberCount * eventCount ;
            final int expectedEvenCount   = subscriberCount * evenEventCount;
            final int expectedOddCount    = subscriberCount * oddEventCount;
            final int totalExceptionCount = exceptionThrowingSubscriberCount * eventCount;

            final AtomicInteger evenCounter = new AtomicInteger(ZERO);
            final AtomicInteger oddCounter = new AtomicInteger(ZERO);
            final AtomicInteger handledCounter = new AtomicInteger(ZERO);
            final AtomicInteger exceptionCounter = new AtomicInteger(ZERO);

            final Map<TestEvent, Set<Integer>> threadHashCodesMap = new ConcurrentHashMap<>();

            final Consumer<TestEvent> eventConsumer = (dispatchedEvent) -> {
                final int threadHashCode = Thread.currentThread().hashCode();

                final Set<Integer> threadHashCodes = threadHashCodesMap.computeIfAbsent(
                                dispatchedEvent,
                                (event) -> ConcurrentHashMap.newKeySet());

                threadHashCodes.add(threadHashCode);

                randomDelay(subscriberCount);

                final int n = Integer.valueOf(dispatchedEvent.id);
                final boolean even = isEven(n);
                if (even) {
                    evenCounter.incrementAndGet();
                } else {
                    oddCounter.incrementAndGet();
                }
                handledCounter.incrementAndGet();

                dispatchedEvent.dispatchedLatch.countDown();
            };

            final Consumer<TestEvent> exceptionalConsumer = (dispatchedEvent) -> {
                // reuse logic from "regular" consumer
                eventConsumer.accept(dispatchedEvent);

                try {
                    // now behave badly and throw an exception
                    throw new RuntimeException("Oops, this consumer throws an exception");
                } finally {
                    exceptionCounter.incrementAndGet();
                    dispatchedEvent.exceptionLatch.countDown();
                }
            };

            final AtomicInteger exceptionalConsumerCounter = new AtomicInteger(ZERO);
            final List<Consumer<TestEvent>> eventSubscriberCollection = new ArrayList<>();
            for(int i = 0; i < subscriberCount; i++) {
                final boolean odd = isOdd(i);
                if (testExceptionToo && odd) {
                    eventSubscriberCollection.add(exceptionalConsumer);
                    exceptionalConsumerCounter.incrementAndGet();
                } else {
                    eventSubscriberCollection.add(eventConsumer);
                }
            }

            for(int loopIndex = 0; loopIndex < eventCount; loopIndex++) {
                // create unique event
                final TestEvent event = createEvent(
                        loopIndex,
                        subscriberCount,
                        exceptionalConsumerCounter.get());
                dispatchEventAndAssert(
                        event, eventSubscriberCollection,
                        loopIndex,
                        handledCounter, exceptionCounter,
                        threadHashCodesMap,
                        useDefaultLogger,
                        testExceptionToo, RuntimeException.class, exceptionThrowingSubscriberCount,
                        executorService);
            }

            assertEquals(expectedEvenCount, evenCounter.get());
            assertEquals(expectedOddCount, oddCounter.get());
            if (testExceptionToo) {
                assertEquals(totalExceptionCount, exceptionCounter.get());
            }
            assertEquals(totalEventCount, handledCounter.get());

            assertEquals(eventCount, threadHashCodesMap.size());

            assertThreadHandling(threadHashCodesMap);
            assertEquals(eventCount, threadHashCodesMap.size());
        }

        private TestEvent createEvent(
                final int loopIndex,
                final int subscriberCount,
                final int exceptionalConsumerCount
        ) {
            final String id = String.format("%03d", loopIndex);
            final CountDownLatch dispatchedLatch = new CountDownLatch(subscriberCount);
            final CountDownLatch exceptionalLatch = new CountDownLatch(exceptionalConsumerCount);
            final TestEvent event = new TestEvent(id, dispatchedLatch, exceptionalLatch);
            return event;
        }

        private <T extends TestEvent> void dispatchEventAndAssert(
                final T event,
                final Collection<Consumer<T>> eventSubscriberCollection,
                final int loopIndex,
                final AtomicInteger handledCounter,
                final AtomicInteger exceptionCounter,
                final Map<T, Set<Integer>> threadHashCodesMap,
                final boolean useDefaultLogger,
                final boolean testExceptionToo,
                final Class<? extends Exception> exceptionClass,
                final int exceptionThrowingSubscriberCount,
                final Executor executor
        ) throws ExecutionException, InterruptedException, TimeoutException {
            final Logger spyLogger = Mockito.spy(new SpyableLogger(logger));
            if (useDefaultLogger) {
                DispatchingUtil.dispatchEventToSubscriberThreadedPerSubscriber(
                        event,
                        eventSubscriberCollection,
                        executor);
            } else {
                DispatchingUtil.dispatchEventToSubscriberThreadedPerSubscriber(
                        event,
                        eventSubscriberCollection,
                        executor,
                        spyLogger);
            }

            final int handledCount = (loopIndex + ONE) * eventSubscriberCollection.size();

            // wait for dispatching of the event to have completed
            assertTimeoutPreemptively(
                    ofMillis(MAX_WAIT_TIME_IN_MS),
                    (Executable) event.dispatchedLatch::await,
                    "event.dispatchedLatch value: " + event.dispatchedLatch.getCount());

            // the event was dispatched to all subscriber
            assertEquals(handledCount, handledCounter.get());

            if (testExceptionToo) {
                // wait for dispatching of the event to have completed
                assertTimeoutPreemptively(
                        ofMillis(MAX_WAIT_TIME_IN_MS),
                        (Executable) event.exceptionLatch::await,
                        "event.exceptionLatch value: " + event.exceptionLatch.getCount());
            }

            if (testExceptionToo) {
                final int exceptionCount = (loopIndex + ONE) * exceptionThrowingSubscriberCount;
                // the exceptions were raised as expected
                assertEquals(exceptionCount, exceptionCounter.get());
            }

            if(!useDefaultLogger) {
                if (testExceptionToo) {
                    verify(spyLogger, timeout(MAX_WAIT_TIME_IN_MS).times(exceptionThrowingSubscriberCount))
                        .warn(
                                anyString(),
                                eq(event),
                                isA(exceptionClass)
                        );
                } else {
                    verifyNoMoreInteractions(spyLogger);
                }
            }

        }

        private <T> void assertThreadHandling(final Map<T, Set<Integer>> threadHashCodesMap) {
            final int thisThreadHashCode = Thread.currentThread().hashCode();
            for(final Map.Entry<T, Set<Integer>> threadHashCodesEntry : threadHashCodesMap.entrySet()) {
                assertThreadHandling(thisThreadHashCode, threadHashCodesEntry.getValue());
            }
        }

        private <T> void assertThreadHandling(
                final int thisThreadHashCode,
                final Set<Integer> threadHashCodes
        ) {
            assertNotNull(threadHashCodes);

            // dispatching occurred in more than one thread per event
            assertTrue(ZERO < threadHashCodes.size());

            // dispatching occurred in thread different to this one
            assertFalse(threadHashCodes.contains(thisThreadHashCode));
        }

    }

    //##########################################################################
    // Static helper methods
    //##########################################################################

    /**
     * Compiler infers <E> to a RuntimeException. Now we can throw everything!
     * 
     * @param <E>
     *            type of exception to throw
     * @param e
     *            exception to throw
     * @throws E
     *             given exception
     */
    @SuppressWarnings("unchecked")
    private static <E extends Throwable> void sneakyThrow(final Throwable e) throws E {
        throw (E) e;
    }

    private static boolean isEven(int i) {
        return i % 2 == 0;
    }

    private static boolean isOdd(int i) {
        return i % 2 != 0;
    }

    /**
     * Delay with a bit of randomness.<br>
     * Internally it uses a recursive Fibonaicci function to to find a new
     * sequence index for a second call to the Fibonacci sequence.
     * 
     * @param minIndex
     *            minimum index of the first Fibonacci sequence value
     */
    private static void randomDelay(final int minIndex) {
        final int partiallyRandomN = minIndex + rnd.nextInt(minIndex);
        final int randomIndex = fibonacci(partiallyRandomN);

        fibonacci(randomIndex);
    }

    /**
     * Recursive Fibonacci implementation. Used to introduce some delay into
     * consumers.<br>
     * {@link Thread#sleep(long)} does not seem to be fine grained enough.
     * 
     * @param index
     *            of value in Fibonacci sequence
     * @return value of Fibonacci at {@code index}
     */
    private static int fibonacci(final int index) {
        if (ZERO < index) {
            return index + fibonacci(index - ONE);
        } else {
            return ZERO;
        }
    }

    /**
     * Special event used to track when an event has been dispatched to all subscriber.
     */
    private static class TestEvent {
        public final String id;
        public final CountDownLatch dispatchedLatch;
        public final CountDownLatch exceptionLatch;

        public TestEvent(
                final String id,
                final CountDownLatch dispatchedLatch
        ) {
            this(id, dispatchedLatch, new CountDownLatch(ZERO));
        }

        public TestEvent(
                final String id,
                final CountDownLatch dispatchedLatch,
                final CountDownLatch exceptionLatch
        ) {
            this.id = Objects.requireNonNull(id, "'id' must not be null");
            this.dispatchedLatch = Objects.requireNonNull(dispatchedLatch, "'dispatchedLatch' must not be null");
            this.exceptionLatch = Objects.requireNonNull(exceptionLatch, "'exceptionLatch' must not be null");
        }

        public int hashCode() {
            int tmpHashCode = 37;
            tmpHashCode *= id.hashCode() + 17;
            tmpHashCode *= dispatchedLatch.hashCode() + 17;
            tmpHashCode *= exceptionLatch.hashCode() + 17;

            return tmpHashCode;
        }
    }

}
