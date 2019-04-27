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
package org.kromo.lambdabus.dispatcher.impl;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.kromo.lambdabus.ThreadingMode;
import org.kromo.lambdabus.dispatcher.EventDispatcher;
import org.kromo.lambdabus.impl.DispatchingLambdaBus;

/**
 * This class provides non-blocking posting and asynchronous dispatching.<br>
 * Events are put into a queue and dispatched in a different thread.<br>
 * Supported {@link ThreadingMode}s depend on the {@code EventDipatcher} the
 * actual. dispatching is delegated to. Events posted as
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
     * The {@link Set} of supported {@link ThreadingMode}s of this
     * {@link EventDispatcher}. As an event can be dispatched directly
     * ({@link ThreadingMode#SYNC}) or in the queue processing {@link Thread}
     * ({@link ThreadingMode#ASYNC}), this is the smallest possible {@link Set}.
     */
    private static final Set<ThreadingMode> DEFAULT_SUPPORTED_THREADING_MODES = EnumSet.of( //
            DEFAULT_THREADING_MODE, //
            ThreadingMode.SYNC //
            );

    /**
     * Instance counter used to create unique names for the thread processing the queue.
     */
    private static final AtomicInteger INSTANCE_COUNT = new AtomicInteger();

    private final Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * Queue holding events (including associated information) to be dispatched.
     */
    private final BlockingQueue<Runnable> eventQueue = new LinkedBlockingQueue<>();

    /**
     * EventDispatcher used by queued events for actual dispatching.
     */
    private final EventDispatcher eventDispatcher;

    /**
     * Non-{@code null} {@link ExecutorService} used to process the queued events.
     */
    private final Thread queueLoopThread;

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
                new SynchronousEventDispatcher() //
        );
    }

    /**
     * Prepares a queuing {@code EventDispatcher} instance using an external
     * {@link eventDispatcher} for dispatching of asynchronous tasks.
     *
     * @param eventDispatcher
     *            non-{@code null} {@link EventDispatcher} used to dispatch
     *            events asynchronously
     * @throws NullPointerException
     *             if {@code eventQueue} is {@code null}
     */
    public QueuedEventDispatcher(final EventDispatcher eventDispatcher) {
        this(
                DEFAULT_THREADING_MODE,
                eventDispatcher);
    }

    /**
     * Prepares a queuing threaded {@code EventDispatcher} instance using an external
     * {@link EventDispatcher}.
     *
     * @param defaultThreadingMode
     *            non-{@code null} {@link ThreadingMode} to be used as default
     *            when posting to the bus (unsupported modes used in
     *            {@link DispatchingLambdaBus#post(Object, ThreadingMode)} will be mapped to this
     *            one)
     * @param eventDispatcher
     *            non-{@code null} {@link EventDispatcher} used to dispatch queued events
     * @throws NullPointerException
     *             if any of {@code defaultThreadingMode} or {@code eventDispatcher}
     *             is {@code null}
     * @throws IllegalArgumentException
     *             if {@code defaultThreadingMode} is not supported (not contained
     *             within the calculated supported {@link ThreadingMode}s
     * @see QueuedEventDispatcher#calculateSupportedThreadingModes(EventDispatcher)
     */
    public QueuedEventDispatcher(
            final ThreadingMode defaultThreadingMode,
            final EventDispatcher eventDispatcher
    ) {
        super( //
                Objects.requireNonNull(defaultThreadingMode, "'defaultThreadingMode' must not be null"), //
                calculateSupportedThreadingModes( //
                        Objects.requireNonNull(eventDispatcher, "'eventDispatcher' must not be null")
                ) //
        );

        this.eventDispatcher = eventDispatcher;

        queueLoopThread = createQueueProcessingThread(this::takeEventsFromQueueAndTryToDispatch);
        queueLoopThread.start();
    }

    @Override
    protected void cleanupBeforeClose() {
        eventQueue.clear();
        eventDispatcher.close();
        queueLoopThread.interrupt();
    }

    //##########################################################################
    // Protected helper methods
    //##########################################################################

    /**
     * Dispatching request using {@link ThreadingMode#SYNC}) are already handled
     * by the base class. This method adds a {@link Runnable}-like lambda to
     * the internal queue for further processing, with the runnable holding the
     * event, its {@link Consumer}s and the {@link ThreadingMode} to be used.
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
     * @param eventHandlerCollection
     *            non-{@code null} {@link Collection} of non-{@code null}
     *            {@link Consumer}s registered for the {@link Class} of the
     *            event
     * @param supportedThreadingMode
     *            non-{@code null} {@link ThreadingMode} how the event should be
     *            dispatched
     */
    @Override
    protected final <T> void dispatchEventToHandlerNonSync(
            final T event,
            final Collection<Consumer<T>> eventHandlerCollection,
            final ThreadingMode supportedThreadingMode
    ) {
        // SYNC events have been already been handled by base class

        // for all other modes events are enqueued
        final ThreadingMode mappedThreadingMode;
        if (ThreadingMode.ASYNC == supportedThreadingMode) {
            // mapping ASYNC to SYNC since the "sync" dispatching will be handled
            // from within the queue processing thread
            mappedThreadingMode = ThreadingMode.SYNC;
        } else {
            mappedThreadingMode = supportedThreadingMode;
        }

        final Runnable eventDispatchingRunnable = () -> eventDispatcher.dispatchEventToHandler(
                event,
                eventHandlerCollection,
                mappedThreadingMode);

        enqueueEventForDispatching(eventDispatchingRunnable);
    }

    //##########################################################################
    // Private helper methods
    //##########################################################################

    /**
     * Creates a {@link Set} of {@link ThreadingMode} by combining our
     * {@link #DEFAULT_SUPPORTED_THREADING_MODES}s with the
     * {@link ThreadingMode}s supported by the {@link EventDispatcher}.<br>
     * As an event can be dispatched directly ({@link ThreadingMode#SYNC}) or in
     * the queue processing {@link Thread} ({@link ThreadingMode#ASYNC}), this
     * is the smallest possible {@link Set}.<br>
     * If the {@link EventDispatcher} is using an {@link ExecutorService}
     * additionally
     * <ul>
     * <li>{@link ThreadingMode#ASYNC_PER_EVENT}</li>
     * <li>{@link ThreadingMode#ASYNC_PER_SUBSCRIBER}</li>
     * </ul>
     * might be supported.
     *
     * @param eventDispatcher
     *            {@link EventDispatcher} used to calculate the unified
     *            {@link Set} of {@link ThreadingMode}s
     * @return {@link Set} of {@link ThreadingMode}
     */
    private static Set<ThreadingMode> calculateSupportedThreadingModes(final EventDispatcher eventDispatcher) {
        final Set<ThreadingMode> threadingModes = EnumSet.copyOf(DEFAULT_SUPPORTED_THREADING_MODES);

        eventDispatcher.getSupportedThreadingModes().forEach( //
                threadingMode -> {
                    if (ThreadingMode.SYNC == threadingMode) {
                        // mapping the SYNC mode of the EventDispatcher to ASYNC
                        // since the SYNC operation will be performed in queue
                        // processing thread
                        threadingModes.add(ThreadingMode.ASYNC);
                    } else {
                        threadingModes.add(threadingMode);
                    }
                }
        );

        return Collections.unmodifiableSet(threadingModes);
    }

    /**
     * Creates a daemon {@link Thread} with a unique name (to this class).
     *
     * @param queueProcessingRunnable
     *            {@link Runnable} which will process the internal queue
     * @return setup daemon thread
     */
    private Thread createQueueProcessingThread(final Runnable queueProcessingRunnable) {
        final String threadName= getClass().getSimpleName() + "-" + INSTANCE_COUNT.incrementAndGet();
        final Thread thread = new Thread(queueProcessingRunnable, threadName);
        thread.setDaemon(true);

        return thread;
    }

    /**
     * Adds a {@link Runnable} which encloses the dispatching of an event to the
     * internal queue.
     * 
     * @param eventDispatchingRunnable
     *            a {@link Runnable} which will perform the dispatching
     */
    private void enqueueEventForDispatching(
            final Runnable eventDispatchingRunnable
    ) {
        try {
            eventQueue.put(eventDispatchingRunnable);
        }
        catch (final InterruptedException e) {
            if (!isClosed()) {
                logger.warn("Interrupted while trying to insert event into queue.", e);
            }
            // restore interrupted state
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Waits for events to appear in the queue and tries to dispatch them.
     * <p>
     * Implementation note:<br>
     * As this method is doing the dispatching within the queue processing thread
     * it might delay dispatching base on the behavior of the consumers.
     * </p>
     */
    private void takeEventsFromQueueAndTryToDispatch() {
        while (!isClosed()) {
            try {
                final Runnable eventDispatchingRunnable = eventQueue.take();
                eventDispatchingRunnable.run();
            } catch (final InterruptedException e) {
                if (!isClosed()) {
                    logger.warn("Interrupted while waiting for queued event.");
                }
                // restore interrupted state
                Thread.currentThread().interrupt();
            }
        }
        if (!eventQueue.isEmpty()) {
            logger.warn("{} stopped. Events still queued: {}", getClass().getSimpleName(), eventQueue);
        }
    }

}
