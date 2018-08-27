package core;

import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Template for a fork-join task - should be overwritten by user-defined implementations.
 * @param <V> The type of the result of evaluating the task
 *           - can be void if the task is just an action, returning no value.
 */
abstract public class Task<V> {

    // Stack, storing successive attempts at forking this task.
    // (In normal usage, we would only fork a task once and join a task once; however, this implementation does permit
    //  a task to be forked/joined multiple times. The rule here is "last-in-first-out", i.e. the first join call will
    //  retrieve the result of the last fork, and so on.)
    private Deque<Evaluation<V>> evalAttempts = new ConcurrentLinkedDeque<>();


    /**
     * Compute the result of the evaluation.
     * This method defines the computation, and should be overwritten in subclasses.
     * @return The result of the computation.
     *         (This can be null, with the type V = Void, if the computation is merely an action.)
     */
    abstract public V compute();


    /**
     * Adds this task to the current worker's job queue, to be computed asynchronously.
     * This method should only be called from within a fork-join pool.
     *
     * The implementation guarantees a happens-before relationship between the forking thread entering the .fork() call
     * and the beginning of the evaluation of the task by the evaluating thread. (This ensures that the evaluating
     * thread sees the most up-to-date version of the data that it is operating on as of the time of the .fork() call.)
     *
     * @throws IllegalStateException if called from outside of a fork-join pool.
     */
    public void fork() {

        Evaluation<V> evalOfThisTask = new Evaluation<>(this);

        // Place a record of this evaluation job on the stack of evaluation attempts of the present task,
        // for LIFO retrival by subsequent .join() calls on this same task.
        evalAttempts.offerLast(evalOfThisTask);

        // Add this evaluation job to the current thread's job queue.
        ThreadManager.getSamplerForThread().add(evalOfThisTask);
    }


    /**
     * Retrieves the result of an asynchronous evaluation of this task, triggered by a previous fork call.
     * If the result is unavailable, then the current thread will in the meantime work on other jobs in its job queue,
     * or steal from other workers' queues if its own queue is empty, until the result becomes available.
     *
     * In case of multiple fork/join calls on a single task, the successive join calls retrieve the results of the
     * evaluations of previous fork calls in last-in-first-out order. (For this to make sense, it is necessary that the
     * number of join calls made on a given task never exceeds the number of fork calls.)
     *
     * The join method should only be called from within a fork-join pool.
     *
     * The implementation guarantees a happens-before relationship between the end of the evaluation of the task by
     * the evaluating thread, and the return of the .join() call by the joining thread. (So if the task is merely an
     * action, returning no value, then the results of performing this action are guaranteed to be visible to the
     * joining thread once it has returned from the .join() call. And of course, if the task does return a value,
     * then the value returned by .join() will be a true copy of the value evaluated by the evaluating thread.)
     *
     * @throws IllegalStateException if called from outside of a fork-join pool.
     * @throws IllegalStateException if join has been called more often than fork.
     * @return The result of evaluating the task.
     */
    public V join() {

        // Retrieve an evaluation attempt of this task from the stack of all evaluation attempts of this task
        // that have previously been forked.
        Evaluation<V> evalOfThisTask = evalAttempts.pollLast();

        if (evalOfThisTask != null) {

            while (!evalOfThisTask.isComplete()) {

                // If the result is not yet available, then try to work on another job from the current thread's queue
                // in the meantime (or if this thread's queue is empty, then try to steal a job from another queues
                // from within the same pool)
                Evaluation<?> evalOfAnotherTask = ThreadManager.getSamplerForThread().get();

                if (evalOfAnotherTask != null) {
                    // Case: successfully found another job to work on in the meantime - proceed with computation.
                    evalOfAnotherTask.runComputation();
                }
                else {
                    /*
                     TODO: Ideally the thread should wait here, until either it is notified that a new evaluation job
                     has been forked, or until it is notified that the evaluation of the present task is complete.
                     (At the moment, the thread just goes round and round the while loop.)
                     */
                }
            }

            // Once the result of the evaluation of the present task is available,
            // then we are ready to return the answer.
            return evalOfThisTask.getAnswer();
        }
        else {
            throw new IllegalStateException("Task has been joined more times than it has been forked.");
        }
    }

}
