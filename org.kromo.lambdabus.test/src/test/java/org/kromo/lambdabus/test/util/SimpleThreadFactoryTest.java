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
package org.kromo.lambdabus.test.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadFactory;
import java.util.function.Supplier;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Test for the {@link SimpleThreadFactory}.
 *
 * @author Victor Toni - initial implementation
 *
 */
public class SimpleThreadFactoryTest {

    @Test
    public void constructor() {
        final String threadNamePrefix = getClass().getSimpleName();

        new SimpleThreadFactory(threadNamePrefix);
    }

    @Test
    public void nullThreadNamePrefix() {
        final String nullThreadNamePrefix = null;
        assertThrows(
                NullPointerException.class,
                () -> new SimpleThreadFactory(nullThreadNamePrefix));
    }

    @Test
    public void nullThreadNameSupplier() {
        final Supplier<String> nullThreadNameSupplier = null;
        assertThrows(
                NullPointerException.class,
                () -> new SimpleThreadFactory(nullThreadNameSupplier));
    }

    @Test
    public void testNewThreadWithThreadNamePrefix() {

        final String threadNamePrefix = "threadNamePrefix";

        final ThreadFactory threadFactory = new SimpleThreadFactory(threadNamePrefix);

        assertThreadFactoryNewThread(threadFactory);
    }

    @Test
    public void testNewThreadWithThreadNameSupplier() {

        final String threadNamePrefix = "threadNamePrefix";

        final Supplier<String> threadNameSupplier = new SimpleThreadFactory.ThreadNameSupplier(
                threadNamePrefix);
        final ThreadFactory threadFactory = new SimpleThreadFactory(threadNameSupplier);

        assertThreadFactoryNewThread(threadFactory);
    }

    private void assertThreadFactoryNewThread(final ThreadFactory threadFactory) {
        final Runnable runnable = () -> {};

        final Set<String> threadNames = new HashSet<>();
        final List<Thread> threads = new ArrayList<>();
        final int count = 100;
        for (int i = 0; i < count; i++) {
            final Thread thread = threadFactory.newThread(runnable);
            assertTrue(thread.isDaemon(), "Thread is not a daemon thread");
            assertEquals(thread.getPriority(), Thread.NORM_PRIORITY,
                    "Thread has not normal priority");
            assertFalse(threads.contains(thread), "Thread has not normal priority");

            threads.add(thread);
            threadNames.add(thread.getName());
        }
        assertEquals(count, threadNames.size(), "Not enough unique Thread names created.");
    }

    @Nested
    public static class ThreadNameSupplierTest {

        @Test
        public void constructor() {
            final String threadNamePrefix = getClass().getSimpleName();

            new SimpleThreadFactory.ThreadNameSupplier(threadNamePrefix);
        }

        @Test
        public void nullThreadNamePrefix() {
            final String nullThreadNamePrefix = null;
            assertThrows(
                    NullPointerException.class,
                    () -> new SimpleThreadFactory.ThreadNameSupplier(nullThreadNamePrefix));
        }

        @Test
        public void testNewThread() {
            final String threadNamePrefix = "threadNamePrefix";

            final Supplier<String> threadNameSupplier = new SimpleThreadFactory.ThreadNameSupplier(
                    threadNamePrefix);

            final Set<String> threadNames = new HashSet<>();
            final int count = 100;
            for (int i = 0; i < count; i++) {
                threadNames.add(threadNameSupplier.get());
            }
            assertEquals(count, threadNames.size(), "Not enough unique Thread names created.");
        }
    }

}
