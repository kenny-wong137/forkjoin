package core;

// An EvalSampler object belongs either to one worker, or to the external threads.
// This object takes care of the way in which the worker dumps/finds new jobs (including stealing from other workers)
class EvalSampler {

    private EvalQueue ownQueue; // belongs to current worker (or to the external workers, if this is the external sampler)
    private EvalQueue[] otherQueues; // queues from which the current worker can steal jobs, if its own queue is empty

    EvalSampler(EvalQueue ownQueue, EvalQueue[] otherQueues) {
        this.ownQueue = ownQueue;
        this.otherQueues = otherQueues;
    }

    void add(Evaluation<?> evalJob) {
        // Any new evaluation jobs forked by the current worker go in the worker's own queue.
        ownQueue.add(evalJob);
    }

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

        // No evaluation jobs found anywhere - return null after a short pause
        return null;
    }

}
