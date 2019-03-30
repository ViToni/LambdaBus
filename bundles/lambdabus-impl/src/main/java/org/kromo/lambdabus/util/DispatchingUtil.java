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
package org.kromo.lambdabus.util;

import java.util.Collection;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.kromo.lambdabus.LambdaBus;

/**
 * Helper class which consolidates dispatching logic so that it can be reused by different
 * {@link LambdaBus} implementations.
 *
 * @author Victor Toni - initial API and implementation
 *
 */
public final class DispatchingUtil {

    private static final DispatchingUtil INSTANCE = new DispatchingUtil();

    private final Logger defaultLogger = LoggerFactory.getLogger(getClass());

    private DispatchingUtil() {
        /* no public instance */
    }

    /**
     * Dispatches an event to a {@link Consumer} while catching any exception if
     * thrown, {@link Error}s are not caught.
     *
     * @param <T>
     *            type of event
     * @param event
     *            non-{@code null} object to dispatch
     * @param eventSubscriber
     *            non-{@code null} {@link Consumer} for objects of type
     *            {@code T} to which the event should be dispatched
     * @param logger
     *            to be used in case of an {@link Exception}
     * @throws Error
     *             if the eventSubscriber throws it while handling the event,
     *             {@link Exception}s are caught and logged
     */
    private final <T> void internalDispatchEventSafely(
            final T event,
            final Consumer<? super T> eventSubscriber,
            final Logger logger
    ) {
        try {
            eventSubscriber.accept(event);
        }
        catch (final Exception e) {
            /*
             * Since we are using lambdas and threads the stack-trace might not be very
             * useful. We reduce the stack-trace to the place were the exception occurred.
             */
            final StackTraceElement[] reducedStackTrace = {
                    e.getStackTrace()[0]
            };
            e.setStackTrace(reducedStackTrace);
            logger.warn("Exception while dispatching event '{}'", event, e);
        }
    }

    /**
     * Dispatches an event to a {@link Consumer} while catching any exception if
     * thrown, {@link Error}s are not caught.
     *
     * @param <T>
     *            type of event
     * @param event
     *            non-{@code null} object to dispatch
     * @param eventSubscriber
     *            non-{@code null} {@link Consumer} for objects of type {@code T} to
     *            which the event should be dispatched
     * @throws Error
     *             if the eventSubscriber throws it while handling the event,
     *             {@link Exception}s are caught and logged
     */
    private final <T> void internalDispatchEventSafely(
            final T event,
            final Consumer<? super T> eventSubscriber
    ) {
        internalDispatchEventSafely(
                event,
                eventSubscriber,
                defaultLogger);
    }

    /**
     * Dispatches an event to a {@link Collection} of matching {@link Consumer}
     * while catching potential exceptions.
     *
     * @param <T>
     *            type of event
     * @param event
     *            non-{@code null} object to dispatch
     * @param eventSubscriberCollection
     *            non-empty {@link Collection} of non-{@code null}
     *            {@link Consumer} of type {@code T} to which the event should
     *            be dispatched
     * @param logger
     *            to be used in case of an {@link Exception}
     * @throws Error
     *             if any of eventSubscriber in the {@link Collection} throws it
     *             while handling the event, {@link Exception}s are caught and
     *             logged
     */
    private final <T> void internalDispatchEventToSubscriber(
            final T event,
            final Collection<Consumer<T>> eventSubscriberCollection,
            final Logger logger
    ) {
        for (final Consumer<? super T> eventSubscriber : eventSubscriberCollection) {
            internalDispatchEventSafely(
                    event,
                    eventSubscriber,
                    logger);
        }
    }

    /**
     * Dispatches an event to a {@link Collection} of matching {@link Consumer}
     * while catching potential exceptions.
     *
     * @param <T>
     *            type of event
     * @param event
     *            non-{@code null} object to dispatch
     * @param eventSubscriberCollection
     *            non-empty {@link Collection} of non-{@code null}
     *            {@link Consumer} of type {@code T} to which the event should
     *            be dispatched
     * @throws Error
     *             if any of eventSubscriber in the {@link Collection} throws it
     *             while handling the event, {@link Exception}s are caught and
     *             logged
     */
    private final <T> void internalDispatchEventToSubscriber(
            final T event,
            final Collection<Consumer<T>> eventSubscriberCollection
    ) {
        internalDispatchEventToSubscriber(
                event,
                eventSubscriberCollection,
                defaultLogger);
    }

    /**
     * Dispatches an event to a {@link Collection} of matching {@link Consumer}
     * while catching potential exceptions. Dispatching is done in one
     * {@link Thread} per event.
     *
     * @param <T>
     *            type of event
     *
     * @param event
     *            non-{@code null} object to dispatch
     *
     * @param eventSubscriberCollection
     *            non-empty {@link Collection} of non-{@code null}
     *            {@link Consumer} of type {@code T} to which the event should
     *            be dispatched
     *
     * @param executor
     *            {@link Executor} which will be used to execute the dispatching
     *            tasks
     *
     * @param logger
     *            to be used in case of an {@link Exception}
     */
    private final <T> void internalDispatchEventToSubscriberThreadedPerEvent(
            final T event,
            final Collection<Consumer<T>> eventSubscriberCollection,
            final Executor executor,
            final Logger logger
    ) {
        /**
         * Create one task for dispatching the event.
         */
        final Runnable dispatchingTask =
                () -> internalDispatchEventToSubscriber(
                        event,
                        eventSubscriberCollection,
                        logger);

        try {
            /**
             * Execute the dispatching task per event in its own thread.
             */
            executor.execute(dispatchingTask);
        } catch (final RejectedExecutionException e) {
            logger.error("Failed to execute dispatching task for event: {}", event, e);
        }
    }

    /**
     * Dispatches an event to a {@link Collection} of matching {@link Consumer}
     * while catching potential exceptions. Dispatching is done in one
     * {@link Thread} per event.
     *
     * @param <T>
     *            type of event
     * @param event
     *            non-{@code null} object to dispatch
     * @param eventSubscriberCollection
     *            non-empty {@link Collection} of non-{@code null}
     *            {@link Consumer} of type {@code T} to which the event should
     *            be dispatched
     * @param executor
     *            {@link Executor} which will be used to execute the dispatching
     *            tasks
     */
    private final <T> void internalDispatchEventToSubscriberThreadedPerEvent(
            final T event,
            final Collection<Consumer<T>> eventSubscriberCollection,
            final Executor executor
    ) {
        internalDispatchEventToSubscriberThreadedPerEvent(
                event,
                eventSubscriberCollection,
                executor,
                defaultLogger);
    }

    /**
     * Dispatches an event to a {@link Collection} of matching {@link Consumer}
     * while catching potential exceptions. Dispatching is done in one
     * {@link Thread} per subscriber.
     *
     * @param <T>
     *            type of event
     * @param event
     *            non-{@code null} object to dispatch
     * @param eventSubscriberCollection
     *            non-empty {@link Collection} of non-{@code null}
     *            {@link Consumer} of type {@code T} to which the event should
     *            be dispatched
     * @param executor
     *            {@link Executor} which will be used to execute the dispatching
     *            tasks
     * @param logger
     *            to be used in case of an {@link Exception}
     */
    private final <T> void internalDispatchEventToSubscriberThreadedPerSubscriber(
            final T event,
            final Collection<Consumer<T>> eventSubscriberCollection,
            final Executor executor,
            final Logger logger
    ) {
        for (final Consumer<T> eventSubscriber : eventSubscriberCollection) {
            /**
             * Create a task for each subscriber to be informed so that long
             * running subscriber processes don't block others.
             */
            final Runnable dispatchingTask = () -> internalDispatchEventSafely(event, eventSubscriber, logger);
            try {
                /**
                 * Execute the dispatching task per subscriber in its own
                 * thread.
                 */
                executor.execute(dispatchingTask);
            } catch (final RejectedExecutionException e) {
                logger.error("Failed to execute subscriber dispatching task for event: {}", event, e);
            }
        }
    }

    /**
     * Dispatches an event to a {@link Collection} of matching {@link Consumer}
     * while catching potential exceptions. Dispatching is done in one
     * {@link Thread} per subscriber.
     *
     * @param <T>
     *            type of event
     * @param event
     *            non-{@code null} object to dispatch
     * @param eventSubscriberCollection
     *            non-empty {@link Collection} of non-{@code null}
     *            {@link Consumer} of type {@code T} to which the event should
     *            be dispatched
     * @param executor
     *            {@link Executor} which will be used to execute the dispatching
     *            tasks
     */
    private final <T> void internalDispatchEventToSubscriberThreadedPerSubscriber(
            final T event,
            final Collection<Consumer<T>> eventSubscriberCollection,
            final Executor executor
    ) {
        internalDispatchEventToSubscriberThreadedPerSubscriber(
                event,
                eventSubscriberCollection,
                executor,
                defaultLogger);
    }

    //##########################################################################
    // Statically exposed methods
    //##########################################################################

    /**
     * Dispatches an event to a {@link Consumer} while catching any exception if
     * thrown, {@link Error}s are not caught.
     *
     * @param <T>
     *            type of event
     * @param event
     *            non-{@code null} object to dispatch
     * @param eventSubscriber
     *            non-{@code null} {@link Consumer} for objects of type {@code T} to
     *            which the event should be dispatched
     * @param logger
     *            to be used in case of an {@link Exception}
     * @throws Error
     *             if the eventSubscriber throws it while handling the event,
     *             {@link Exception}s are caught and logged
     */
    public static <T> void dispatchEventSafely(
            final T event,
            final Consumer<T> eventSubscriber,
            final Logger logger
   ) {
        INSTANCE.internalDispatchEventSafely(
                event,
                eventSubscriber,
                logger);
    }

    /**
     * Dispatches an event to a {@link Consumer} while catching any exception if
     * thrown, {@link Error}s are not caught.
     *
     * @param <T>
     *            type of event
     * @param event
     *            non-{@code null} object to dispatch
     * @param eventSubscriber
     *            non-{@code null} {@link Consumer} for objects of type {@code T} to
     *            which the event should be dispatched
     * @throws Error
     *             if the eventSubscriber throws it while handling the event,
     *             {@link Exception}s are caught and logged
     */
    public static <T> void dispatchEventSafely(
            final T event,
            final Consumer<T> eventSubscriber
    ) {
        INSTANCE.internalDispatchEventSafely(
                event,
                eventSubscriber);
    }

    /**
     * Dispatches an event to a {@link Collection} of matching {@link Consumer}s
     * while catching potential exceptions.
     *
     * @param <T>
     *            type of event
     * @param event
     *            non-{@code null} object to dispatch
     * @param eventSubscriberCollection
     *            non-empty {@link Collection} of non-{@code null}
     *            {@link Consumer} of type {@code T} to which the event should
     *            be dispatched
     * @param logger
     *            to be used in case of an {@link Exception}
     * @throws Error
     *             if any of eventSubscriber in the {@link Collection} throws it
     *             while handling the event, {@link Exception}s are caught and
     *             logged
     */
    public static <T> void dispatchEventToSubscriber(
            final T event,
            final Collection<Consumer<T>> eventSubscriberCollection,
            final Logger logger
    ) {
        INSTANCE.internalDispatchEventToSubscriber(
                event,
                eventSubscriberCollection,
                logger);
    }

    /**
     * Dispatches an event to a {@link Collection} of matching {@link Consumer}s
     * while catching potential exceptions.
     *
     * @param <T>
     *            type of event
     * @param event
     *            non-{@code null} object to dispatch
     * @param eventSubscriberCollection
     *            non-empty {@link Collection} of non-{@code null}
     *            {@link Consumer} of type {@code T} to which the event should
     *            be dispatched
     * @throws Error
     *             if any of eventSubscriber in the {@link Collection} throws it
     *             while handling the event, {@link Exception}s are caught and
     *             logged
     */
    public static <T> void dispatchEventToSubscriber(
            final T event,
            final Collection<Consumer<T>> eventSubscriberCollection
    ) {
        INSTANCE.internalDispatchEventToSubscriber(
                event,
                eventSubscriberCollection);
    }

    /**
     * Dispatches an event to a {@link Collection} of matching {@link Consumer}
     * while catching potential exceptions.
     *
     * @param <T>
     *            type of event
     * @param event
     *            non-{@code null} object to dispatch
     * @param eventSubscriberCollection
     *            non-empty {@link Collection} of non-{@code null}
     *            {@link Consumer} of type {@code T} to which the event should
     *            be dispatched
     * @param executor
     *            {@link Executor} which will be used to execute the dispatching
     *            task
     * @param logger
     *            to be used in case of an {@link Exception}
     */
    public static <T> void dispatchEventToSubscriberThreadedPerEvent(
            final T event,
            final Collection<Consumer<T>> eventSubscriberCollection,
            final Executor executor,
            final Logger logger
    ) {
        INSTANCE.internalDispatchEventToSubscriberThreadedPerEvent(
                event,
                eventSubscriberCollection,
                executor,
                logger);
    }

    /**
     * Dispatches an event to a {@link Collection} of matching {@link Consumer}
     * while catching potential exceptions.
     *
     * @param <T>
     *            type of event
     * @param event
     *            non-{@code null} object to dispatch
     * @param eventSubscriberCollection
     *            non-empty {@link Collection} of non-{@code null}
     *            {@link Consumer} of type {@code T} to which the event should
     *            be dispatched
     * @param executor
     *            {@link Executor} which will be used to execute the dispatching
     *            task
     */
    public static <T> void dispatchEventToSubscriberThreadedPerEvent(
            final T event,
            final Collection<Consumer<T>> eventSubscriberCollection,
            final Executor executor
    ) {
        INSTANCE.internalDispatchEventToSubscriberThreadedPerEvent(
                event,
                eventSubscriberCollection,
                executor);
    }

    /**
     * Dispatches an event to a {@link Collection} of matching {@link Consumer}
     * while catching potential exceptions.
     *
     * @param <T>
     *            type of event
     * @param event
     *            non-{@code null} object to dispatch
     * @param eventSubscriberCollection
     *            non-empty {@link Collection} of non-{@code null}
     *            {@link Consumer} of type {@code T} to which the event should
     *            be dispatched
     * @param executor
     *            {@link Executor} which will be used to execute the dispatching
     *            tasks
     * @param logger
     *            to be used in case of an {@link Exception}
     */
    public static <T> void dispatchEventToSubscriberThreadedPerSubscriber(
            final T event,
            final Collection<Consumer<T>> eventSubscriberCollection,
            final Executor executor,
            final Logger logger
    ) {
        INSTANCE.internalDispatchEventToSubscriberThreadedPerSubscriber(
                event,
                eventSubscriberCollection,
                executor,
                logger
        );
    }

    /**
     * Dispatches an event to a {@link Collection} of matching {@link Consumer}
     * while catching potential exceptions.
     *
     * @param <T>
     *            type of event
     * @param event
     *            non-{@code null} object to dispatch
     * @param eventSubscriberCollection
     *            non-empty {@link Collection} of non-{@code null}
     *            {@link Consumer} of type {@code T} to which the event should
     *            be dispatched
     * @param executor
     *            {@link Executor} which will be used to execute the dispatching
     *            tasks
     */
    public static <T> void dispatchEventToSubscriberThreadedPerSubscriber(
            final T event,
            final Collection<Consumer<T>> eventSubscriberCollection,
            final Executor executor
    ) {
        INSTANCE.internalDispatchEventToSubscriberThreadedPerSubscriber(
                event,
                eventSubscriberCollection,
                executor
        );
    }

}
