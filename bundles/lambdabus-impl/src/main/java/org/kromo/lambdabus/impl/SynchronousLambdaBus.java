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
package org.kromo.lambdabus.impl;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.Consumer;

import org.kromo.lambdabus.LambdaBus;
import org.kromo.lambdabus.ThreadingMode;
import org.kromo.lambdabus.util.DispatchingUtil;

/**
 * Implementation of {@link LambdaBus} which dispatches events synchronously to
 * subscribers.
 *
 * @author Victor Toni - initial implementation
 *
 */
public class SynchronousLambdaBus
    extends AbstractLambdaBus {

    private static final ThreadingMode DEFAULT_THREADING_MODE = ThreadingMode.SYNC;
    private static final Set<ThreadingMode> SUPPORTED_THREADING_MODES = EnumSet.of(DEFAULT_THREADING_MODE);

    public SynchronousLambdaBus() {
        super( //
                DEFAULT_THREADING_MODE, //
                SUPPORTED_THREADING_MODES
        );
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
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
        DispatchingUtil.dispatchEventToSubscriber(
            event,
            eventSubscriberCollection);
    }

}
