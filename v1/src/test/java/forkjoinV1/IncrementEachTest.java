package forkjoinV1;

import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class IncrementEachTest {

    private static class IncrementTask extends Task<Void> {

        private List<AtomicInteger> list;
        private int MIN_SPLIT_SIZE = 100000;

        IncrementTask(List<AtomicInteger> list) {
            this.list = list;
        }

        @Override
        public Void compute() {
            System.out.println(Thread.currentThread().getName() + " : list size " + list.size());

            if (list.size() == 0) {
                return null;
            }

            if (list.size() > MIN_SPLIT_SIZE) {
                IncrementTask leftSubtask = new IncrementTask(list.subList(0, list.size() / 2));
                IncrementTask rightSubtask = new IncrementTask(list.subList(list.size() / 2, list.size()));
                rightSubtask.fork();
                leftSubtask.compute();
                rightSubtask.join();
                return null;
            }
            else {
                list.forEach(AtomicInteger::getAndIncrement);
                return null;
            }
        }
    }

    @Test
    public void test() {
        List<AtomicInteger> myList = IntStream.range(0, 10000000)
                .mapToObj(i -> new AtomicInteger())
                .collect(Collectors.toList());

        IncrementTask fullTask = new IncrementTask(myList);

        Pool pool = new Pool();

        try {
            final int numIters = 10;
            for (int i = 0; i < numIters; i++) {
                System.out.println("Iteration: " + i);

                long startTime = System.currentTimeMillis();
                pool.invoke(fullTask);
                long endTime = System.currentTimeMillis();

                System.out.println("Time: " + (endTime - startTime) + "\n");
            }

            for (AtomicInteger container : myList) {
                Assert.assertEquals(numIters, container.get());
            }
        }
        finally {
            pool.terminate();
        }
    }

}
