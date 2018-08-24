package core;

import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;

// Manages a worker's pending evaluation tasks
// (will have one for each worker in pool, plus an extra one for the external threads)
class EvalQueue {

    // Queue of evaluation jobs not yet started - owned by a single worker (or possibly by the external threads)
    // (This worker accesses the front of the queue. Other workers steal from the back.)
    private Deque<Evaluation<?>> pendingEvalJobs = new ConcurrentLinkedDeque<>();

    void add(Evaluation<?> evalJob) {
        pendingEvalJobs.offerFirst(evalJob);
    }

    Evaluation<?> get() {
        return pendingEvalJobs.pollFirst(); // null if deque is empty
    }

    Evaluation<?> steal() {
        return pendingEvalJobs.pollLast(); // null if deque is empty
    }

}
