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
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.kromo.lambdabus.DeadEvent;
import org.kromo.lambdabus.LambdaBus;
import org.kromo.lambdabus.Subscription;
import org.kromo.lambdabus.ThreadingMode;
import org.kromo.lambdabus.util.NullEventPublisherLogger;
import org.kromo.lambdabus.util.UnsupportedThreadingModeReporter;
import org.kromo.lambdabus.dispatcher.EventDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link DispatchingLambdaBus} uses a strategy pattern to change
 * dispatching behavior. Dispatching is delegated to an implementation of the
 * {@link EventDispatcher}.
 *
 * @author Victor Toni - initial API and implementation
 *
 */
public class DispatchingLambdaBus implements LambdaBus {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * {@link Map} of event classes to {@link Collection} of subscribed {@link Consumer}s.
     */
    private final Map<Class<?>, Collection<Consumer<?>>> eventHandlerCollectionMap;

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
     * The actual implementation handling dispatching of events.
     */
    private final EventDispatcher eventDispatcher;

    /**
     * Creates an instance using the given {@link EventDispatcher} for actual dispatching.
     *
     * @param eventDispatcher
     *            non-{@code null} {@link EventDispatcher} which handles the actual event dispatching
     * @throws NullPointerException
     *             if {@code eventDispatcher} is {@code null}
     */
    public DispatchingLambdaBus(final EventDispatcher eventDispatcher) {
        this.eventDispatcher = Objects.requireNonNull(eventDispatcher, "'eventDispatcher' must not be null");

        eventHandlerCollectionMap = createEventHandlerCollectionMap();

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
            final Consumer<T> eventHandler
    ) {
        validateBusIsOpen(", subscriber not accepted anymore!");

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
    public <T> boolean hasSubscriberForClass(
            final Class<T> eventClass
    ) {
        /*
         * We don't use getSubscriber() because we don't need any casting related to T.
         */
        final Collection<Consumer<?>> eventHandlerCollection = eventHandlerCollectionMap.get(eventClass);

        return containsHandler(eventHandlerCollection);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final void close() {
        if(closed.compareAndSet(false, true)) {
            // quit dispatching
            eventDispatcher.close();

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
        return this.eventDispatcher.getDefaultThreadingMode();
    }

    /**
     * Gets the supported {@link ThreadingMode}s of this instance.
     *
     * @return {@link Set} of supported {@link ThreadingMode}s
     */
    protected final Set<ThreadingMode> getSupportedThreadingModes() {
        return this.eventDispatcher.getSupportedThreadingModes();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + '(' + eventDispatcher + ')';
    }

    private <T> void acceptNonNullEvent(
            final T event,
            final ThreadingMode supportedThreadingMode
    ) {
        tryToDispatchNonNullEvent(event, supportedThreadingMode);
    }

    private <T> void dispatchNonNullEventToHandler(
            final T event,
            final Collection<Consumer<T>> eventHandlerCollection,
            final ThreadingMode supportedThreadingMode
    ) {
        /*
        * Delegate to the dispatcher so that it can decide on its own which
        * dispatching strategy to use.
        */
        eventDispatcher.dispatchEventToHandler(
                event,
                eventHandlerCollection,
                supportedThreadingMode);
    }

    //##########################################################################
    // Methods which can be overridden to customize behavior
    //##########################################################################

    /**
     * Adds an event handler ({@link Consumer}) as subscriber to the internal
     * registry and returns a {@link Subscription} which will unsubscribe the
     * {@link Consumer} on {@link Subscription#close()}.
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
     * Creates a new {@link Map} to store the lookup table for {@link Class} to {@link Collection}
     * of event subscriber.
     *
     * @param <K>
     *            the type of keys in the map
     * @param <V>
     *            the type of values in the map
     * @return {@link Map}, default class used is {@link ConcurrentHashMap}
     */
    protected <K,V> Map<K,V> createEventHandlerCollectionMap() {
        return new ConcurrentHashMap<>();
    }

    /**
     * Creates a new {@link Collection} to store event subscriber.
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
    protected <T> Collection<T> createEventHandlerCollection(final Class<?> clazz) {
        return new CopyOnWriteArrayList<>();
    }

    /**
     * Returns an empty {@link Collection} of subscriber.
     * <p>
     * Implementation note:<br>
     * The default implementation of {@link #createEventHandlerCollection(Class)} returns an
     * instance of type {@link java.util.List}. When overriding
     * {@link #createEventHandlerCollection(Class)} it might be useful to override this method,
     * too, so that both return a {@link Collection} of the same type.
     * </p>
     *
     * @param <T>
     *            event type the subscriber registered for
     * @return {@link Collection}, default is {@link Collections#emptyList()}
     */
    protected <T> Collection<Consumer<T>> emptyEventHandlerCollection() {
        return Collections.emptyList();
    }

    //##########################################################################
    // Protected helper methods
    //##########################################################################

    /**
     * Closes all open {@link Subscription}s.
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
     * @see DispatchingLambdaBus#getDefaultThreadingMode()
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
     * Tries to dispatch a non-{@code null} event to matching subscriber.<br>
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

        final Collection<Consumer<T>> eventHandlerCollection = getNonNullEventHandlerCollection(eventClass);
        if (!eventHandlerCollection.isEmpty()) {
            dispatchNonNullEventToHandler(
                    event,
                    eventHandlerCollection,
                    supportedThreadingMode);
        } else {
            final Collection<Consumer<DeadEvent>> deadEventHandlerCollection = getNonNullEventHandlerCollection(DeadEvent.class);
            if (!deadEventHandlerCollection.isEmpty()) {
                final DeadEvent deadEvent = new DeadEvent(event);
                dispatchNonNullEventToHandler(
                        deadEvent,
                        deadEventHandlerCollection,
                        supportedThreadingMode);
            }
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
    protected final <T> Collection<Consumer<T>> getNonNullEventHandlerCollection(
            final Class<T> eventClass
    ) {
        final Collection<Consumer<?>> eventHandlerCollectionForClass = eventHandlerCollectionMap.get(eventClass);
        if (containsHandler(eventHandlerCollectionForClass)) {
            return castEventHandlerCollection(eventHandlerCollectionForClass);
        }

        // no event handler found for class, searching directly implemented interfaces
        final Collection<Consumer<?>> eventHandlerCollectionForDirectInterfaceOfClass = getEventHandlerCollectionForDirectInterface(eventClass);
        if (containsHandler(eventHandlerCollectionForDirectInterfaceOfClass)) {
            return castEventHandlerCollection(eventHandlerCollectionForDirectInterfaceOfClass);
        }

        // no event handler found for class or directly implemented interfaces
        return emptyEventHandlerCollection();
    }

    /**
     * Casts a {@link Collection} of subscriber for a given {@link Class}.
     *
     * @param <T>
     *            type of event
     * @param eventHandlerCollection
     *            {@link Collection} of subscribed {@link Consumer}s to cast
     * @return cast {@link Collection}
     */
    protected final <T> Collection<Consumer<T>> castEventHandlerCollection(
            final Collection<Consumer<?>> eventHandlerCollection
    ) {
        /*
         * This is a dirty hack because otherwise we cannot cast directly to Collection<Consumer<T>>
         * even if we know for sure that the subscriber are for type T.
         */
        @SuppressWarnings("unchecked")
        final Collection<Consumer<T>> typedEventHandlerCollection = (Collection<Consumer<T>>) (Object) eventHandlerCollection;

        return typedEventHandlerCollection;
    }

    /**
     * Returns {@link Collection} of subscriber for a given {@link Class}
     *
     * @param eventClass
     *            to get subscriber {@link Collection} for
     * @return found {@link Collection}, empty {@link Collection} otherwise
     */
    protected final Collection<Consumer<?>> getEventHandlerCollectionForDirectInterface(
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

        return castEventHandlerCollection(eventHandlerCollection);
    }

    /**
     * Logs an unsupported {@link ThreadingMode}.
     *
     * <p>
     * Implementation note:<br>
     * Internally the logging is done via the class
     * {@link UnsupportedThreadingModeReporter} so that excessive occurrence of
     * log entries about unsupported {@link ThreadingMode}s can be filtered out easily.
     * </p>
     *
     * @param unsupportedThreadingMode
     *            unsupported {@link ThreadingMode} to be logged
     */
    protected final void logUnsupportedThreadingMode(
            final ThreadingMode unsupportedThreadingMode
    ) {
        UnsupportedThreadingModeReporter.report(getDefaultThreadingMode(), unsupportedThreadingMode);
    }

    /**
     * Checks whether the {@link Collection} of subscriber contains any subscriber.
     *
     * @param eventHandlerCollection
     *            {@link Collection} of subscriber to check
     * @return {@code true} if {@link Collection} is not {@code null} and not empty,
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
