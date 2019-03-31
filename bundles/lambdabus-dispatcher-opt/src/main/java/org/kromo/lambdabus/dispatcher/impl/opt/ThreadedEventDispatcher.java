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
package org.kromo.lambdabus.dispatcher.impl.opt;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

import org.kromo.lambdabus.ThreadingMode;
import org.kromo.lambdabus.impl.concurrent.DaemonThreadPoolExecutor;
import org.kromo.lambdabus.util.DispatchingUtil;

/**
 * The {@link ThreadedEventDispatcher} supports synchronous and asynchronous
 * dispatching of events.<br>
 * Supported {@link ThreadingMode}s are
 * <ul>
 * <li>{@link ThreadingMode#SYNC}</li>
 * <li>{@link ThreadingMode#ASYNC_PER_EVENT}</li>
 * <li>{@link ThreadingMode#ASYNC_PER_SUBSCRIBER}</li>
 * </ul>
 * Unsupported {@link ThreadingMode}s ({@link ThreadingMode#ASYNC}) and will be
 * mapped to the default {@link ThreadingMode#ASYNC_PER_EVENT}.
 *
 * @author Victor Toni - initial implementation
 *
 */
public class ThreadedEventDispatcher
    extends AbstractThreadedEventDispatcher {

    private static final ThreadingMode DEFAULT_THREADING_MODE = ThreadingMode.ASYNC_PER_EVENT;
    private static final EnumSet<ThreadingMode> SUPPORTED_THREADING_MODES;
    static {
        final EnumSet<ThreadingMode> unsupportedThreadingModes = EnumSet.of(ThreadingMode.ASYNC);
        SUPPORTED_THREADING_MODES = EnumSet.complementOf(unsupportedThreadingModes);
    }

    /**
     * Prepares a threaded {@code EventDispatcher} instance.
     */
    public ThreadedEventDispatcher() {
        this(
                new DaemonThreadPoolExecutor(
                        new LinkedBlockingQueue<>()
                )
        );
    }

    /**
     * Prepares a threaded {@code EventDispatcher} instance.
     *
     * @param executorService
     *            non-{@code null} {@link ExecutorService} used to execute the
     *            dispatching jobs
     * @throws NullPointerException
     *             if {@code executorService} is {@code null}
     */
    public ThreadedEventDispatcher(
            final ExecutorService executorService
    ) {
        super(
                DEFAULT_THREADING_MODE,
                SUPPORTED_THREADING_MODES,
                Objects.requireNonNull(executorService, "'executorService' must not be null"));
    }

    @Override
    protected final <T> void internalDispatchEventToSubscriber(
            final T event,
            final Collection<Consumer<T>> eventSubscriberCollection,
            final ThreadingMode supportedThreadingMode
    ) {
        switch (supportedThreadingMode) {
            case SYNC:
                DispatchingUtil.dispatchEventToSubscriber(
                        event,
                        eventSubscriberCollection);
                break;
            case ASYNC_PER_SUBSCRIBER:
                DispatchingUtil.dispatchEventToSubscriberThreadedPerSubscriber(
                        event,
                        eventSubscriberCollection,
                        getExecutor());
                break;
            default:
                DispatchingUtil.dispatchEventToSubscriberThreadedPerEvent(
                        event,
                        eventSubscriberCollection,
                        getExecutor());
                break;
        }
    }

}
