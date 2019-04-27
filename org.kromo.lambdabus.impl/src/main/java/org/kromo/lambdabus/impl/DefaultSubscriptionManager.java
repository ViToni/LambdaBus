/*******************************************************************************
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
 *     Victor Toni - initial API and implementation
 *******************************************************************************/
package org.kromo.lambdabus.impl;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.kromo.lambdabus.Subscription;

/**
 * Implements the {@link SubscriptionManager} interface and provides a private
 * {@link Subscription} implementation to allow simple subscription termination.
 *
 * @author Victor Toni - initial API and implementation
 *
 */
public class DefaultSubscriptionManager implements SubscriptionManager {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * {@link Map} of event classes to {@link Collection} of subscribed event
     * handler as {@link Consumer}s.
     */
    private final Map<Class<?>, Collection<Consumer<?>>> eventHandlerCollectionMap;

    /**
     * Tracks all {@link Subscription}s not closed yet so that they can be
     * closed on {@link #close()}.
     */
    private final Collection<Subscription> subscriptionCollection = new CopyOnWriteArrayList<>();

    /**
     * Flag indicating whether the {@code SubscriptionManager} is closed.
     */
    private final AtomicBoolean closed = new AtomicBoolean(false);


    /**
     * Used to create a new {@link Collection} to store event handler.
     * This supplier is used when a new type has been used the first time to
     * create a subscription.
     */
    private final Supplier<Collection<Consumer<?>>> eventHandlerCollectionSupplier;

    /**
     * Creates an instance using defaults.
     */
    public DefaultSubscriptionManager() {
        this(ConcurrentHashMap::new, CopyOnWriteArrayList::new);
    }


    /**
     * Creates an instance using the given {@link Supplier} for actual
     * dispatching.
     *
     * @param eventHandlerCollectionMapSupplier
     *            non-{@code null} {@link Supplier} used to create the map
     *            between types and their collection of event handlers
     * @param eventHandlerCollectionSupplier
     *            non-{@code null} {@link Supplier} which creates
     *            {@link Collection}s storing the event handler
     * @throws NullPointerException
     *             if any of {@code eventHandlerCollectionMapSupplier} or
     *             {@code eventHandlerCollectionSupplier} is {@code null}
     */
    public DefaultSubscriptionManager( //
            final Supplier<Map<Class<?>, Collection<Consumer<?>>>> eventHandlerCollectionMapSupplier,
            final Supplier<Collection<Consumer<?>>> eventHandlerCollectionSupplier
    ) {
        eventHandlerCollectionMap = Objects.requireNonNull(eventHandlerCollectionMapSupplier,
                "'eventHandlerCollectionMapSupplier' must not be null")
                .get();

        this.eventHandlerCollectionSupplier = Objects.requireNonNull(eventHandlerCollectionSupplier,
                "'eventHandlerCollectionSupplier' must not be null");
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public <T> Subscription subscribe(
            final Class<T> eventClass,
            final Consumer<T> eventHandler
    ) {
        validateIsOpen(", subscriber not accepted anymore!");

        Objects.requireNonNull(eventClass, "'eventClass' must not be null");
        Objects.requireNonNull(eventHandler, "'eventHandler' must not be null");

        final Collection<Consumer<T>> eventHandlerCollection = getEventHandlerCollectionOrCreateIfAbsent(eventClass);

        final Subscription subscription = addEventHandlerAndReturnSubscription(
                eventClass,
                eventHandler,
                eventHandlerCollection);

        subscriptionCollection.add(subscription);

        return subscription;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> boolean hasHandlerForSpecificType(
            final Class<T> eventClass
    ) {

        // We don't use getHandlerFor() because we don't need any casting
        // related to T and we want to restrict the search to the specific
        // type only.
        final Collection<Consumer<?>> handlerCollection = eventHandlerCollectionMap.get(eventClass);

        return containsHandler(handlerCollection);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> boolean hasHandlerFor(
            final Class<T> eventClass
    ) {
        if (hasHandlerForSpecificType(eventClass)) {
            return true;
        }

        for (final Class<?> eventInterface : eventClass.getInterfaces()) {
            final Collection<Consumer<?>> eventHandlerCollection = eventHandlerCollectionMap.get(eventInterface);
            // First match wins. Interfaces retain declaration order.
            if (containsHandler(eventHandlerCollection)) {
                return true;
            }
        }


        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final void close() {
        if(closed.compareAndSet(false, true)) {
            closeSubscriptions();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final boolean isClosed() {
        return closed.get();
    }

    //##########################################################################
    // Methods which can be overridden to customize behavior
    //##########################################################################

    /**
     * Adds an event handler ({@link Consumer}) as subscriber to the internal
     * registry and returns a {@link Subscription} which will unsubscribe the
     * {@link Consumer} on {@code close()}.
     *
     * @param <T>
     *            event type the subscriber registered for
     * @param eventClass
     *            non-{@code null} {@link Class} of events the subscription is
     *            for
     * @param eventHandler
     *            non-{@code null} {@link Consumer} to subscribe
     * @param eventHandlerCollection
     *            non-{@code null} {@link Collection} of {@link Consumer} to
     *            which the subscriber should be added to
     * @return non-{@code null} {@link Subscription} which will unsubscribe the
     *         {@link Consumer} (remove it from the {@link Collection}
     */
    protected <T> Subscription addEventHandlerAndReturnSubscription(
            final Class<T> eventClass,
            final Consumer<T> eventHandler,
            final Collection<Consumer<T>> eventHandlerCollection
    ) {
        eventHandlerCollection.add(eventHandler);

        final Runnable closeRunnable =
                () -> eventHandlerCollection.remove(eventHandler);

        return new SubscriptionImpl(eventClass, closeRunnable);
    }

    /**
     * Creates a new {@link Collection} to store event handler.
     *
     * @param <T>
     *            type of the subscriber
     * @param clazz
     *            parameter only used to match signature for functional
     *            interface needed for
     *            {@link Map#computeIfAbsent(Object, java.util.function.Function)}
     * @return {@link Collection}, default class used is
     *            {@link CopyOnWriteArrayList}
     */
    protected <T> Collection<T> createEventHandlerCollection(
            final Class<?> clazz
    ) {
        @SuppressWarnings("unchecked")
        final Collection<T> eventHandlerCollection = (Collection<T>) this.eventHandlerCollectionSupplier.get();

        return eventHandlerCollection;
    }

    /**
     * Returns an empty {@link Collection} of subscriber.
     * <p>
     * Implementation note:<br>
     * The default implementation of {@link #createEventHandlerCollection(Class)}
     * returns an instance of type {@link java.util.List}. When overriding
     * {@link #createEventHandlerCollection(Class)} it might be useful to
     * override this method, too, so that both return a {@link Collection} of
     * the same type.
     * </p>
     *
     * @param <T>
     *            event type the subscriber registered for
     * @return {@link Collection}, default is {@link Collections#emptyList()}
     */
    protected <T> Collection<Consumer<T>> emptySubscriberCollection() {
        return Collections.emptyList();
    }

    //##########################################################################
    // Protected helper methods
    //##########################################################################

    /**
     * Closes all subscriber {@link Subscription}s.
     */
    protected final void closeSubscriptions() {
        subscriptionCollection.forEach(Subscription::close);
    }

    /**
     * Checks whether the {@code SubscriberManager} is still open.
     *
     * @param errorMessage
     *            message to attach to the {@link IllegalStateException}
     * @throws IllegalStateException
     *             if the {@code SubscriberManager} has been closed already
     */
    protected final void validateIsOpen(
            final String errorMessage
    ) {
        if (isClosed()) {
            throw new IllegalStateException(getClass().getSimpleName() + " already closed" + errorMessage);
        }
    }

    /**
     * Returns {@link Collection} of subscriber for a given {@link Class}.
     *
     * @param <T>
     *            type of event
     * @param eventClass
     *            class to get subscriber {@link Collection} for
     * @return found {@link Collection}, empty {@link Collection} otherwise
     */
    @Override
    public final <T> Collection<Consumer<T>> getHandlerFor(
            final Class<T> eventClass
    ) {
        final Collection<Consumer<?>> eventHandlerCollectionForClass = eventHandlerCollectionMap.get(eventClass);
        if (containsHandler(eventHandlerCollectionForClass)) {
            return castHandlerCollection(eventHandlerCollectionForClass);
        }

        // no subscriber found for class, searching directly implemented interfaces
        final Collection<Consumer<?>> eventHandlerCollectionForDirectInterfaceOfClass = getHandlerCollectionForDirectInterface(eventClass);
        if (containsHandler(eventHandlerCollectionForDirectInterfaceOfClass)) {
            return castHandlerCollection(eventHandlerCollectionForDirectInterfaceOfClass);
        }

        // no subscriber found for class or directly implemented interfaces
        return emptySubscriberCollection();
    }

    /**
     * Casts a {@link Collection} of handler for a given {@link Class}.
     *
     * @param <T>
     *            type of event
     * @param eventHandlerCollection
     *            collection of subscribed {@link Consumer} to cast
     * @return cast {@link Collection}
     */
    protected final <T> Collection<Consumer<T>> castHandlerCollection(
            final Collection<Consumer<?>> eventHandlerCollection
    ) {

        // This is a dirty hack because otherwise we cannot cast directly to
        // Collection<Consumer<T>> even if we know for sure that the handler
        // are for type T.
        @SuppressWarnings("unchecked")
        final Collection<Consumer<T>> typedEventHandlerCollection = (Collection<Consumer<T>>) (Object) eventHandlerCollection;

        return typedEventHandlerCollection;
    }

    /**
     * Returns {@link Collection} of handler for a given {@link Class}
     *
     * @param eventClass
     *            to get handler {@link Collection} for
     * @return found {@link Collection}, empty {@link Collection} otherwise
     */
    protected final Collection<Consumer<?>> getHandlerCollectionForDirectInterface(
            final Class<?> eventClass
    ) {
        for (final Class<?> eventInterface : eventClass.getInterfaces()) {
            final Collection<Consumer<?>> eventHandlerCollection = eventHandlerCollectionMap.get(eventInterface);
            // First match wins. Interfaces retain declaration order.
            if (containsHandler(eventHandlerCollection)) {
                return eventHandlerCollection;
            }
        }

        return Collections.emptySet();
    }

    /**
     * Returns {@link Collection} of subscriber for a given {@link Class}.<br>
     * If no {@link Collection} exist a new instance will be created using
     * {@link #createEventHandlerCollection(Class)}.
     *
     * @param <T>
     *            type of event
     * @param eventClass
     *            class of event to get subscriber {@link Collection} for
     * @return found {@link Collection}, or a new {@link Collection} (created by
     *         {@link #createEventHandlerCollection(Class)}) otherwise
     */
    protected final <T> Collection<Consumer<T>> getEventHandlerCollectionOrCreateIfAbsent(
            final Class<T> eventClass
    ) {
        final Collection<Consumer<?>> eventHandlerCollection = eventHandlerCollectionMap.computeIfAbsent(
                eventClass,
                this::createEventHandlerCollection

        );

        return castHandlerCollection(eventHandlerCollection);
    }

    /**
     * Checks whether the {@link Collection} of {@link Consumer} contains any
     * event handler.
     *
     * @param eventHandlerCollection
     *            {@code Collection} of {@code Consumer} to check
     * @return {@code true} if {@code Collection} is not {@code null} and not empty,
     *         {@code false} otherwise
     */
    protected static boolean containsHandler(
            final Collection<Consumer<?>> eventHandlerCollection
    ) {
        return (Objects.nonNull(eventHandlerCollection) && !eventHandlerCollection.isEmpty());
    }

    /**
     * Implementation of the {@link Subscription} interface ensuring the
     * unsubscription happens only once.
     *
     * @author Victor Toni - initial implementation
     *
     */
    private final class SubscriptionImpl
            implements Subscription {

        private final AtomicBoolean closed = new AtomicBoolean(false);

        private final Class<?> eventClass;
        private Runnable closeRunnable;

        SubscriptionImpl(
                final Class<?> eventClass,
                final Runnable closeRunnable
        ) {
            this.eventClass = Objects.requireNonNull(eventClass, "'eventClass' must not be null");
            this.closeRunnable = Objects.requireNonNull(closeRunnable, "'closeRunnable' must not be null");

            logger.trace("Created subscription for class: {}", eventClass);
        }

        @Override
        public Class<?> forClass() {
            return eventClass;
        }

        @Override
        public boolean isClosed() {
            return closed.get();
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                closeRunnable.run();
                closeRunnable = null;
                subscriptionCollection.remove(this);
                logger.trace("Closed subscription for class: {}", eventClass);
            }
        }
    }

}
