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
package org.kromo.lambdabus.impl;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.kromo.lambdabus.DeadEvent;
import org.kromo.lambdabus.LambdaBus;
import org.kromo.lambdabus.Subscription;
import org.kromo.lambdabus.ThreadingMode;
import org.kromo.lambdabus.util.NullEventPublisherLogger;
import org.kromo.lambdabus.util.UnsupportedThreadingModeReporter;

/**
 * Base class providing the basic {@link LambdaBus} functionality, implementations can focus on the
 * dispatching algorithm.
 * <p>
 * Implementations have to implement only the methods:
 * <ul>
 * <li>{@link #acceptNonNullEvent(Object, ThreadingMode)}</li>
 * <li>{@link #dispatchNonNullEventToSubscriber(Object, Collection, ThreadingMode)}</li>
 * </ul>
 *
 * @author Victor Toni - initial API and implementation
 *
 */
public abstract class AbstractLambdaBus
    implements LambdaBus {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * {@link ThreadingMode} supported by the bus.
     */
    private final Set<ThreadingMode> supportedThreadingModes;

    /**
     * {@link ThreadingMode} used by default.
     */
    private final ThreadingMode defaultThreadingMode;

    /**
     * {@link Map} of event classes to {@link Collection} of subscribed {@link Consumer}s.
     */
    private final Map<Class<?>, Collection<Consumer<?>>> subscriberCollectionMap;

    /**
     * {@link Collection} of all {@link Subscription}s so that they can be
     * closed on {@link #close()}.
     */
    private final Collection<Subscription> subscriptionCollection = new CopyOnWriteArrayList<>();

    /**
     * Logger which detects which publisher posted a {@code null} event to the bus.
     */
    private final NullEventPublisherLogger nullEventPublisherLogger = new NullEventPublisherLogger();

    /**
     * Default {@link Runnable} to be executed when a {@code null} event has been received.
     */
    private final Runnable defaultRunnableForNullEvent = nullEventPublisherLogger::logNullEventSource;

    /**
     * {@link Runnable} to be executed when a {@code null} event has been posted to the bus.
     */
    private final AtomicReference<Runnable> runnableForNullEventRef = new AtomicReference<>();

    /**
     * Flag indicating whether the bus is closed.
     */
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /**
     * Prepares a {@code LambdaBus} instance for use by subclasses.
     *
     * @param defaultThreadingMode
     *            non-{@code null} {@link ThreadingMode} to be used as default
     *            when posting to the bus (unsupported modes will be mapped to
     *            this one)
     * @param supportedThreadingModes
     *            non-empty {@link Set} of supported {@link ThreadingMode}s
     * @throws NullPointerException
     *             if {@code defaultThreadingMode} or {@code supportedThreadingModes} is
     *             {@code null}
     * @throws IllegalArgumentException
     *             if {@code supportedThreadingModes} is empty or the
     *             {@code defaultThreadingMode} is not contained within
     *             {@code supportedThreadingModes}
     */
    protected AbstractLambdaBus(
            final ThreadingMode defaultThreadingMode,
            final Set<ThreadingMode> supportedThreadingModes
    ) {
        Objects.requireNonNull(defaultThreadingMode, "'defaultThreadingMode' must not be null");

        Objects.requireNonNull(supportedThreadingModes, "'supportedThreadingModes' must not be null");
        if (supportedThreadingModes.isEmpty()) {
            throw new IllegalArgumentException("'supportedThreadingModes' must not be empty");
        }

        final Set<ThreadingMode> copyOfSupportedThreadingModes;
        try {
            copyOfSupportedThreadingModes = EnumSet.copyOf(supportedThreadingModes);
        } catch (final NullPointerException e) {
            throw new NullPointerException("'supportedThreadingModes' contains NULL ThreadingMode");
        }

        if (!copyOfSupportedThreadingModes.contains(defaultThreadingMode)) {
            throw new IllegalArgumentException("Default ThreadingMode " + defaultThreadingMode
                    + " not contained within supported ThreadingModes: " + copyOfSupportedThreadingModes);
        }

        this.defaultThreadingMode = defaultThreadingMode;
        this.supportedThreadingModes = Collections.unmodifiableSet(copyOfSupportedThreadingModes);

        subscriberCollectionMap = createSubscriberCollectionMap();

        setDefaultRunnableForNullEventWithoutCheckingBusState();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final <T> void post(
            final T event
    ) {
        post(event, getDefaultThreadingMode());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final <T> void post(
            final T event,
            final ThreadingMode threadingModeHint
    ) {
        validateBusIsOpen(", events not accepted anymore!");

        Objects.requireNonNull(threadingModeHint, "'threadingModeHint' must not be null");

        if (Objects.nonNull(event)) {
            // ensure that we pass only supported ThreadingModes
            final ThreadingMode supportedThreadingMode = getSupportedThreadingMode(threadingModeHint);

            // has to be implemented by subclass
            acceptNonNullEvent(event, supportedThreadingMode);
        } else {
            final Runnable nullEventRunnable = runnableForNullEventRef.get();
            if (Objects.nonNull(nullEventRunnable)) {
                try {
                    nullEventRunnable.run();
                } catch (final Exception e) {
                    logger.warn("Failed to execute null event Runnable", e);
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> Subscription subscribe(
            final Class<T> eventClass,
            final Consumer<T> eventSubscriber
    ) {
        validateBusIsOpen(", subscribers not accepted anymore!");

        Objects.requireNonNull(eventClass, "'eventClass' must not be null");
        Objects.requireNonNull(eventSubscriber, "'eventSubscriber' must not be null");

        final Collection<Consumer<T>> subscriberCollection = getSubscriberCollectionOrCreateIfAbsent(eventClass);

        final Subscription subscription = addEventSubscriberAndReturnSubscription(
                eventClass,
                eventSubscriber,
                subscriberCollection);

        subscriptionCollection.add(subscription);

        return subscription;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> boolean hasSubscriberForClass(
            final Class<T> eventClass
    ) {
        /*
         * We don't use getSubscriber() because we don't need any casting related to T.
         */
        final Collection<Consumer<?>> subscriberCollection = subscriberCollectionMap.get(eventClass);

        return containsSubscriber(subscriberCollection);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final void close() {
        if(closed.compareAndSet(false, true)) {
            cleanupBeforeClose();
            closeSubscriptions();
        }
    }

    /**
     * Gets the closed state of the bus.
     *
     * @return returns {@code true} if {@link #close()} has been called,
     *         {@code false} otherwise
     */
    public final boolean isClosed() {
        return closed.get();
    }

    /**
     * Sets the default {@link Runnable} which gets executed whenever a
     * {@code null} event is posted to the bus.<br>
     * The {@link #defaultRunnableForNullEvent} is used as default.
     */
    public final void setDefaultRunnableForNullEvent() {
        setRunnableForNullEvent(defaultRunnableForNullEvent);
    }

    /**
     * Sets the default {@link Runnable} which gets executed whenever a
     * {@code null} event is posted to the bus.<br>
     * The {@link #defaultRunnableForNullEvent} is used as default.
     */
    protected final void setDefaultRunnableForNullEventWithoutCheckingBusState() {
        setRunnableForNullEventWithoutCheckingBusState(defaultRunnableForNullEvent);
    }

    /**
     * Sets the {@link Runnable} which gets executed whenever a {@code null}
     * event is posted to the bus.
     *
     * @param runnable
     *            non-{@code null} {@link Runnable} which gets executed on
     *            {@code null} event
     * @throws NullPointerException
     *             if runnable is {@code null}
     * @throws IllegalStateException
     *             if the bus has been closed already
     */
    public final void setRunnableForNullEvent(
            final Runnable runnable
    ) {
        validateBusIsOpen();

        setRunnableForNullEventWithoutCheckingBusState(runnable);
    }

    /**
     * Sets the {@link Runnable} which gets executed whenever a {@code null}
     * event is posted to the bus.
     *
     * @param runnableForNullEvent
     *            non-{@code null} {@link Runnable} which gets executed on any {@code null}
     *            event
     * @throws NullPointerException
     *             if runnable is {@code null}
     * @throws IllegalStateException
     *             if the bus has been closed already
     */
    protected final void setRunnableForNullEventWithoutCheckingBusState(
        final Runnable runnableForNullEvent
    ) {
        Objects.requireNonNull(runnableForNullEvent, "'runnableForNullEvent' must not be null");
        runnableForNullEventRef.set(runnableForNullEvent);
    }

    /**
     * Removes the {@link Runnable} which gets executed whenever a {@code null}
     * event is posted to the bus.
     *
     * @throws IllegalStateException
     *             if the bus has been closed already
     */
    public final void unsetRunnableForNullEvent() {
        validateBusIsOpen();

        runnableForNullEventRef.set(null);
    }

    /**
     * Checks if a {@link Runnable} was set to get executed whenever a
     * {@code null} event is posted to the bus.
     *
     * @throws IllegalStateException
     *             if the bus has been closed already
     * @return {@code true} if a {@link Runnable} is set, {@code false}
     *         otherwise
     */
    public final boolean hasRunnableForNullEvent() {
        validateBusIsOpen();

        return Objects.nonNull(
                runnableForNullEventRef.get()
            );
    }

    /**
     * Gets the default {@link ThreadingMode} of this instance.
     *
     * @return {@link ThreadingMode} used as default
     */
    protected final ThreadingMode getDefaultThreadingMode() {
        return defaultThreadingMode;
    }

    /**
     * Gets the supported {@link ThreadingMode}s of this instance.
     *
     * @return {@link Set} of supported {@link ThreadingMode}s
     */
    protected final Set<ThreadingMode> getSupportedThreadingModes() {
        return this.supportedThreadingModes;
    }

    //##########################################################################
    // Methods to be implemented to customize behavior
    //##########################################################################

    /**
     * Accepts the event for further processing.
     * <p>
     * All parameters are non-{@code null} because the calling method has
     * checked them already.
     * </p>
     *
     * @param <T>
     *            type of posted event
     * @param event
     *            non-{@code null} object to be dispatched
     * @param supportedThreadingMode
     *            non-{@code null} {@link ThreadingMode} how the event should be
     *            dispatched
     */
    protected abstract <T> void acceptNonNullEvent(
            final T event,
            final ThreadingMode supportedThreadingMode
    );

    /**
     * Dispatch event to subscribed {@link Consumer}s.
     *
     * <p>
     * All parameters are non-{@code null} because the calling method has
     * checked them already.
     * </p>
     *
     * @param <T>
     *            type of posted event
     * @param event
     *            non-{@code null} object to be dispatched
     * @param eventSubscriberCollection
     *            non-{@code null} {@link Collection} of {@link Consumer}s
     *            registered for the {@link Class} of the event
     * @param supportedThreadingMode
     *            non-{@code null} {@link ThreadingMode} how the event should be
     *            dispatched
     */
    protected abstract <T> void dispatchNonNullEventToSubscriber(
            final T event,
            final Collection<Consumer<T>> eventSubscriberCollection,
            final ThreadingMode supportedThreadingMode
    );

    //##########################################################################
    // Methods which can be overridden to customize behavior
    //##########################################################################

    /**
     * Adds an event subscriber ({@link Consumer}) to the internal registry and
     * returns a {@link Subscription} which will unsubscribe the
     * {@link Consumer} on {@code close()}.
     *
     * @param <T>
     *            event type the subscriber registered for
     * @param eventClass
     *            non-{@code null} {@link Class} of events the subscription is
     *            for
     * @param eventSubscriber
     *            non-{@code null} {@link Consumer} to subscribe
     * @param subscriberCollection
     *            non-{@code null} {@link Collection} of {@link Consumer} to
     *            which the subscriber should be added to
     * @return non-{@code null} {@link Subscription} which will unsubscribe the
     *         {@link Consumer} (remove it from the {@link Collection}
     */
    protected <T> Subscription addEventSubscriberAndReturnSubscription(
            final Class<T> eventClass,
            final Consumer<T> eventSubscriber,
            final Collection<Consumer<T>> subscriberCollection
    ) {
        subscriberCollection.add(eventSubscriber);

        final Runnable closeRunnable =
                () -> subscriberCollection.remove(eventSubscriber);

        return new SubscriptionImpl(eventClass, closeRunnable);
    }

    /**
     * Creates a new {@link Map} to store the lookup table for {@link Class} to {@link Collection}
     * of event subscribers.
     *
     * @param <K>
     *            the type of keys in the map
     * @param <V>
     *            the type of values in the map
     * @return {@link Map}, default class used is {@link ConcurrentHashMap}
     */
    protected <K,V> Map<K,V> createSubscriberCollectionMap() {
        return new ConcurrentHashMap<>();
    }

    /**
     * Creates a new {@link Collection} to store event subscribers.
     *
     * @param <T>
     *            type of the subscriber
     * @param clazz
     *            parameter only used to match signature for functional
     *            interface needed for
     *            {@link Map#computeIfAbsent(Object, java.util.function.Function)}
     * @return {@link Collection}, default class used is
     *         {@link CopyOnWriteArrayList}
     */
    protected <T> Collection<T> createSubscriberCollection(final Class<?> clazz) {
        return new CopyOnWriteArrayList<>();
    }

    /**
     * Returns an empty {@link Collection} of subscribers.
     * Used to provide a non-{@code null} {@code Collection} when no
     * subscribers are found.
     * <p>
     * Implementation note:<br>
     * The default implementation of {@link #createSubscriberCollection(Class)} returns an
     * instance of type {@link java.util.List}. When overriding
     * {@link #createSubscriberCollection(Class)} it might be useful to override this method,
     * too, so that both return a {@link Collection} of the same type.
     * </p>
     *
     * @param <T>
     *            event type for which registered subscribers were searched
     * @return {@link Collection}, default is {@link Collections#emptyList()}
     */
    protected <T> Collection<Consumer<T>> emptySubscriberCollection() {
        return Collections.emptyList();
    }

    /**
     * Cleanup before closing the bus.
     */
    protected void cleanupBeforeClose() {
    }

    //##########################################################################
    // Protected helper methods
    //##########################################################################

    /**
     * Closes all subscribers {@link Subscription}s.
     */
    protected final synchronized void closeSubscriptions() {
        for (final Subscription subscription : subscriptionCollection) {
            subscription.close();
        }
        subscriptionCollection.clear();
    }

    /**
     * Checks whether a {@link ThreadingMode} is supported by the event bus, if it is it will be
     * returned otherwise the default {@link ThreadingMode} ({@link #getDefaultThreadingMode()})
     * will be returned.
     *
     * @param threadingModeHint
     *            {@link ThreadingMode} to check
     * @return provided {@link ThreadingMode} if supported, otherwise the default
     *         {@link ThreadingMode}
     * @see AbstractLambdaBus#getDefaultThreadingMode()
     */
    protected final ThreadingMode getSupportedThreadingMode(
            final ThreadingMode threadingModeHint
    ) {
        if (isSupportedThreadingMode(threadingModeHint)) {
            return threadingModeHint;
        }

        logUnsupportedThreadingMode(threadingModeHint);

        return getDefaultThreadingMode();
    }

    /**
     * Checks if the given {@link ThreadingMode} is supported.
     *
     * @param threadingMode
     *            to check if it is supported
     * @return {@code true} if {@link ThreadingMode} is supported, {@code false} otherwise
     */
    protected final boolean isSupportedThreadingMode(final ThreadingMode threadingMode) {
        return getSupportedThreadingModes().contains(threadingMode);
    }

    /**
     * Checks whether the bus is still open.
     *
     * @throws IllegalStateException
     *             if the bus has been closed already
     */
    protected final void validateBusIsOpen() {
        validateBusIsOpen("!");
    }

    /**
     * Checks whether the bus is still open.
     *
     * @param errorMessage
     *            message to attach to the {@link IllegalStateException}
     * @throws IllegalStateException
     *             if the bus has been closed already
     */
    protected final void validateBusIsOpen(final String errorMessage) {
        if (isClosed()) {
            throw new IllegalStateException(getClass().getSimpleName() + " already stopped" + errorMessage);
        }
    }

    /**
     * Tries to dispatch a non-{@code null} event to matching subscribers.<br>
     * If no matching subscriber were found the {@link DeadEvent}
     * {@link Consumer} will be called (if registered).
     *
     * @param <T>
     *            type of event
     * @param event
     *            non-{@code null} object to be dispatched
     * @param supportedThreadingMode
     *            non-{@code null} {@link ThreadingMode} how the event should be
     *            dispatched
     * @throws NullPointerException
     *             if event is {@code null}
     */
    protected final <T> void tryToDispatchNonNullEvent(
            final T event,
            final ThreadingMode supportedThreadingMode
    ) {
        @SuppressWarnings("unchecked")
        final Class<T> eventClass = (Class<T>) event.getClass();

        final Collection<Consumer<T>> eventSubscriberCollection = getNonNullEventSubscriberCollection(eventClass);
        if (!eventSubscriberCollection.isEmpty()) {
            dispatchNonNullEventToSubscriber(
                event,
                eventSubscriberCollection,
                supportedThreadingMode);
        } else {
            final Collection<Consumer<DeadEvent>> deadEventSubscriberCollection = getNonNullEventSubscriberCollection(DeadEvent.class);
            if (!deadEventSubscriberCollection.isEmpty()) {
                final DeadEvent deadEvent = new DeadEvent(event);
                dispatchNonNullEventToSubscriber(
                    deadEvent,
                    deadEventSubscriberCollection,
                    supportedThreadingMode);
            }
        }
    }

    /**
     * Returns {@link Collection} of subscribers for a given {@link Class}.
     *
     * @param <T>
     *            type of event
     * @param eventClass
     *            class to get {@code Collection} of subscribers for
     * @return found {@code Collection}, empty {@code Collection} otherwise
     */
    protected final <T> Collection<Consumer<T>> getNonNullEventSubscriberCollection(
            final Class<T> eventClass
    ) {
        final Collection<Consumer<?>> subscriberCollectionForClass = subscriberCollectionMap.get(eventClass);
        if (containsSubscriber(subscriberCollectionForClass)) {
            return castSubscriberCollection(subscriberCollectionForClass);
        }

        // no subscriber found for class, searching directly implemented interfaces
        final Collection<Consumer<?>> subscriberCollectionForDirectInterfaceOfClass = getSubscriberCollectionForDirectInterface(eventClass);
        if (containsSubscriber(subscriberCollectionForDirectInterfaceOfClass)) {
            return castSubscriberCollection(subscriberCollectionForDirectInterfaceOfClass);
        }

        // no subscriber found for class or directly implemented interfaces
        return emptySubscriberCollection();
    }

    /**
     * Casts a {@link Collection} of subscribers for a given {@link Class}.
     *
     * @param <T>
     *            type of event
     * @param subscriberCollection
     *            {@code Collection} of subscriber to cast
     * @return cast {@code Collection}
     */
    protected final <T> Collection<Consumer<T>> castSubscriberCollection(
            final Collection<Consumer<?>> subscriberCollection
    ) {
        /*
         * This is a dirty hack because otherwise we cannot cast directly to
         * Collection<Consumer<T>> even if we know for sure that the subscribers
         * are of type Consumer<T>.
         */
        @SuppressWarnings("unchecked")
        final Collection<Consumer<T>> typedSubscriberCollection = (Collection<Consumer<T>>) (Object) subscriberCollection;

        return typedSubscriberCollection;
    }

    /**
     * Returns {@link Collection} of subscribers for a given {@link Class}
     *
     * @param eventClass
     *            to get {@code Collection} of subscribers for
     * @return found {@code Collection}, empty {@code Collection} otherwise
     */
    protected final Collection<Consumer<?>> getSubscriberCollectionForDirectInterface(
            final Class<?> eventClass
    ) {
        for (final Class<?> eventInterface : eventClass.getInterfaces()) {
            final Collection<Consumer<?>> subscriberCollection = subscriberCollectionMap.get(eventInterface);
            // First match wins. Interfaces retain declaration order.
            if (containsSubscriber(subscriberCollection)) {
                return subscriberCollection;
            }
        }

        return Collections.emptySet();
    }

    /**
     * Returns {@link Collection} of subscribers for a given {@link Class}.<br>
     * If no {@code Collection} exist a new instance will be created using
     * {@link #createSubscriberCollection(Class)}.
     *
     * @param <T>
     *            type of event
     * @param eventClass
     *            class of event to get {@code Collection} of subscribers for
     * @return found {@code Collection}, or a new {@code Collection} (created by
     *         {@link #createSubscriberCollection(Class)}) otherwise
     */
    protected final <T> Collection<Consumer<T>> getSubscriberCollectionOrCreateIfAbsent(
            final Class<T> eventClass
    ) {
        final Collection<Consumer<?>> subscriberCollection = subscriberCollectionMap.computeIfAbsent(
            eventClass,
            this::createSubscriberCollection
        );

        return castSubscriberCollection(subscriberCollection);
    }

    /**
     * Logs an unsupported {@link ThreadingMode}.
     *
     * <p>
     * Implementation note:<br>
     * Internally the logging is done via the class
     * {@link UnsupportedThreadingModeReporter} so that excessive occurrence of
     * log entries about unsupported {@code ThreadingMode}s can be filtered out easily.
     * </p>
     *
     * @param unsupportedThreadingMode
     *            unsupported {@code ThreadingMode} to be logged
     */
    protected final void logUnsupportedThreadingMode(
            final ThreadingMode unsupportedThreadingMode
    ) {
        UnsupportedThreadingModeReporter.report(getDefaultThreadingMode(), unsupportedThreadingMode);
    }

    /**
     * Checks whether the {@link Collection} of subscribers is non-{@code null}
     * and contains any subscriber.
     *
     * @param subscriberCollection
     *            {@code Collection} of subscribers to check
     * @return {@code true} if {@code Collection} is not {@code null} and not empty,
     *         {@code false} otherwise
     */
    protected static boolean containsSubscriber(
            final Collection<Consumer<?>> subscriberCollection
    ) {
        return (Objects.nonNull(subscriberCollection) && !subscriberCollection.isEmpty());
    }

    /**
     * Implementation of the {@link Subscription} interface ensuring the
     * unsubscription happens only once.
     *
     * @author Victor Toni - initial implementation
     *
     */
    private static final class SubscriptionImpl
        implements Subscription {
        private final AtomicBoolean closed = new AtomicBoolean(false);

        private final Class<?> eventClass;
        private Runnable closeRunnable;

        public SubscriptionImpl(
                final Class<?> eventClass,
                final Runnable closeRunnable
        ) {
            this.eventClass = Objects.requireNonNull(eventClass, "'eventClass' must not be null");
            this.closeRunnable = Objects.requireNonNull(closeRunnable, "'closeRunnable' must not be null");
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
            }
        }
    }
}
