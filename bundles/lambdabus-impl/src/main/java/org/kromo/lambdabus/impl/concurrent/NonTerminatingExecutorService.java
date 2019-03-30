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
package org.kromo.lambdabus.impl.concurrent;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class acts as a decorator for an existing {@link ExecutorService} to avoid shutdown.
 * <p>When sharing an {@link ExecutorService} between classes which are supposed to clean up
 * used executors this class helps keeping the control of the {@link ExecutorService} in a
 * central place.
 * <p>
 * @author Victor Toni - initial implementation
 *
 */
public class NonTerminatingExecutorService
    implements ExecutorService {

    private final Logger logger = LoggerFactory.getLogger(NonTerminatingExecutorService.class);

    private final ExecutorService executorService;

    private final AtomicBoolean isShutdown;

    public NonTerminatingExecutorService(final ExecutorService executorService) {
        this.executorService = Objects.requireNonNull(executorService, "'executorService' must not be null");
        this.isShutdown = new AtomicBoolean(executorService.isShutdown());
    }

    @Override
    public void shutdown() {
        if (isShutdown.compareAndSet(false, true)) {
            logger.debug("Called shutdown() - call will not be delegated to: {}", executorService.getClass().getSimpleName());
        }
    }

    @Override
    public List<Runnable> shutdownNow() {
        if (isShutdown.compareAndSet(false, true)) {
            logger.debug("Called shutdownNow() - call will not be delegated to: {}", executorService.getClass().getSimpleName());
        }
        return Collections.emptyList();
    }

    @Override
    public boolean isShutdown() {
        if (executorService.isShutdown() && isShutdown.compareAndSet(false, true)) {
            logger.debug("Shutdown because of shutdown internal ExecutorService: {}", executorService.getClass().getSimpleName());
        }

        return isShutdown.get();
    }

    @Override
    public boolean isTerminated() {
        if (executorService.isTerminated() && isShutdown.compareAndSet(false, true)) {
            logger.debug("Shutdown because of terminated internal ExecutorService: {}", executorService.getClass().getSimpleName());
        }

        return isShutdown.get();
    }

    @Override
    public boolean awaitTermination( //
            final long timeout, //
            final TimeUnit unit //
    ) {
        final long futureTime = System.nanoTime() + unit.toNanos(timeout);

        // very naive approach, might need some love
        while (futureTime < System.nanoTime()) {
            if (isTerminated()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void execute(final Runnable command) {
        rejectIfTerminated(command, "command");
        executorService.execute(command);
    }

    @Override
    public <T> Future<T> submit( //
            final Callable<T> task //
    ) {
        rejectIfTerminated(task, "task");
        return executorService.submit(task);
    }

    @Override
    public <T> Future<T> submit( //
            final Runnable task, //
            final T result //
    ) {
        rejectIfTerminated(task, "task");
        return executorService.submit(task, result);
    }

    @Override
    public Future<?> submit(
            final Runnable task //
    ) {
        rejectIfTerminated(task, "task");
        return executorService.submit(task);
    }

    @Override
    public <T> List<Future<T>> invokeAll( //
            final Collection<? extends Callable<T>> tasks //
    ) throws InterruptedException {
        rejectIfTerminated(tasks, "tasks");
        return executorService.invokeAll(tasks);
    }

    @Override
    public <T> List<Future<T>> invokeAll( //
            final Collection<? extends Callable<T>> tasks, //
            final long timeout, //
            final TimeUnit unit //
    ) throws InterruptedException {
        rejectIfTerminated(tasks, "tasks");
        return executorService.invokeAll(tasks, timeout, unit);
    }

    @Override
    public <T> T invokeAny( //
            final Collection<? extends Callable<T>> tasks //
    ) throws InterruptedException, ExecutionException {
        rejectIfTerminated(tasks, "tasks");
        return executorService.invokeAny(tasks);
    }

    @Override
    public <T> T invokeAny( //
            final Collection<? extends Callable<T>> tasks, //
            final long timeout, //
            final TimeUnit unit //
    ) throws InterruptedException, ExecutionException, TimeoutException {
        rejectIfTerminated(tasks, "tasks");
        return executorService.invokeAny(tasks, timeout, unit);
    }

    protected <T> void rejectIfTerminated(final T t, final String name) {
        Objects.requireNonNull(t, name);
        if (isShutdown()) {
            throw new RejectedExecutionException( //
                    "Rejected " + //
                    t.toString() + //
                    " because ExecutorService is shutdown.");
        }
    }
}
