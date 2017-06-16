# LambdaBus

Tiny event bus for Java using lambda expressions and method references.

## Introduction

The LambdaBus defines the API of an event bus using class types as implicit topics for which `Consumer` can be subscribed.

Java 8 provides lambda expressions and method references which can be used to create an abstraction for (nearly any) event bus and allows publisher and subscriber to be totally agnostic about the bus itself. Subscriber do not need to know about the bus at all, publisher can use a generic `Consumer` reference. This makes the code easy to write (and easy to change event bus implementations if the need should arise).

There is no need for annotations, classes using the event bus don't even have to know that an event bus exists only the wiring parts needs to know about it.


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


## tl;dr

While working on an embedded project it became apparent that using an event bus would make the code simpler and easier to test. Tests would be easier to write because one has only to take care of the input / output of a specific handler and the behavior of a specific handler could be tested stand alone.

Existing event bus implementations are mostly parts of bigger projects. As size matters (especially for embedded) this seemed too much as a library dependency (also in the context of library updates which might be not related to event bus itself).
Furthermore the use of annotations to mark handler methods and/or provide threading hints seemed to invasive because code has to be adapted to a specific event bus. As the consuming library should be mostly agnostic of the event bus used I looked for something different.

Using Java 8 lambda expressions and method enables creating an event bus which subscriber and publisher can be totally agnostic of. There is no need for annotations, the classes using the event bus don't even have to know that an event bus exists only the wiring parts needs to know about it.
