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
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.kromo.lambdabus.ThreadingMode;
import org.kromo.lambdabus.dispatcher.EventDispatcher;

/**
 * Decorator class for shared {@link EventDispatcher} to prevent closure.
 *
 * @author Victor Toni - initial implementation
 *
 */
public class NonClosingEventDispatcher
    implements EventDispatcher {

    private final Logger logger = LoggerFactory.getLogger(NonClosingEventDispatcher.class);

    private final EventDispatcher eventDispatcher;

    private final AtomicBoolean isClosed;

    public NonClosingEventDispatcher(final EventDispatcher eventDispatcher) {
        this.eventDispatcher = Objects.requireNonNull(eventDispatcher, "'eventDispatcher' must not be null");

        this.isClosed = new AtomicBoolean(eventDispatcher.isClosed());
    }

    @Override
    public <T> void dispatchEventToHandler(
            final T event,
            final Collection<Consumer<T>> eventHandlerCollection,
            final ThreadingMode supportedThreadingMode
    ) {
        if (isClosed()) {
            throw new IllegalStateException(getClass().getSimpleName() + " is closed. Event not dispatched: " + event);
        }

        eventDispatcher.dispatchEventToHandler(
                event,
                eventHandlerCollection,
                supportedThreadingMode);
    }

    @Override
    public ThreadingMode getDefaultThreadingMode() {
        return eventDispatcher.getDefaultThreadingMode();
    }

    @Override
    public Set<ThreadingMode> getSupportedThreadingModes() {
        return eventDispatcher.getSupportedThreadingModes();
    }

    @Override
    public void close() {
        if (isClosed.compareAndSet(false, true)) {
            logger.debug("Called close() - call will not be delegated to: {}", eventDispatcher.getClass().getSimpleName());
        }
    }

    /**
     * Gets the close state of the event dispatcher.
     *
     * @return returns {@code true} if {@link #close()} has been called yet, {@code false} otherwise
     */
    @Override
    public boolean isClosed() {
        // check if the event dispatcher we are delegating to, is already closed
        if (eventDispatcher.isClosed() && isClosed.compareAndSet(false, true)) {
            logger.debug("Closing because of closed internal EventDispatcher: {}", eventDispatcher.getClass().getSimpleName());
        }

        return isClosed.get();
    }

}
