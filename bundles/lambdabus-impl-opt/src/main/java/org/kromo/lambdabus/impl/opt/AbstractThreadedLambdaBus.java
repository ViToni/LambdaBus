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
package org.kromo.lambdabus.impl.opt;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.kromo.lambdabus.LambdaBus;
import org.kromo.lambdabus.ThreadingMode;
import org.kromo.lambdabus.impl.AbstractLambdaBus;

/**
 * Base class providing functionality for implementing multi-threaded versions of the
 * {@link LambdaBus}.
 *
 * @author Victor Toni - initial implementation
 *
 */
public abstract class AbstractThreadedLambdaBus
    extends AbstractLambdaBus {

    /**
     * Non-{@code null} {@link ExecutorService} used to dispatching asynchronous events.
     */
    private final ExecutorService executorService;

    private final String toString;

    /**
     * Prepares a threaded {@code LambdaBus} instance for use by sub-classes.
     *
     * @param defaultThreadingMode
     *            non-{@code null} {@link ThreadingMode} to be used as default
     *            when posting to the bus (unsupported modes will be mapped to
     *            this one)
     * @param supportedThreadingModes
     *            non-empty {@link Set} of supported {@link ThreadingMode}s
     * @param executorService
     *            non-{@code null} {@link ExecutorService} used to execute the
     *            dispatching jobs
     * @throws NullPointerException
     *             if {@code defaultThreadingMode}, {@code supportedThreadingModes} or
     *             {@code executorService} is {@code null}
     * @throws IllegalArgumentException
     *             if {@code supportedThreadingModes} is empty or the
     *             {@code defaultThreadingMode} is not contained within
     *             {@code supportedThreadingModes}
     */
    protected AbstractThreadedLambdaBus(
            final ThreadingMode defaultThreadingMode,
            final Set<ThreadingMode> supportedThreadingModes,
            final ExecutorService executorService
    ) {
        super(defaultThreadingMode, supportedThreadingModes);

        Objects.requireNonNull(executorService, "'executorService' must not be null");
        this.executorService = Executors.unconfigurableExecutorService(executorService);

        this.toString = getClass().getSimpleName() + '(' + executorService + ')';
    }

    @Override
    public String toString() {
        return toString;
    }

    @Override
    protected final void cleanupBeforeClose() {
        preExecutorShutdownHook();
        executorService.shutdownNow();
    }

    /**
     * This method will be executed before the {@link ExecutorService} shutdown is initiated.
     * Might be overriden to customize behavior.
     */
    protected void preExecutorShutdownHook() {
        // nothing to do here, might be overriden by sub-classes
    }

    /**
     * Gets the {@link ExecutorService} of this instance. It is exposed as an {@link Executor}
     * only because it's supposed to be used only to submit dispatching tasks.
     *
     * @return non-{@code null} {@link ExecutorService} as {@link Executor}
     */
    protected final Executor getExecutor() {
        return executorService;
    }

}
