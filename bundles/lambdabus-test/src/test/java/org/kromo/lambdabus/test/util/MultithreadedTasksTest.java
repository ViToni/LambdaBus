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
package org.kromo.lambdabus.test.util;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Testing that {@link MultithreadedTasks} is executing tasks in parallel and does not exceed the count
 * of tasks to be executed.
 *
 * @author Victor Toni - initial implementation
 *
 */
public class MultithreadedTasksTest {

    private static final int N_THREADS = 113;

    private static final int N_TIMES_HUGE    =     2503;
    private static final int N_TIMES_LARGE   =      499;
    private static final int N_TIMES_EQUAL   = N_THREADS;
    private static final int N_TIMES_SMALL   =       31;
    private static final int N_TIMES_TINY    =        7;
    private static final int N_TIMES_ONE     =        1;

    private final Logger log = LoggerFactory.getLogger(MultithreadedTasksTest.class);

    private final String staticThreadNamePrefix = getClass().getSimpleName();
    private final AtomicInteger testCount = new AtomicInteger(0);

    private String threadNamePrefix;
    private String testName = getClass().getSimpleName();

    @BeforeEach
    public void beforeEachTest(final TestInfo testInfo) {
        testName = testInfo.getDisplayName();
        threadNamePrefix = staticThreadNamePrefix + testCount.incrementAndGet();
    }

    @Test
    @DisplayName("Constructor does not throw exception")
    public void constructor() {
        assertDoesNotThrow(
                () -> new MultithreadedTasks(N_THREADS)
        );
        assertDoesNotThrow(
                () -> new MultithreadedTasks(N_THREADS, threadNamePrefix)
        );
    }

    @Test
    @DisplayName("Constructor - null ThreadFactory throws NullPointerException")
    public void constructor_null_ThreadFactory_throws_NPE() {
        assertThrows(
                NullPointerException.class,
                () -> new MultithreadedTasks(N_THREADS, null)
        );
    }

    @Test
    @DisplayName("Constructor - thread count less ONE throws IllegalArgumentException")
    public void constructor_thread_count_less_ONE_throws_exception() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new MultithreadedTasks(0)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new MultithreadedTasks(-1)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new MultithreadedTasks(0, threadNamePrefix)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new MultithreadedTasks(-1, threadNamePrefix)
        );
    }

    @ParameterizedTest
    @EnumSource(MultithreadedTasks.ExecutionPolicy.class)
    public void excuteTaskThrowsIllegalArgumentExceptionIfNTimesSmallerThanOne(
            final MultithreadedTasks.ExecutionPolicy executionPolicy
    ) {
        final MultithreadedTasks multithreadedTasks = new MultithreadedTasks(N_THREADS, threadNamePrefix);

        final int nTimes = 0; // not an allowed value
        final Runnable task = () -> { };
        assertThrows(
                IllegalArgumentException.class,
                () -> multithreadedTasks.executeTask(nTimes, task, executionPolicy)
        );
    }

    @ParameterizedTest
    @EnumSource(MultithreadedTasks.ExecutionPolicy.class)
    public void excuteTaskThrowsNullPointerExceptionIfTaskIsNull(
            final MultithreadedTasks.ExecutionPolicy executionPolicy
    ) {
        final MultithreadedTasks multithreadedTasks = new MultithreadedTasks(N_THREADS, threadNamePrefix);

        final int nTimes = 1;
        final Runnable task = null;
        assertThrows(
                NullPointerException.class,
                () -> multithreadedTasks.executeTask(nTimes, task)
        );
    }

    @Test
    public void taskThrowsException() {
        final String msg = "Test exception";

        final int nTimes = 300;
        final int throwExceptionAfterNtimes = nTimes / 2;

        final AtomicInteger counter = new AtomicInteger();

        final Runnable task = new Runnable() {
                @Override
                public void run() {
                    if (throwExceptionAfterNtimes < counter.incrementAndGet()) {
                        throw new RuntimeException(msg);
                    }
                }
        };

        final MultithreadedTasks multithreadedTasks = new MultithreadedTasks(N_THREADS, threadNamePrefix);

        final RuntimeException exceptionThrown = assertThrows(
                RuntimeException.class,
                () -> multithreadedTasks.executeTask(nTimes, task));

        assertEquals(msg, exceptionThrown.getMessage(), "Exception message did NOT match expected message");
        assertTrue(throwExceptionAfterNtimes <= counter.get(), "MultithreadedTasks.executeTask did ended prematurely exceptionally");
        assertTrue(counter.get() <= nTimes, "MultithreadedTasks.executeTask did NOT end exceptionally");
    }

    @ParameterizedTest
    @EnumSource(MultithreadedTasks.ExecutionPolicy.class)
    public void excutionsCountDoesNotExceedHugeCount(
            final MultithreadedTasks.ExecutionPolicy executionPolicy
    ) {
        executionsCountDoesNotExceedCount(N_TIMES_HUGE, executionPolicy);
    }

    @Test
    public void excutionsCountDoesNotExceedHugeCount(
    ) {
        executionsCountDoesNotExceedCount(N_TIMES_HUGE, null);
    }

    @ParameterizedTest
    @EnumSource(MultithreadedTasks.ExecutionPolicy.class)
    public void excutionsCountDoesNotExceedLargeCount(
            final MultithreadedTasks.ExecutionPolicy executionPolicy
    ) {
        executionsCountDoesNotExceedCount(N_TIMES_LARGE, executionPolicy);
    }

    @Test
    public void excutionsCountDoesNotExceedLargeCount(
    ) {
        executionsCountDoesNotExceedCount(N_TIMES_LARGE, null);
    }

    @ParameterizedTest
    @EnumSource(MultithreadedTasks.ExecutionPolicy.class)
    public void excutionsCountDoesNotExceedSmallCount(
            final MultithreadedTasks.ExecutionPolicy executionPolicy
    ) {
        executionsCountDoesNotExceedCount(N_TIMES_SMALL, executionPolicy);
    }

    @Test
    public void excutionsCountDoesNotExceedSmallCount(
    ) {
        executionsCountDoesNotExceedCount(N_TIMES_SMALL, null);
    }

    @ParameterizedTest
    @EnumSource(MultithreadedTasks.ExecutionPolicy.class)
    public void excutionsCountDoesNotExceedEqualCount(
            final MultithreadedTasks.ExecutionPolicy executionPolicy
    ) {
        executionsCountDoesNotExceedCount(N_TIMES_EQUAL, executionPolicy);
    }

    @Test
    public void excutionsCountDoesNotExceedEqualCount(
    ) {
        executionsCountDoesNotExceedCount(N_TIMES_EQUAL, null);
    }

    @ParameterizedTest
    @EnumSource(MultithreadedTasks.ExecutionPolicy.class)
    public void excutionsCountDoesNotExceedTinyCount(
            final MultithreadedTasks.ExecutionPolicy executionPolicy
    ) {
        executionsCountDoesNotExceedCount(N_TIMES_TINY, executionPolicy);
    }

    @Test
    public void excutionsCountDoesNotExceedTinyCount(
    ) {
        executionsCountDoesNotExceedCount(N_TIMES_TINY, null);
    }

    @ParameterizedTest
    @EnumSource(MultithreadedTasks.ExecutionPolicy.class)
    public void excutionsDoesNotExceedCountOfOne(
            final MultithreadedTasks.ExecutionPolicy executionPolicy
    ) {
        executionsCountDoesNotExceedCount(N_TIMES_ONE, executionPolicy);
    }

    @Test
    public void excutionsDoesNotExceedCountOfOne(
    ) {
        executionsCountDoesNotExceedCount(N_TIMES_ONE, null);
    }

    //##########################################################################
    // Private helper methods
    //##########################################################################

    private void executionsCountDoesNotExceedCount(
            final int eventCount,
            final MultithreadedTasks.ExecutionPolicy executionPolicy
    ) {
        final AtomicInteger counter = new AtomicInteger();
        final Set<Integer> threadHashCodes = ConcurrentHashMap.newKeySet();

        final Runnable task = new Runnable() {
                @Override
                public void run() {
                    // might have used the name but names might change
                    final int threadHashCode = Thread.currentThread().hashCode();
                    counter.incrementAndGet();
                    threadHashCodes.add(threadHashCode);
                }
        };

        final MultithreadedTasks multithreadedTasks = new MultithreadedTasks(N_THREADS, threadNamePrefix);

        final int numberOfThreads;
        if (null == executionPolicy) {
            numberOfThreads = multithreadedTasks.executeTask(eventCount, task);
        } else {
            numberOfThreads = multithreadedTasks.executeTask(eventCount, task, executionPolicy);
        }

        assertEquals(eventCount, counter.get());
        assertTrue(N_THREADS >= threadHashCodes.size(), "Did use more threads (" + threadHashCodes.size() +  ") than supposed to: " + N_THREADS);

        log.debug("{} - Threads: {}/{} used.", testName, threadHashCodes.size(), numberOfThreads);
    }

}