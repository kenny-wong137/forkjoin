package core;

/**
 * My simple implementation of a fork-join pool.
 *
 * In this implementation, each worker in the pool maintains its own job queue:
 * - A worker's job queue is the place where the worker dumps the jobs that it forks, to be evaluated later.
 * - A worker's job queue is also the place from where it finds new jobs to work on, either because it has just
 *   completed the previous job that it was working on, or because it is joining on a job that is not yet complete
 *   and so is looking for another job to get on with in the meantime.
 *
 * If a worker is looking for a new job but its job queue is empty, then it will try to steal jobs from other workers'
 * queues. (Note that workers access their own queues at the front, but steal from other queues at the back.)
 *
 * There is also an external job queue, owned collectively by all the external threads that have submitted tasks to this
 * pool by calling the invoke method. (External threads effectively operate as a part of the pool, just like a genuine
 * pool worker, until the tasks they submitted are complete, at which point they drop out from the pool. In particular,
 * external threads can steal from the queues owned by internal workers.)
 *
 * WARNING: In the current (very basic!!!) implementation, the workers in the pool start actively searching for jobs to
 * do as soon as the pool is created, and continue actively searching for jobs until the pool is terminated by
 * calling the terminate method. This means that each worker will occupy a CPU constantly from the time the pool is
 * created to the time the pool is terminated.
 */
public class Pool {

    private EvalSampler externalSampler; // to/from which external threads dump/find tasks
    private Worker[] workers;
    private volatile boolean terminated = false;


    /**
     * Constructor.
     * @param numWorkers Number of workers in the pool.
     * @throws IllegalArgumentException if the number of workers is negative.
     */
    public Pool(int numWorkers) {
        if (numWorkers < 0) {
            throw new IllegalArgumentException("Number of workers must be non-negative");
            // NB a pool with 0 workers is technically possible - the external thread(s) will do all of the work.
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
                // adding other workers' queues in "cyclic" order (see extended comment below).
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

    /*
     ^^^ Note on ordering:
     The workers are arranged in a circle. (For the purposes of this exercise, the external
     threads are collectively treated as one single worker in the circle.)

     e.g. If numWorkers = 3, then we have the circle,
          worker-1 -> worker-2 -> worker-3 -> external -> worker-1

     Work-retrieval/work-stealing is carried out in cyclic fashion.
     e.g. worker-1 tries worker-1-queue, then worker-2-queue, then worker-3-queue, then external-queue
          worker-2 tries worker-2-queue, then worker-3-queue, then external-queue, then worker-1-queue
          worker-3 tries worker-3-queue, then external-queue, then worker-1-queue, then worker-2-queue
          external tries external-queue, then worker-1-queue, then worker-2-queue, then worker-3-queue
     */


    /**
     * Terminates the pool, i.e. signals the pool workers to stop work once their current jobs are complete.
     * (However, external threads will continue processing the jobs in the pool, including stealing from the
     *  internal workers' queues, until these external threads have received the answers to the tasks that they
     *  originally submitted to the pool. This ensures that all external tasks will eventually get completed.)
     *
     * If the pool has already been terminated by the time this .terminate() call is made, then the terminate call
     * has no effect.
     *
     * Note that this method returns immediately - it does not wait for the pool workers to finish their current jobs.
     */
    public void terminate() {
        terminated = true; // i.e. cannot receive more tasks from external threads
        for (Worker worker : workers) {
            worker.terminate(); // this tells each worker to stop work once its current job is complete
        }
        // ... but the external threads are untouched...
    }


    /**
     * Submits task to pool, and returns the result of evaluating this task once it is complete.
     *
     * Note that, in between submitting the task and receiving the answer, the calling thread does not sit idle!
     * Rather, it participates in the activity of the pool, just like any other worker thread in the pool, until
     * the result of the task it submitted is complete. (In particular, it may steal tasks from other pool workers.)
     *
     * This implementation guarantees a happens-before relationship between the calling of "invoke" and the beginning
     * of the evaluation of the task, and a happens-before relationship between the completion of the evaluation and
     * the calling thread returning from the "invoke" call.
     *
     * @param task The task to compute.
     * @param <V> The output type of the task
     * @throws IllegalStateException if the pool has already been terminated.
     * @return The result of the computation of the task.
     */
    public <V> V invoke (Task<V> task) {

        if (terminated) {
            throw new IllegalStateException("Cannot receive task - pool has been terminated.");
        }

        // Use calling thread as part of pool until the task is complete
        // (Note: If the calling thread was originally part of a different fork-join pool, then its will temporarily
        //  transfer to this pool, until it has received the result of the task it submitted)
        ThreadManager.setSamplerForThread(externalSampler);

        // Begins computation *synchronously*, on the calling thread.
        // (Asynchronous computations only get triggered if the implementation of task.compute() forks subtasks
        //  - any such subtasks forked by task.compute() would then get placed on the external job queue, from where
        //  internal pool workers can steal them - and so on.)
        V answer = task.compute();

        // Once the evaluation is complete, the calling thread then drops out of this pool.
        ThreadManager.deleteSamplerForThread();

        return answer;
    }

}
