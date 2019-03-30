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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.kromo.lambdabus.util.ThreadingAssertions;

/**
 * Tests for the {@link SynchronousLambdaBus}.
 *
 * @author Victor Toni - initial implementation
 *
 */
public class SynchronousLambdaBusTest
    extends AbstractLambdaBusContract<SynchronousLambdaBus> {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    protected SynchronousLambdaBus createLambdaBus() {
        return new SynchronousLambdaBus();
    }

    /**
     * Test that all events are processed in order.<br>
     * If posting would be asynchronously a previous dispatching might be not
     * completed yet.
     */
    @Test
    @DisplayName("All events are processed in order")
    public void events_are_processed_in_order() {
        ThreadingAssertions.assertThatEventsAreProcessedInOrder(
                EVENTS_OF_TYPE_A_COUNT,
                this::createLambdaBus,
                (lb, event) -> lb.post(event),
                logger);
    }

    /**
     * Events posted to the {@link SynchronousLambdaBus} are directly dispatched
     * to their subscriber means in the same thread.<br>
     * This is tested by this test.<br>
     */
    @Test
    @DisplayName("Dispatching is done in caller thread")
    public void disptaching_is_done_in_caller_thread() {
        ThreadingAssertions.assertDisptachingIsDoneInCallerThread(
                EVENTS_OF_TYPE_A_COUNT,
                THREAD_COUNT,
                this::createLambdaBus,
                (lb, event) -> lb.post(event),
                getClass(),
                logger);
    }

}
