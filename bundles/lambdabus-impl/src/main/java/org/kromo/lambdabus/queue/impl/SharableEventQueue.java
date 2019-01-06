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
 *     Victor Toni - initial implementation
 *******************************************************************************/
package org.kromo.lambdabus.queue.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.kromo.lambdabus.ThreadingMode;
import org.kromo.lambdabus.impl.concurrent.DaemonThreadFactory;
import org.kromo.lambdabus.queue.EventQueue;
import org.kromo.lambdabus.queue.QueuedEvent;
import org.kromo.lambdabus.util.DispatchingUtil;

/**
 * This class provides a queue for events which will be dispatched asynchronously.<br>
 * Events are put into an internal queue as {@link QueuedEvent}s and dispatched in the
 * queue-processing thread or if an {@link ExecutorService} is provided in dedicated {@link Thread}s.<br>
 * As {@link QueuedEvent}s contain the event itself and its consumers (at the time the event has
 * been published) this queue can be shared among low-throughput event-bus instances, blocking
 * consumer might block all event-busses though.
 * 
 * @author Victor Toni - initial implementation
 *
 */
public class SharableEventQueue
    implements EventQueue, AutoCloseable {

    /**
     * Instance counter used to create unique names for the thread processing the queue.
     */
    private static final AtomicInteger INSTANCE_COUNT = new AtomicInteger();

    private final Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * Queue holding events (including associated information) to be dispatched. 
     */
    private final BlockingQueue<QueuedEvent<?>> eventQueue = new LinkedBlockingQueue<>();

    /**
     * Non-{@code null} {@link ExecutorService} used to process the queued events.
     */
    private final ExecutorService queueExecutorService;

    /**
     * Flag indicating whether the queue has been is closed.
     */
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /**
     * {@link Optional} {@link ExecutorService} for threaded dispatching.
     */
    private final Optional<ExecutorService> optionalDispatchingExecutorService;

    /**
     * Per default only {@link ThreadingMode#ASYNC} is supported. If an external {@link ExecutorService}
     * is provided the {@code ThreadingMode}s {@link ThreadingMode#ASYNC_PER_EVENT} and
     * {@link ThreadingMode#ASYNC_PER_SUBSCRIBER} are also supported.
     */
    private Set<ThreadingMode> supportedThreadingModes;

    /**
     * Prepares a {@code SharableDispatchingEventQueue} instance which dispatches events in the queue
     * processing thread.
     */
    public SharableEventQueue() {
        this(Optional.empty());
    }

    /**
     * Prepares a {@code SharableDispatchingEventQueue} instance which can dispatch events in the queue
     * processing thread.
     * 
     * @param dispatchingExecutorService
     *            non-{@code null} {@link ExecutorService} used to execute the dispatching jobs
     * @throws NullPointerException
     *             if {@code dispatchingExecutorService} is {@code null}
     * @throws IllegalStateException
     *             if {@code dispatchingExecutorService} is already {@code shutdown}
     */
    public SharableEventQueue(
            final ExecutorService dispatchingExecutorService
    ) {
        this( //
                Optional.of( //
                        Objects.requireNonNull(dispatchingExecutorService, "'dispatchingExecutorService' must not be null") //
                ) //
        );
    }

    /**
     * Prepares a threaded {@code EventDispatcher} instance.
     * 
     * @param optionalDispatchingExecutorService
     *            non-{@code null} {@link ExecutorService} used to execute the dispatching jobs
     */
    protected SharableEventQueue(
            final Optional<ExecutorService> optionalDispatchingExecutorService
    ) {
        this.optionalDispatchingExecutorService = Objects.requireNonNull(optionalDispatchingExecutorService, "'optionalDispatchingExecutorService' must not be null");

        if (optionalDispatchingExecutorService.isPresent() && optionalDispatchingExecutorService.get().isShutdown()) {
            throw new IllegalStateException("'dispatchingExecutorService' must not be shutdown");            
        }

        queueExecutorService = createDedicatedSingleThreadedExecutor();
        queueExecutorService.execute(this::takeEventsFromQueueAndTryToDispatch);
        
        final List<ThreadingMode> threadingModes = new ArrayList<>();
        
        // dispatching in the queue processing loop is ASYNC
        threadingModes.add(ThreadingMode.ASYNC);

        // if there is an external ExecutorService we can spawn threads for dispatching
        // allowing more than just ASYNC in the queue processing thread
        if (optionalDispatchingExecutorService.isPresent()) {
            threadingModes.add(ThreadingMode.ASYNC_PER_EVENT);
            threadingModes.add(ThreadingMode.ASYNC_PER_SUBSCRIBER);
        }
        
        supportedThreadingModes = Collections.unmodifiableSet( //
                EnumSet.copyOf(threadingModes)
        );
    }

    @Override
    public final void close() {
        // mark the queue as closed to stop events to be accepted
        if(closed.compareAndSet(false, true)) {
            // shutdown queue executor first to stop processing of events still queued
            queueExecutorService.shutdownNow();
            optionalDispatchingExecutorService.ifPresent(ExecutorService::shutdownNow);
        }
    }

    /**
     * Gets the closed state of the event dispatcher.
     * 
     * @return returns {@code true} if {@link #close()} has been called,
     *         {@code false} otherwise
     */
    @Override
    public final boolean isClosed() {
        return closed.get();
    }

    @Override
    public final <T> void add(final QueuedEvent<T> queuedEvent) {
        if (isClosed()) {
            throw new IllegalStateException("Queue is closed. Event not queued: " + queuedEvent);
        }
        try {
            eventQueue.put(queuedEvent);
        }
        catch (final InterruptedException e) {
            if (!isClosed()) {
                logger.warn("Interrupted while trying to insert event into queue. Event might be lost: {}", queuedEvent);
            }
            // restore interrupted state
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Gets the supported {@link ThreadingMode}s of the event queue.
     * 
     * @return {@link Set} of supported {@link ThreadingMode}s
     */
    public final Set<ThreadingMode> getSupportedThreadingModes() {
        return supportedThreadingModes;
    }

    //##########################################################################
    // Private helper methods
    //##########################################################################

    /**
     * Waits for events to appear in the queue and tries to dispatch them.
     */
    private final <T> void takeEventsFromQueueAndTryToDispatch() {
        while (!isClosed()) {
            try {
                final QueuedEvent<?> queuedEvent = eventQueue.take();
                dispatchQueuedEventToSubscriber(queuedEvent);
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

    /**
     * Dispatch queued event to subscribed {@link Consumer}s.
     * 
     * <p>
     * Implementation note:<br>
     * As this method is doing the dispatching within the queue processing thread
     * it might delay dispatching base on the behavior of the consumers.
     * <p>
     * 
     * @param <T>
     *            type of posted event
     * @param queuedEvent
     *            {@code non-null} containing the actual event, the subscriber
     *            collection. (The {@link ThreadingMode} is not used since we support only {@link ThreadingMode#ASYNC}.)
     */
    private final <T> void dispatchQueuedEventToSubscriber(
            final QueuedEvent<T> queuedEvent
    ) {
        // unsupported ThreadingModes have been filtered out before we get here
        // consumers of this EventQueue are restricted to ThreadingModes of this instance.
        // if the instance does not have an additional ExecutorServicethe the additional
        // ThreadingModes won't be available and only the default case will be used.
        switch(queuedEvent.threadingMode) {
            case ASYNC_PER_EVENT:
                DispatchingUtil.dispatchEventToSubscriberThreadedPerEvent(
                        queuedEvent.event,
                        queuedEvent.eventSubscriberCollection,
                        optionalDispatchingExecutorService.get());
                return;

            case ASYNC_PER_SUBSCRIBER:
                DispatchingUtil.dispatchEventToSubscriberThreadedPerSubscriber(
                        queuedEvent.event,
                        queuedEvent.eventSubscriberCollection,
                        optionalDispatchingExecutorService.get());
                return;

            default:
                // none of the additional ThreadingModes was used / is available
                DispatchingUtil.dispatchEventToSubscriber(
                        queuedEvent.event,
                        queuedEvent.eventSubscriberCollection);
        }
    }

    /**
     * Creates an {@link ExecutorService} which provides a daemon thread with an unique name (to this
     * class).
     * 
     * @return setup daemon thread {@link ExecutorService}
     */
    private final ExecutorService createDedicatedSingleThreadedExecutor() {
        final String threadFactoryName= getClass().getSimpleName() + "-" + INSTANCE_COUNT.incrementAndGet();
        final ThreadFactory threadFactory = new DaemonThreadFactory(threadFactoryName);

        return Executors.unconfigurableExecutorService( //
                Executors.newSingleThreadExecutor(threadFactory) //
        );
    }

}
