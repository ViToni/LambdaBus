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
package org.kromo.lambdabus.dispatcher;

import org.kromo.lambdabus.LambdaBusContract;
import org.kromo.lambdabus.dispatcher.impl.opt.QueuedEventDispatcher;

/**
 * Test for the {@link DispatchingLambdaBus} using the
 * {@link QueuedEventDispatcher}.
 * 
 * @author Victor Toni - initial implementation
 *
 */
public class DispatchingLambdaBus_QueuedEventDispatcherTest 
    extends LambdaBusContract<DispatchingLambdaBus> {

    protected DispatchingLambdaBus createLambdaBus() {
        return new DispatchingLambdaBus(new QueuedEventDispatcher());
    }

}
