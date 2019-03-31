# LambdaBus

Tiny event bus for Java using lambda expressions and method references.

## Introduction

The LambdaBus defines the API of an event bus using class types as implicit topics for which `Consumer` can be subscribed.

Java 8 introduced lambda expressions and method references that can be used to create an abstraction for (almost any) event bus, allowing publishers and subscribers to be completely independent of the event bus itself.
Subscribers do not need to have any knowledge of the event bus, the publisher can use a generic `Consumer` reference.
This makes the code easy to write (and easy to change event bus implementations should the need arise).

There is no need for annotations, classes that use the event bus do not even need to know that an event bus exists, only the wiring parts need to know about it.

## "Hello World"

```java
public class HelloWorld {
    public static void main(String[] args) {
        final LambdaBus lb = ...

        /*
         * For every "String" event published to the bus
         * call System.out.println() with the String as
         * argument.
         */
        lb.subscribe(String.class, System.out::println);

        // publish a "String" event by posting to the bus
        lb.post("Hello World.");

        lb.close();
    }
}
```

## "Hello World" using method references of the bus

```java
public class HelloLambdaWorld {
    public static void main(String[] args) {
        final LambdaBus lb = ...

        /*
         * For every "String" event published to the bus
         * call System.out.println() with the String as
         * argument.
         */
        lb.subscribe(String.class, System.out::println);

        final Consumer<?> postRef = lb::post;

        // publish a "String" event via regular method call
        lb.post("Hello Old World.");

        /*
         * Publishes a "String" event via the method reference
         * of the event bus. The use of method references
         * avoids the need to "know" the event bus.
         */
        postRef.accept("Hello Lambda World.");

        lb.close();
    }
}
```

## Build

### Checkout

```sh
~$ git clone https://github.com/ViToni/lambdabus.git
```

### Building with Maven

```sh
~$ cd lambdabus
~$ mvn clean install
```

The build creates the library jars and makes result available in the `target/lib` folder and JavaDoc JARs in the `target/javadoc` folder.

```sh
~$ mvn site site:stage
```

This build creates the documentation and test coverage and the result is provided in the `target/site` folder.

## Gotcha!

Lambdas and method references are not objects!
The resulting far-reaching implications are that they do not support comparison using `equals()` / `hashCode()` as expected from regular objects.

This means that if an event handler is ever to be unsubscribed from the event bus, the reference returned by the `subscribe()` method must be kept, since it is the only reference that exists and there is no other way to identify this subscription!

The use of lambdas allows the `subscribe()` method to wrap the unsubscribe logic and return only a `Closable`, the closing of which unsubscribes the registered handler.

## tl;dr

While working on an embedded project, it became evident that using an event bus would make the code simpler and easier to test.
Tests would be easier to write because one would only have to worry about the input/output of a particular handler and the behavior of a particular handler could be tested independently.

Existing event bus implementations are usually part of larger projects.
Since size matters (especially for embedded), this seemed to be too much of a library dependency (also related to library updates that might not be related to the event bus itself).

In addition, using annotations to mark handler methods and/or provide threading hints seemed too invasive, since the code must be adapted to a specific event bus.
Since the consuming library should be largely agnostic to the event bus used, I looked for something different.

The use of lambda expressions and method references, available since Java 8, allows the creation of an event bus of which subscribers and publishers can be completely agnostic.
There is no need for annotations, the classes using the event bus do not even need to know that an event bus exists, only the wiring parts need to know about it.
