package core;

import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;

// Manages a worker's pending evaluation tasks.
// (will have one for each worker in pool, plus an extra one used collectively by all external threads.)
class EvalQueue {

    // Queue of evaluation jobs not yet started - owned by a single worker (or possibly owned collectively by the
    // external threads)
    private final Deque<Evaluation<?>> pendingEvalJobs = new ConcurrentLinkedDeque<>();

    // Note: The worker who owns this queue accesses it front from the front. Other workers steal jobs from the back.

    // Called when forking.
    void add(Evaluation<?> evalJob) {
        pendingEvalJobs.offerFirst(evalJob);
    }

    // Called when joining, or when looking for a new task to do after a previous task has been completed.
    // (Only used by the worker that own this EvalQueue)
    Evaluation<?> get() {
        return pendingEvalJobs.pollFirst(); // null if deque is empty
    }

    // Called when joining, or when looking for a new task to do after a previous task has been completed.
    // (Only used when stealing jobs from other workers EvalQueues)
    Evaluation<?> steal() {
        return pendingEvalJobs.pollLast(); // null if deque is empty
    }

}
