package forkjoinV2;

class Worker extends Thread {

    private final Pool pool;

    Worker(Pool pool) {
        this.pool = pool;
    }

    Pool getPool() {
        return pool;
    }

    @Override
    public void run() {
        while (true) {
            Task<?> task;
            synchronized (pool.getLock()) {
                while (!(pool.isShutdown() || pool.hasTask())) {
                    try {
                        pool.getLock().wait();
                    } catch (InterruptedException ex) {
                        throw new RuntimeException(ex);
                    }
                }
                if (pool.isShutdown()) {
                    return;
                } else {
                    task = pool.getTask();
                }
            }
            task.asyncCompute();
        }
    }

}
