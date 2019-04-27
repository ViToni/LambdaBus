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
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

import org.kromo.lambdabus.ThreadingMode;
import org.kromo.lambdabus.impl.concurrent.DaemonThreadPoolExecutor;
import org.kromo.lambdabus.util.DispatchingUtil;

/**
 * The {@link ThreadedEventDispatcher} supports synchronous and asynchronous
 * dispatching of events. Since this class does not hold any state of for any
 * event bus it could be safely shared between multiple instances.<br>
 *
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
    extends AbstractEventDispatcher {

    private static final ThreadingMode DEFAULT_THREADING_MODE = ThreadingMode.ASYNC_PER_EVENT;
    private static final EnumSet<ThreadingMode> SUPPORTED_THREADING_MODES;
    static {
        final EnumSet<ThreadingMode> unsupportedThreadingModes = EnumSet.of(ThreadingMode.ASYNC);
        SUPPORTED_THREADING_MODES = EnumSet.complementOf(unsupportedThreadingModes);
    }

    /**
     * Non-{@code null} {@link ExecutorService} used to dispatching asynchronous events.
     */
    private final ExecutorService executorService;
    private final String toString;

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
     *            dispatching of events
     *
     * @throws NullPointerException
     *             if {@code executorService} is {@code null}
     */
    public ThreadedEventDispatcher(
            final ExecutorService executorService
    ) {
        this(
                executorService,
                DEFAULT_THREADING_MODE,
                SUPPORTED_THREADING_MODES);
    }

    /**
     * Prepares a threaded {@code EventDispatcher} instance.
     *
     * @param executorService
     *            non-{@code null} {@link ExecutorService} used to execute the
     *            dispatching of events
     * @param defaultThreadingMode
     *            non-{@code null} {@link ThreadingMode} to be used as default
     *            when posting to the bus (unsupported modes will be mapped to
     *            this one)
     *
     * @throws NullPointerException
     *             if any of {@code executorService} or
     *             {@code defaultThreadingMode} is {@code null}
     * @throws IllegalArgumentException
     *             if {@code defaultThreadingMode} is not supported
     */
    public ThreadedEventDispatcher(
            final ExecutorService executorService,
            final ThreadingMode defaultThreadingMode
    ) {
        this(
                executorService,
                defaultThreadingMode,
                SUPPORTED_THREADING_MODES);
    }

    /**
     * Prepares a threaded {@code EventDispatcher} instance for use by subclasses.
     *
     * @param executorService
     *            non-{@code null} {@link ExecutorService} used to execute the
     *            dispatching of events
     * @param defaultThreadingMode
     *            non-{@code null} {@link ThreadingMode} to be used as default
     *            when posting to the bus (unsupported modes will be mapped to
     *            this one)
     * @param supportedThreadingModes
     *            non-empty {@link Set} of supported {@link ThreadingMode}s
     *
     * @throws NullPointerException
     *             if any of {@code executorService}, {@code defaultThreadingMode}
     *             or {@code supportedThreadingModes} is {@code null}
     * @throws IllegalArgumentException
     *             if {@code supportedThreadingModes} is empty or the
     *             {@code defaultThreadingMode} is not contained within
     *             {@code supportedThreadingModes}
     */
    private ThreadedEventDispatcher(
            final ExecutorService executorService,
            final ThreadingMode defaultThreadingMode,
            final Set<ThreadingMode> supportedThreadingModes
    ) {
        super(defaultThreadingMode, supportedThreadingModes);

        Objects.requireNonNull(executorService,"'executorService' must not be null");

        this.toString = getClass().getSimpleName() + '(' + executorService + ')';

        this.executorService = Executors.unconfigurableExecutorService(executorService);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return toString;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected final <T> void dispatchEventToHandlerNonSync(
            final T event,
            final Collection<Consumer<T>> eventHandlerCollection,
            final ThreadingMode supportedThreadingMode
    ) {
        switch (supportedThreadingMode) {
            case ASYNC_PER_SUBSCRIBER:
                DispatchingUtil.dispatchEventToHandlerThreadedPerHandler(
                        event,
                        eventHandlerCollection,
                        executorService);
                break;
            default:
                DispatchingUtil.dispatchEventToHandlerThreadedPerEvent(
                        event,
                        eventHandlerCollection,
                        executorService);
                break;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected final void cleanupBeforeClose() {
        executorService.shutdownNow();
    }

}
