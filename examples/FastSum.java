package examples;

import core.Pool;
import core.Task;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class FastSum {

    private static class SumTask extends Task<Long> {

        private List<Integer> list;

        SumTask(List<Integer> list) {
            this.list = list;
        }

        private static final int MIN_SPLIT_SIZE = 100000;

        @Override
        public Long compute() {

             System.out.println(Thread.currentThread().getName()
            + " : " + list.get(0) + " -> " + list.get(list.size() - 1));

            if (list.size() == 0) {
                return 0L;
            }

            if (list.size() > MIN_SPLIT_SIZE) {
                SumTask leftSubtask = new SumTask(list.subList(0, list.size() / 2));
                SumTask rightSubtask = new SumTask(list.subList(list.size() / 2, list.size()));
                rightSubtask.fork();
                Long leftSum = leftSubtask.compute();
                Long rightSum = rightSubtask.join();
                return leftSum + rightSum;
            }
            else {
                long sum = 0;
                for (Integer value : list) {
                    sum += value;
                }
                return sum;
            }
        }
    }

    public static void main(String[] args) {

        List<Integer> myList = IntStream.range(0, 10000000).boxed().collect(Collectors.toList());
        SumTask fullTask = new SumTask(myList);

        Pool pool = new Pool();

        for (int i = 0; i < 20; i++) {
            System.out.println("Iteration: " + i);
            long startTime = System.currentTimeMillis();
            Long answer = pool.invoke(fullTask);
            long endTime = System.currentTimeMillis();

            System.out.println("Answer: " + answer);
            System.out.println("Time: " + (endTime - startTime));
            System.out.println("");
        }

        pool.terminate();
    }


}
