package core;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Template for a fork-join task - should be overwritten by user-defined implementations.
 * @param <V> The type of the result of evaluating the task
 *           - can be void if the task is just an action, returning no value.
 */
abstract public class Task<V> {

    // Reference to the asynchronous evaluation of this task (if this task has been previously forked)
    // ... or the null pointer (if this task has never previously been forked).
    // Hence, implicitly, this atomic variable also records whether or not this task has previously been forked.
    private final AtomicReference<AsyncEvaluation<V>> referenceToEvaluation = new AtomicReference<>(null);

    // Marker, indicating whether or not this task has previously been joined.
    private final AtomicBoolean hasBeenJoined = new AtomicBoolean(false);

    /**
     * Computes the result of the evaluation.
     * This method defines the computation, and should be overwritten in subclasses.
     *
     * The compute() method should only be called from within a pool. Typically, the compute() method carries out the
     * computation using a divide-and-conquer approach. If the task is big, then it breaks it down into two sub-tasks.
     * It works on one of the sub-tasks synchronously by directly calling its compute() method.
     * Meanwhile, it sends the other sub-task for asynchronous computation in the pool by calling its
     * fork() method, and obtains the result of this asynchronous computation by calling its join() method.
     * The results of the two sub-tasks are then combined to give a final answer.
     *
     * (If we're outside of a pool and we wish to submit a task to the pool for computation, then we should NOT call
     *  task.compute() directly - instead, we should call pool.invoke(task).)
     *
     * @return The result of the computation.
     *         (This can be null, with the type V = Void, if the computation is merely an action.)
     */
    abstract public V compute();

    /**
     * Adds this task to the current worker's job queue, to be computed *asynchronously*.
     *
     * Rules about usage:
     * (1) .fork() should only be called from within a fork-join pool.
     * (2) .fork() should not be called more than once on any given task.
     * [On the other hand, there is no limit to how many times .compute() can be called on a given task...]
     *
     * The implementation guarantees a happens-before relationship between the forking thread entering the .fork() call
     * and the beginning of the evaluation of the task by the evaluating thread. (This ensures that the evaluating
     * thread sees the most up-to-date version of the data that it is operating on as of the time of the .fork() call.)
     *
     * @throws IllegalStateException if called from outside of a fork-join pool.
     * @throws IllegalStateException if called more than once.
     */
    public void fork() {

        // Get the AsyncEvalSampler object for the current thread.
        // Also verify that the current thread is indeed within a fork-join pool.
        AsyncEvalSampler sampler = ThreadManager.getSamplerForThread();
        if (sampler == null) {
            throw new IllegalStateException("Fork must be called from within a fork-join pool.");
        }

        // Create an AsyncEvaluation object for this task, and store a reference to this.
        // At the same time, verify that .fork() has not previously been called on this task, by verifying that
        // no previous reference to an AsyncEvaluation object has been stored for this task.
        AsyncEvaluation<V> evalOfThisTask = new AsyncEvaluation<>(this, sampler.getPool());
        AsyncEvaluation<V> prevEvalIfExists = referenceToEvaluation.getAndSet(evalOfThisTask);
        if (prevEvalIfExists != null) {
            throw new IllegalStateException("Fork must not be called more than once.");
        }

        // Add this evaluation job to the current thread's async job queue.
        sampler.add(evalOfThisTask);
    }

    /**
     * Retrieves the result of an asynchronous evaluation of this task, triggered by a previous fork call.
     * If the result is not yet unavailable, then the current thread will work on other jobs from its job queue
     * in the meantime (or steal from other workers' queues if its own queue is empty),
     * until the result of the evaluation of this task becomes available.
     *
     * Rules about usage:
     * (1) .join() should only be called from within a fork-join pool
     * (2) .join() should only be called after .fork() has been called on the same task.
     * (3) .join() should only be called from the same fork-join pool as the pool from which .fork() was called
     *      previously.
     * (4) .join() should not be called more than once.
     *
     * The implementation guarantees a happens-before relationship between the end of the evaluation of the task by
     * the evaluating thread, and the return of the .join() call by the joining thread. (So if the task is merely an
     * action, returning no value, then the results of performing this action are guaranteed to be visible to the
     * joining thread once it has returned from the .join() call. And of course, if the task does return a value,
     * then the value returned by .join() will be a true copy of the value evaluated by the evaluating thread.)
     *
     * @throws IllegalStateException if called from outside of a fork-join pool.
     * @throws IllegalStateException if called before join has been called before fork() has been called.
     * @throws IllegalStateException if called from a different pool to the one that originally called fork().
     * @throws IllegalStateException if called more than once.
     * @return The result of evaluating the task.
     */
    public V join() {

        // Retrieve the relevant AsyncEvalSampler object and AsyncEvaluation object.
        // Also verify that the conditions for calling join are met, and record the fact that join() has been called
        // to enable us to prevent a subsequent join() call later on.
        AsyncEvalSampler sampler = ThreadManager.getSamplerForThread();
        if (sampler == null) {
            throw new IllegalStateException("Join must only be called from within a fork-join pool.");
        }

        AsyncEvaluation<V> evalOfThisTask = referenceToEvaluation.get();
        if (evalOfThisTask == null) {
            throw new IllegalStateException("Join must not be called before fork has been called.");
        }

        if (sampler.getPool() != evalOfThisTask.getPoolUsed()) {
            throw new IllegalStateException("Join must be called from same fork-join pool as preceding fork call.");
        }

        boolean hasPreviouslyBeenJoined = hasBeenJoined.getAndSet(true);
        if (hasPreviouslyBeenJoined) {
            // This check isn't essential for correct execution, but preserves symmetry between fork() and join().
            throw new IllegalStateException("Join must not be called more than once.");
        }

        // Loop runs until the result of the evaluation of the task is available.
        while (!evalOfThisTask.isComplete()) {

            // If the result is not yet available, then try to work on another job from the current thread's queue
            // in the meantime (or if this thread's queue is empty, then try to steal a job from another queues
            // from within the same pool). (Note that there is a possibility that this other job is in fact the
            // same as the present task - which is okay!)
            AsyncEvaluation<?> evalOfAnotherTask = sampler.get();

            if (evalOfAnotherTask != null) {
                // Successfully found another job to work on in the meantime - proceed with computation.
                evalOfAnotherTask.runComputation();
            }

            /*
             To improve in future: Ideally the thread should wait here, until either it is notified that a new
             evaluation job has been forked, or until it is notified that the evaluation of the present task is
             complete. (At the moment, the thread just cycles the while loop, broken only by the periodic sleeps in
             the sampler.get() method.)
             */
        }

        // AsyncEvaluation of task is now available - return answer.
        return evalOfThisTask.getAnswer();
    }

}
