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
 *     Victor Toni - initial API
 *******************************************************************************/
package org.kromo.lambdabus;

import java.util.function.Consumer;

/**
 * This interface defines the API of an event bus using class types as
 * implicit topics for which {@link Consumer}s can be subscribed. Events
 * are published by posting them to the bus using the {@link #post(Object)}
 * or {@link #post(Object, ThreadingMode)} method defined in the interface.
 * <blockquote><pre>{@code
final LambdaBus lb = ...

// for every "String" event published to the bus call System.out.println() with this String as parameter
lb.subscribe(String.class, System.out::println);

// publish a "String" event by posting to the bus
lb.post("Hello World.");
 * }</pre></blockquote>
 *
 * <h2>Using method references</h2>
 * <p>
 * Another approach is to use a reference to the {@link #post(Object)} method so that
 * publisher can be agnostic about the event bus itself.</p>
 * <blockquote><pre>{@code
final LambdaBus lb = ...

// for every "String" event published to the bus call System.out.println() with this String as parameter
lb.subscribe(String.class, System.out::println);

final Consumer<?> postRef = lb::post;

// publish a "String" event via regular method call
lb.post("Hello Old World.");

// publish a "String" event via the method reference of the event bus
// using method references avoids needing to "know" about the event bus
postRef.accept("Hello Lambda World.");
 * }</pre></blockquote>
 *
 * <h2>Subscription and subscriber lookup</h2>
 * <p>
 * Subscription is mainly based on classes but {@link Consumer}s might also subscribe
 * to an interface. Interface lookup will occur only if no subscriber for the
 * class of the event has been found.<br>
 * The event bus will scan the event's class for its directly implemented interfaces, lookup
 * of sub-classes and interfaces of sub-classes is omitted for simplicity and performance
 * reasons.</p>
 * <blockquote><pre>{@code
class BaseImpl
    implements A, B, C {}

class SubImpl extends BaseImpl
    implements X, Y, Z {}
 * }</pre></blockquote>
 *
 * Subscribers for {@code BaseImpl} will receive only events of type {@code BaseImpl},
 * events of subclasses such as {@code SubImpl} won't match.<br>
 * Subscriptions for {@code A}, {@code B} or {@code C} will only receive events of type
 * {@code BaseImpl} if there is no {@link Consumer} subscribed for {@code BaseImpl}.<br>
 * Lookup of subscribers will occur in order of appearance in the class file, e.g. for an
 * event of type {@code BaseImpl} lookup will search in this order for matching subscribers:<br>
 * <blockquote><pre>
 *
 * BaseImpl =&gt; A =&gt; B =&gt; C
 *
 * </pre></blockquote>
 *
 * First match wins. If there is no subscriber for {@code BaseImpl} but
 * subscribers for {@code B} and {@code C} an event of type {@code BaseImpl}
 * will be dispatched only to subscribers of {@code B} as the {@code B}
 * interface is mentioned before the {@code C} interface.<br>
 * <p>
 * A subscription for {@code X}, {@code Y} or {@code Z} will receive events of
 * type {@code SubImpl} only if there is no {@link Consumer} for
 * {@code SubImpl}.<br>
 * Events of type {@code SubImpl} will never be dispatched to subscribers of
 * {@code BaseImpl}, {@code A}, {@code B} or {@code C}.
 * </p>
 *
 * <h2>Dead events - events without subscribers</h2>
 * Events for which no subscriber could be found (neither class nor directly implemented interfaces)
 * will be posted to the bus wrapped in an instance of {@link DeadEvent}.<br>
 * To avoid creation of unused objects a {@link DeadEvent} should be created only if there is a subscriber
 * for the {@link DeadEvent} class.<br>
 * A use case could be to log such events to detect missing subscriptions.
 *
 * <h2>Publishers &amp; Subscribers</h2>
 * <p>
 * The {@link LambdaBus#post(Object)} can be passed as a method reference in form of a {@link Consumer}.<br>
 * Publisher using the {@link Consumer} have no dependency to {@link LambdaBus} implementations.</p>
 * <blockquote><pre>{@code
final LambdaBus lb = ...

final Consumer<?> postRef = lb::post;

// publish a "String" event via the method reference of the event bus
// using method references avoids needing to "know" about the event bus
postRef.accept("Hello Lambda World.");
}</pre></blockquote>
 *
 *
 * <p>
 * A subscriber is just a {@link Consumer} associated with a class or interface and does not need any changes
 * on the side of the subscriber.<br>
 * Subscribers are totally agnostic about the event bus.</p>
 * <blockquote><pre>{@code
final LambdaBus lb = ...

// for every String published call System.out.println() with this String as parameter
lb.subscribe(String.class, System.out::println);
}</pre></blockquote>
 *
 * <p>Lambda expressions might be used directly as {@link Consumer}s.</p>
 * <blockquote><pre>{@code
final LambdaBus lb = ...

// for every String published call System.out.println() with this String as parameter
lb.subscribe(String.class, (str) -> System.out.println("Received event: " + str));
}</pre></blockquote>
 *
 *
 * <h2>Behavior</h2>
 * <p>
 * Publishing and dispatching is supposed to work in multi-threaded applications.<br>
 * Wiring of publishers and subscribers is expected to be done a start time but might
 * also work dynamically at runtime.
 * </p>
 * <p>
 * Implementation of this interface have to adhere to the contract specified by
 * the tests in the class {@code LambdaBusContract}.
 * </p>
 *
 * @author Victor Toni - initial API
 *
 */
public interface LambdaBus
    extends AutoCloseable {

    /**
     * Posts an event to the bus.<br>
     * <p>
     * The event will be dispatched based on its {@link Class}. If no subscriber
     * was registered for the {@link Class} of the event, subscribers will be
     * searched by the directly implemented interfaces of the event. Dispatching
     * will occur for the first matching interface if any was found.
     * </p>
     *
     * @param <T>
     *            type of the event
     * @param event
     *            object to be dispatched
     * @throws IllegalStateException
     *             if the bus has been closed already
     */
    <T> void post(final T event);

    /**
     * Posts an event to the bus using the given {@link ThreadingMode} if
     * supported.<br>
     * <p>
     * The event will be dispatched based on its class. If no subscriber was
     * registered for the class of the event, subscribers will be searched by
     * the directly implemented interfaces of the event. Dispatching will occur
     * for the first matching interface if any was found.
     * </p>
     *
     * @param <T>
     *            type of the event
     * @param event
     *            object to be dispatched
     * @param threadingModeHint
     *            {@link Enum} indicating the bus how the event should be
     *            dispatched, if the value is not supported the default behavior
     *            of {@link #post(Object)} should be applied.
     * @throws NullPointerException
     *             if threadingModeHint is {@code null}
     * @throws IllegalStateException
     *             if the bus has been closed already
     */
    <T> void post(final T event, final ThreadingMode threadingModeHint);

    /**
     * Subscribe a consumer for events of a given class or interface.
     *
     * <p>
     * By convention the {@link Class} of an event is evaluated first for
     * subscriber lookup.<br>
     * If there are is no matching subscriber for the {@code Class} of an event
     * the directly implemented interfaces of the event will be evaluated in
     * order of appearance.<br>
     * Dispatching will occur for the first matching interface if any was found.
     * </p>
     *
     * @param <T>
     *            the type of events subscription is for
     * @param eventClass
     *            non-{@code null} {@link Class} of events the subscription is
     *            for
     * @param eventSubscriber
     *            non-{@code null} {@link Consumer} which should get events of
     *            the given class
     * @return non-{@code null} {@link Subscription} which will unsubscribe
     *             the consumer from the bus on closure
     * @throws NullPointerException
     *             if any of {@code  eventClass} or {@code eventSubscriber} is
     *             {@code null}
     * @throws IllegalStateException
     *             if the bus has been closed already
     */
    <T> Subscription subscribe(final Class<T> eventClass, final Consumer<T> eventSubscriber);

    /**
     * Checks if there is any {@link Consumer} subscribed for the given
     * {@link Class}.
     *
     * @param <T>
     *            the type of event
     * @param eventClass
     *            event {@link Class} to check subscribers for
     * @return {@code true} if any subscriber was found, {@code false}
     *             otherwise
     */
    <T> boolean hasSubscriberForClass(final Class<T> eventClass);

    /**
     * Close event bus and unsubscribe consumers.
     * <p>
     * Posting events to the bus or subscribing to the bus after
     * {@code close()} is expected to throw an {@link IllegalStateException}.
     * </p>
     *
     * @throws IllegalStateException
     *             if the bus has been closed already
     */
    @Override
    void close();
}
