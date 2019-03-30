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
package org.kromo.lambdabus.queue;

import java.util.Collection;
import java.util.function.Consumer;

import org.kromo.lambdabus.ThreadingMode;

/**
 * Wrapper class for the event, its subscribed {@link Consumer}s and the
 * requested {@link ThreadingMode}.<br>
 * This class does not do any checks on the data as it is just used as tuple for
 * queuing.
 *
 * @param <T>
 *            type of event
 *
 * @author Victor Toni - initial implementation
 *
 */
public final class QueuedEvent<T> {

    public final T event;
    public final Collection<Consumer<T>> eventSubscriberCollection;
    public final ThreadingMode threadingMode;

    /**
     * Creates an instance which holds required information for deferred processing
     * of an event.
     *
     * @param event
     *            which has been posted to the bus
     * @param eventSubscriberCollection
     *            {@link Collection} of subscriber the event will be dispatched to
     * @param threadingMode
     *            how should the event be dispatched
     */
    public QueuedEvent(
            final T event,
            final Collection<Consumer<T>> eventSubscriberCollection,
            final ThreadingMode threadingMode
    ) {
        this.event = event;
        this.eventSubscriberCollection = eventSubscriberCollection;
        this.threadingMode = threadingMode;
    }

}