package core;

import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;

// Manages a worker's pending asynchronous evaluation jobs.
// (will have one for each worker in pool, plus an extra one used collectively by all external threads.)
class AsyncEvalQueue {

    // Queue of evaluation jobs not yet started
    // - owned by a single worker (or possibly owned collectively by the external threads)
    private final Deque<AsyncEvaluation<?>> pendingEvalJobs = new ConcurrentLinkedDeque<>();

    // Note: The worker who owns this queue accesses it front from the front. Other workers steal jobs from the back.

    // Called when forking.
    void add(AsyncEvaluation<?> evalJob) {
        pendingEvalJobs.offerFirst(evalJob);
    }

    // Called when joining, or when looking for a new job to start after a previous job has been completed.
    // (Only used by the worker that owns this AsyncEvalQueue.)
    AsyncEvaluation<?> get() {
        return pendingEvalJobs.pollFirst();
        // null if deque is empty
    }

    // Called when joining, or when looking for a new job to start after a previous job has been completed.
    // (Only used when stealing jobs from other workers' EvalQueues.)
    AsyncEvaluation<?> steal() {
        return pendingEvalJobs.pollLast();
        // null if deque is empty
    }

}
