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
package org.kromo.lambdabus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Tests for the {@link DeadEvent} class.
 *
 * @author Victor Toni - initial implementation
 *
 */
public class DeadEventTest {

    protected static final int ONE = 1;

    @Test
    public void construtor() {
        new DeadEvent(new Object());
    }

    @Test
    public void construtorThrowsNPEOnNull() {
        assertThrows(
                NullPointerException.class,
                () -> new DeadEvent(null));
    }

    @Test
    public void testEqualsOnIdentity() {
        final Object event = new Object();

        final DeadEvent deadEvent = new DeadEvent(event);

        assertEquals(deadEvent, deadEvent, "Events should be identical");
    }

    @Test
    public void testEquals() {
        final Object event = new Object();

        final int count = 10;

        final Set<DeadEvent> set = new HashSet<>();
        final List<DeadEvent> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            final DeadEvent deadEvent = new DeadEvent(event);

            // adding to the Set should not increase the set size beyond 1
            set.add(deadEvent);
            assertEquals(ONE, set.size(), "Events are not identical");

            // compare all DeadEvents created yet
            list.add(deadEvent);
            assertEquals(i + ONE, list.size(), "No events added");
            for (int j = 0; j < i; j++) {
                assertEquals(list.get(j), deadEvent, "Events are not identical");
            }
        }
    }

    @Test
    public void testNotEquals() {
        final Object event = new Object();

        final DeadEvent deadEvent = new DeadEvent(event);

        assertNotEquals(deadEvent, event, "Events should not be equal");
    }

    @Test
    public void testHashCode() {
        final Object event = new Object();

        final int count = 10;

        final List<DeadEvent> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            final DeadEvent deadEvent = new DeadEvent(event);
            list.add(deadEvent);
            assertEquals(i+1, list.size(), "No events added");

            // compare hashCode of all DeadEvents created yet
            for (int j = 0; j < i; j++) {
                assertEquals(list.get(j).hashCode(), deadEvent.hashCode(), "Events do not have the same hashCode");
            }
        }
    }

    @Test
    public void identityOfEventAndStoredEvent() {
        final Object event = new Object();
        final DeadEvent deadEvent = new DeadEvent(event);

        assertTrue(event == deadEvent.event, "Events are not identical");
    }

}
