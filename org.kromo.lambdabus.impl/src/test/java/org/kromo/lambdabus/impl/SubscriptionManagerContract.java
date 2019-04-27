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
package org.kromo.lambdabus.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.kromo.lambdabus.Subscription;

/**
 * Defines tests for behavioral aspects expected from implementations of the
 * {@link SubscriptionManager} interface.
 *
 * @param <SubscriptionManagerType>
 *            type to test which implements the {@link SubscriptionManager}
 *            interface
 *
 * @author Victor Toni - initial implementation
 *
 */
public abstract class SubscriptionManagerContract<SubscriptionManagerType extends SubscriptionManager> {

    /**
     * Method must be implemented to return an instance of the
     * {@link SubscriptionManager} implementation to be tested if it adheres to this
     * contract.
     *
     * @return an instance of the {@link SubscriptionManager} implementation to be
     *         tested
     */
    protected abstract SubscriptionManagerType createSubscriptionManager();

    /**
     * Checks that the {@link SubscriptionManager} implementation can be created
     * without exceptions.
     */
    @Test
    @DisplayName("Creating a SubscriptionManager does not throw exceptions")
    public void createSubscriptionManagerDoesNotThrowExceptions() {
        try (final SubscriptionManager subscriptionManager = createSubscriptionManager()) {
            assertNotNull(subscriptionManager);
        }
    }

    /**
     * Checks that the {@link SubscriptionManager} implementation can be created and
     * closed explicitly without exceptions.
     */
    @Test
    @DisplayName("Creating a SubscriptionManager and explicit SubscriptionManager.close() does not throw exceptions")
    public void createSubscriptionManagerAndExplicitCloseDoesNotThrowExceptions() {
        try (final SubscriptionManager subscriptionManager = createSubscriptionManager()) {
            subscriptionManager.close();
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
    @DisplayName("SubscriptionManager.subscribe(null, Consumer) throws NullPointerException")
    public void subscribingWithNullClassThrowsNPE() {
        final Class<A> nullEventClass = null;
        final Consumer<A> eventHandler = this::handleA;
        try (final SubscriptionManager subscriptionManager = createSubscriptionManager()) {
            assertThrows(
                    NullPointerException.class,
                    () -> subscriptionManager.subscribe(nullEventClass, eventHandler));
        }
    }

    /**
     * Tests that a {@link NullPointerException} is thrown when {@code null} is used
     * for {@link Consumer} while subscribing.
     */
    @Test
    @DisplayName("SubscriptionManager.subscribe(Class, null) throws NullPointerException")
    public void subscribingWithNullHandlerThrowsNPE() {
        final Class<A> eventClass = A.class;
        final Consumer<A> nullEventHandler = null;
        try (final SubscriptionManager subscriptionManager = createSubscriptionManager()) {
            assertThrows(
                    NullPointerException.class,
                    () -> subscriptionManager.subscribe(eventClass, nullEventHandler));
        }
    }

    /**
     * Tests that a {@link IllegalStateException} is thrown when subscribing when
     * the SubscriptionManager is already closed.
     */
    @Test
    @DisplayName("SubscriptionManager.subscribe(Class<T>, Consumer<T>) throws IllegalStateException when SubscriptionManager is already closed")
    public void subscribingWhenAlreadyClosedThrowsIllegalStateException() {
        final Class<A> eventClass = A.class;
        final Consumer<A> eventHandler = this::handleA;
        try (final SubscriptionManager subscriptionManager = createSubscriptionManager()) {
            subscriptionManager.close();

            assertThrows(
                    IllegalStateException.class,
                    () -> subscriptionManager.subscribe(eventClass, eventHandler));
        }
    }

    /**
     * Test that subscriber can be added to the SubscriptionManager without
     * exceptions.
     */
    @Test
    @DisplayName("Regular SubscriptionManager.subscribe() does not throw exceptions")
    public void subscribe() {
        try (final SubscriptionManager subscriptionManager = createSubscriptionManager()) {
            assertNotNull(subscriptionManager.subscribe(A.class, this::handleA));
            assertNotNull(subscriptionManager.subscribe(A.class, this::handleA1Interface));

            assertNotNull(
                    subscriptionManager.subscribe(A2Interface.class, this::handleA2Interface));

            assertNotNull(subscriptionManager.subscribe(B.class, this::handleB));

            assertNotNull(
                    subscriptionManager.subscribe(B1Interface.class, this::handleB1Interface));

            assertNotNull(subscriptionManager.subscribe(C.class, this::handleC));
        }
    }

    /**
     * Test that subscribers of class type are recognized as subscriber regardless
     * of related interfaces.
     */
    @Test
    @DisplayName("Handler subscribed by class is found by SubscriptionManager.hasSubscriber()")
    public void hasSubscriberAfterSubscribingWithClass() {
        try (final SubscriptionManager subscriptionManager = createSubscriptionManager()) {

            // assert start configuration
            assertNoSubscriber(subscriptionManager);

            subscriptionManager.subscribe(A.class, this::handleA);
            assertHasSubscriber(subscriptionManager, A.class);

            assertNoSubscriber(subscriptionManager, B.class, C.class);
            assertNoSubscriber(subscriptionManager, A1Interface.class, B1Interface.class,
                    C1Interface.class);

            subscriptionManager.subscribe(B.class, this::handleB);
            assertHasSubscriber(subscriptionManager, A.class, B.class);

            assertNoSubscriber(subscriptionManager, C.class);
            assertNoSubscriber(subscriptionManager, A1Interface.class, B1Interface.class,
                    C1Interface.class);

            subscriptionManager.subscribe(C.class, this::handleC);
            assertHasSubscriber(subscriptionManager, A.class, B.class, C.class);

            assertNoSubscriber(subscriptionManager, A1Interface.class, B1Interface.class,
                    C1Interface.class);
        }
    }

    /**
     * Test that subscribers of interface type are recognized as subscriber
     * regardless of related classes.
     */
    @Test
    @DisplayName("Handler subscribed by interface is found by SubscriptionManager.hasSubscriber()")
    public void hasSubscriberAfterSubscribeWithInterface_() {
        try (final SubscriptionManager subscriptionManager = createSubscriptionManager()) {

            // assert start configuration
            assertNoSubscriber(subscriptionManager);

            subscriptionManager.subscribe(A1Interface.class, this::handleA1Interface);
            assertHasSubscriber(subscriptionManager, A1Interface.class);

            assertNoSubscriber(subscriptionManager, B1Interface.class, C1Interface.class);
            assertNoSubscriber(subscriptionManager, A.class, B.class, C.class);

            subscriptionManager.subscribe(B1Interface.class, this::handleB1Interface);
            assertHasSubscriber(subscriptionManager, A1Interface.class, B1Interface.class);

            assertNoSubscriber(subscriptionManager, C1Interface.class);
            assertNoSubscriber(subscriptionManager, A.class, B.class, C.class);

            subscriptionManager.subscribe(C1Interface.class, this::handleC1Interface);
            assertHasSubscriber(subscriptionManager, A1Interface.class, B1Interface.class,
                    C1Interface.class);

            assertNoSubscriber(subscriptionManager, A.class, B.class, C.class);
        }
    }

    /**
     * Test that subscribers can be unsubscribed via {@link Subscription#close()}.
     */
    @Test
    @DisplayName("Subscriber is unsubscribed by Subscription.close()")
    public void closingSubscriptionRemovesSubscription() {
        try (final SubscriptionManager subscriptionManager = createSubscriptionManager()) {

            // assert start configuration
            assertNoSubscriber(subscriptionManager);

            // subscribe to classes

            final Subscription handleASubscription = subscriptionManager.subscribe(A.class,
                    this::handleA);
            assertHasSubscriber(subscriptionManager, A.class);

            assertNoSubscriber(subscriptionManager, B.class, C.class);
            assertNoSubscriber(subscriptionManager, A1Interface.class, B1Interface.class,
                    C1Interface.class);

            final Subscription handleBSubscription = subscriptionManager.subscribe(B.class,
                    this::handleB);
            assertHasSubscriber(subscriptionManager, A.class, B.class);

            assertNoSubscriber(subscriptionManager, C.class);
            assertNoSubscriber(subscriptionManager, A1Interface.class, B1Interface.class,
                    C1Interface.class);

            final Subscription handleCSubscription = subscriptionManager.subscribe(C.class,
                    this::handleC);
            assertHasSubscriber(subscriptionManager, A.class, B.class, C.class);

            assertNoSubscriber(subscriptionManager, A1Interface.class, B1Interface.class,
                    C1Interface.class);

            // subscribe to interfaces

            final Subscription handleA1InterfaceSubscription = subscriptionManager
                    .subscribe(A1Interface.class, this::handleA1Interface);
            assertHasSubscriber(subscriptionManager, A.class, B.class, C.class);
            assertHasSubscriber(subscriptionManager, A1Interface.class);

            assertNoSubscriber(subscriptionManager, B1Interface.class, C1Interface.class);

            final Subscription handleB1InterfaceSubscription = subscriptionManager
                    .subscribe(B1Interface.class, this::handleB1Interface);
            assertHasSubscriber(subscriptionManager, A.class, B.class, C.class);
            assertHasSubscriber(subscriptionManager, A1Interface.class, B1Interface.class);

            assertNoSubscriber(subscriptionManager, C1Interface.class);

            final Subscription handleC1InterfaceSubscription = subscriptionManager
                    .subscribe(C1Interface.class, this::handleC1Interface);
            assertHasSubscriber(subscriptionManager, A.class, B.class, C.class);
            assertHasSubscriber(subscriptionManager, A1Interface.class, B1Interface.class,
                    C1Interface.class);

            // unsubscribe class handler

            handleASubscription.close();
            assertNoSubscriber(subscriptionManager, A.class);

            assertHasSubscriber(subscriptionManager, B.class, C.class);
            assertHasSubscriber(subscriptionManager, A1Interface.class, B1Interface.class,
                    C1Interface.class);

            handleBSubscription.close();
            assertNoSubscriber(subscriptionManager, A.class, B.class);

            assertHasSubscriber(subscriptionManager, C.class);
            assertHasSubscriber(subscriptionManager, A1Interface.class, B1Interface.class,
                    C1Interface.class);

            handleCSubscription.close();
            assertNoSubscriber(subscriptionManager, A.class, B.class, C.class);

            assertHasSubscriber(subscriptionManager, A1Interface.class, B1Interface.class,
                    C1Interface.class);

            // unsubscribe interface handler

            handleA1InterfaceSubscription.close();
            assertNoSubscriber(subscriptionManager, A.class, B.class, C.class);
            assertNoSubscriber(subscriptionManager, A1Interface.class);

            assertHasSubscriber(subscriptionManager, B1Interface.class, C1Interface.class);

            handleB1InterfaceSubscription.close();
            assertNoSubscriber(subscriptionManager, A.class, B.class, C.class);
            assertNoSubscriber(subscriptionManager, A1Interface.class, B1Interface.class);

            assertHasSubscriber(subscriptionManager, C1Interface.class);

            handleC1InterfaceSubscription.close();
            assertNoSubscriber(subscriptionManager, A.class, B.class, C.class);
            assertNoSubscriber(subscriptionManager, A1Interface.class, B1Interface.class,
                    C1Interface.class);

            // removed everything, assert start/end configuration
            assertNoSubscriber(subscriptionManager);
        }
    }

    /**
     * Test that subscribers can be unsubscribed in reverse order via
     * {@link Subscription#close()}.
     */
    @Test
    @DisplayName("Unsubscribing via Subscription.close() is side-effect free and can be done in reverse/any order")
    public void closingSubscriptionInReverseOrderIsSideEffectFree() {
        try (final SubscriptionManager subscriptionManager = createSubscriptionManager()) {

            // assert start configuration
            assertNoSubscriber(subscriptionManager);

            // subscribe to classes

            final Subscription handleASubscription = subscriptionManager.subscribe(A.class,
                    this::handleA);
            assertHasSubscriber(subscriptionManager, A.class);

            assertNoSubscriber(subscriptionManager, B.class, C.class);
            assertNoSubscriber(subscriptionManager, A1Interface.class, B1Interface.class,
                    C1Interface.class);

            final Subscription handleBSubscription = subscriptionManager.subscribe(B.class,
                    this::handleB);
            assertHasSubscriber(subscriptionManager, A.class, B.class);

            assertNoSubscriber(subscriptionManager, C.class);
            assertNoSubscriber(subscriptionManager, A1Interface.class, B1Interface.class,
                    C1Interface.class);

            final Subscription handleCSubscription = subscriptionManager.subscribe(C.class,
                    this::handleC);
            assertHasSubscriber(subscriptionManager, A.class, B.class, C.class);

            assertNoSubscriber(subscriptionManager, A1Interface.class, B1Interface.class,
                    C1Interface.class);

            // subscribe to interfaces

            final Subscription handleA1InterfaceSubscription = subscriptionManager
                    .subscribe(A1Interface.class, this::handleA1Interface);
            assertHasSubscriber(subscriptionManager, A.class, B.class, C.class);
            assertHasSubscriber(subscriptionManager, A1Interface.class);

            assertNoSubscriber(subscriptionManager, B1Interface.class, C1Interface.class);

            final Subscription handleB1InterfaceSubscription = subscriptionManager
                    .subscribe(B1Interface.class, this::handleB1Interface);
            assertHasSubscriber(subscriptionManager, A.class, B.class, C.class);
            assertHasSubscriber(subscriptionManager, A1Interface.class, B1Interface.class);

            assertNoSubscriber(subscriptionManager, C1Interface.class);

            final Subscription handleC1InterfaceSubscription = subscriptionManager
                    .subscribe(C1Interface.class, this::handleC1Interface);
            assertHasSubscriber(subscriptionManager, A.class, B.class, C.class);
            assertHasSubscriber(subscriptionManager, A1Interface.class, B1Interface.class,
                    C1Interface.class);

            // unsubscribe interface handler

            handleC1InterfaceSubscription.close();
            assertHasSubscriber(subscriptionManager, A.class, B.class, C.class);
            assertHasSubscriber(subscriptionManager, A1Interface.class, B1Interface.class);

            assertNoSubscriber(subscriptionManager, C1Interface.class);

            handleB1InterfaceSubscription.close();
            assertHasSubscriber(subscriptionManager, A.class, B.class, C.class);
            assertHasSubscriber(subscriptionManager, A1Interface.class);

            assertNoSubscriber(subscriptionManager, B1Interface.class, C1Interface.class);

            handleA1InterfaceSubscription.close();
            assertHasSubscriber(subscriptionManager, A.class, B.class, C.class);

            assertNoSubscriber(subscriptionManager, A1Interface.class, B1Interface.class,
                    C1Interface.class);

            // unsubscribe class handler

            handleCSubscription.close();
            assertHasSubscriber(subscriptionManager, A.class, B.class);

            assertNoSubscriber(subscriptionManager, C.class);
            assertNoSubscriber(subscriptionManager, A1Interface.class, B1Interface.class,
                    C1Interface.class);

            handleBSubscription.close();
            assertHasSubscriber(subscriptionManager, A.class);

            assertNoSubscriber(subscriptionManager, B.class, C.class);
            assertNoSubscriber(subscriptionManager, A1Interface.class, B1Interface.class,
                    C1Interface.class);

            handleASubscription.close();
            assertNoSubscriber(subscriptionManager, A.class, B.class, C.class);
            assertNoSubscriber(subscriptionManager, A1Interface.class, B1Interface.class,
                    C1Interface.class);

            // removed everything, assert start/end configuration
            assertNoSubscriber(subscriptionManager);
        }
    }

    /**
     * Test that unsubscribing multiple times via {@link Subscription#close()} is
     * side effect free.
     */
    @Test
    @DisplayName("Unsubscribing via Subscription.close() is side-effect free and can be done multiple times")
    public void closingSubscriptionMultipleTimesIsSideEffectFree() {
        try (final SubscriptionManager subscriptionManager = createSubscriptionManager()) {

            // assert start configuration
            assertNoSubscriber(subscriptionManager);

            final Subscription handleASubscription = subscriptionManager.subscribe(A.class,
                    this::handleA);
            assertFalse(handleASubscription.isClosed(), "Created Subscription must not be closed.");
            assertEquals(A.class, handleASubscription.forClass(),
                    "Created Subscription is not for expected class.");
            assertHasSubscriber(subscriptionManager, A.class);

            assertNoSubscriber(subscriptionManager, B.class, C.class);
            assertNoSubscriber(subscriptionManager, A1Interface.class, B1Interface.class,
                    C1Interface.class);

            handleASubscription.close();
            assertTrue(handleASubscription.isClosed(), "Closed Subscription must be closed.");
            assertNoSubscriber(subscriptionManager, A.class, B.class, C.class);
            assertNoSubscriber(subscriptionManager, A1Interface.class, B1Interface.class,
                    C1Interface.class);

            final Subscription handleCSubscription = subscriptionManager.subscribe(C.class,
                    this::handleC);
            assertFalse(handleCSubscription.isClosed(), "Created Subscription must not be closed.");
            assertEquals(C.class, handleCSubscription.forClass(),
                    "Created Subscription is not for expected class.");
            assertHasSubscriber(subscriptionManager, C.class);

            assertNoSubscriber(subscriptionManager, A.class, B.class);
            assertNoSubscriber(subscriptionManager, A1Interface.class, B1Interface.class,
                    C1Interface.class);

            handleASubscription.close();
            assertTrue(handleASubscription.isClosed(), "Closed Subscription must be closed.");
            assertHasSubscriber(subscriptionManager, C.class);

            assertNoSubscriber(subscriptionManager, A.class, B.class);
            assertNoSubscriber(subscriptionManager, A1Interface.class, B1Interface.class,
                    C1Interface.class);

            handleCSubscription.close();
            assertTrue(handleCSubscription.isClosed(), "Closed Subscription must be closed.");
            assertNoSubscriber(subscriptionManager, A.class, B.class, C.class);
            assertNoSubscriber(subscriptionManager, A1Interface.class, B1Interface.class,
                    C1Interface.class);

            handleCSubscription.close();
            assertTrue(handleCSubscription.isClosed(), "Closed Subscription must be closed.");
            assertNoSubscriber(subscriptionManager, A.class, B.class, C.class);
            assertNoSubscriber(subscriptionManager, A1Interface.class, B1Interface.class,
                    C1Interface.class);

            handleASubscription.close();
            assertTrue(handleASubscription.isClosed(), "Closed Subscription must be closed.");
            assertNoSubscriber(subscriptionManager, A.class, B.class, C.class);
            assertNoSubscriber(subscriptionManager, A1Interface.class, B1Interface.class,
                    C1Interface.class);

            // removed everything, assert start/end configuration
            assertNoSubscriber(subscriptionManager);
        }
    }

    /**
     * Test closing the event bus removes subscriber.
     */
    @Test
    @DisplayName("SubscriptionManager.close() unsubscribes all subscribed handlers")
    public void subscriptionManagerCloseClosesAllSubscriptions() {
        final List<Subscription> subscriptions = new ArrayList<>();
        final SubscriptionManager subscriptionManager = createSubscriptionManager();
        try {

            // assert start configuration
            assertNoSubscriber(subscriptionManager);

            final Subscription subscriptionA = subscriptionManager.subscribe(A.class,
                    this::handleA);
            assertFalse(subscriptionA.isClosed(), "Created Subscription must not be closed.");
            assertEquals(A.class, subscriptionA.forClass(),
                    "Created Subscription is not for expected class.");
            assertHasSubscriber(subscriptionManager, A.class);

            subscriptions.add(subscriptionA);

            assertNoSubscriber(subscriptionManager, B.class, C.class);
            assertNoSubscriber(subscriptionManager, A1Interface.class, B1Interface.class,
                    C1Interface.class);

            final Subscription subscriptionB = subscriptionManager.subscribe(B.class,
                    this::handleB);
            assertFalse(subscriptionB.isClosed(), "Created Subscription must not be closed.");
            assertEquals(B.class, subscriptionB.forClass(),
                    "Created Subscription is not for expected class.");
            assertHasSubscriber(subscriptionManager, A.class, B.class);

            subscriptions.add(subscriptionB);

            assertNoSubscriber(subscriptionManager, C.class);
            assertNoSubscriber(subscriptionManager, A1Interface.class, B1Interface.class,
                    C1Interface.class);

            final Subscription subscriptionC = subscriptionManager.subscribe(C.class,
                    this::handleC);
            assertFalse(subscriptionC.isClosed(), "Created Subscription must not be closed.");
            assertEquals(C.class, subscriptionC.forClass(),
                    "Created Subscription is not for expected class.");
            assertHasSubscriber(subscriptionManager, A.class, B.class, C.class);

            subscriptions.add(subscriptionC);

            assertNoSubscriber(subscriptionManager, A1Interface.class, B1Interface.class,
                    C1Interface.class);
        } finally {
            subscriptionManager.close();
        }

        // now all subscriber should be g1
        assertNoSubscriber(subscriptionManager);

        for (final Subscription subscription : subscriptions) {
            assertTrue(subscription.isClosed(), "Created Subscription for '"
                    + subscription.forClass() + "' must be closed after bus closure.");
        }

    }

    /**
     * Test that subscribers cannot be added after the bus has been closed.
     */
    @Test
    @DisplayName("SubscriptionManager.subscribe() throws IllegalStateException after SubscriptionManager.close()")
    public void subscribeThrowsIllegalStateExceptionAfterClose() {
        final SubscriptionManager subscriptionManager = createSubscriptionManager();
        try {
            subscriptionManager.subscribe(A.class, this::handleA);
        } finally {
            subscriptionManager.close();
        }

        assertThrows(
                IllegalStateException.class,
                () -> subscriptionManager.subscribe(A.class, this::handleA));

        assertThrows(
                IllegalStateException.class,
                () -> subscriptionManager.subscribe(B.class, this::handleB));

        assertThrows(
                IllegalStateException.class,
                () -> subscriptionManager.subscribe(C.class, this::handleC));
    }

    /**
     * Tests that subscriber which were subscribed by class can receive events.
     */
    @Test
    @DisplayName("Get handler subscribed by class")
    public void getSubscribedByClass() {
        try (final SubscriptionManager subscriberManager = createSubscriptionManager()) {

            // assert start configuration
            assertNoSubscriber(subscriberManager);

            // subscribe handler for the class types A, B, C

            subscriberManager.subscribe(A.class, this::handleA);
            assertEquals(1, subscriberManager.getHandlerFor(A.class)
                    .size());

            subscriberManager.subscribe(A.class, this::handleA1Interface);
            assertEquals(2, subscriberManager.getHandlerFor(A.class)
                    .size());


            assertEquals(0, subscriberManager.getHandlerFor(B.class)
                    .size());
            assertEquals(0, subscriberManager.getHandlerFor(C.class)
                    .size());


            subscriberManager.subscribe(B.class, this::handleB);
            assertEquals(1, subscriberManager.getHandlerFor(B.class)
                    .size());

            subscriberManager.subscribe(B.class, this::handleB1Interface);
            assertEquals(2, subscriberManager.getHandlerFor(B.class)
                    .size());


            assertEquals(0, subscriberManager.getHandlerFor(C.class)
                    .size());


            subscriberManager.subscribe(C.class, this::handleC);
            assertEquals(1, subscriberManager.getHandlerFor(C.class)
                    .size());

            subscriberManager.subscribe(C.class, this::handleC1Interface);
            assertEquals(2, subscriberManager.getHandlerFor(C.class)
                    .size());


        }
    }

    /**
     * Tests that subscriber which were subscribed by interface can receive events.
     */
    @Test
    @DisplayName("Get handler subscribed by interface")
    public void getSubscribedByInterface() {
        try (final SubscriptionManager subscriberManager = createSubscriptionManager()) {

            // assert start configuration
            assertNoSubscriber(subscriberManager);

            // subscribe handler for the interface types AInterface, BInterface, CInterface

            subscriberManager.subscribe(A1Interface.class, this::handleA1Interface);
            assertEquals(1, subscriberManager.getHandlerFor(A1Interface.class)
                    .size());
            assertEquals(0, subscriberManager.getHandlerFor(B1Interface.class)
                    .size());
            assertEquals(0, subscriberManager.getHandlerFor(C1Interface.class)
                    .size());

            subscriberManager.subscribe(B1Interface.class, this::handleB1Interface);
            assertEquals(1, subscriberManager.getHandlerFor(A1Interface.class)
                    .size());
            assertEquals(1, subscriberManager.getHandlerFor(B1Interface.class)
                    .size());
            assertEquals(0, subscriberManager.getHandlerFor(C1Interface.class)
                    .size());

            subscriberManager.subscribe(C1Interface.class, this::handleC1Interface);
            assertEquals(1, subscriberManager.getHandlerFor(A1Interface.class)
                    .size());
            assertEquals(1, subscriberManager.getHandlerFor(B1Interface.class)
                    .size());
            assertEquals(1, subscriberManager.getHandlerFor(C1Interface.class)
                    .size());


            // even though there is no direct handler
            // the SubscriberManager will search by direct interface since no match by class
            // was found
            assertEquals(1, subscriberManager.getHandlerFor(A.class)
                    .size());
            assertEquals(1, subscriberManager.getHandlerFor(B.class)
                    .size());
            assertEquals(1, subscriberManager.getHandlerFor(C.class)
                    .size());
        }
    }

    /**
     * Tests that a handler which has been unsubscribed is removed from subscriber
     * list.
     */
    @Test
    @DisplayName("Subscribed handler is not found anymore after Subscription.close()")
    public void subscribedHandlerIsNoFoundAnymoreAfterSubscriptionClose() {
        try (final SubscriptionManager subscriberManager = createSubscriptionManager()) {

            // assert start configuration
            assertNoSubscriber(subscriberManager);

            // subscribe handleA for A events
            final Subscription handleASubscription = subscriberManager.subscribe(A.class,
                    this::handleA);
            assertHasSubscriber(subscriberManager, A.class);
            assertNoSubscriber(subscriberManager, B.class, C.class);

            // remove handleA subscriber
            handleASubscription.close();
            assertNoSubscriber(subscriberManager, A.class, B.class, C.class);


            // subscribe handleA1Interface for A events
            final Subscription handleA1InterfaceSubscription = subscriberManager.subscribe(A.class,
                    this::handleA1Interface);
            assertHasSubscriber(subscriberManager, A.class);
            assertNoSubscriber(subscriberManager, B.class, C.class);

            // remove handleA1Interface subscriber
            handleA1InterfaceSubscription.close();
            assertNoSubscriber(subscriberManager, A.class, B.class, C.class);

        }
    }

    /**
     * Tests that an unsubscribed handler is not used with other subscribed handler
     * (for same type) present.
     *
     */
    @Test
    @DisplayName("Handler is correctly unsubscribed with other subscribed handler present for same type")
    public void handlerIsCorrectlyUnsubscribedWithOtherSubscribedHandlerPresentForSameType() {
        try (final SubscriptionManager subscriberManager = createSubscriptionManager()) {

            // assert start configuration
            assertNoSubscriber(subscriberManager);

            final Consumer<A> handleA = this::handleA;

            // subscribe handleA for A events
            final Subscription handleASubscription = subscriberManager.subscribe(A.class, handleA);
            assertHasSubscriber(subscriberManager, A.class);
            assertNoSubscriber(subscriberManager, B.class, C.class);

            assertTrue(subscriberManager.getHandlerFor(A.class)
                    .contains(handleA));


            final Consumer<A> handleA1Interface = this::handleA1Interface;

            // subscribe handleA1Interface for A events
            final Subscription handleA1InterfaceSubscription = subscriberManager.subscribe(A.class,
                    handleA1Interface);
            assertHasSubscriber(subscriberManager, A.class);
            assertNoSubscriber(subscriberManager, B.class, C.class);

            assertTrue(subscriberManager.getHandlerFor(A.class)
                    .contains(handleA));
            assertTrue(subscriberManager.getHandlerFor(A.class)
                    .contains(handleA1Interface));

            // remove handleA subscriber
            handleASubscription.close();

            assertHasSubscriber(subscriberManager, A.class);
            assertNoSubscriber(subscriberManager, B.class, C.class);

            assertFalse(subscriberManager.getHandlerFor(A.class)
                    .contains(handleA));
            assertTrue(subscriberManager.getHandlerFor(A.class)
                    .contains(handleA1Interface));

            // remove handleA1Interface subscriber
            handleA1InterfaceSubscription.close();
            assertNoSubscriber(subscriberManager, A.class, B.class, C.class);

            assertFalse(subscriberManager.getHandlerFor(A.class)
                    .contains(handleA));
            assertFalse(subscriberManager.getHandlerFor(A.class)
                    .contains(handleA1Interface));
        }
    }

    /**
     * Tests that handler subscribed by class override handler subscribed by
     * interface.
     */
    @Test
    @DisplayName("Handler subscribed by class overrides handler subscribed by interface")
    public void handlerSubscribedByClassOverridesHandlerSubscribedByInterface() {
        try (final SubscriptionManager subscriberManager = createSubscriptionManager()) {

            // assert start configuration
            assertNoSubscriber(subscriberManager);

            final Consumer<A1Interface> handleA1Interface = this::handleA1Interface;

            // subscribe handleA1Interface for A1Interface events
            subscriberManager.subscribe(A1Interface.class, handleA1Interface);
            assertHasSubscriber(subscriberManager, A1Interface.class);
            assertNoSubscriber(subscriberManager, A.class, B.class, C.class);

            assertTrue(subscriberManager.getHandlerFor(A.class)
                    .contains(handleA1Interface));
            assertTrue(subscriberManager.getHandlerFor(A1Interface.class)
                    .contains(handleA1Interface));


            final Consumer<A> handleA = this::handleA;

            // subscribe handleA for A events
            final Subscription handleA1Subscription = subscriberManager.subscribe(A.class, handleA);
            assertHasSubscriber(subscriberManager, A1Interface.class);
            assertHasSubscriber(subscriberManager, A.class);
            assertNoSubscriber(subscriberManager, B.class, C.class);

            // the handler added for type A is more specific than for type A1Interface
            assertTrue(subscriberManager.getHandlerFor(A.class)
                    .contains(handleA));
            assertFalse(subscriberManager.getHandlerFor(A.class)
                    .contains(handleA1Interface));
            assertTrue(subscriberManager.getHandlerFor(A1Interface.class)
                    .contains(handleA1Interface));

            // remove handleA1 subscriber
            handleA1Subscription.close();
            assertHasSubscriber(subscriberManager, A1Interface.class);
            assertNoSubscriber(subscriberManager, A.class, B.class, C.class);

            // since the handler for type A has been closed
            // fall back to handler for type A1Interface
            assertFalse(subscriberManager.getHandlerFor(A.class)
                    .contains(handleA));
            assertTrue(subscriberManager.getHandlerFor(A.class)
                    .contains(handleA1Interface));
            assertTrue(subscriberManager.getHandlerFor(A1Interface.class)
                    .contains(handleA1Interface));

            // subscribe handleA1 for A events
            subscriberManager.subscribe(A.class, handleA);
            assertHasSubscriber(subscriberManager, A1Interface.class);
            assertHasSubscriber(subscriberManager, A.class);
            assertNoSubscriber(subscriberManager, B.class, C.class);

            // the handler added for type A is more specific than for type A1Interface
            assertTrue(subscriberManager.getHandlerFor(A.class)
                    .contains(handleA));
            assertFalse(subscriberManager.getHandlerFor(A.class)
                    .contains(handleA1Interface));
            assertTrue(subscriberManager.getHandlerFor(A1Interface.class)
                    .contains(handleA1Interface));
        }
    }

    /**
     * Tests that handler subscribed by interface override handler subscribed by
     * interface in order of appearance.
     */
    @Test
    @DisplayName("Handler subscribed by interface overrides handler subscribed by interface in order of appearance")
    public void handlerSubscribedByInterfaceOverridesHandlerSubscribedByInterfaceInOrderOfAppearance() {
        try (final SubscriptionManager subscriberManager = createSubscriptionManager()) {

            // assert start configuration
            assertNoSubscriber(subscriberManager);


            final Consumer<A1Interface> handleA1Interface = this::handleA1Interface;

            // subscribe handleA1Interface for A1Interface events
            final Subscription handleA1InterfaceSubscription = subscriberManager
                    .subscribe(A1Interface.class, handleA1Interface);
            assertHasSubscriber(subscriberManager, A1Interface.class);
            assertNoSubscriber(subscriberManager, A2Interface.class);
            assertNoSubscriber(subscriberManager, A.class);

            assertTrue(subscriberManager.getHandlerFor(A.class)
                    .contains(handleA1Interface));
            assertTrue(subscriberManager.getHandlerFor(A1Interface.class)
                    .contains(handleA1Interface));


            final Consumer<A2Interface> handleA2Interface = this::handleA2Interface;

            // subscribe handleA2Interface for A2Interface events
            subscriberManager.subscribe(A2Interface.class, handleA2Interface);
            assertHasSubscriber(subscriberManager, A1Interface.class, A2Interface.class);
            assertNoSubscriber(subscriberManager, A.class);


            assertTrue(subscriberManager.getHandlerFor(A.class)
                    .contains(handleA1Interface));
            assertFalse(subscriberManager.getHandlerFor(A.class)
                    .contains(handleA2Interface));
            assertTrue(subscriberManager.getHandlerFor(A2Interface.class)
                    .contains(handleA2Interface));

            // unsubscribe handleA1Interface() for A events
            handleA1InterfaceSubscription.close();
            assertNoSubscriber(subscriberManager, A1Interface.class);
            assertHasSubscriber(subscriberManager, A2Interface.class);
            assertNoSubscriber(subscriberManager, A.class);

            assertFalse(subscriberManager.getHandlerFor(A.class)
                    .contains(handleA1Interface));
            assertFalse(subscriberManager.getHandlerFor(A1Interface.class)
                    .contains(handleA1Interface));
            assertTrue(subscriberManager.getHandlerFor(A.class)
                    .contains(handleA2Interface));
            assertTrue(subscriberManager.getHandlerFor(A2Interface.class)
                    .contains(handleA2Interface));

            // subscribe handleA1Interface for A1Interface events once more
            subscriberManager.subscribe(A1Interface.class, handleA1Interface);
            assertHasSubscriber(subscriberManager, A1Interface.class);
            assertHasSubscriber(subscriberManager, A2Interface.class);

            assertTrue(subscriberManager.getHandlerFor(A.class)
                    .contains(handleA1Interface));
            assertTrue(subscriberManager.getHandlerFor(A1Interface.class)
                    .contains(handleA1Interface));
            assertFalse(subscriberManager.getHandlerFor(A.class)
                    .contains(handleA2Interface));
            assertTrue(subscriberManager.getHandlerFor(A2Interface.class)
                    .contains(handleA2Interface));
        }
    }

    // ##########################################################################
    // Helper methods
    // ##########################################################################

    /**
     * Asserts that the supplied {@link Class}es have subscribed handlers for the
     * given {@link SubscriptionManager}.
     *
     * @param <SubscriptionManagerType>
     *            type of {@link SubscriptionManager} to test
     * @param subscriptionManager
     *            {@link SubscriptionManager} to check
     * @param clazzes
     *            {@link Class}es to check
     */
    protected static <SubscriptionManagerType extends SubscriptionManager> void assertHasSubscriber(
            final SubscriptionManagerType subscriptionManager,
            final Class<?>... clazzes) {
        for (final Class<?> clazz : clazzes) {
            assertTrue(subscriptionManager.hasHandlerForSpecificType(clazz),
                    "Has subscriber for class: " + clazz);
        }
    }

    /**
     * Asserts that the supplied {@link Class}es do NOT have subscribed handlers for
     * the given {@link SubscriptionManager}.
     *
     * @param <SubscriptionManagerType>
     *            type of {@link SubscriptionManager} to test
     * @param subscriptionManager
     *            {@link SubscriptionManager} to check
     * @param clazzes
     *            {@link Class}es to check
     */
    protected static <SubscriptionManagerType extends SubscriptionManager> void assertNoSubscriber(
            final SubscriptionManagerType subscriptionManager,
            final Class<?>... clazzes) {
        for (final Class<?> clazz : clazzes) {
            assertFalse(subscriptionManager.hasHandlerForSpecificType(clazz),
                    "No subscriber for class: " + clazz);
        }
    }

    /**
     * Asserts that the supplied {@link SubscriptionManager} has no subscriber, for
     * the common classes /interfaces used for testing.
     *
     * @param <SubscriptionManagerType>
     *            type of {@link SubscriptionManager} to test
     * @param subscriptionManager
     *            {@link SubscriptionManager} to check
     */
    protected static <SubscriptionManagerType extends SubscriptionManager> void assertNoSubscriber(
            final SubscriptionManagerType subscriptionManager) {
        assertNoSubscriber(subscriptionManager,
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
     * Class implementing 2 interfaces ({@link A1Interface} and
     * {@link A2Interface})used for testing.
     */
    protected static class A
            implements A1Interface, A2Interface {
        private final CountDownLatch doneLatch;

        // constructor must be public to be visible for subclasses (maybe in different
        // packages)
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
     * Sub-Class of {@link A} implementing 2 interfaces ({@link B1Interface} and
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
     * Sub-Class of {@link B} implementing 2 interfaces ({@link B1Interface} and
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
    // Handler methods to be used as subscriber for events.
    // ##########################################################################

    /**
     * Handler for events of type {@link A}.
     *
     * @param a
     *            event to handle
     */
    protected void handleA(
            final A a) {
        a.process();
    }

    /**
     * Handler for events of type {@link A1Interface}.
     *
     * @param a1Interface
     *            event to handle
     */
    protected void handleA1Interface(
            final A1Interface a1Interface) {
        a1Interface.process();
    }

    /**
     * Handler for events of type {@link A2Interface}.
     *
     * @param a2Interface
     *            event to handle
     */
    protected void handleA2Interface(
            final A2Interface a2Interface) {
        a2Interface.process();
    }

    /**
     * Handler for events of type {@link B}.
     *
     * @param b
     *            event to handle
     */
    protected void handleB(
            final B b) {
        b.process();
    }

    /**
     * Handler for events of type {@link B1Interface}.
     *
     * @param b1Interface
     *            event to handle
     */
    protected void handleB1Interface(
            final B1Interface b1Interface) {
        b1Interface.process();
    }

    /**
     * Handler for events of type {@link C}.
     *
     * @param c
     *            event to handle
     */
    protected void handleC(
            final C c) {
        c.process();
    }

    /**
     * Handler for events of type {@link C1Interface}.
     *
     * @param c1Interface
     *            event to handle
     */
    protected void handleC1Interface(
            final C1Interface c1Interface) {
        c1Interface.process();
    }

}
