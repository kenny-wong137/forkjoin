package core;

import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Template for a fork-join task.
 * @param <V> The type of the result of evaluating the task.
 */
abstract public class Task<V> {

    private Deque<Evaluation<V>> evalAttempts = new ConcurrentLinkedDeque<>();
        // stores successive attempts at forking this task (though in normal usage, we would only fork a task once)

    /**
     * Compute the result of the evaluation.
     * @return Result
     */
    abstract public V compute();

    /**
     * Adds this task to the current worker's job queue, to be computed asynchronously.
     * (Must be called from within a fork-join pool.)
     */
    public void fork() {
        Evaluation<V> evalJob = new Evaluation<>(this);
        evalAttempts.offerLast(evalJob);
        // Add to the current thread's job queue.
        ThreadManager.getSamplerForThread().add(evalJob);
    }

    /**
     * Retrieves the result of forking this task.
     * If the result is unavailable, then the current thread will work on other job in the worker's queue, or steal from
     * other workers' queues if its own queue is empty, until the result is available.
     * (Must be called from within a fork-join pool.)
     * (If the task has been forked/joined multiple times, then the joins will retrieve the evaluations in "reverse order".
     *  A task must not be joined more times than it has been forked.)
     * @return The result of evaluating the task.
     */
    public V join() {
        Evaluation<V> evalJob = evalAttempts.pollLast();
        if (evalJob != null) {
            while (!evalJob.isComplete()) {
                // If the result is not yet available, then work on other jobs from this thread's queue
                // (or if this thread's queue is empty, then steal jobs from other queues from within the same pool)
                Evaluation<?> otherEvalJob = ThreadManager.getSamplerForThread().get();
                if (otherEvalJob != null) {
                    otherEvalJob.runComputation();
                }
            }
            return evalJob.getAnswer();
        } else {
            throw new IllegalStateException("Task has been joined more times than it has been forked.");
        }
    }

}
