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
 *     Victor Toni - initial API
 *******************************************************************************/
package org.kromo.lambdabus;

import java.util.concurrent.ExecutorService;

/**
 * The {@link ThreadingMode} is used to indicate to the event bus how to dispatch
 * events.
 * <p>As event subscriber might be blocking when an event is dispatched to them
 * specific events might be posted with a non-default {@link ThreadingMode}.</p>
 *
 * @author Victor Toni - initial API
 *
 */
public enum ThreadingMode {

    /**
     * Dispatching should be done synchronously in the thread of the caller.
     * <p>
     * Note:<br>
     * Single-threaded applications with deeply nested calls might want to use another {@link ThreadingMode}.
     * </p>
     */
    SYNC,

    /**
     * Posting should be non-blocking and dispatching should be done in a separate thread.
     * <p>
     * Note:<br>
     * As all dispatching is executed in one thread subscriber (even of different subscriber
     * classes) might block each other in the dispatching thread.
     * </p>
     */
    ASYNC,

    /**
     * Dispatching should be done in one thread per event, individual subscriber for one event might
     * block each other.
     * <p>
     * Note:<br>
     * An event will be dispatched in its own thread regardless of the number of registered
     * subscribers for this event class.<br>
     * When there are many events for blocking subscriber using a constrained
     * {@link ExecutorService} might lead to congestion.
     * </p>
     */
    ASYNC_PER_EVENT,

    /**
     * Dispatching should be done in one thread per subscriber, individual subscriber won't block each
     * other.
     * <p>
     * Note:<br>
     * If an event class has {@code N} subscriber up to {@code N} threads will be used while
     * dispatching (depending on the executor used).<br>
     * When there are many events for blocking subscriber using a constrained
     * {@link ExecutorService} might lead to congestion of the pool.
     * </p>
     */
    ASYNC_PER_SUBSCRIBER;

}
