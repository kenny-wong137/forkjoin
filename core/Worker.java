package core;

class Worker implements Runnable {

    private EvalSampler sampler; // to/from which this worker dumps/finds tasks
    private boolean isTerminated = false;

    Worker(EvalSampler sampler) {
        this.sampler = sampler;
        Thread thread = new Thread(this);
        thread.start();
    }

    void terminate() {
        isTerminated = true;
    }

    @Override
    public void run() {
        ThreadManager.setSamplerForThread(sampler);
        // Runs until notified that the pool is terminated.
        // (though tasks in its queue can still be stolen by external threads that are still waiting for answers)
        while (!isTerminated) {
            Evaluation<?> evalJob = sampler.get();
            if (evalJob != null) {
                evalJob.runComputation();
            }
        }
        ThreadManager.deleteSamplerForThread();
    }
}
