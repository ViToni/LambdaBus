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
package org.kromo.lambdabus.dispatcher.impl;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

import org.kromo.lambdabus.ThreadingMode;
import org.kromo.lambdabus.dispatcher.DispatchingLambdaBus;
import org.kromo.lambdabus.dispatcher.EventDispatcher;
import org.kromo.lambdabus.impl.concurrent.DaemonThreadPoolExecutor;
import org.kromo.lambdabus.queue.EventQueue;
import org.kromo.lambdabus.queue.QueuedEvent;
import org.kromo.lambdabus.queue.impl.SharableEventQueue;
import org.kromo.lambdabus.util.DispatchingUtil;

/**
 * This class provides non-blocking posting and multi-threaded dispatching.<br>
 * Events are put into a queue and dispatched in a different tread.<br>
 * All {@link ThreadingMode}s are supported. Events posted as
 * {@link ThreadingMode#SYNC} are dispatched directly, other events are queued
 * and dispatched based on requested (or default) {@link ThreadingMode}.
 * 
 * @author Victor Toni - initial implementation
 *
 */
public class QueuedEventDispatcher
    extends AbstractEventDispatcher {

    private static final ThreadingMode DEFAULT_THREADING_MODE = ThreadingMode.ASYNC;

    /**
     * The {@link Set} of supported {@link ThreadingMode}s of this {@link EventDispatcher}.
     * As an event can be dispatched directly ({@link ThreadingMode#SYNC}) or in the {@link Thread} of
     * the {@link EventQueue} ({@link ThreadingMode#ASYNC}) this is the smallest possible {@link Set}.
     */
    private static final Set<ThreadingMode> DEFAULT_SUPPORTED_THREADING_MODES = EnumSet.of( //
            DEFAULT_THREADING_MODE, //
            ThreadingMode.ASYNC //
            );

    /**
     * Queue holding events (and associated information) to be dispatched.
     */
    private final EventQueue eventQueue;

    /**
     * Prepares a queuing threaded {@code EventDispatcher} instance.
     */
    public QueuedEventDispatcher() {
        this(DEFAULT_THREADING_MODE);
    }

    /**
     * Prepares a queuing threaded {@code EventDispatcher} instance.
     * 
     * @param defaultThreadingMode
     *            non-{@code null} {@link ThreadingMode} to be used as default
     *            when posting to the bus (unsupported modes used in 
     *            {@link DispatchingLambdaBus#post(Object, ThreadingMode)} will be mapped to this
     *            one)
     * @throws IllegalArgumentException
     *             if {@code defaultThreadingMode} is not supported (not contained
     *             within {@link #DEFAULT_SUPPORTED_THREADING_MODES}
     * @throws NullPointerException
     *             if {@code defaultThreadingMode} is {@code null}
     */
    public QueuedEventDispatcher(final ThreadingMode defaultThreadingMode) {
        this( //
                Objects.requireNonNull(defaultThreadingMode, "'defaultThreadingMode' must not be null"), //
                new SharableEventQueue( //
                        new DaemonThreadPoolExecutor( //
                                new LinkedBlockingQueue<>() //
                        ) //
                ) //
        );
    }

    /**
     * Prepares a queuing threaded {@code EventDispatcher} instance using an external
     * {@link EventQueue}.
     * 
     * @param eventQueue
     *            non-{@code null} {@link EventQueue} used to queue and dispatch events
     * @throws NullPointerException
     *             if {@code eventQueue} is {@code null}
     */
    public QueuedEventDispatcher(final EventQueue eventQueue) {
        this(
                DEFAULT_THREADING_MODE,
                eventQueue);
    }

    /**
     * Prepares a queuing threaded {@code EventDispatcher} instance using an external
     * {@link EventQueue}.
     * 
     * @param defaultThreadingMode
     *            non-{@code null} {@link ThreadingMode} to be used as default
     *            when posting to the bus (unsupported modes used in 
     *            {@link DispatchingLambdaBus#post(Object, ThreadingMode)} will be mapped to this
     *            one)
     * @param eventQueue
     *            non-{@code null} {@link EventQueue} used to queue and dispatch events
     * @throws NullPointerException
     *             if any of {@code defaultThreadingMode} or {@code eventQueue}
     *             is {@code null}
     * @throws IllegalArgumentException
     *             if {@code defaultThreadingMode} is not supported (not contained
     *             within the calculated supported {@link ThreadingMode}s
     * @see QueuedEventDispatcher#calculateSupportedThreadingModes(EventQueue)
     */
    public QueuedEventDispatcher(
            final ThreadingMode defaultThreadingMode,
            final EventQueue eventQueue
    ) {
        super( //
                Objects.requireNonNull(defaultThreadingMode, "'defaultThreadingMode' must not be null"), //
                calculateSupportedThreadingModes( //
                        Objects.requireNonNull(eventQueue, "'eventQueue' must not be null")
                ) //
        );

        this.eventQueue = eventQueue;
    }

    @Override
    protected void cleanupBeforeClose() {
        eventQueue.close();
    }

    //##########################################################################
    // Protected helper methods
    //##########################################################################

    /**
     * Either dispatches the given event directly (in case of
     * {@link ThreadingMode#SYNC}) or adds the event, its subscribed
     * {@link Consumer}s and the {@link ThreadingMode} to the internal {@link EventQueue} for
     * further processing.
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
     *            non-{@code null} {@link Collection} of non-{@code null}
     *            {@link Consumer}s registered for the {@link Class} of the
     *            event
     * @param supportedThreadingMode
     *            non-{@code null} {@link ThreadingMode} how the event should be
     *            dispatched
     */
    @Override
    protected final <T> void internalDispatchEventToSubscriber(
            final T event,
            final Collection<Consumer<T>> eventSubscriberCollection,
            final ThreadingMode supportedThreadingMode
    ) {
        // SYNC events dispatched directly
        if (ThreadingMode.SYNC == supportedThreadingMode) {
            DispatchingUtil.dispatchEventToSubscriber(
                    event,
                    eventSubscriberCollection);
        } else {
            // for all other modes events are enqueued
            enqueuEventForDispatching(
                    event,
                    eventSubscriberCollection,
                    supportedThreadingMode);
        }
    }

    //##########################################################################
    // Methods which can be overridden to customize behavior
    //##########################################################################

    /**
     * Creates a queued event and adds it to the configured {@link EventQueue}.
     * 
     * @param <T>
     *            type of event
     * @param event
     *            non-{@code null} object
     * @param eventSubscriberCollection
     *            non-{@code null} {@link Collection} of non-{@code null} {@link Consumer}s registered
     *            for the {@link Class} of the event
     * @param supportedThreadingMode
     *            how the event should be dispatched
     */
    protected <T> void enqueuEventForDispatching(
            final T event,
            final Collection<Consumer<T>> eventSubscriberCollection,
            final ThreadingMode supportedThreadingMode
    ) {
        final QueuedEvent<T> queuedEvent = new QueuedEvent<>(
                event,
                eventSubscriberCollection,
                supportedThreadingMode
        );
        eventQueue.add(queuedEvent);
    }

    /**
     * Creates a {@link Set} of {@link ThreadingMode} by combining our
     * {@link #DEFAULT_SUPPORTED_THREADING_MODES}s with the {@link ThreadingMode}s supported by the
     * {@link EventQueue}.<br>
     * As an event can be dispatched directly ({@link ThreadingMode#SYNC}) or in the {@link Thread} of
     * the {@link EventQueue} ({@link ThreadingMode#ASYNC}) this is the smallest possible
     * {@link Set}.<br>
     * If the {@link EventQueue} is using an {@link ExecutorService} additionally
     * <ul>
     * <li>{@link ThreadingMode#ASYNC_PER_EVENT}</li>
     * <li>{@link ThreadingMode#ASYNC_PER_SUBSCRIBER}</li>
     * </ul>
     * might be supported.
     * 
     * @param eventQueue
     *            {@link EventQueue} used to calculated the unified {@link Set} of
     *            {@link ThreadingMode}s
     * @return {@link Set} of {@link ThreadingMode}
     */
    protected static Set<ThreadingMode> calculateSupportedThreadingModes(final EventQueue eventQueue) {
        final Set<ThreadingMode> threadingModes = EnumSet.copyOf(DEFAULT_SUPPORTED_THREADING_MODES);

        threadingModes.addAll(eventQueue.getSupportedThreadingModes());

        return Collections.unmodifiableSet(threadingModes);
    }

}
