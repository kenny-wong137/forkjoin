package core;

// An EvalSampler object belongs either to one worker, or to the external threads.
// This object takes care of the way in which the worker dumps/finds new jobs (including stealing from other workers).
class EvalSampler {

    // Job queue belonging to current worker
    private final EvalQueue ownQueue;

    // Job queues owned by other workers, from which the current worker can steal jobs, if its own queue is empty.
    // NB stealing is always attempted in sequential order, i.e. if the worker can't find a job from its ownQueue,
    // then it tries otherQueues[0], then otherQueues[1], then otherQueues[2], etc.
    private final EvalQueue[] otherQueues;

    private static final int SLEEP_NANOSECONDS = 10000; // period of time to sleep if no tasks found.


    EvalSampler(EvalQueue ownQueue, EvalQueue[] otherQueues) {
        this.ownQueue = ownQueue;
        this.otherQueues = otherQueues;
    }

    // Called when the worker forks a task.
    // Any new evaluation jobs forked by the current worker go in the worker's own queue.
    void add(Evaluation<?> evalJob) {
        ownQueue.add(evalJob);
    }

    // Called when a worker is looking for a new task to do.
    // Also called when a worker is joining a task that hasn't completed, and is trying to find a different task
    // to get on with in the meantime.
    Evaluation<?> get() {

        // First, try to get a job from the worker's own queue.
        Evaluation<?> evalJob = ownQueue.get();
        if (evalJob != null) {
            return evalJob;
        }

        // If the worker's own queue is empty, then try to steal evaluation jobs from other queues.
        for (EvalQueue queue : otherQueues) {
            Evaluation<?> stolenEvalJob = queue.steal();
            if (stolenEvalJob != null) {
                return stolenEvalJob;
            }
        }

        // No evaluation jobs found anywhere - return null after a brief pause
        try {
            Thread.sleep(0, SLEEP_NANOSECONDS);
        }
        catch (InterruptedException ex) {
            // ignore this exception - continue as usual
        }
        return null;
    }

}
