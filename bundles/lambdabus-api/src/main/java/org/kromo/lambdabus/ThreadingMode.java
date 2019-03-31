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

import java.util.concurrent.Executor;

/**
 * The {@link ThreadingMode} is used to indicate to the event bus how events
 * should be dispatched.
 *
 * <p>As event subscribers might block when an event is dispatched to them,
 * specific events can be posted with a non-default {@link ThreadingMode}.</p>
 *
 * @author Victor Toni - initial API
 *
 */
public enum ThreadingMode {

    /**
     * Dispatching should be done synchronously in the thread of the caller.
     * <p>
     * Note:<br>
     * Single-threaded applications with deeply nested calls might want to use
     * another {@link ThreadingMode}.
     * </p>
     */
    SYNC,

    /**
     * Posting should be non-blocking and dispatching should be done in a
     * separate thread.
     * <p>
     * Note:<br>
     * Since all dispatching is done in one thread, subscribers (even of
     * different subscriber classes) can block each other in the dispatching
     * thread.
     * </p>
     */
    ASYNC,

    /**
     * Dispatching should be done in one thread per event, individual
     * subscribers to one event type might block each other.
     * <p>
     * Note:<br>
     * An event will be dispatched in a separate thread regardless of the
     * number of registered subscribers for this event class.<br>
     * If there are many events for blocking subscribers, using a constrained
     * {@link Executor} may cause a bottleneck.
     * </p>
     */
    ASYNC_PER_EVENT,

    /**
     * Dispatching should be done in one thread per subscriber, individual
     * subscribers won't block each other.
     * <p>
     * Note:<br>
     * If an event class has {@code N} subscribers up to {@code N} threads will be used while
     * dispatching (depending on the executor used).<br>
     * If there are many events for blocking subscribers, using a constrained
     * {@link Executor} may cause a bottleneck.
     * </p>
     */
    ASYNC_PER_SUBSCRIBER

}
