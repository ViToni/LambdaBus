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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

}
