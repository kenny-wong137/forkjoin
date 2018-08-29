package core;

// The execution of a worker in the fork-join pool.
class WorkerRunnable implements Runnable {

    // Sampler object, used by this worker to dump forked tasks and find pending tasks.
    private final EvalSampler sampler;

    // Reference to the pool that owns this worker
    private final Pool pool;

    // Constructor.
    WorkerRunnable(EvalSampler sampler, Pool pool) {
        this.sampler = sampler;
        this.pool = pool;
    }

    @Override
    public void run() {

        // Make a global record of the EvalSampler object associated with this worker.
        ThreadManager.setSamplerForThread(sampler);

        // Loop runs until notified that the pool is terminated.
        while (!pool.isTerminated()) {

            // Sample a job from this workers' queue (or, if its queue is empty, then try to steal from another worker.)
            Evaluation<?> evalJob = sampler.get();

            if (evalJob != null) {
                // Successfully found a job - proceed with computation of this job.
                evalJob.runComputation();
            }

            /*
             To improve in future: Ideally the thread should wait here, until either it is notified that a new
             evaluation job has been forked, or until it is notified that the thread-pool has been terminated.
             (At the moment, the thread just cycles around the while loop, broken only by the periodic sleeps in
              the sampler.get() method.)
             */
        }

        // Once terminated, remove the global record of the EvalSampler object associated with this worker.
        ThreadManager.deleteSamplerForThread();
    }

}
