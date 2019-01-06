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

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.kromo.lambdabus.ThreadingMode;

/**
 * Implementations of this interface can queue events and dispatch them asynchronously.<br>
 * Events are added into an internal queue as {@link QueuedEvent}s and dispatched in the
 * queue-processing thread or using an {@link ExecutorService}.<br>
 * As {@link QueuedEvent}s contain the event itself and its registered consumers (at the time the
 * event has been published) this queue can be shared among event-bus instances to reduce the number
 * of needed {@link Thread}s. Blocking consumer might block all event-busses though.
 * 
 * @author Victor Toni - initial implementation
 *
 */
public class NonClosingEventQueue
    implements EventQueue {

    private final Logger logger = LoggerFactory.getLogger(NonClosingEventQueue.class);
    
    private final EventQueue eventQueue;
    
    private final AtomicBoolean isClosed;
    
    public NonClosingEventQueue(final EventQueue eventQueue) {    
        this.eventQueue = Objects.requireNonNull(eventQueue, "'eventQueue' must not be null");
        this.isClosed = new AtomicBoolean(eventQueue.isClosed());
    }

    @Override
    public Set<ThreadingMode> getSupportedThreadingModes() {
        return eventQueue.getSupportedThreadingModes();
    }

    @Override
    public <T> void add(final QueuedEvent<T> queuedEvent) {
        eventQueue.add(queuedEvent);
    }

    @Override
    public void close() {
        if (isClosed.compareAndSet(false, true)) {
            logger.debug("Called close() - call will not be delegated to: {}", eventQueue.getClass().getSimpleName());
        }
    }

    /**
     * Gets the closed state of the event queue.
     * 
     * @return returns {@code true} if {@link #close()} has been called yet, {@code false} otherwise
     */
    @Override
    public boolean isClosed() {
        if (eventQueue.isClosed() && isClosed.compareAndSet(false, true)) {
            logger.debug("Closing because of closed internal EventQueue: {}", eventQueue.getClass().getSimpleName());
        }

        return isClosed.get();
    }

}
