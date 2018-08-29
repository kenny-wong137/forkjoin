package core;

// An AsyncEvalSampler object either belongs to one single pool worker, or is shared by all the external threads.
// This object takes care of the way in which its owning thread(s) dumps/finds new asynchronous evaluation jobs,
// including the possibility of stealing jobs from other threads' queues.
class AsyncEvalSampler {

    // Job queue belonging to worker who owns this AsyncEvalSampler object.
    private final AsyncEvalQueue ownQueue;

    // Job queues owned by other workers, from which the current worker can steal jobs if its own queue is empty.
    // NB stealing is always attempted in sequential order, i.e. if the worker can't find a job from its ownQueue,
    // then it tries otherQueues[0], then otherQueues[1], then otherQueues[2], etc.
    private final AsyncEvalQueue[] otherQueues;

    // Reference to the pool to which this sampler belongs.
    private final Pool pool;

    // Period of time to sleep, if no jobs found.
    private final int sleepNanos;

    // Constructor.
    AsyncEvalSampler(AsyncEvalQueue ownQueue, AsyncEvalQueue[] otherQueues, Pool pool, int sleepNanos) {
        this.ownQueue = ownQueue;
        this.otherQueues = otherQueues;
        this.pool = pool;
        this.sleepNanos = sleepNanos;
    }

    // Called when the worker forks a task.
    // Any new evaluation jobs forked by the current worker go in the worker's own queue.
    void add(AsyncEvaluation<?> evalJob) {
        ownQueue.add(evalJob);
    }

    // Called when a worker is looking for a new task to start after completing its previous one.
    // Also called when a thread is joining on task that hasn't yet completed, and is trying to find a different task
    // to get on with in the meantime.
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

        // No evaluation jobs found anywhere - return null after a brief pause
        try {
            Thread.sleep(0, sleepNanos);
        }
        catch (InterruptedException ex) {
            // Ignore this exception - continue as usual.
        }
        return null;
    }

    // Gets reference to the pool to which this sampler belongs.
    Pool getPool() {
        return pool;
    }

}
