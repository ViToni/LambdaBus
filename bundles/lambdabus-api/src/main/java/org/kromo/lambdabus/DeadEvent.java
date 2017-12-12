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
package org.kromo.lambdabus;

import java.util.Objects;

/**
 * A generic wrapper for events for which no subscriber could be found.
 *
 * <p>
 * Events without subscriber can be detected by subscribing to
 * {@link DeadEvent}s. Such a subscriber could be used to log otherwise lost
 * events.
 * </p>
 *
 * @author Victor Toni - initial API and implementation
 *
 */
public final class DeadEvent {

    /**
     * The object for which no subscriber could be found.
     * <p> 
     * Since the member is final it is made public instead of using a getter.
     * </p> 
     */
    public final Object event;

    /**
     * Creates an instance with the given event.
     * 
     * @param event
     *            non-{@code null} object
     * @throws NullPointerException
     *             if the event is {@code null}
     */
    public DeadEvent(final Object event) {
        Objects.requireNonNull(event, "'event' must not be null");

        this.event = event;
    }

    @Override
    public final boolean equals(final Object object) {
        if (this == object) {
            return true;
        }

        if (object instanceof DeadEvent) {
            final DeadEvent other = (DeadEvent) object;
            return event.equals(other.event);
        }

        return false;
    }

    @Override
    public final int hashCode() {
        final int start = 37;

        int tempHashCode = start;
        tempHashCode = (tempHashCode + event.hashCode()) * 17;

        return tempHashCode;
    }

}
