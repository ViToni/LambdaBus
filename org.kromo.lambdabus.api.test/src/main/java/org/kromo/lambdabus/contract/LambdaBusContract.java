/*
 * Copyright (c) 2019: Victor Toni
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
 */
package org.kromo.lambdabus.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import org.kromo.lambdabus.DeadEvent;
import org.kromo.lambdabus.LambdaBus;
import org.kromo.lambdabus.Subscription;
import org.kromo.lambdabus.ThreadingMode;
import org.kromo.lambdabus.test.util.MultithreadedTasks;

/**
 * Defines tests for behavioral aspects expected from implementations of the
 * {@link LambdaBus} interface.
 *
 * @param <LambdaBusType>
 *            type to test which implements the {@link LambdaBus} interface
 *
 * @author Victor Toni - initial implementation
 *
 */
public abstract class LambdaBusContract<LambdaBusType extends LambdaBus> {

    /**
     * Timeout used to wait for published events to complete
     */
    protected static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(2);

    /**
     * Number of threads to use for multithreaded dispatching
     */
    protected static final int THREAD_COUNT = 61;

    // using prime numbers to avoid working tests by coincidence
    protected static final int EVENTS_OF_TYPE_A_COUNT = 20011;
    protected static final int EVENTS_OF_TYPE_B_COUNT = 10007;
    protected static final int EVENTS_OF_TYPE_C_COUNT = 5003;

    // used for "small" tests were we don't need many samples
    protected static final int TINY_EVENT_COUNT = 17;

    // using prime numbers to avoid working tests by coincidence
    protected static final int EXCEPTION_THROWING_SUBSCRIBER_COUNT = 101;

    private final AtomicInteger aExceptionCounter = new AtomicInteger();
    private final AtomicInteger a1InterfaceExceptionCounter = new AtomicInteger();
    private final AtomicInteger handleA2InterfaceException_counter = new AtomicInteger();

    private final AtomicInteger handleA_counter = new AtomicInteger();
    private final AtomicInteger handleA1Interface_counter = new AtomicInteger();
    private final AtomicInteger handleA2Interface_counter = new AtomicInteger();

    private final AtomicInteger handleB_counter = new AtomicInteger();
    private final AtomicInteger handleB1Interface_counter = new AtomicInteger();

    private final AtomicInteger handleC_counter = new AtomicInteger();
    private final AtomicInteger handleC1Interface_counter = new AtomicInteger();

    /**
     * Fixed mapping for the {@link LambdaBus#post(Object)} method used for generic
     * tests.
     */
    private static final BiConsumer<LambdaBus, Object> POST_METHOD = LambdaBus::post;

    /**
     * Fixed mapping for the {@link LambdaBus#post(Object)} and
     * {@link LambdaBus#post(Object, ThreadingMode)} methods used for generic tests.
     */
    private static final List<Arguments> POST_METHODS_WITH_NAMES;
    static {
        final List<Arguments> postMethods = new ArrayList<>(1 + ThreadingMode.values().length);

        // regular LambdaBus.post(event)
        postMethods.add(Arguments.of(getPostMethod(), "LambdaBus.post(event)"));

        // all variations of LambdaBus.post(event, ThreadingMode)
        for (final ThreadingMode threadingModeHint : ThreadingMode.values()) {
            final String nameParameter = "LambdaBus.post(event, " + threadingModeHint + ")";
            postMethods.add(Arguments.of(getPostMethod(threadingModeHint), nameParameter));
        }

        POST_METHODS_WITH_NAMES = Collections.unmodifiableList(postMethods);
    }

    /**
     * Provider for all variations of {@link LambdaBus#post(Object)} and
     * {@link LambdaBus#post(Object, ThreadingMode)} so that we can write only one
     * parameterized test per use case.
     *
     * @return {@link Stream} of {@link Arguments} used for parameterized tests
     */
    protected static Stream<Arguments> getPostMethodsWithNames() {
        return POST_METHODS_WITH_NAMES.stream();
    }

    /**
     * Method must be implemented to return an instance of the {@link LambdaBus}
     * implementation to be tested if it adheres to this contract.
     *
     * @return an instance of the {@link LambdaBus} implementation to be tested
     */
    protected abstract LambdaBusType createLambdaBus();

    /**
     * Each test gets an index which will be used to customize the
     * {@link #threadNamePrefix}.
     */
    private final AtomicInteger testCount = new AtomicInteger();

    /**
     * Used to customize the names of {@link Thread}s used for each test.
     */
    private String threadNamePrefix;

    /**
     * Helper for parallel dispatching.
     */
    private MultithreadedTasks parallelTasks = null;

    /**
     * Initialize all counter used before each test.
     */
    @BeforeEach
    public void beforeEachTest() {
        aExceptionCounter.set(0);
        a1InterfaceExceptionCounter.set(0);
        handleA2InterfaceException_counter.set(0);

        handleA_counter.set(0);
        handleA1Interface_counter.set(0);
        handleA2Interface_counter.set(0);

        handleB_counter.set(0);
        handleB1Interface_counter.set(0);

        handleC_counter.set(0);
        handleC1Interface_counter.set(0);

        // increment for next test
        threadNamePrefix = getClass().getSimpleName() + testCount.incrementAndGet();

        // create a new instance for each test
        parallelTasks = new MultithreadedTasks(THREAD_COUNT, threadNamePrefix);
    }

    /**
     * Checks that the {@link LambdaBus} implementation can be created without
     * exceptions.
     */
    @Test
    @DisplayName("Creating a LambdaBus does not throw exceptions")
    public void createLambdaBusDoesNotThrowExceptions() {
        try (final LambdaBus lb = createLambdaBus()) {
            assertNotNull(lb);
        }
    }

    /**
     * Checks that the {@link LambdaBus} implementation can be created and closed
     * without exceptions.
     */
    @Test
    @DisplayName("Creating a LambdaBus and explicit LambdaBus.close() does not throw exceptions")
    public void createLambdaBusAndExplicitCloseDoesNotThrowExceptions() {
        try (final LambdaBus lb = createLambdaBus()) {
            lb.close();
        }
    }

    /**
     * Checks that the {@link LambdaBus} implementation has a custom toString()
     * method.
     */
    @Test
    @DisplayName("LambdaBus.toString() is overridden")
    public void toStringIsOverridden() {
        try (final LambdaBus lb = createLambdaBus()) {
            final String customToString = lb.toString();

            assertFalse(customToString.startsWith("java.lang.Object@"),
                    "No custom toString() method");
        }
    }

    // ##########################################################################
    // Test subscription, unsubscription and detection of handlers
    // ##########################################################################

    /**
     * Tests that a {@link NullPointerException} is thrown when {@code null} is used
     * for {@link Class} while subscribing.
     */
    @Test
    @DisplayName("LambdaBus.subscribe(null, Consumer) throws NullPointerException")
    public void subscribingWithNullClassThrowsNullPointerException() {
        try (final LambdaBus lb = createLambdaBus()) {
            assertThrows(
                    NullPointerException.class,
                    () -> lb.subscribe(null, this::handleA));
        }
    }

    /**
     * Tests that a {@link NullPointerException} is thrown when {@code null} is used
     * for {@link Consumer} while subscribing.
     */
    @Test
    @DisplayName("LambdaBus.subscribe(Class, null) throws NullPointerException")
    public void subscribingWithNullSubscriberThrowsNullPointerException() {
        try (final LambdaBus lb = createLambdaBus()) {
            assertThrows(
                    NullPointerException.class,
                    () -> lb.subscribe(A.class, null));
        }
    }

    /**
     * Test that subscribers can be added to the bus without exceptions.
     */
    @Test
    @DisplayName("Regular LambdaBus.subscribe() does not throw exceptions")
    public void subscribe() {
        try (final LambdaBus lb = createLambdaBus()) {
            lb.subscribe(A.class, this::handleA);
            lb.subscribe(A.class, this::handleA1Interface);

            lb.subscribe(A2Interface.class, this::handleA2Interface);

            lb.subscribe(B.class, this::handleB);

            lb.subscribe(B1Interface.class, this::handleB1Interface);

            lb.subscribe(C.class, this::handleC);
        }
    }

    /**
     * Test that subscribers of class type are recognized as subscriber regardless
     * of related interfaces.
     */
    @Test
    @DisplayName("Handler subscribed by class is found by LambdaBus.hasSubscriber()")
    public void hasSubscriberAfterSubscribingWithClass() {
        try (final LambdaBus lb = createLambdaBus()) {

            // assert start configuration
            assertNoSubscriber(lb);

            lb.subscribe(A.class, this::handleA);
            assertHasSubscriber(lb, A.class);

            assertNoSubscriber(lb, B.class, C.class);
            assertNoSubscriber(lb, A1Interface.class, B1Interface.class, C1Interface.class);

            lb.subscribe(B.class, this::handleB);
            assertHasSubscriber(lb, A.class, B.class);

            assertNoSubscriber(lb, C.class);
            assertNoSubscriber(lb, A1Interface.class, B1Interface.class, C1Interface.class);

            lb.subscribe(C.class, this::handleC);
            assertHasSubscriber(lb, A.class, B.class, C.class);

            assertNoSubscriber(lb, A1Interface.class, B1Interface.class, C1Interface.class);
        }
    }

    /**
     * Test that subscribers of interface type are recognized as subscriber
     * regardless of related classes.
     */
    @Test
    @DisplayName("Handler subscribed by interface is found by LambdaBus.hasSubscriber()")
    public void hasSubscriberAfterSubscribeWithInterface_() {
        try (final LambdaBus lb = createLambdaBus()) {

            // assert start configuration
            assertNoSubscriber(lb);

            lb.subscribe(A1Interface.class, this::handleA1Interface);
            assertHasSubscriber(lb, A1Interface.class);

            assertNoSubscriber(lb, B1Interface.class, C1Interface.class);
            assertNoSubscriber(lb, A.class, B.class, C.class);

            lb.subscribe(B1Interface.class, this::handleB1Interface);
            assertHasSubscriber(lb, A1Interface.class, B1Interface.class);

            assertNoSubscriber(lb, C1Interface.class);
            assertNoSubscriber(lb, A.class, B.class, C.class);

            lb.subscribe(C1Interface.class, this::handleC1Interface);
            assertHasSubscriber(lb, A1Interface.class, B1Interface.class, C1Interface.class);

            assertNoSubscriber(lb, A.class, B.class, C.class);
        }
    }

    /**
     * Test that subscribers can be unsubscribed via {@link Subscription#close()}.
     */
    @Test
    @DisplayName("Subscriber is unsubscribed by Subscription.close()")
    public void closingSubscriptionRemovesSubscription() {
        try (final LambdaBus lb = createLambdaBus()) {

            // assert start configuration
            assertNoSubscriber(lb);

            // subscribe to classes

            final Subscription handleASubscription = lb.subscribe(A.class, this::handleA);
            assertHasSubscriber(lb, A.class);

            assertNoSubscriber(lb, B.class, C.class);
            assertNoSubscriber(lb, A1Interface.class, B1Interface.class, C1Interface.class);

            final Subscription handleBSubscription = lb.subscribe(B.class, this::handleB);
            assertHasSubscriber(lb, A.class, B.class);

            assertNoSubscriber(lb, C.class);
            assertNoSubscriber(lb, A1Interface.class, B1Interface.class, C1Interface.class);

            final Subscription handleCSubscription = lb.subscribe(C.class, this::handleC);
            assertHasSubscriber(lb, A.class, B.class, C.class);

            assertNoSubscriber(lb, A1Interface.class, B1Interface.class, C1Interface.class);

            // subscribe to interfaces

            final Subscription handleA1InterfaceSubscription = lb.subscribe(A1Interface.class,
                    this::handleA1Interface);
            assertHasSubscriber(lb, A.class, B.class, C.class);
            assertHasSubscriber(lb, A1Interface.class);

            assertNoSubscriber(lb, B1Interface.class, C1Interface.class);

            final Subscription handleB1InterfaceSubscription = lb.subscribe(B1Interface.class,
                    this::handleB1Interface);
            assertHasSubscriber(lb, A.class, B.class, C.class);
            assertHasSubscriber(lb, A1Interface.class, B1Interface.class);

            assertNoSubscriber(lb, C1Interface.class);

            final Subscription handleC1InterfaceSubscription = lb.subscribe(C1Interface.class,
                    this::handleC1Interface);
            assertHasSubscriber(lb, A.class, B.class, C.class);
            assertHasSubscriber(lb, A1Interface.class, B1Interface.class, C1Interface.class);

            // unsubscribe class handler

            handleASubscription.close();
            assertNoSubscriber(lb, A.class);

            assertHasSubscriber(lb, B.class, C.class);
            assertHasSubscriber(lb, A1Interface.class, B1Interface.class, C1Interface.class);

            handleBSubscription.close();
            assertNoSubscriber(lb, A.class, B.class);

            assertHasSubscriber(lb, C.class);
            assertHasSubscriber(lb, A1Interface.class, B1Interface.class, C1Interface.class);

            handleCSubscription.close();
            assertNoSubscriber(lb, A.class, B.class, C.class);

            assertHasSubscriber(lb, A1Interface.class, B1Interface.class, C1Interface.class);

            // unsubscribe interface handler

            handleA1InterfaceSubscription.close();
            assertNoSubscriber(lb, A.class, B.class, C.class);
            assertNoSubscriber(lb, A1Interface.class);

            assertHasSubscriber(lb, B1Interface.class, C1Interface.class);

            handleB1InterfaceSubscription.close();
            assertNoSubscriber(lb, A.class, B.class, C.class);
            assertNoSubscriber(lb, A1Interface.class, B1Interface.class);

            assertHasSubscriber(lb, C1Interface.class);

            handleC1InterfaceSubscription.close();
            assertNoSubscriber(lb, A.class, B.class, C.class);
            assertNoSubscriber(lb, A1Interface.class, B1Interface.class, C1Interface.class);

            // removed everything, assert start/end configuration
            assertNoSubscriber(lb);
        }
    }

    /**
     * Test that subscribers can be unsubscribed in reverse order via
     * {@link Subscription#close()}.
     */
    @Test
    @DisplayName("Unsubscribing via Subscription.close() is side-effect free and can be done in reverse/any order")
    public void closingSubscriptionInReverseOrderIsSideEffectFree() {
        try (final LambdaBus lb = createLambdaBus()) {

            // assert start configuration
            assertNoSubscriber(lb);

            // subscribe to classes

            final Subscription handleASubscription = lb.subscribe(A.class, this::handleA);
            assertHasSubscriber(lb, A.class);

            assertNoSubscriber(lb, B.class, C.class);
            assertNoSubscriber(lb, A1Interface.class, B1Interface.class, C1Interface.class);

            final Subscription handleBSubscription = lb.subscribe(B.class, this::handleB);
            assertHasSubscriber(lb, A.class, B.class);

            assertNoSubscriber(lb, C.class);
            assertNoSubscriber(lb, A1Interface.class, B1Interface.class, C1Interface.class);

            final Subscription handleCSubscription = lb.subscribe(C.class, this::handleC);
            assertHasSubscriber(lb, A.class, B.class, C.class);

            assertNoSubscriber(lb, A1Interface.class, B1Interface.class, C1Interface.class);

            // subscribe to interfaces

            final Subscription handleA1InterfaceSubscription = lb.subscribe(A1Interface.class,
                    this::handleA1Interface);
            assertHasSubscriber(lb, A.class, B.class, C.class);
            assertHasSubscriber(lb, A1Interface.class);

            assertNoSubscriber(lb, B1Interface.class, C1Interface.class);

            final Subscription handleB1InterfaceSubscription = lb.subscribe(B1Interface.class,
                    this::handleB1Interface);
            assertHasSubscriber(lb, A.class, B.class, C.class);
            assertHasSubscriber(lb, A1Interface.class, B1Interface.class);

            assertNoSubscriber(lb, C1Interface.class);

            final Subscription handleC1InterfaceSubscription = lb.subscribe(C1Interface.class,
                    this::handleC1Interface);
            assertHasSubscriber(lb, A.class, B.class, C.class);
            assertHasSubscriber(lb, A1Interface.class, B1Interface.class, C1Interface.class);

            // unsubscribe interface handler

            handleC1InterfaceSubscription.close();
            assertHasSubscriber(lb, A.class, B.class, C.class);
            assertHasSubscriber(lb, A1Interface.class, B1Interface.class);

            assertNoSubscriber(lb, C1Interface.class);

            handleB1InterfaceSubscription.close();
            assertHasSubscriber(lb, A.class, B.class, C.class);
            assertHasSubscriber(lb, A1Interface.class);

            assertNoSubscriber(lb, B1Interface.class, C1Interface.class);

            handleA1InterfaceSubscription.close();
            assertHasSubscriber(lb, A.class, B.class, C.class);

            assertNoSubscriber(lb, A1Interface.class, B1Interface.class, C1Interface.class);

            // unsubscribe class handler

            handleCSubscription.close();
            assertHasSubscriber(lb, A.class, B.class);

            assertNoSubscriber(lb, C.class);
            assertNoSubscriber(lb, A1Interface.class, B1Interface.class, C1Interface.class);

            handleBSubscription.close();
            assertHasSubscriber(lb, A.class);

            assertNoSubscriber(lb, B.class, C.class);
            assertNoSubscriber(lb, A1Interface.class, B1Interface.class, C1Interface.class);

            handleASubscription.close();
            assertNoSubscriber(lb, A.class, B.class, C.class);
            assertNoSubscriber(lb, A1Interface.class, B1Interface.class, C1Interface.class);

            // removed everything, assert start/end configuration
            assertNoSubscriber(lb);
        }
    }

    /**
     * Test that unsubscribing multiple times via {@link Subscription#close()} is
     * side effect free.
     */
    @Test
    @DisplayName("Unsubscribing via Subscription.close() is side-effect free and can be done multiple times")
    public void closingSubscriptionMultipleTimesIsSideEffectFree() {
        try (final LambdaBus lb = createLambdaBus()) {

            // assert start configuration
            assertNoSubscriber(lb);

            final Subscription handleASubscription = lb.subscribe(A.class, this::handleA);
            assertFalse(handleASubscription.isClosed(), "Created Subscription must not be closed.");
            assertEquals(A.class, handleASubscription.forClass(),
                    "Created Subscription is not for expected class.");
            assertHasSubscriber(lb, A.class);

            assertNoSubscriber(lb, B.class, C.class);
            assertNoSubscriber(lb, A1Interface.class, B1Interface.class, C1Interface.class);

            handleASubscription.close();
            assertTrue(handleASubscription.isClosed(), "Closed Subscription must be closed.");
            assertNoSubscriber(lb, A.class, B.class, C.class);
            assertNoSubscriber(lb, A1Interface.class, B1Interface.class, C1Interface.class);

            final Subscription handleCSubscription = lb.subscribe(C.class, this::handleC);
            assertFalse(handleCSubscription.isClosed(), "Created Subscription must not be closed.");
            assertEquals(C.class, handleCSubscription.forClass(),
                    "Created Subscription is not for expected class.");
            assertHasSubscriber(lb, C.class);

            assertNoSubscriber(lb, A.class, B.class);
            assertNoSubscriber(lb, A1Interface.class, B1Interface.class, C1Interface.class);

            handleASubscription.close();
            assertTrue(handleASubscription.isClosed(), "Closed Subscription must be closed.");
            assertHasSubscriber(lb, C.class);

            assertNoSubscriber(lb, A.class, B.class);
            assertNoSubscriber(lb, A1Interface.class, B1Interface.class, C1Interface.class);

            handleCSubscription.close();
            assertTrue(handleCSubscription.isClosed(), "Closed Subscription must be closed.");
            assertNoSubscriber(lb, A.class, B.class, C.class);
            assertNoSubscriber(lb, A1Interface.class, B1Interface.class, C1Interface.class);

            handleCSubscription.close();
            assertTrue(handleCSubscription.isClosed(), "Closed Subscription must be closed.");
            assertNoSubscriber(lb, A.class, B.class, C.class);
            assertNoSubscriber(lb, A1Interface.class, B1Interface.class, C1Interface.class);

            handleASubscription.close();
            assertTrue(handleASubscription.isClosed(), "Closed Subscription must be closed.");
            assertNoSubscriber(lb, A.class, B.class, C.class);
            assertNoSubscriber(lb, A1Interface.class, B1Interface.class, C1Interface.class);

            // removed everything, assert start/end configuration
            assertNoSubscriber(lb);
        }
    }

    /**
     * Test closing the event bus removes subscribers.
     */
    @Test
    @DisplayName("LambdaBus.close() unsubscribes all subscribed handlers")
    public void lambdaBusCloseClosesAllSubscriptions() {
        final List<Subscription> subscriptions = new ArrayList<>();
        final LambdaBus lb = createLambdaBus();
        try {

            // assert start configuration
            assertNoSubscriber(lb);

            final Subscription subscriptionA = lb.subscribe(A.class, this::handleA);
            assertFalse(subscriptionA.isClosed(), "Created Subscription must not be closed.");
            assertEquals(A.class, subscriptionA.forClass(),
                    "Created Subscription is not for expected class.");
            assertHasSubscriber(lb, A.class);

            subscriptions.add(subscriptionA);

            assertNoSubscriber(lb, B.class, C.class);
            assertNoSubscriber(lb, A1Interface.class, B1Interface.class, C1Interface.class);

            final Subscription subscriptionB = lb.subscribe(B.class, this::handleB);
            assertFalse(subscriptionB.isClosed(), "Created Subscription must not be closed.");
            assertEquals(B.class, subscriptionB.forClass(),
                    "Created Subscription is not for expected class.");
            assertHasSubscriber(lb, A.class, B.class);

            subscriptions.add(subscriptionB);

            assertNoSubscriber(lb, C.class);
            assertNoSubscriber(lb, A1Interface.class, B1Interface.class, C1Interface.class);

            final Subscription subscriptionC = lb.subscribe(C.class, this::handleC);
            assertFalse(subscriptionC.isClosed(), "Created Subscription must not be closed.");
            assertEquals(C.class, subscriptionC.forClass(),
                    "Created Subscription is not for expected class.");
            assertHasSubscriber(lb, A.class, B.class, C.class);

            subscriptions.add(subscriptionC);

            assertNoSubscriber(lb, A1Interface.class, B1Interface.class, C1Interface.class);
        } finally {
            lb.close();
        }

        // now all subscribers should be gone
        assertNoSubscriber(lb);

        for (final Subscription subscription : subscriptions) {
            assertTrue(subscription.isClosed(), "Created Subscription for '"
                    + subscription.forClass() + "' must be closed after bus closure.");
        }

    }

    /**
     * Test that subscribers cannot be added after the bus has been closed.
     */
    @Test
    @DisplayName("LambdaBus.subscribe() throws IllegalStateException after LambdaBus.close()")
    public void subscribeThrowsIllegalStateExceptionAfterClose() {
        final LambdaBus lb = createLambdaBus();
        try {
            lb.subscribe(A.class, this::handleA);
        } finally {
            lb.close();
        }

        assertThrows(
                IllegalStateException.class,
                () -> lb.subscribe(A.class, this::handleA));

        assertThrows(
                IllegalStateException.class,
                () -> lb.subscribe(B.class, this::handleB));

        assertThrows(
                IllegalStateException.class,
                () -> lb.subscribe(C.class, this::handleC));
    }

    // ##########################################################################
    // Tests for LambdaBus.post(event) and LambdaBus.post(event, ThreadingMode).
    // These tests are parameterized with an abstraction of the posting methods
    // so that LambdaBus.post(event) and LambdaBus.post(event, ThreadingMode)
    // can
    // be tested with one test case.
    // ##########################################################################

    /**
     * Tests that events can be posted to the bus without exceptions when no
     * subscriber is subscribed.
     *
     * @param postMethod
     *            abstraction of {@link LambdaBus#post(Object)} and
     *            {@link LambdaBus#post(Object, ThreadingMode)} so that this test
     *            can be used for both (including all values of
     *            {@link ThreadingMode})
     *
     * @param nameOnlyUsedForUnitTestName
     *            used to have descriptive test names
     */
    @ParameterizedTest(name = "{1} without subscribers does not throw exceptions")
    @MethodSource("getPostMethodsWithNames")
    @DisplayName("LambdaBus.post(...) without subscribers does not throw exceptions")
    public void postWithoutSubscribersDoesNotThrowExceptions(
            final BiConsumer<LambdaBus, Object> postMethod,
            final String nameOnlyUsedForUnitTestName) {
        final int subscriberCount = 0;
        try (final LambdaBus lb = createLambdaBus()) {

            /*
             * Create events and wait for all events to be dispatched (or rather handled
             * since there is no subscriber)
             */
            createEventsPostAndWaitForDispatchingToComplete(
                    lb,
                    postMethod,
                    EVENTS_OF_TYPE_A_COUNT,
                    subscriberCount,
                    A::new);
        }
    }

    /**
     * Tests that events can be posted to the bus without exceptions when a
     * subscriber is subscribed.
     *
     * @param postMethod
     *            abstraction of {@link LambdaBus#post(Object)} and
     *            {@link LambdaBus#post(Object, ThreadingMode)} so that this test
     *            can be used for both (including all values of
     *            {@link ThreadingMode})
     *
     * @param nameOnlyUsedForUnitTestName
     *            used to have descriptive test names
     */
    @ParameterizedTest(name = "{1} with subscribers does not throw exceptions")
    @MethodSource("getPostMethodsWithNames")
    @DisplayName("LambdaBus.post(...) with subscribers does not throw exception")
    public void postWithSubscriberDoesNotThrowExceptions(
            final BiConsumer<LambdaBus, Object> postMethod,
            final String nameOnlyUsedForUnitTestName) {
        final int subscriberCount = 1;
        try (final LambdaBus lb = createLambdaBus()) {

            final AtomicInteger counter = new AtomicInteger();
            lb.subscribe(A.class,
                    (eventOfTypeA) -> {
                        eventOfTypeA.process();
                        counter.incrementAndGet();
                    });

            createEventsPostAndWaitForDispatchingToComplete(
                    lb,
                    postMethod,
                    EVENTS_OF_TYPE_A_COUNT,
                    subscriberCount,
                    A::new);

            assertEquals(EVENTS_OF_TYPE_A_COUNT, counter.get());
        }
    }

    /**
     * Tests that if subscribers throw exceptions during event dispatching the other
     * subscribers will still see the event.
     *
     * @param postMethod
     *            abstraction of {@link LambdaBus#post(Object)} and
     *            {@link LambdaBus#post(Object, ThreadingMode)} so that this test
     *            can be used for both (including all values of
     *            {@link ThreadingMode})
     *
     * @param nameOnlyUsedForUnitTestName
     *            used to have descriptive test names
     */
    @ParameterizedTest(
        name = "{1}: Subscribed handler gets called even if other subscribers for same event throw exceptions")
    @MethodSource("getPostMethodsWithNames")
    @DisplayName("Subscribed handler gets called even if other subscribers for same event throw exceptions")
    public void subscribedHandlerGetsCalledEvenIfOtherHandlerForSameEventThrowExceptions(
            final BiConsumer<LambdaBus, Object> postMethod,
            final String nameOnlyUsedForUnitTestName) {
        try (final LambdaBus lb = createLambdaBus()) {

            // assert start configuration
            assertNoSubscriber(lb);

            // assert start values
            assertEquals(0, aExceptionCounter.get());
            assertEquals(0, a1InterfaceExceptionCounter.get());
            assertEquals(0, handleA_counter.get());
            assertEquals(0, handleA1Interface_counter.get());

            // subscribe many exception throwing handler for A events
            for (int i = 0; i < EXCEPTION_THROWING_SUBSCRIBER_COUNT; i++) {
                lb.subscribe(A.class, this::handleA_andThrowException);
            }
            assertHasSubscriber(lb, A.class);
            assertNoSubscriber(lb, B.class, C.class);

            // subscribe first "regular" handler for A events
            lb.subscribe(A.class, this::handleA);
            assertHasSubscriber(lb, A.class);
            assertNoSubscriber(lb, B.class, C.class);

            // subscribe some more exception throwing handler for A events
            for (int i = 0; i < EXCEPTION_THROWING_SUBSCRIBER_COUNT; i++) {
                lb.subscribe(A.class, this::handleA1Interface_andThrowException);
            }
            assertHasSubscriber(lb, A.class);
            assertNoSubscriber(lb, B.class, C.class);

            // subscribe second "regular" handler for A events
            lb.subscribe(A.class, this::handleA1Interface);
            assertHasSubscriber(lb, A.class);
            assertNoSubscriber(lb, B.class, C.class);

            final int subscriberCount = EXCEPTION_THROWING_SUBSCRIBER_COUNT + 1 +
                    EXCEPTION_THROWING_SUBSCRIBER_COUNT + 1;

            // post A events and wait until dispatched/handled
            createEventPostAndWaitForDispatchingToComplete(
                    lb,
                    postMethod,
                    subscriberCount,
                    A::new);

            // only one event was published, but we registered the same handler
            // EXCEPTION_THROWING_SUBSCRIBER_COUNT times
            assertEquals(EXCEPTION_THROWING_SUBSCRIBER_COUNT, aExceptionCounter.get());
            assertEquals(EXCEPTION_THROWING_SUBSCRIBER_COUNT, a1InterfaceExceptionCounter.get());

            // only one event published, only one event changed
            // regardless of the exceptions around the A handler the event was
            // dispatched
            assertEquals(1, handleA_counter.get());
            assertEquals(1, handleA1Interface_counter.get());
        }
    }

    /**
     * Tests that subscribers which were subscribed by class can receive events.
     *
     * @param postMethod
     *            abstraction of {@link LambdaBus#post(Object)} and
     *            {@link LambdaBus#post(Object, ThreadingMode)} so that this test
     *            can be used for both (including all values of
     *            {@link ThreadingMode})
     *
     * @param nameOnlyUsedForUnitTestName
     *            used to have descriptive test names
     *
     */
    @ParameterizedTest(name = "{1} dispatches to handler subscribed by class")
    @MethodSource("getPostMethodsWithNames")
    @DisplayName("LambdaBus.post(...) dispatches to handler subscribed by class")
    public void postDispatchesToHandlerSubscribedByClass(
            final BiConsumer<LambdaBus, Object> postMethod,
            final String nameOnlyUsedForUnitTestName) {
        try (final LambdaBus lb = createLambdaBus()) {

            // assert start configuration
            assertNoSubscriber(lb);

            // assert start values
            assertEquals(0, handleA_counter.get());
            assertEquals(0, handleA1Interface_counter.get());
            assertEquals(0, handleB_counter.get());
            assertEquals(0, handleB1Interface_counter.get());
            assertEquals(0, handleC_counter.get());
            assertEquals(0, handleC1Interface_counter.get());

            // subscribe handler for the class types A, B, C

            lb.subscribe(A.class, this::handleA);
            lb.subscribe(A.class, this::handleA1Interface);
            final int subscriberCountForTypeA = 2;

            lb.subscribe(B.class, this::handleB);
            lb.subscribe(B.class, this::handleB1Interface);
            final int subscriberCountForTypeB = 2;

            lb.subscribe(C.class, this::handleC);
            lb.subscribe(C.class, this::handleC1Interface);
            final int subscriberCountForTypeC = 2;

            createEventsPostAndWaitForDispatchingToComplete(
                    lb,
                    postMethod,
                    EVENTS_OF_TYPE_A_COUNT,
                    subscriberCountForTypeA,
                    A::new);

            // all A events dispatched
            assertEquals(EVENTS_OF_TYPE_A_COUNT, handleA_counter.get());
            assertEquals(EVENTS_OF_TYPE_A_COUNT, handleA1Interface_counter.get());

            // no B events dispatched yet
            assertEquals(0, handleB_counter.get());
            assertEquals(0, handleB1Interface_counter.get());

            // no C events dispatched yet
            assertEquals(0, handleC_counter.get());
            assertEquals(0, handleC1Interface_counter.get());

            createEventsPostAndWaitForDispatchingToComplete(
                    lb,
                    postMethod,
                    EVENTS_OF_TYPE_B_COUNT,
                    subscriberCountForTypeB,
                    B::new);

            // unchanged, no new A events dispatched
            assertEquals(EVENTS_OF_TYPE_A_COUNT, handleA_counter.get());
            assertEquals(EVENTS_OF_TYPE_A_COUNT, handleA1Interface_counter.get());

            // all B events dispatched
            assertEquals(EVENTS_OF_TYPE_B_COUNT, handleB_counter.get());
            assertEquals(EVENTS_OF_TYPE_B_COUNT, handleB1Interface_counter.get());

            // no C events dispatched yet
            assertEquals(0, handleC_counter.get());
            assertEquals(0, handleC1Interface_counter.get());

            createEventsPostAndWaitForDispatchingToComplete(
                    lb,
                    postMethod,
                    EVENTS_OF_TYPE_C_COUNT,
                    subscriberCountForTypeC,
                    C::new);

            // unchanged, no new A events dispatched
            assertEquals(EVENTS_OF_TYPE_A_COUNT, handleA_counter.get());
            assertEquals(EVENTS_OF_TYPE_A_COUNT, handleA1Interface_counter.get());

            // unchanged, no new B events dispatched
            assertEquals(EVENTS_OF_TYPE_B_COUNT, handleB_counter.get());
            assertEquals(EVENTS_OF_TYPE_B_COUNT, handleB1Interface_counter.get());

            // all C events dispatched
            assertEquals(EVENTS_OF_TYPE_C_COUNT, handleC_counter.get());
            assertEquals(EVENTS_OF_TYPE_C_COUNT, handleC1Interface_counter.get());
        }
    }

    /**
     * Tests that subscribers which were subscribed by interface can receive events.
     *
     * @param postMethod
     *            abstraction of {@link LambdaBus#post(Object)} and
     *            {@link LambdaBus#post(Object, ThreadingMode)} so that this test
     *            can be used for both (including all values of
     *            {@link ThreadingMode})
     *
     * @param nameOnlyUsedForUnitTestName
     *            used to have descriptive test names
     */
    @ParameterizedTest(name = "{1} dispatches to handler subscribed by interface")
    @MethodSource("getPostMethodsWithNames")
    @DisplayName("LambdaBus.post(...) dispatches to handler subscribed by interface")
    public void postDispatchesToHandlerSubscribedByInterface(
            final BiConsumer<LambdaBus, Object> postMethod,
            final String nameOnlyUsedForUnitTestName) {
        try (final LambdaBus lb = createLambdaBus()) {

            // assert start configuration
            assertNoSubscriber(lb);

            // assert start values
            assertEquals(0, handleA_counter.get());
            assertEquals(0, handleA1Interface_counter.get());
            assertEquals(0, handleB_counter.get());
            assertEquals(0, handleB1Interface_counter.get());
            assertEquals(0, handleC_counter.get());
            assertEquals(0, handleC1Interface_counter.get());

            // subscribe handler for the interface types AInterface, BInterface,
            // CInterface
            lb.subscribe(A1Interface.class, this::handleA1Interface);
            final int subscriberCountOfTypeA1Interface = 1;

            lb.subscribe(B1Interface.class, this::handleB1Interface);
            final int subscriberCountOfTypeB1Interface = 1;

            lb.subscribe(C1Interface.class, this::handleC1Interface);
            final int subscriberCountOfTypeC1Interface = 1;

            createEventsPostAndWaitForDispatchingToComplete(
                    lb,
                    postMethod,
                    EVENTS_OF_TYPE_A_COUNT,
                    subscriberCountOfTypeA1Interface,
                    A::new);

            assertEquals(0, handleA_counter.get());
            // all A events dispatched to interface registered handler
            assertEquals(EVENTS_OF_TYPE_A_COUNT, handleA1Interface_counter.get());

            assertEquals(0, handleB_counter.get());
            assertEquals(0, handleB1Interface_counter.get());

            assertEquals(0, handleC_counter.get());
            assertEquals(0, handleC1Interface_counter.get());

            createEventsPostAndWaitForDispatchingToComplete(
                    lb,
                    postMethod,
                    EVENTS_OF_TYPE_B_COUNT,
                    subscriberCountOfTypeB1Interface,
                    B::new);

            assertEquals(0, handleA_counter.get());
            // all A events dispatched to interface registered handler
            assertEquals(EVENTS_OF_TYPE_A_COUNT, handleA1Interface_counter.get());

            assertEquals(0, handleB_counter.get());
            // all B events dispatched to interface registered handler
            assertEquals(EVENTS_OF_TYPE_B_COUNT, handleB1Interface_counter.get());

            assertEquals(0, handleC_counter.get());
            assertEquals(0, handleC1Interface_counter.get());

            createEventsPostAndWaitForDispatchingToComplete(
                    lb,
                    postMethod,
                    EVENTS_OF_TYPE_C_COUNT,
                    subscriberCountOfTypeC1Interface,
                    C::new);

            assertEquals(0, handleA_counter.get());
            // all A events dispatched to interface registered handler
            assertEquals(EVENTS_OF_TYPE_A_COUNT, handleA1Interface_counter.get());

            assertEquals(0, handleB_counter.get());
            // all B events dispatched to interface registered handler
            assertEquals(EVENTS_OF_TYPE_B_COUNT, handleB1Interface_counter.get());

            assertEquals(0, handleC_counter.get());
            // all C events dispatched to interface registered handler
            assertEquals(EVENTS_OF_TYPE_C_COUNT, handleC1Interface_counter.get());
        }
    }

    /**
     * Tests that subscribers which were subscribed by class can receive events.
     *
     * @param postMethod
     *            abstraction of {@link LambdaBus#post(Object)} and
     *            {@link LambdaBus#post(Object, ThreadingMode)} so that this test
     *            can be used for both (including all values of
     *            {@link ThreadingMode})
     *
     * @param nameOnlyUsedForUnitTestName
     *            used to have descriptive test names
     */
    @ParameterizedTest(name = "{1} dispatches to DeadEvent handler when no subscribers found")
    @MethodSource("getPostMethodsWithNames")
    @DisplayName("LambdaBus.post(...) dispatches to DeadEvent handler when no subscribers found")
    public void postDispatchesToDeadEventHandlerWhenNoSubscriberFound(
            final BiConsumer<LambdaBus, Object> postMethod,
            final String nameOnlyUsedForUnitTestName) {
        try (final LambdaBus lb = createLambdaBus()) {

            // assert start configuration
            assertNoSubscriber(lb);

            // assert start values
            assertEquals(0, handleA_counter.get());
            assertEquals(0, handleA1Interface_counter.get());
            assertEquals(0, handleB_counter.get());
            assertEquals(0, handleB1Interface_counter.get());
            assertEquals(0, handleC_counter.get());
            assertEquals(0, handleC1Interface_counter.get());

            // subscribe handler for the DeadEvent class

            final AtomicInteger deadEventCount = new AtomicInteger();

            lb.subscribe(DeadEvent.class,
                    (deadEvent) -> {
                        /*
                         * We have to check for this specific classes since we post also some more
                         * classes for "fuzziness".
                         */
                        if (deadEvent.event instanceof A ||
                                deadEvent.event instanceof B ||
                                deadEvent.event instanceof C) {
                            deadEventCount.incrementAndGet();
                            final Processable processable = (Processable) deadEvent.event;
                            processable.process();
                        }
                    });

            createEventsPostAndWaitForDispatchingToComplete(
                    lb,
                    postMethod,
                    EVENTS_OF_TYPE_A_COUNT,
                    1,
                    A::new);

            // all A events dispatched
            assertEquals(EVENTS_OF_TYPE_A_COUNT, deadEventCount.get());

            createEventsPostAndWaitForDispatchingToComplete(
                    lb,
                    postMethod,
                    EVENTS_OF_TYPE_B_COUNT,
                    1,
                    B::new);

            // all A+B events dispatched
            assertEquals(
                    EVENTS_OF_TYPE_A_COUNT + EVENTS_OF_TYPE_B_COUNT,
                    deadEventCount.get());

            createEventsPostAndWaitForDispatchingToComplete(
                    lb,
                    postMethod,
                    EVENTS_OF_TYPE_C_COUNT,
                    1,
                    C::new);

            // all A+B+C events dispatched
            assertEquals(
                    EVENTS_OF_TYPE_A_COUNT + EVENTS_OF_TYPE_B_COUNT + EVENTS_OF_TYPE_C_COUNT,
                    deadEventCount.get());

            // subscribe handler for the classes A, B, C

            lb.subscribe(A.class, this::handleA);
            lb.subscribe(A.class, this::handleA1Interface);
            final int subscriberCountOfTypeA = 2;

            lb.subscribe(B.class, this::handleB);
            lb.subscribe(B.class, this::handleB1Interface);
            final int subscriberCountOfTypeB = 2;

            lb.subscribe(C.class, this::handleC);
            lb.subscribe(C.class, this::handleC1Interface);
            final int subscriberCountOfTypeC = 2;

            // no A events dispatched yet
            assertEquals(0, handleA_counter.get());
            assertEquals(0, handleA1Interface_counter.get());

            // no B events dispatched yet
            assertEquals(0, handleB_counter.get());
            assertEquals(0, handleB1Interface_counter.get());

            // no C events dispatched yet
            assertEquals(0, handleC_counter.get());
            assertEquals(0, handleC1Interface_counter.get());

            createEventsPostAndWaitForDispatchingToComplete(
                    lb,
                    postMethod,
                    EVENTS_OF_TYPE_A_COUNT,
                    subscriberCountOfTypeA,
                    A::new);

            /*
             * No change for the dead event count since only A events were published for
             * which there is a subscriber.
             */
            assertEquals(
                    EVENTS_OF_TYPE_A_COUNT + EVENTS_OF_TYPE_B_COUNT + EVENTS_OF_TYPE_C_COUNT,
                    deadEventCount.get());

            // all A events dispatched
            assertEquals(EVENTS_OF_TYPE_A_COUNT, handleA_counter.get());
            assertEquals(EVENTS_OF_TYPE_A_COUNT, handleA1Interface_counter.get());

            // no B events dispatched yet
            assertEquals(0, handleB_counter.get());
            assertEquals(0, handleB1Interface_counter.get());

            // no C events dispatched yet
            assertEquals(0, handleC_counter.get());
            assertEquals(0, handleC1Interface_counter.get());

            createEventsPostAndWaitForDispatchingToComplete(
                    lb,
                    postMethod,
                    EVENTS_OF_TYPE_B_COUNT,
                    subscriberCountOfTypeB,
                    B::new);

            /*
             * No change for the dead event count since only B events were published for
             * which there is a subscriber.
             */
            assertEquals(
                    EVENTS_OF_TYPE_A_COUNT + EVENTS_OF_TYPE_B_COUNT + EVENTS_OF_TYPE_C_COUNT,
                    deadEventCount.get());

            // unchanged, no new A events dispatched
            assertEquals(EVENTS_OF_TYPE_A_COUNT, handleA_counter.get());
            assertEquals(EVENTS_OF_TYPE_A_COUNT, handleA1Interface_counter.get());

            // all B events dispatched
            assertEquals(EVENTS_OF_TYPE_B_COUNT, handleB_counter.get());
            assertEquals(EVENTS_OF_TYPE_B_COUNT, handleB1Interface_counter.get());

            // no C events dispatched yet
            assertEquals(0, handleC_counter.get());
            assertEquals(0, handleC1Interface_counter.get());

            createEventsPostAndWaitForDispatchingToComplete(
                    lb,
                    postMethod,
                    EVENTS_OF_TYPE_C_COUNT,
                    subscriberCountOfTypeC,
                    C::new);

            /*
             * No change for the dead event count since only C events were published for
             * which there is a subscriber.
             */
            assertEquals(
                    EVENTS_OF_TYPE_A_COUNT + EVENTS_OF_TYPE_B_COUNT + EVENTS_OF_TYPE_C_COUNT,
                    deadEventCount.get());

            // unchanged, no new A events dispatched
            assertEquals(EVENTS_OF_TYPE_A_COUNT, handleA_counter.get());
            assertEquals(EVENTS_OF_TYPE_A_COUNT, handleA1Interface_counter.get());

            // unchanged, no new B events dispatched
            assertEquals(EVENTS_OF_TYPE_B_COUNT, handleB_counter.get());
            assertEquals(EVENTS_OF_TYPE_B_COUNT, handleB1Interface_counter.get());

            // all C events dispatched
            assertEquals(EVENTS_OF_TYPE_C_COUNT, handleC_counter.get());
            assertEquals(EVENTS_OF_TYPE_C_COUNT, handleC1Interface_counter.get());
        }
    }

    /**
     * Test that events cannot be posted to the bus after it has been closed.
     *
     * @param postMethod
     *            abstraction of {@link LambdaBus#post(Object)} and
     *            {@link LambdaBus#post(Object, ThreadingMode)} so that this test
     *            can be used for both (including all values of
     *            {@link ThreadingMode})
     *
     * @param nameOnlyUsedForUnitTestName
     *            used to have descriptive test names
     */
    @ParameterizedTest(name = "{1} after LambdaBus.close() throws IllegalStateException")
    @MethodSource("getPostMethodsWithNames")
    @DisplayName("LambdaBus.post(...) after LambdaBus.close() throws IllegalStateException")
    public void postingAfterCloseThrowsIllegalStateException(
            final BiConsumer<LambdaBus, Object> postMethod,
            final String nameOnlyUsedForUnitTestName) {
        final LambdaBus lb = createLambdaBus();
        try {
            createEventPostAndWaitForDispatchingToComplete(
                    lb,
                    postMethod,
                    0,
                    A::new);
        } finally {
            lb.close();
        }

        final CountDownLatch doneLatch = new CountDownLatch(0);
        final A event = new A(doneLatch);

        // as the bus is closed now we expect an exception to be thrown
        assertThrows(
                IllegalStateException.class,
                () -> postMethod.accept(lb, event));
    }

    /**
     * Tests that a handler which has been unsubscribed does not receive any events
     * anymore.
     *
     * @param postMethod
     *            abstraction of {@link LambdaBus#post(Object)} and
     *            {@link LambdaBus#post(Object, ThreadingMode)} so that this test
     *            can be used for both (including all values of
     *            {@link ThreadingMode})
     *
     * @param nameOnlyUsedForUnitTestName
     *            used to have descriptive test names
     */
    @ParameterizedTest(
        name = "{1}: Subscribed handler is not used anymore after Subscription.close()")
    @MethodSource("getPostMethodsWithNames")
    @DisplayName("Subscribed handler is not used anymore after Subscription.close()")
    public void subscribedHandlerIsNotUsedAnymoreAfterSubscriptionClose(
            final BiConsumer<LambdaBus, Object> postMethod,
            final String nameOnlyUsedForUnitTestName) {
        try (final LambdaBus lb = createLambdaBus()) {

            // assert start configuration
            assertNoSubscriber(lb);

            final int initialSubscriberCountForTypeA = 0;

            // post event and wait until dispatched/handled
            createEventPostAndWaitForDispatchingToComplete(
                    lb,
                    postMethod,
                    initialSubscriberCountForTypeA,
                    A::new);

            // nothing has changed because there are no subscribers
            assertEquals(0, handleA_counter.get());
            assertEquals(0, handleA1Interface_counter.get());

            // subscribe handleA for A events
            final Subscription handleASubscription = lb.subscribe(A.class, this::handleA);
            assertHasSubscriber(lb, A.class);
            assertNoSubscriber(lb, B.class, C.class);

            final int subscriberCountForTypeA = 1;

            // post event and wait until dispatched/handled
            createEventPostAndWaitForDispatchingToComplete(
                    lb,
                    postMethod,
                    subscriberCountForTypeA,
                    A::new);

            // only handleA subscriber so only handleA counter has changed
            assertEquals(1, handleA_counter.get());
            assertEquals(0, handleA1Interface_counter.get());

            // remove handleA subscriber
            handleASubscription.close();
            assertNoSubscriber(lb, A.class, B.class, C.class);

            final int subscriberCountAfterCloseForTypeA = 0;

            // post event and wait until dispatched/handled
            createEventPostAndWaitForDispatchingToComplete(
                    lb,
                    postMethod,
                    subscriberCountAfterCloseForTypeA,
                    A::new);

            // nothing has changed for handleA counter because handler is not
            // subscribed
            assertEquals(1, handleA_counter.get());
            assertEquals(0, handleA1Interface_counter.get());

            // subscribe handleA1Interface for A events
            final Subscription handleA1InterfaceSubscription = lb.subscribe(A.class,
                    this::handleA1Interface);
            assertHasSubscriber(lb, A.class);
            assertNoSubscriber(lb, B.class, C.class);

            // post event and wait until dispatched/handled
            createEventPostAndWaitForDispatchingToComplete(
                    lb,
                    postMethod,
                    subscriberCountForTypeA,
                    A::new);

            // nothing has changed for handleA counter because handler is not
            // subscribed
            assertEquals(1, handleA_counter.get());

            // only handleA1Interface subscriber so only handleA1Interface
            // counter has changed
            assertEquals(1, handleA1Interface_counter.get());

            // remove handleA1Interface subscriber
            handleA1InterfaceSubscription.close();
            assertNoSubscriber(lb, A.class, B.class, C.class);

            // post event and wait until dispatched/handled
            createEventPostAndWaitForDispatchingToComplete(
                    lb,
                    postMethod,
                    subscriberCountAfterCloseForTypeA,
                    A::new);

            // nothing has changed for handleA counter because handler is not
            // subscribed
            assertEquals(1, handleA_counter.get());

            // nothing has changed for handleA1Interface counter because handler
            // is not subscribed
            assertEquals(1, handleA1Interface_counter.get());
        }
    }

    /**
     * Tests that an unsubscribed handler is not used with other subscribed handler
     * (for same type) present.
     *
     * @param postMethod
     *            abstraction of {@link LambdaBus#post(Object)} and
     *            {@link LambdaBus#post(Object, ThreadingMode)} so that this test
     *            can be used for both (including all values of
     *            {@link ThreadingMode})
     *
     * @param nameOnlyUsedForUnitTestName
     *            used to have descriptive test names
     */
    @ParameterizedTest(
        name = "{1}: Unsubscribed handler is not used with other subscribed handler present for same type")
    @MethodSource("getPostMethodsWithNames")
    @DisplayName("Unsubscribed handler is not used with other subscribed handler present for same type")
    public void unsubscribedHandlerIsNotUsedWithOtherSubscribedHandlerPresentForSameEventType(
            final BiConsumer<LambdaBus, Object> postMethod,
            final String nameOnlyUsedForUnitTestName) {
        try (final LambdaBus lb = createLambdaBus()) {

            // assert start configuration
            assertNoSubscriber(lb);

            // assert start values
            assertEquals(0, handleA_counter.get());
            assertEquals(0, handleA1Interface_counter.get());

            // subscribe handleA for A events
            final Subscription handleASubscription = lb.subscribe(A.class, this::handleA);
            assertHasSubscriber(lb, A.class);
            assertNoSubscriber(lb, B.class, C.class);

            final int subscriberForTypeA = 1;

            // post event and wait until dispatched/handled
            createEventPostAndWaitForDispatchingToComplete(
                    lb,
                    postMethod,
                    subscriberForTypeA,
                    A::new);

            // one event was dispatched, only to handleA
            assertEquals(1, handleA_counter.get());
            assertEquals(0, handleA1Interface_counter.get());

            // subscribe handleA1Interface for A events
            final Subscription handleA1InterfaceSubscription = lb.subscribe(A.class,
                    this::handleA1Interface);
            assertHasSubscriber(lb, A.class);
            assertNoSubscriber(lb, B.class, C.class);

            // post event and wait until dispatched/handled
            createEventPostAndWaitForDispatchingToComplete(
                    lb,
                    postMethod,
                    subscriberForTypeA + subscriberForTypeA,
                    A::new);

            // one more event was dispatched but now to handleA
            assertEquals(2, handleA_counter.get());
            // and to handleA1Interface
            assertEquals(1, handleA1Interface_counter.get());

            // remove handleA subscriber
            handleASubscription.close();
            assertHasSubscriber(lb, A.class);
            assertNoSubscriber(lb, B.class, C.class);

            // post event and wait until dispatched/handled
            createEventPostAndWaitForDispatchingToComplete(
                    lb,
                    postMethod,
                    subscriberForTypeA,
                    A::new);

            // event was dispatched but not to handleA because it is not
            // subscribed anymore
            assertEquals(2, handleA_counter.get());
            // event was dispatched only to handleA1Interface
            assertEquals(2, handleA1Interface_counter.get());

            // remove handleA1Interface subscriber
            handleA1InterfaceSubscription.close();
            assertNoSubscriber(lb, A.class, B.class, C.class);

            // post event and wait until dispatched/handled
            createEventPostAndWaitForDispatchingToComplete(
                    lb,
                    postMethod,
                    0,
                    A::new);

            // nothing has changed since the handler are not subscribed
            assertEquals(2, handleA_counter.get());
            assertEquals(2, handleA1Interface_counter.get());
        }
    }

    /**
     * Tests that handler subscribed by class override handler subscribed by
     * interface.
     *
     * @param postMethod
     *            abstraction of {@link LambdaBus#post(Object)} and
     *            {@link LambdaBus#post(Object, ThreadingMode)} so that this test
     *            can be used for both (including all values of
     *            {@link ThreadingMode})
     *
     * @param nameOnlyUsedForUnitTestName
     *            used to have descriptive test names
     */
    @ParameterizedTest(
        name = "{1}: Handler subscribed by class overrides handler subscribed by interface")
    @MethodSource("getPostMethodsWithNames")
    @DisplayName("Handler subscribed by class overrides handler subscribed by interface")
    public void handlerSubscribedByClassOverridesHandlerSubscribedByInterface(
            final BiConsumer<LambdaBus, Object> postMethod,
            final String nameOnlyUsedForUnitTestName) {
        try (final LambdaBus lb = createLambdaBus()) {

            // assert start configuration
            assertNoSubscriber(lb);

            // assert start values
            assertEquals(0, handleA_counter.get());
            assertEquals(0, handleA1Interface_counter.get());

            // subscribe handleA1Interface for A1Interface events
            lb.subscribe(A1Interface.class, this::handleA1Interface);
            assertHasSubscriber(lb, A1Interface.class);
            assertNoSubscriber(lb, A.class, B.class, C.class);

            final int subscriberForTypeA1Interface = 1;

            // post A events and wait until dispatched/handled
            createEventsPostAndWaitForDispatchingToComplete(
                    lb,
                    postMethod,
                    EVENTS_OF_TYPE_A_COUNT,
                    subscriberForTypeA1Interface,
                    A::new);

            // handler not subscribed so no events
            assertEquals(0, handleA_counter.get());
            // event were dispatched only to handleA1Interface
            assertEquals(EVENTS_OF_TYPE_A_COUNT, handleA1Interface_counter.get());

            // subscribe handleA for A events
            final Subscription handleA1Subscription = lb.subscribe(A.class, this::handleA);
            assertHasSubscriber(lb, A1Interface.class);
            assertHasSubscriber(lb, A.class);
            assertNoSubscriber(lb, B.class, C.class);

            final int subscriberForTypeA = 1;

            // post A events and wait until dispatched/handled
            createEventsPostAndWaitForDispatchingToComplete(
                    lb,
                    postMethod,
                    EVENTS_OF_TYPE_A_COUNT,
                    subscriberForTypeA,
                    A::new);

            // event was dispatched, only to handleA1, because class overrides
            // interface
            assertEquals(EVENTS_OF_TYPE_A_COUNT, handleA_counter.get());
            // event count for handleA2 unchanged
            assertEquals(EVENTS_OF_TYPE_A_COUNT, handleA1Interface_counter.get());

            // remove handleA1 subscriber
            handleA1Subscription.close();
            assertHasSubscriber(lb, A1Interface.class);
            assertNoSubscriber(lb, A.class, B.class, C.class);

            // post A events and wait until dispatched/handled
            createEventsPostAndWaitForDispatchingToComplete(
                    lb,
                    postMethod,
                    EVENTS_OF_TYPE_A_COUNT,
                    subscriberForTypeA1Interface,
                    A::new);

            // event count for handleA unchanged, because handleA was
            // unsubscribed before posting
            assertEquals(EVENTS_OF_TYPE_A_COUNT, handleA_counter.get());
            // events were dispatched to "handlerAiInterface" handler since the
            // class handler "handleA" was unsubscribed and does not override
            // the interface handler anymore
            assertEquals(2 * EVENTS_OF_TYPE_A_COUNT, handleA1Interface_counter.get());

            // subscribe handleA1 for A events
            lb.subscribe(A.class, this::handleA);
            assertHasSubscriber(lb, A1Interface.class);
            assertHasSubscriber(lb, A.class);
            assertNoSubscriber(lb, B.class, C.class);

            // post A events and wait until dispatched/handled
            createEventsPostAndWaitForDispatchingToComplete(
                    lb,
                    postMethod,
                    EVENTS_OF_TYPE_A_COUNT,
                    subscriberForTypeA,
                    A::new);

            // event were dispatched, only to handleA, because class overrides
            // interface
            assertEquals(2 * EVENTS_OF_TYPE_A_COUNT, handleA_counter.get());
            // event count for handleA1Interface unchanged
            assertEquals(2 * EVENTS_OF_TYPE_A_COUNT, handleA1Interface_counter.get());
        }
    }

    /**
     * Tests that handler subscribed by interface override handler subscribed by
     * interface in order of appearance.
     *
     * @param postMethod
     *            abstraction of {@link LambdaBus#post(Object)} and
     *            {@link LambdaBus#post(Object, ThreadingMode)} so that this test
     *            can be used for both (including all values of
     *            {@link ThreadingMode})
     *
     * @param nameOnlyUsedForUnitTestName
     *            used to have descriptive test names
     */
    @ParameterizedTest(
        name = "{1}: Handler subscribed by interface overrides handler subscribed by interface in order of appearance")
    @MethodSource("getPostMethodsWithNames")
    @DisplayName("Handler subscribed by interface overrides handler subscribed by interface in order of appearance")
    public void handlerSubscribedByInterfaceOverridesHandlerSubscribedByInterfaceInOrderOfAppearance(
            final BiConsumer<LambdaBus, Object> postMethod,
            final String nameOnlyUsedForUnitTestName) {
        try (final LambdaBus lb = createLambdaBus()) {

            // assert start configuration
            assertNoSubscriber(lb);

            // assert start values
            assertEquals(0, handleA_counter.get());
            assertEquals(0, handleA1Interface_counter.get());

            // subscribe handleA1Interface for A1Interface events
            final Subscription handleA1InterfaceSubscription = lb.subscribe(A1Interface.class,
                    this::handleA1Interface);
            assertHasSubscriber(lb, A1Interface.class);
            assertNoSubscriber(lb, A2Interface.class);
            assertNoSubscriber(lb, A.class);

            final int subscriberForTypeA1Interface = 1;

            // subscribe handleA2Interface for A2Interface events
            lb.subscribe(A2Interface.class, this::handleA2Interface);
            assertHasSubscriber(lb, A1Interface.class, A2Interface.class);
            assertNoSubscriber(lb, A.class);

            final int subscriberForTypeA2Interface = 1;

            // post A events and wait until dispatched/handled
            createEventsPostAndWaitForDispatchingToComplete(
                    lb,
                    postMethod,
                    EVENTS_OF_TYPE_A_COUNT,
                    subscriberForTypeA1Interface,
                    A::new);

            // event were dispatched, only to handleA1Interface() since
            // A1Interface appears before A2Interface in the definition of class
            // A and therefore overrides A2Interface for subscriber lookup
            assertEquals(EVENTS_OF_TYPE_A_COUNT, handleA1Interface_counter.get());
            assertEquals(0, handleA2Interface_counter.get());

            // unsubscribe handleA1Interface() for A events
            handleA1InterfaceSubscription.close();
            assertNoSubscriber(lb, A1Interface.class);
            assertHasSubscriber(lb, A2Interface.class);
            assertNoSubscriber(lb, A.class);

            // post A events and wait until dispatched/handled
            createEventsPostAndWaitForDispatchingToComplete(
                    lb,
                    postMethod,
                    EVENTS_OF_TYPE_A_COUNT,
                    subscriberForTypeA2Interface,
                    A::new);

            // event count for handleA1Interface unchanged, since handler is not
            // subscribed anymore
            assertEquals(EVENTS_OF_TYPE_A_COUNT, handleA1Interface_counter.get());
            // events were dispatched only to handleA2Interface, because
            // overriding interface handler handlerA1Interface is not subscribed
            // anymore
            assertEquals(EVENTS_OF_TYPE_A_COUNT, handleA2Interface_counter.get());

            // subscribe handleA1Interface for A1Interface events once more
            lb.subscribe(A1Interface.class, this::handleA1Interface);
            assertHasSubscriber(lb, A1Interface.class);
            assertHasSubscriber(lb, A2Interface.class);

            // post A events and wait until dispatched/handled
            createEventsPostAndWaitForDispatchingToComplete(
                    lb,
                    postMethod,
                    EVENTS_OF_TYPE_A_COUNT,
                    subscriberForTypeA1Interface,
                    A::new);

            // event were dispatched, only to handleA1Interface() since
            // A1Interface appears before A2Interface in the definition of class
            // A and therefore overrides A2Interface for subscriber lookup
            assertEquals(2 * EVENTS_OF_TYPE_A_COUNT, handleA1Interface_counter.get());
            // event count unchanged since presence of handleA1Interface()
            // overrides handleA2Interface() for subscriber lookup
            assertEquals(EVENTS_OF_TYPE_A_COUNT, handleA2Interface_counter.get());
        }
    }

    // ##########################################################################
    // Helper methods
    // ##########################################################################

    /**
     * Abstraction of the LambdaBus.post(Object, ThreadingMode) method used for
     * parameterized tests.
     *
     * @param threadingModeHint
     *            {@link Enum} indicating the bus how the event should be
     *            dispatched, if the value is not supported the default behavior of
     *            {@link LambdaBus#post(Object)} should be applied.
     * @return configured {@link LambdaBus#post(Object, ThreadingMode)} as
     *         {@link BiConsumer}
     */
    protected static BiConsumer<LambdaBus, Object> getPostMethod(
            final ThreadingMode threadingModeHint) {
        return (lb, event) -> lb.post(event, threadingModeHint);
    }

    /**
     * Abstraction of the LambdaBus.post(Object) method used for parameterized
     * tests.
     *
     * @return {@link LambdaBus#post(Object)} as {@link BiConsumer}
     */
    protected static BiConsumer<LambdaBus, Object> getPostMethod() {
        return POST_METHOD;
    }

    /**
     * Create events, post them to the bus using multiple threads and wait until
     * they get dispatched.
     *
     * @param <T>
     *            type of the event
     * @param lb
     *            {@link LambdaBus} to post events to
     * @param postMethod
     *            {@link BiFunction} doing the posting
     * @param eventCount
     *            number of events to create / post
     * @param subscriberCount
     *            number of subscribers for events of type {@code T} (supplied by
     *            the {@code eventProvider})
     * @param eventProvider
     *            supplier of events of type {@code T}
     */
    protected <T> void createEventsPostAndWaitForDispatchingToComplete(
            final LambdaBus lb,
            final BiConsumer<LambdaBus, Object> postMethod,
            final int eventCount,
            final int subscriberCount,
            final Function<CountDownLatch, T> eventProvider) {
        final int totalCount = eventCount * subscriberCount;
        final CountDownLatch doneLatch = new CountDownLatch(totalCount);
        final Runnable postRunnable = () -> {
            postMethod.accept(lb, null);       // do some fuzzing around the
                                               // real event
            postMethod.accept(lb, new XYZ());  // do some fuzzing around the
                                               // real event
            postMethod.accept(lb, eventProvider.apply(doneLatch)); // post the
                                                                   // "real"
                                                                   // event
            postMethod.accept(lb, new XYZ()); // do some fuzzing around the real
                                              // event
            postMethod.accept(lb, null);      // do some fuzzing around the real
                                              // event
        };

        parallelTasks.executeTask(eventCount, postRunnable);

        final Executable executable = doneLatch::await;

        // wait until dispatched/handled
        assertTimeoutPreemptively(DEFAULT_TIMEOUT, executable);
    }

    /**
     * Create one event, post it to the bus and wait until it gets dispatched.
     *
     * @param <T>
     *            type of the event
     * @param lb
     *            {@link LambdaBus} to post event to
     * @param postMethod
     *            {@link BiConsumer} doing the posting
     * @param subscriberCount
     *            number of subscribers for events of type {@code T} (supplied by
     *            the {@code eventProvider})
     * @param eventProvider
     *            supplier of events of type {@code T}
     */
    protected static <T> void createEventPostAndWaitForDispatchingToComplete(
            final LambdaBus lb,
            final BiConsumer<LambdaBus, Object> postMethod,
            final int subscriberCount,
            final Function<CountDownLatch, T> eventProvider) {
        final CountDownLatch doneLatch = new CountDownLatch(subscriberCount);

        postMethod.accept(lb, eventProvider.apply(doneLatch));

        final Executable executable = doneLatch::await;

        // post event and wait until dispatched/handled
        assertTimeoutPreemptively(DEFAULT_TIMEOUT, executable);
    }

    /**
     * Asserts that the supplied {@link Class}es have subscribed handlers for the
     * given {@link LambdaBus}.
     *
     * @param lb
     *            {@link LambdaBus} to check
     * @param clazzes
     *            {@link Class}es to check
     */
    protected static void assertHasSubscriber(
            final LambdaBus lb,
            final Class<?>... clazzes) {
        for (final Class<?> clazz : clazzes) {
            assertTrue(lb.hasSubscriberForClass(clazz), "Has subscriber(s) for class: " + clazz);
        }
    }

    /**
     * Asserts that the supplied {@link Class}es do NOT have subscribed handlers for
     * the given {@link LambdaBus}.
     *
     * @param lb
     *            {@link LambdaBus} to check
     * @param clazzes
     *            {@link Class}es to check
     */
    protected static void assertNoSubscriber(
            final LambdaBus lb,
            final Class<?>... clazzes) {
        for (final Class<?> clazz : clazzes) {
            assertFalse(lb.hasSubscriberForClass(clazz), "No subscriber for class: " + clazz);
        }
    }

    /**
     * Asserts that the supplied {@link LambdaBus} has no subscribers, for the
     * common classes /interfaces used for testing.
     *
     * @param lb
     *            {@link LambdaBus} to check
     */
    protected static void assertNoSubscriber(
            final LambdaBus lb) {
        assertNoSubscriber(lb,
                A1Interface.class,
                A2Interface.class,
                B1Interface.class,
                B2Interface.class,
                C1Interface.class,
                C2Interface.class,
                A.class,
                B.class,
                C.class);
    }

    // ##########################################################################
    // Test interfaces / classes for events.
    // ##########################################################################

    /**
     * Basic interface used for testing.
     */
    protected interface Processable {
        void process();
    }

    /**
     * Interface used for testing.
     */
    protected interface A1Interface
            extends Processable {
    }

    /**
     * Interface used for testing.
     */
    protected interface A2Interface
            extends Processable {
    }

    /**
     * Class implementing two interfaces ({@link A1Interface} and
     * {@link A2Interface})used for testing.
     */
    protected static class A
            implements A1Interface, A2Interface {
        private final CountDownLatch doneLatch;

        // constructor must be public to be visible for subclasses (maybe in
        // different packages)
        public A(final CountDownLatch doneLatch) {
            this.doneLatch = Objects.requireNonNull(doneLatch, "'doneLatch' must not be null");
        }

        @Override
        public void process() {
            doneLatch.countDown();
        }
    }

    /**
     * Interface used for testing.
     */
    protected interface B1Interface
            extends Processable {
    }

    /**
     * Interface used for testing.
     */
    protected interface B2Interface
            extends Processable {
    }

    /**
     * Sub-Class of {@link A} implementing two interfaces ({@link B1Interface} and
     * {@link B2Interface}) used for testing.
     */
    protected static class B
            extends A
            implements B1Interface, B2Interface {

        public B(final CountDownLatch doneLatch) {
            super(doneLatch);
        }
    }

    /**
     * Interface used for testing.
     */
    protected interface C1Interface
            extends Processable {
    }

    /**
     * Interface used for testing.
     */
    protected interface C2Interface
            extends Processable {
    }

    /**
     * Sub-Class of {@link B} implementing two interfaces ({@link B1Interface} and
     * {@link C2Interface}) used for testing.
     */
    protected static class C
            extends B
            implements C1Interface, C2Interface {

        public C(final CountDownLatch doneLatch) {
            super(doneLatch);
        }
    }

    /**
     * Class without any interfaces and without superclass used for testing.
     */
    protected static class XYZ {
        public XYZ() {}
    }

    // ##########################################################################
    // Handler methods to be used as subscribers for events.
    // ##########################################################################

    /**
     * Handler for events of type {@link A}.<br>
     * Increments the {@link #handleA_counter}.
     *
     * @param a
     *            event to handle
     */
    protected void handleA(
            final A a) {
        handleA_counter.incrementAndGet();
        a.process();
    }

    /**
     * Handler for events of type {@link A}. Used to test if other subscribers for
     * the same event are called if one throws an exception.<br>
     * Increments the {@link #aExceptionCounter}.
     *
     * @param a
     *            event to handle
     * @throws RuntimeException
     *             (always)
     */
    protected void handleA_andThrowException(
            final A a) {
        try {
            aExceptionCounter.incrementAndGet();
            throw new RuntimeException(a.getClass()
                    .getName());
        } finally {
            a.process();
        }
    }

    /**
     * Handler for events of type {@link A1Interface}.<br>
     * Increments the {@link #handleA1Interface_counter}.
     *
     * @param a1Interface
     *            event to handle
     */
    protected void handleA1Interface(
            final A1Interface a1Interface) {
        handleA1Interface_counter.incrementAndGet();
        a1Interface.process();
    }

    /**
     * Handler for events of type {@link A1Interface}. Used to test if other
     * subscribers for the same event are called if one throws an exception.<br>
     * Increments the {@link #a1InterfaceExceptionCounter}.
     *
     * @param a1Interface
     *            event to handle
     * @throws RuntimeException
     *             (always)
     */
    protected void handleA1Interface_andThrowException(
            final A1Interface a1Interface) {
        try {
            a1InterfaceExceptionCounter.incrementAndGet();
            throw new RuntimeException(a1Interface.getClass()
                    .getName());
        } finally {
            a1Interface.process();
        }
    }

    /**
     * Handler for events of type {@link A2Interface}.<br>
     * Increments the {@link #handleA2Interface_counter}.
     *
     * @param a2Interface
     *            event to handle
     */
    protected void handleA2Interface(
            final A2Interface a2Interface) {
        handleA2Interface_counter.incrementAndGet();
        a2Interface.process();
    }

    /**
     * Handler for events of type {@link A2Interface}. Used to test if other
     * subscriber for the same event are called if one throws an exception.<br>
     * Increments the {@link #handleA2InterfaceException_counter}.
     *
     * @param a2Interface
     *            event
     * @throws RuntimeException
     *             (always)
     */
    protected void handleA2Interface_andThrowException(
            final A2Interface a2Interface) {
        try {
            handleA2InterfaceException_counter.incrementAndGet();
            throw new RuntimeException(a2Interface.getClass()
                    .getName());
        } finally {
            a2Interface.process();
        }
    }

    /**
     * Handler for events of type {@link B}.<br>
     * Increments the {@link #handleB_counter}.
     *
     * @param b
     *            event to handle
     */
    protected void handleB(
            final B b) {
        handleB_counter.incrementAndGet();
        b.process();
    }

    /**
     * Handler for events of type {@link B1Interface}.<br>
     * Increments the {@link #handleB1Interface_counter}.
     *
     * @param b1Interface
     *            event to handle
     */
    protected void handleB1Interface(
            final B1Interface b1Interface) {
        handleB1Interface_counter.incrementAndGet();
        b1Interface.process();
    }

    /**
     * Handler for events of type {@link C}.<br>
     * Increments the {@link #handleC_counter}.
     *
     * @param c
     *            event to handle
     */
    protected void handleC(
            final C c) {
        handleC_counter.incrementAndGet();
        c.process();
    }

    /**
     * Handler for events of type {@link C1Interface}.<br>
     * Increments the {@link #handleC1Interface_counter}.
     *
     * @param c1Interface
     *            event to handle
     */
    protected void handleC1Interface(
            final C1Interface c1Interface) {
        handleC1Interface_counter.incrementAndGet();
        c1Interface.process();
    }

}
