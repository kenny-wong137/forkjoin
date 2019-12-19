package examples;

import core.Pool;
import core.Task;

import java.util.ArrayList;
import java.util.List;

public class FastSum {

    private static class SumTask extends Task<Long> {

        private final List<Integer> list;

        private static final int MIN_SPLIT_SIZE = 100000;

        SumTask(List<Integer> list) {
            this.list = list;
        }

        @Override
        public Long compute() {
            if (list.size() == 0) {
                return 0L;
            }
            if (list.size() > MIN_SPLIT_SIZE) {
                SumTask leftSubTask = new SumTask(list.subList(0, list.size() / 2));
                SumTask rightSubTask = new SumTask(list.subList(list.size() / 2, list.size()));
                rightSubTask.fork();
                Long leftSum = leftSubTask.compute();
                Long rightSum = rightSubTask.join();
                return leftSum + rightSum;
            } else {
                long sum = 0L;
                for (int val : list) {
                    sum += val;
                }
                return sum;
            }
        }
    }

    private static void runSequential(int size, int numIters) {
        List<Integer> myList = new ArrayList<>();
        for (int val = 0; val < size; val++) {
            myList.add(val);
        }
        System.out.println("Serial list loaded");

        for (int iter = 0; iter < numIters; iter++) {
            long startTime = System.currentTimeMillis();
            long answer = 0L;
            for (int val : myList) {
                answer += val;
            }
            System.out.printf("Serial iteration %d: Time = %d%n", iter + 1, System.currentTimeMillis() - startTime);
        }
    }

    private static void runParallel(int size, int numIters, int numWorkers) {
        long expectedAns = ((long) size) * ((long) size - 1) / 2;
        List<Integer> myList = new ArrayList<>();
        for (int val = 0; val < size; val++) {
            myList.add(val);
        }
        System.out.printf("Parallel list loaded, thread count = %d%n", numWorkers);

        Pool pool = new Pool(numWorkers);
        pool.start();
        try {
            for (int iter = 0; iter < numIters; iter++) {
                SumTask fullTask = new SumTask(myList);
                long startTime = System.currentTimeMillis();
                Long answer = pool.invoke(fullTask);
                if (answer != expectedAns) {
                    throw new RuntimeException(String.format("Problem: expected %d, actual %d", expectedAns, answer));
                }
                System.out.printf("Parallel iteration %d: Time = %d%n",
                        iter + 1, System.currentTimeMillis() - startTime);
            }
        } finally {
            pool.terminate();
        }
    }

    public static void main(String[] args) {
        runSequential(50000000, 10);
        runParallel(50000000, 100, 8);
    }

}
