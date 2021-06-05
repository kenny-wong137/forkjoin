Fork-join pool exercise
======

Two implementations of fork-join pools in Java.
Very slow compared to the official implementation, but the exercise has taught me loads about Java concurrency.

**Version 1:**
My learning objective here is to understand the Java memory model (i.e. happens-before relations).
The only dependencies I import are the collections in `java.util` and `java.util.concurrent`.
In this implementation, each worker has its own task queue.

**Version 2:**
This version is about helping me understand how to use `.wait()` and `.notify()`.
Everything is written using primitive Java concepts - there are no imports whatsoever.
This implementation uses a single shared task queue for the whole pool.
