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
package org.kromo.lambdabus.impl.concurrent;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The default {@link ExecutorService} implementations provided by
 * {@link Executors} use non-daemon threads. If these {@link ExecutorService}s
 * are not shutdown explicitly, they might run forever. Using this
 * {@link ThreadFactory} avoids the necessity to do the shutdown manually
 * because it returns only daemon threads.
 *
 * @author Victor Toni - initial implementation
 *
 */
public class DaemonThreadFactory
        implements ThreadFactory {

    private final Logger logger = LoggerFactory.getLogger(DaemonThreadFactory.class);

    /**
     * Internal counter for created threads by this instance.
     */
    private final AtomicInteger threadNumber = new AtomicInteger(0);

    /**
     * {@link ThreadGroup} the created {@link Thread}s will bound to.
     */
    private final ThreadGroup threadGroup;

    /**
     * The factory name is used as a prefix for created threads.
     */
    private final String factoryName;

    /**
     * Creates a new instance.
     *
     * @param factoryName
     *            will be the prefix of the created {@link Thread}s name
     * @throws NullPointerException
     *             if {@code factoryName} is {@code null}
     */
    public DaemonThreadFactory(final String factoryName) {
        this(factoryName, getThreadGroupToBeUsed());
    }

    /**
     * Creates a new instance.
     *
     * @param factoryName
     *            will be the prefix of the created {@link Thread}s name
     * @param threadGroup
     *            the thread group threads created by this instance should belong to
     * @throws NullPointerException
     *             if {@code factoryName} or {@code threadGroup} is {@code null}
     */
    public DaemonThreadFactory(
            final String factoryName,
            final ThreadGroup threadGroup) {
        this.factoryName = Objects.requireNonNull(factoryName, "'factoryName' must not be null");
        this.threadGroup = Objects.requireNonNull(threadGroup, "'threadGroup' must not be null");
    }

    @Override
    public Thread newThread(final Runnable runnable) {
        Objects.requireNonNull(runnable, "'runnable' must not be null");

        final String threadName = factoryName + "-thread-" + threadNumber.incrementAndGet();
        final Thread thread = new Thread(
                getThreadGroup(),
                runnable,
                threadName);

        thread.setDaemon(true);

        logger.trace("Created thread: {}", thread);

        return thread;
    }

    private ThreadGroup getThreadGroup() {
        return threadGroup;
    }

    private static ThreadGroup getThreadGroupToBeUsed() {
        final SecurityManager securityManager = System.getSecurityManager();
        if (securityManager != null) {
            return securityManager.getThreadGroup();
        } else {
            return Thread.currentThread()
                    .getThreadGroup();
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() +
                "(name:" + factoryName +
                ",created threads:" + threadNumber.get() + ")";
    }

}
