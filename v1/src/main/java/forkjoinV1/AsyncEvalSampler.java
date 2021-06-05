package forkjoinV1;

/**
 * An AsyncEvalSampler object either belongs to one single pool worker, or is shared by all the external threads.
 * This object takes care of the way in which its owning thread(s) dumps/finds new asynchronous evaluation jobs,
 * including the possibility of stealing jobs from other threads' queues.
 */
class AsyncEvalSampler {

    // Job queue belonging to worker who owns this AsyncEvalSampler object.
    private final AsyncEvalQueue ownQueue;

    // Job queues owned by other workers, from which the current worker can steal jobs if its own queue is empty.
    // NB stealing is always attempted in sequential order, i.e. if the worker can't find a job from its ownQueue,
    // then it tries otherQueues[0], then otherQueues[1], then otherQueues[2], etc.
    private final AsyncEvalQueue[] otherQueues;

    // Reference to the pool to which this sampler belongs (only used for validation purposes).
    private final Pool pool;

    // Period of time to sleep, if no jobs found.
    private final long sleepMillis;

    AsyncEvalSampler(AsyncEvalQueue ownQueue, AsyncEvalQueue[] otherQueues, Pool pool, int sleepMillis) {
        this.ownQueue = ownQueue;
        this.otherQueues = otherQueues;
        this.pool = pool;
        this.sleepMillis = sleepMillis;
    }

    void add(AsyncEvaluation<?> evalJob) {
        ownQueue.add(evalJob);
    }

    AsyncEvaluation<?> get() {
        // First, try to get a job from the worker's own queue.
        AsyncEvaluation<?> evalJob = ownQueue.get();
        if (evalJob != null) {
            return evalJob;
        }

        // If the worker's own queue is empty, then try to steal evaluation jobs from other queues.
        for (AsyncEvalQueue queue : otherQueues) {
            AsyncEvaluation<?> stolenEvalJob = queue.steal();
            if (stolenEvalJob != null) {
                return stolenEvalJob;
            }
        }

        // No evaluation jobs found anywhere - return null after a brief sleep.
        try {
            Thread.sleep(sleepMillis);
        } catch (InterruptedException ex) {
            // Ignore this exception - continue as usual.
        }
        return null;
    }

    Pool getPool() {
        return pool;
    }

}
