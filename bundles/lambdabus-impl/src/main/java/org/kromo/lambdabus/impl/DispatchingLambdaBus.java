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
import java.util.Objects;
import java.util.Set;
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
import org.kromo.lambdabus.dispatcher.impl.SynchronousEventDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link DispatchingLambdaBus} uses a strategy pattern to change
 * dispatching behavior.
 * Dispatching is delegated to an implementation of the {@link EventDispatcher}.
 * Subscriptions management logic is handled by an instance of {@code SubscriptionManager}
 *
 * @author Victor Toni - initial API and implementation
 *
 */
public class DispatchingLambdaBus implements LambdaBus {

    private final Logger logger = LoggerFactory.getLogger(getClass());

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
     * Class used to create, look up and terminate subscriptions.
     */
    private final SubscriptionManager subscriptionManager;

    /**
     * Creates an instance using defaults.
     */
    public DispatchingLambdaBus() {
        this(new SynchronousEventDispatcher());
    }

    /**
     * Creates an instance using the given {@link EventDispatcher} for actual dispatching.
     *
     * @param eventDispatcher
     *            non-{@code null} {@link EventDispatcher} which handles the actual event dispatching
     * @throws NullPointerException
     *             if {@code eventDispatcher} is {@code null}
     */
    public DispatchingLambdaBus(
            final EventDispatcher eventDispatcher
    ) {
        this(eventDispatcher, new DefaultSubscriptionManager());
    }

    /**
     * Creates an instance using the given {@link EventDispatcher} for actual
     * dispatching and {@link SubscriptionManager}.
     *
     * @param eventDispatcher
     *            non-{@code null} {@link EventDispatcher} which handles the
     *            actual event dispatching
     * @param subscriptionManager
     *            non-{@code null} {@link SubscriptionManager}
     * @throws NullPointerException
     *             if any of {@code eventDispatcher} {@code subscriptionManager} are {@code null}
     */
    public DispatchingLambdaBus(
            final EventDispatcher eventDispatcher,
            final SubscriptionManager subscriptionManager
    ) {
        this.eventDispatcher = Objects.requireNonNull(eventDispatcher, "'eventDispatcher' must not be null");

        this.subscriptionManager = Objects.requireNonNull(subscriptionManager, "'subscriptionManager' must not be null");

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

        return subscriptionManager.subscribe(eventClass, eventHandler);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> boolean hasSubscriberForClass(
            final Class<T> eventClass
    ) {
        return subscriptionManager.hasHandlerForSpecificType(eventClass);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final void close() {
        if(closed.compareAndSet(false, true)) {
            // quit dispatching
            eventDispatcher.close();

            subscriptionManager.close();
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
    // Protected helper methods
    //##########################################################################

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

        final Collection<Consumer<T>> eventHandlerCollection = subscriptionManager.getHandlerFor(eventClass);
        if (!eventHandlerCollection.isEmpty()) {
            dispatchNonNullEventToHandler(
                    event,
                    eventHandlerCollection,
                    supportedThreadingMode);
        } else {
            final Collection<Consumer<DeadEvent>> deadEventHandlerCollection = subscriptionManager.getHandlerFor(DeadEvent.class);
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

}
