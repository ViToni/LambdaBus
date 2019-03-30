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
package org.kromo.lambdabus.queue;

import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

import org.kromo.lambdabus.ThreadingMode;

/**
 * Implementations of this interface can queue events and dispatch them asynchronously.<br>
 * Events are added into an internal queue as {@link QueuedEvent}s and dispatched in the
 * queue-processing thread or using an {@link ExecutorService}.<br>
 * As {@link QueuedEvent}s contain the event itself and its registered consumers (at the time the
 * event has been published) implementations might be shared among event-bus instances to reduce the
 * number of needed {@link Thread}s. Blocking consumer might block all event-busses though.
 *
 * @author Victor Toni - initial API
 *
 */
public interface EventQueue extends AutoCloseable {

    /**
     * Gets the supported {@link ThreadingMode}s of the event queue.
     *
     * @return {@link Set} of supported {@link ThreadingMode}s
     */
    Set<ThreadingMode> getSupportedThreadingModes();

    /**
     * Adds an event for asynchronous dispatching.
     *
     * @param <T>
     *            type of event to be dispatched
     * @param queuedEvent
     *            event to be dispatched and associated information
     * @throws NullPointerException
     *             if the {@code queuedEvent} is {@code null}
     * @throws IllegalStateException
     *             if the queue is already closed
     */
    <T> void add(final QueuedEvent<T> queuedEvent);

    /**
     * Closes the queue and does cleanup of internal {@link Executor} (if any).<br>
     * This method is expected to be idempotent.
     */
    @Override
    void close();

    /**
     * Gets the closed state of the event queue.
     *
     * @return returns {@code true} if {@link #close()} has been called yet, {@code false} otherwise
     */
    boolean isClosed();

}
