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

        private static final int MIN_SPLIT_SIZE = 1; // in a more sensible application, this would be higher

        @Override
        public Long compute() {

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

        List<Integer> myList = IntStream.range(0, 100000000).boxed().collect(Collectors.toList());
        SumTask fullTask = new SumTask(myList);

        for (int i = 0; i < 2000; i++) {
            long startTime = System.currentTimeMillis();
            Pool pool = new Pool(3);
            Long answer = pool.invoke(fullTask);
            pool.terminate();
            long endTime = System.currentTimeMillis();

            System.out.println("Iteration: " + i);
            System.out.println("Answer: " + answer);
            System.out.println("Time: " + (endTime - startTime));
        }
    }


}
