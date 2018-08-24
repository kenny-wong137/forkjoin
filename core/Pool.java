package core;

/**
 * My simple implementation of a fork-join pool.
 * In this implementation, each worker in the pool has its own job queue. There is also an external job queue, owned
 * by external thread(s) that submit tasks to this pool. (External thread(s) effectively become part of the pool, until
 * the task(s) they submitted are complete.) Workers are able to steal jobs from other workers' queues,
 * if their own queues are empty. The workers in this pool will continue to be active until the pool is terminated.
 */
public class Pool {

    private EvalSampler externalSampler; // to/from which external threads dump/find tasks
    private Worker[] workers;
    private boolean terminated = false;

    /**
     * Constructor
     * @param numWorkers Number of workers in the pool (must be non-negative)
     */
    public Pool(int numWorkers) {
        if (numWorkers < 0) {
            throw new IllegalArgumentException("Number of workers must be positive");
        }

        // Make evaluation queues.
        EvalQueue[] allQueues = new EvalQueue[numWorkers + 1]; // the final "worker" is really the external thread(s)
        for (int index = 0; index < numWorkers + 1; index++) {
            allQueues[index] = new EvalQueue();
        }

        // Make evaluation samplers.
        EvalSampler[] allSamplers = new EvalSampler[numWorkers + 1]; // the final sampler is the external one
        for (int index = 0; index < numWorkers + 1; index++) {
            EvalQueue ownQueue = allQueues[index]; // the worker's own queue
            EvalQueue[] otherQueues = new EvalQueue[numWorkers]; // for stealing from other workers
            for (int otherIndex = 0; otherIndex < numWorkers; otherIndex++) {
                // adding other workers' queues in "cyclic" order
                otherQueues[otherIndex] = allQueues[(index + otherIndex + 1) % (numWorkers + 1)];
            }
            allSamplers[index] = new EvalSampler(ownQueue, otherQueues);
        }

        // Assign evaluation samplers to workers; assign external sampler to the pool.
        workers = new Worker[numWorkers];
        for (int index = 0; index < numWorkers; index++) {
            workers[index] = new Worker(allSamplers[index]);
        }
        externalSampler = allSamplers[numWorkers];
    }

    /**
     * Terminate the pool.
     * (This tells the workers to stop work one their current jobs are complete.
     * (However, external threads will continue processing the jobs in the pool, including stealing from the
     *  internal workers' queues, until these external threads have received the answers to the tasks that they
     *  originally submitted to the pool. This ensures that all external tasks will eventually get completed.)
     */
    public void terminate() {
        terminated = true; // i.e. cannot receive more tasks from external threads
        for (Worker worker : workers) {
            worker.terminate(); // this tells each worker to stop work once its current job is complete
        }
        // ... but the external threads are untouched...
    }

    /**
     * Submit task to pool. (The calling thread then joins the pool as an external thread.)
     * @param task The task to compute.
     * @param <V> The output type of the task
     * @return The result of the computation of the task.
     */
    public <V> V invoke (Task<V> task) {
        if (terminated) {
            throw new IllegalStateException("Cannot receive task - pool has been terminated.");
        }
        // Use calling thread as part of pool until the task is complete
        // (and if the calling thread was part of a different fork-join pool, then the calling thread will transfer
        //  to this pool, until it has received an answer to the task it has submitted)
        ThreadManager.setSamplerForThread(externalSampler);
        V answer = task.compute();
        ThreadManager.deleteSamplerForThread();
        return answer;
    }

}
