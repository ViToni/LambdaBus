package org.kromo.lambdabus.examples;

import org.kromo.lambdabus.LambdaBus;
import org.kromo.lambdabus.dispatcher.EventDispatcher;
import org.kromo.lambdabus.dispatcher.impl.SynchronousEventDispatcher;
import org.kromo.lambdabus.impl.DispatchingLambdaBus;

public class HelloWorld {

    public static void main(String[] args) {
        // SynchronousEventDispatcher is the simplest EventDispatcher
        final EventDispatcher eventDispatcher = new SynchronousEventDispatcher();
        final LambdaBus lb = new DispatchingLambdaBus(eventDispatcher);

        /*
         * For every "String" event published to the bus
         * call System.out.println() with the String as
         * parameter.
         */
        lb.subscribe(String.class, System.out::println);

        // publish a "String" event to the bus
        lb.post("Hello World.");

        lb.close();
    }
}
