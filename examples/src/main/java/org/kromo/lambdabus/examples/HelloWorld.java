package org.kromo.lambdabus.examples;

import org.kromo.lambdabus.LambdaBus;
import org.kromo.lambdabus.impl.SynchronousLambdaBus;

public class HelloWorld {

    public static void main(String[] args) {
        // SyncLambdaBus is the simplest LambdaBus 
        final LambdaBus lb = new SynchronousLambdaBus();

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
