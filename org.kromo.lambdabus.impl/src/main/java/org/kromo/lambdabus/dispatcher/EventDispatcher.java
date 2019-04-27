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
 *     Victor Toni - initial API and implementation
 */
package org.kromo.lambdabus.dispatcher;

import java.util.Collection;
import java.util.Set;
import java.util.function.Consumer;

import org.kromo.lambdabus.ThreadingMode;

/**
 * Implementing this interface allows to customize the behavior of the event bus
 * without implementing the event bus itself.
 *
 * @author Victor Toni - initial API and implementation
 */
public interface EventDispatcher
        extends AutoCloseable {

    /**
     * Dispatches an event to the {@link Collection} of its subscribers.
     *
     * @param <T>
     *            the type of the event
     * @param event
     *            non-{@code null} object which was published to the bus and shall
     *            be dispatched to subscribers
     * @param eventHandlerCollection
     *            non-{@code null} {@link Collection} of non-{@code null}
     *            {@link Consumer} for events of type {@link Class}
     * @param supportedThreadingMode
     *            non-{@code null} {@link ThreadingMode} how the event should be
     *            dispatched
     * @throws IllegalStateException
     *             if {@link EventDispatcher} is already closed
     */
    <T> void dispatchEventToHandler(
            final T event,
            final Collection<Consumer<T>> eventHandlerCollection,
            final ThreadingMode supportedThreadingMode);

    /**
     * Gets the default {@link ThreadingMode} of this dispatcher.
     *
     * @return {@link ThreadingMode} used as default
     */
    ThreadingMode getDefaultThreadingMode();

    /**
     * Gets the supported {@link ThreadingMode}s of this dispatcher.
     *
     * @return {@link Set} of supported {@link ThreadingMode}s
     */
    Set<ThreadingMode> getSupportedThreadingModes();

    /**
     * Gets the closed state of the event dispatcher.
     *
     * @return returns {@code true} if {@link #close()} has been called,
     *         {@code false} otherwise
     */
    boolean isClosed();

    /**
     * Closes the event dispatcher.
     */
    @Override
    void close();

}
