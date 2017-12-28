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
package org.kromo.lambdabus.impl.opt;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.kromo.lambdabus.ThreadingMode;
import org.kromo.lambdabus.impl.concurrent.DaemonThreadFactory;
import org.kromo.lambdabus.impl.concurrent.DaemonThreadPoolExecutor;
import org.kromo.lambdabus.queue.QueuedEvent;
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
public class QueuedLambdaBus
    extends AbstractThreadedLambdaBus {

    private static final ThreadingMode DEFAULT_THREADING_MODE = ThreadingMode.ASYNC;
    private static final EnumSet<ThreadingMode> SUPPORTED_THREADING_MODES = EnumSet.allOf(ThreadingMode.class);

    private static final AtomicInteger INSTANCE_COUNT = new AtomicInteger();

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final BlockingQueue<QueuedEvent<?>> eventQueue;

    private final ExecutorService queueExecutorService;
    private final Future<?> queueProcessingFuture;

    /**
     * Prepares a queuing threaded {@code LambdaBus} instance.
     */
    public QueuedLambdaBus() {
        this(DEFAULT_THREADING_MODE);
    }

    /**
     * Prepares a queuing threaded {@code EventDispatcher} instance.
     * 
     * @param defaultThreadingMode
     *            non-{@code null} {@link ThreadingMode} to be used as default
     *            when posting to the bus (unsupported modes used in 
     *            {@link #post(Object, ThreadingMode)} will be mapped to this
     *            one)
     * @throws IllegalArgumentException
     *             if {@code defaultThreadingMode} is not supported (not contained
     *             within {@link #SUPPORTED_THREADING_MODES}
     * @throws NullPointerException
     *             if {@code defaultThreadingMode} is {@code null}
     */
    public QueuedLambdaBus(final ThreadingMode defaultThreadingMode) {
        this(
                Objects.requireNonNull(defaultThreadingMode, "'defaultThreadingMode' must not be null"),
                new DaemonThreadPoolExecutor(
                        new LinkedBlockingQueue<>()
                )
        );
    }

    /**
     * Prepares a queuing threaded {@code LambdaBus} instance.
     * 
     * @param executorService
     *            non-{@code null} {@link ExecutorService} used to execute the
     *            dispatching jobs
     * @throws NullPointerException
     *             if {@code executorService} is {@code null}
     */
    public QueuedLambdaBus(final ExecutorService executorService) {
        this(
                DEFAULT_THREADING_MODE,
                executorService);
    }

    /**
     * Prepares a queuing threaded {@code LambdaBus} instance.
     * 
     * @param defaultThreadingMode
     *            non-{@code null} {@link ThreadingMode} to be used as default
     *            when posting to the bus (unsupported modes used in 
     *            {@link #post(Object, ThreadingMode)} will be mapped to this
     *            one)
     * @param executorService
     *            non-{@code null} {@link ExecutorService} used to execute the
     *            dispatching jobs
     * @throws NullPointerException
     *             if any of {@code defaultThreadingMode} or {@code executorService}
     *             is {@code null}
     * @throws IllegalArgumentException
     *             if {@code defaultThreadingMode} is not supported (not contained
     *             within {@link #SUPPORTED_THREADING_MODES}
     */
    public QueuedLambdaBus(
            final ThreadingMode defaultThreadingMode,
            final ExecutorService executorService
    ) {
        super(
                Objects.requireNonNull(defaultThreadingMode, "'defaultThreadingMode' must not be null"),
                SUPPORTED_THREADING_MODES,
                Objects.requireNonNull(executorService, "'executorService' must not be null")
        );

        eventQueue = Objects.requireNonNull(
                createBlockingQueue(),
                "createdBlockingQueue() must not return null");

        final String threadFactoryName= getClass().getSimpleName() + "-" + INSTANCE_COUNT.incrementAndGet();
        final ThreadFactory threadFactory = new DaemonThreadFactory(threadFactoryName);

        queueExecutorService = Executors.newSingleThreadExecutor(threadFactory);
        queueProcessingFuture = queueExecutorService.submit((Runnable) this::takeEventsFromQueueAndTryToDispatch);
    }

    @Override
    protected void preExecutorShutdownHook() {
        if(!queueExecutorService.isShutdown()) {
            final boolean mayInteruptIfRunning = true;
            queueProcessingFuture.cancel(mayInteruptIfRunning);
            queueExecutorService.shutdownNow();
        }
    }

    @Override
    protected <T> void acceptNonNullEvent(
            final T event,
            final ThreadingMode supportedThreadingMode
    ) {
        tryToDispatchNonNullEvent(event, supportedThreadingMode);
    }

    //##########################################################################
    // Protected helper methods
    //##########################################################################

    /**
     * Either dispatches the given event directly (in case of
     * {@link ThreadingMode#SYNC}) or adds the event, its subscribed
     * {@link Consumer}s and the {@link ThreadingMode} to the internal queue for
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
    protected final <T> void dispatchNonNullEventToSubscriber(
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
            enqueuNonNullEventForDispatching(
                    event,
                    eventSubscriberCollection,
                    supportedThreadingMode);
        }
    }

    /**
     * Adds event, its subscribed {@link Consumer}s and the
     * {@link ThreadingMode} to internal queue for further processing.
     * 
     * <p>
     * All parameters are {@code null} because the calling method has checked
     * them already.
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
     *            how should the event be dispatched
     */
    private <T> void enqueuNonNullEventForDispatching(
            final T event,
            final Collection<Consumer<T>> eventSubscriberCollection,
            final ThreadingMode supportedThreadingMode
    ) {
        final QueuedEvent<T> qEvent = new QueuedEvent<>(
                event,
                eventSubscriberCollection,
                supportedThreadingMode
        );
        try {
            eventQueue.put(qEvent);
        } catch (final InterruptedException e) {
            if (!isClosed()) {
                logger.warn("Interrupted while trying to insert event into queue. Event might be lost: {}", event);
            }
            // restore interrupted state
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Waits for events to appear in the queue and tries to dispatch them.
     */
    private final <T> void takeEventsFromQueueAndTryToDispatch() {
        while (!isClosed()) {
            try {
                @SuppressWarnings("unchecked")
                final QueuedEvent<T> qEvent = (QueuedEvent<T>) eventQueue.take();
                dispatchEventToSubscriber(
                        qEvent.event,
                        qEvent.eventSubscriberCollection,
                        qEvent.threadingMode
                );
            } catch (final InterruptedException e) {
                if (!isClosed()) {
                    logger.warn("Interrupted while trying to get event from queue.");
                }
                // restore interrupted state
                Thread.currentThread().interrupt();
            }
        }
        if (!eventQueue.isEmpty()) {
            logger.warn("Stopped with events still in queue: {}", eventQueue);
        }
    }

    //##########################################################################
    // Methods which can be overridden to customize behavior
    //##########################################################################

    /**
     * Creates a new {@link BlockingQueue} to pass events from the main thread
     * to internal worker.
     * <p>
     * Note:<br>
     * This method is abstract on purpose so the implementing party makes a
     * choice fitting its dispatching strategy.
     * </p>
     * 
     * @param <E>
     *            the type of elements held in this {@link BlockingQueue}
     * @return {@link BlockingQueue}
     */
    protected <E> BlockingQueue<E> createBlockingQueue() {
        /*
         * Using the LinkedBlockingQueue works as a buffer for high load peaks.
         * Alternatively one could use a SynchronousQueue for smaller workloads
         * which might prove to be a bit more responsive.
         */
        return new LinkedBlockingQueue<>();
    }

    /**
     * Dispatches queued event to subscribed {@link Consumer}s.
     * 
     * @param <T>
     *            type of posted event
     * @param event
     *            non-{@code null} object
     * @param eventSubscriberCollection
     *            {@link Collection} of {@link Consumer}s registered for the
     *            {@link Class} of the event
     * @param supportedThreadingMode
     *            how the event should be dispatched
     */
    protected <T> void dispatchEventToSubscriber(
        final T event,
        final Collection<Consumer<T>> eventSubscriberCollection,
        final ThreadingMode supportedThreadingMode
    ) {
        switch (supportedThreadingMode) {
            case ASYNC_PER_SUBSCRIBER:
                DispatchingUtil.dispatchEventToSubscriberThreadedPerSubscriber(
                        event,
                        eventSubscriberCollection,
                        getExecutor());
                return;
            case ASYNC_PER_EVENT:
                DispatchingUtil.dispatchEventToSubscriberThreadedPerEvent(
                        event,
                        eventSubscriberCollection,
                        getExecutor());
                return;
            default:
                DispatchingUtil.dispatchEventToSubscriber(
                        event,
                        eventSubscriberCollection);
                return;
        }
    }

}
