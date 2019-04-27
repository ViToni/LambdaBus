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
 *     Victor Toni - initial API and implementation
 */
package org.kromo.lambdabus.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.kromo.lambdabus.DeadEvent;

/**
 * Class used to log events for which no subscriber was found.
 *
 * @author Victor Toni - initial API and implementation
 *
 */
public final class DeadEventLogger {

    private final Logger logger = LoggerFactory.getLogger(DeadEventLogger.class);

    /**
     * Logs events (wrapped inside a {@link DeadEvent}) for which no subscriber was
     * found.
     *
     * @param deadEvent
     *            container for event for without subscribers
     */
    public void logDeadEvent(final DeadEvent deadEvent) {
        logger.debug("No subscribers found for event: {}", deadEvent.event);
    }

}
