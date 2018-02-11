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
package org.kromo.lambdabus.dispatcher;

import java.util.Collection;
import java.util.Objects;
import java.util.function.Consumer;

import org.kromo.lambdabus.ThreadingMode;
import org.kromo.lambdabus.impl.AbstractLambdaBus;

/**
 * The {@link DispatchingLambdaBus} uses a strategy pattern to change
 * dispatching behavior. Dispatching is delegated to an implementation of the
 * {@link EventDispatcher}.
 * 
 * @author Victor Toni - initial API and implementation
 *
 */
public class DispatchingLambdaBus
    extends AbstractLambdaBus {

    private final EventDispatcher eventDispatcher;

    public DispatchingLambdaBus(final EventDispatcher eventDispatcher) {
        super( //
                Objects.requireNonNull(eventDispatcher, "'eventDispatcher' must not be null").getDefaultThreadingMode(), //
                eventDispatcher.getSupportedThreadingModes() //
        );

        this.eventDispatcher = eventDispatcher;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + '(' + eventDispatcher + ')';
    }

    @Override
    protected void cleanupBeforeClose() {
        eventDispatcher.close();
    }

    @Override
    protected <T> void acceptNonNullEvent(
            final T event,
            final ThreadingMode supportedThreadingMode
    ) {
        tryToDispatchNonNullEvent(event, supportedThreadingMode);
    }

    @Override
    protected <T> void dispatchNonNullEventToSubscriber(
            final T event,
            final Collection<Consumer<T>> eventSubscriberCollection,
            final ThreadingMode supportedThreadingMode
    ) {
        /*
        * Delegate to the dispatcher so that it can decide on its own which
        * dispatching strategy to use.
        */
        eventDispatcher.dispatchEventToSubscriber(
                event,
                eventSubscriberCollection,
                supportedThreadingMode);
    }

}
