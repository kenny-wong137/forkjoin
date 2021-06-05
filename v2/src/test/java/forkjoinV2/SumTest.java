package forkjoinV2;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class SumTest {

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

    @Test
    public void test() {
        final int size = 50000000;
        final int numIters = 20;
        final int numWorkers = 4;

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
                Assert.assertEquals(expectedAns, answer.longValue());
                System.out.printf("Iteration %d: Time = %d%n", iter + 1, System.currentTimeMillis() - startTime);
            }
        } finally {
            pool.terminate();
        }
    }

}
