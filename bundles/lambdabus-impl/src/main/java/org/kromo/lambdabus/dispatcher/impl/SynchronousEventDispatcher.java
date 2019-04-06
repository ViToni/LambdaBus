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
package org.kromo.lambdabus.dispatcher.impl;

import java.util.Collection;
import java.util.function.Consumer;

import org.kromo.lambdabus.ThreadingMode;
import org.kromo.lambdabus.dispatcher.EventDispatcher;
import org.kromo.lambdabus.util.DispatchingUtil;

/**
 * Implementation of {@link EventDispatcher} interface which dispatches events
 * synchronously to subscribers (in the same {@link Thread} as the publisher of
 * the event).
 *
 * @author Victor Toni - initial implementation
 *
 */
public class SynchronousEventDispatcher
    extends AbstractEventDispatcher {

    private static final ThreadingMode DEFAULT_THREADING_MODE = ThreadingMode.SYNC;

    public SynchronousEventDispatcher() {
        super(DEFAULT_THREADING_MODE);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }

   @Override
   protected final <T> void internalDispatchEventToSubscriber(
           final T event,
           final Collection<Consumer<T>> eventSubscriberCollection,
           final ThreadingMode supportedThreadingMode
   ) {
       DispatchingUtil.dispatchEventToSubscriber(
               event,
               eventSubscriberCollection);
   }

}
