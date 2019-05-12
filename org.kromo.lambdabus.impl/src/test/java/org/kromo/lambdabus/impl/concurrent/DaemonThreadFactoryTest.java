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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.Permission;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tests for the {@link DaemonThreadFactory}.
 *
 * @author Victor Toni - initial implementation
 *
 */
public class DaemonThreadFactoryTest {

    protected static final Duration DEFAULT_TIMEOUT = Duration.ofMillis(2_000);

    @Test
    @DisplayName("Constructor with factoryName")
    public void constructorFactoryName() {
        final String factoryName = getClass().getSimpleName();
        assertDoesNotThrow(
                () -> new DaemonThreadFactory(factoryName));
    }

    @Test
    @DisplayName("Constructor with null factoryName")
    public void constructorNullFactoryName() {
        final String nullFactoryName = null;
        assertThrows(
                NullPointerException.class,
                () -> new DaemonThreadFactory(nullFactoryName));
    }

    @Test
    @DisplayName("Constructor with factoryName and ThreadGroup")
    public void constructorFactoryNameAndThreadGroup() {
        final String factoryName = getClass().getSimpleName();
        final ThreadGroup threadGroup = new ThreadGroup(factoryName + "-thread-group");
        new DaemonThreadFactory(factoryName, threadGroup);
    }

    @Test
    @DisplayName("Constructor with factoryName and null ThreadGroup")
    public void constructorFactoryNameAndNullThreadGroup() {
        final String factoryName = getClass().getSimpleName();
        final ThreadGroup nullThreadGroup = null;
        assertThrows(
                NullPointerException.class,
                () -> new DaemonThreadFactory(factoryName, nullThreadGroup));
    }

    @Test
    @DisplayName("Constructor with factoryName and priority")
    public void constructorFactoryNameAndPriority() {
        final String factoryName = getClass().getSimpleName();

        for (int i = Thread.MIN_PRIORITY; i <= Thread.MAX_PRIORITY; i++) {
            final int priority = i;
            assertDoesNotThrow(
                    () -> new DaemonThreadFactory(factoryName, priority));
        }
    }

    @Test
    @DisplayName("Constructor with factoryName, ThreadGroup and priority")
    public void constructorFactoryNameAndThreadGroupAndPriority() {
        final String factoryName = getClass().getSimpleName();
        final ThreadGroup threadGroup = new ThreadGroup(factoryName + "-thread-group");

        for (int i = Thread.MIN_PRIORITY; i <= Thread.MAX_PRIORITY; i++) {
            final int priority = i;
            assertDoesNotThrow(
                    () -> new DaemonThreadFactory(factoryName, threadGroup, priority));
        }
    }

    @Test
    @DisplayName("Constructor with illegal priority")
    public void constructor_with_illegal_priority() {
        final String factoryName = getClass().getSimpleName();

        final int priorityTooLow = Thread.MIN_PRIORITY - 1;
        assertThrows(
                IllegalArgumentException.class,
                () -> new DaemonThreadFactory(factoryName, priorityTooLow));

        final int priorityTooHigh = Thread.MAX_PRIORITY + 1;
        assertThrows(
                IllegalArgumentException.class,
                () -> new DaemonThreadFactory(factoryName, priorityTooHigh));

        final ThreadGroup threadGroup = new ThreadGroup(factoryName + "-thread-group");

        assertThrows(
                IllegalArgumentException.class,
                () -> new DaemonThreadFactory(factoryName, threadGroup, priorityTooLow));

        assertThrows(
                IllegalArgumentException.class,
                () -> new DaemonThreadFactory(factoryName, threadGroup, priorityTooHigh));
    }

    @Test
    @DisplayName("ThreadPriority is not inherited and set to custom value")
    public void ThreadPriority_is_not_inherited_and_set_to_custom_value() {
        final String factoryName = getClass().getSimpleName();
        final ThreadGroup threadGroup = new ThreadGroup(factoryName + "-thread-group");
        final DaemonThreadFactory threadFactory = new DaemonThreadFactory(factoryName, threadGroup);

        final Runnable runnable = () -> {};
        {
            Thread.currentThread()
                    .setPriority(Thread.MIN_PRIORITY);
            final Thread thread = threadFactory.newThread(runnable);
            assertEquals(threadGroup, thread.getThreadGroup());
            assertEquals(Thread.NORM_PRIORITY, thread.getPriority());
            assertTrue(thread.isDaemon());
        }
        {
            Thread.currentThread()
                    .setPriority(Thread.MAX_PRIORITY);
            final Thread thread = threadFactory.newThread(runnable);
            assertEquals(threadGroup, thread.getThreadGroup());
            assertEquals(Thread.NORM_PRIORITY, thread.getPriority());
            assertTrue(thread.isDaemon());
        }
    }

    @Test
    @DisplayName("ThreadGroup is inherited correctly (and respects SecurityManager)")
    public void test_ThreadGroup_is_inherited() throws Throwable {
        final String factoryName = getClass().getSimpleName();
        final ThreadGroup threadGroup = new ThreadGroup(factoryName + "-thread-group");

        final AtomicReference<Throwable> throwableRef = new AtomicReference<>();
        final CountDownLatch doneLatch = new CountDownLatch(1);

        final Runnable emptyRunnable = () -> {};

        final Runnable testRunnable = () -> {
            try {
                // testing without a SecurityManager
                {
                    assertNull(System.getSecurityManager());

                    final DaemonThreadFactory threadFactory = new DaemonThreadFactory(factoryName);

                    final Thread thread = threadFactory.newThread(emptyRunnable);

                    // inherited the ThreadGroup from the Thread we are running in (testingThread)
                    assertEquals(threadGroup, thread.getThreadGroup());
                }

                // testing with a SecurityManager
                {
                    final SecurityManager securityManager = new LenientSecurityManager();
                    final ThreadGroup securityManagerThreadGroup = securityManager.getThreadGroup();
                    System.setSecurityManager(securityManager);
                    try {
                        assertNotNull(System.getSecurityManager());

                        final DaemonThreadFactory threadFactory = new DaemonThreadFactory(
                                factoryName);

                        final Thread thread = threadFactory.newThread(emptyRunnable);

                        // inherited the ThreadGroup from the SecurityManager
                        assertEquals(securityManagerThreadGroup, thread.getThreadGroup());
                    } finally {
                        System.setSecurityManager(null);
                    }
                }

                // testing without a SecurityManager (SecurityManager has been unset)
                {
                    assertNull(System.getSecurityManager());

                    final DaemonThreadFactory threadFactory = new DaemonThreadFactory(factoryName);

                    final Thread thread = threadFactory.newThread(emptyRunnable);

                    // inherited the ThreadGroup from the Thread we are running in (testingThread)
                    assertEquals(threadGroup, thread.getThreadGroup());
                }
            } finally {
                doneLatch.countDown();
                System.setSecurityManager(null);
            }
        };

        final Thread testingThread = new Thread(threadGroup, testRunnable);
        testingThread.setUncaughtExceptionHandler(
                (thread, throwable) -> {
                    // catch the first exception thrown within thread
                    throwableRef.compareAndSet(null, throwable);
                });

        testingThread.start();

        assertTimeout(DEFAULT_TIMEOUT, (Executable) doneLatch::await);

        if (null != throwableRef.get()) {
            throw throwableRef.get();
        }
    }

    @ParameterizedTest
    @MethodSource("priorityRange")
    @DisplayName("Threads created and setup with corrected defaults")
    public void newThread_creates_thread_and_sets_it_up(
            final int priority) throws InterruptedException {
        final int count = 10;

        final String factoryName = getClass().getSimpleName();
        final ThreadGroup threadGroup = new ThreadGroup(
                factoryName + "-thread-group--priority-" + priority);
        final DaemonThreadFactory threadFactory = new DaemonThreadFactory(factoryName, threadGroup,
                priority);

        final AtomicInteger counter = new AtomicInteger();
        final CountDownLatch doneLatch = new CountDownLatch(count);
        final Set<String> threadNames = new HashSet<>();
        for (int i = 0; i < count; i++) {
            final Runnable runnable = () -> {
                counter.incrementAndGet();
                doneLatch.countDown();
            };
            final Thread thread = threadFactory.newThread(runnable);
            assertEquals(threadGroup, thread.getThreadGroup());
            assertEquals(priority, thread.getPriority());
            assertTrue(thread.isDaemon());
            assertTrue(threadNames.add(thread.getName()));

            thread.start();
        }
        doneLatch.await();
        assertEquals(count, counter.get());
        assertEquals(count, threadNames.size());
    }

    @Test
    @DisplayName("toString changes on new Thread (because it contains stats)")
    public void toString_changes_on_new_Thread() {
        final int count = 100;
        final Set<String> strings = new HashSet<>(count + 1);

        final String factoryName = getClass().getSimpleName();
        final ThreadGroup threadGroup = new ThreadGroup(factoryName + "-thread-group");
        final DaemonThreadFactory threadFactory = new DaemonThreadFactory(factoryName, threadGroup);

        assertNotNull(threadFactory.toString());
        strings.add(threadFactory.toString());

        final Runnable emptyRunnable = () -> {};
        for (int i = 0; i < count; i++) {
            threadFactory.newThread(emptyRunnable);
            assertNotNull(threadFactory.toString());
            strings.add(threadFactory.toString());
        }

        assertEquals(count + 1, strings.size());
    }

    @Test
    @DisplayName("toString output is reproducible")
    public void toString_output_is_reproducible() {
        final int factoryCount = 13;
        final int threadCount = 17;

        final List<Set<String>> stringSets = new ArrayList<>(threadCount + 1);
        for (int i = 0; i <= threadCount; i++) {
            final Set<String> strings = new HashSet<>(factoryCount);
            stringSets.add(strings);
        }

        final List<ThreadFactory> threadFactories = new ArrayList<>(factoryCount);
        for (int i = 0; i < factoryCount; i++) {
            final Set<String> strings = stringSets.get(0);

            final String factoryName = getClass().getSimpleName();
            final ThreadGroup threadGroup = new ThreadGroup(factoryName + "-thread-group");
            final DaemonThreadFactory threadFactory = new DaemonThreadFactory(factoryName,
                    threadGroup);

            assertNotNull(threadFactory.toString());
            strings.add(threadFactory.toString());
            assertEquals(1, strings.size());

            threadFactories.add(threadFactory);
        }

        final Runnable emptyRunnable = () -> {};

        for (int i = 1; i <= threadCount; i++) {
            for (int j = 0; j < factoryCount; j++) {
                final Set<String> strings = stringSets.get(i);

                final ThreadFactory threadFactory = threadFactories.get(j);
                threadFactory.newThread(emptyRunnable);
                assertNotNull(threadFactory.toString());
                strings.add(threadFactory.toString());
                assertEquals(1, strings.size());
            }
        }
    }

    /**
     * This {@link SecurityManager} allows all and is needed so that we can unset
     * the {@link SecurityManager} after the test run (and don't influence other
     * tests).
     *
     */
    private static class LenientSecurityManager
            extends SecurityManager {
        @Override
        public void checkPermission(final Permission permission) {
            // we need this to be able to unset the SecurityManager after usage
        }

        @Override
        public void checkPermission(final Permission permission, final Object context) {
            // we need this to be able to unset the SecurityManager after usage
        }
    }

    static IntStream priorityRange() {
        return IntStream.range(Thread.MIN_PRIORITY, Thread.MAX_PRIORITY);
    }
}
