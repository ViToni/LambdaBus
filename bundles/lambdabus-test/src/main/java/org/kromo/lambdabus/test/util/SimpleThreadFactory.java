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
package org.kromo.lambdabus.test.util;

import java.util.Objects;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * {@link ThreadFactory} implementation creating daemon threads with normal priority
 * ({@link Thread#NORM_PRIORITY}) and custom naming.
 * 
 * @author Victor Toni - initial API and implementation
 *
 */
public class SimpleThreadFactory
        implements ThreadFactory {

    /**
     * Supplier for threads names.
     */
    private final Supplier<String> threadNameSupplier;

    /**
     * Creates a new instance.
     * 
     * @param threadNamePrefix
     *            prefix for all threads names.
     */
    public SimpleThreadFactory(final String threadNamePrefix) {
        Objects.requireNonNull(threadNamePrefix, "'threadNamePrefix' must not be null");

        this.threadNameSupplier = new ThreadNameSupplier(threadNamePrefix);
    }

    /**
     * Creates a new instance.
     * 
     * @param threadNameSupplier
     *            {@link Supplier} of {@link String}s for threads names.
     */
    public SimpleThreadFactory(final Supplier<String> threadNameSupplier) {
        Objects.requireNonNull(threadNameSupplier, "'threadNameSupplier' must not be null");

        this.threadNameSupplier = threadNameSupplier;
    }

    @Override
    public Thread newThread(final Runnable runnable) {
        final String threadName = threadNameSupplier.get();

        final Thread thread = new Thread(runnable, threadName);

        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY);

        return thread;
    }

    /**
     * {@link ThreadFactory} implementation creating daemon threads with normal priority
     * ({@link Thread#NORM_PRIORITY}) and custom naming.
     * 
     * @author Victor Toni
     *
     */
    public static class ThreadNameSupplier
            implements Supplier<String> {

        /**
         * Prefix for all threads names.
         */
        private final String threadNamePrefix;

        /**
         * Counter used to indicate which {@link Thread}s was created. The counter will be incremented
         * by this instance and can be used to detect how many {@link Thread}s have been created.
         */
        private final AtomicInteger threadCount = new AtomicInteger();

        /**
         * Creates a new instance.
         * 
         * @param threadNamePrefix
         *            prefix for all threads names.
         */
        public ThreadNameSupplier(final String threadNamePrefix) {
            this.threadNamePrefix = Objects.requireNonNull(threadNamePrefix, "'threadNamePrefix' must not be null");
        }

        @Override
        public String get() {
            return threadNamePrefix + "-thread-" + threadCount.incrementAndGet();
        }

    }

}
