# LambdaBus

Tiny event bus for Java using lambda expressions and method references.

## Introduction

The LambdaBus defines the API of an event bus using class types as implicit topics for which `Consumer` can be subscribed.

Java 8 provides lambda expressions and method references which can be used to create an abstraction for (nearly any) event bus and allows publishers and subscribers to be totally agnostic about the bus itself. Subscribers do not need to know about the bus at all, publishers can use a generic `Consumer` reference. This makes the code easy to write (and easy to change event bus implementations if the need should arise).

There is no need for annotations, classes using the event bus don't even have to know that an event bus exists only the wiring parts needs to know about it.

## "Hello World"

```java
public class HelloWorld {
    public static void main(String[] args) {
        final LambdaBus lb = ...;

        /*
         * For every "String" event published to the bus
         * call System.out.println() with the String as
         * parameter.
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
         * parameter.
         */
        lb.subscribe(String.class, System.out::println);

        final Consumer<?> postRef = lb::post;

        // publish a "String" event via regular method call
        lb.post("Hello Old World.");

        /*
         * Publish a "String" event via the method reference
         * of the event bus. Using method references
         * avoids needing to "know" about the event bus.
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

The build will create the library jars and make result available in the folder `target/lib` and JavaDoc JARs in the folder `target/javadoc`.

```sh
~$ mvn site site:stage
```

This build will create the documentation and test coverage and the result will be available in the folder `target/site`

## Gotcha!

Lambdas and method references are not objects! This has some serious implications as they don't have and don't support (as of Java 9) comparison or `equals()` / `hashCode()` as one expects from regular Objects.

Means if an event handler should ever be unsubscribed from the event bus the reference returned by `subscribe()` has to be kept since this is the only reference that exists!
Using Lambdas allows the `subscribe()` method to wrap the unsubscribe logic and return just a `Closable`, by closing it the registered handler is unsubscribed.

## tl;dr

While working on an embedded project it became apparent that using an event bus would make the code simpler and easier to test. Tests would be easier to write because one has only to take care of the input / output of a specific handler and the behavior of a specific handler could be tested stand alone.

Existing event bus implementations are mostly parts of bigger projects. As size matters (especially for embedded) this seemed too much as a library dependency (also in the context of library updates which might be not related to event bus itself).
Furthermore the use of annotations to mark handler methods and/or provide threading hints seemed to invasive because code has to be adapted to a specific event bus. As the consuming library should be mostly agnostic of the event bus used I looked for something different.

Using Java 8 lambda expressions and method enables creating an event bus which subscribers and publishers can be totally agnostic of. There is no need for annotations, the classes using the event bus don't even have to know that an event bus exists only the wiring parts needs to know about it.
