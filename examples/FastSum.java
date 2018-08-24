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

        @Override
        public Long compute() {

            if (list.size() == 0) {
                return 0L;
            }

            if (list.size() > 1) {
                SumTask leftSubtask = new SumTask(list.subList(0, list.size() / 2));
                SumTask rightSubtask = new SumTask(list.subList(list.size() / 2, list.size()));
                rightSubtask.fork();
                Long leftSum = leftSubtask.compute();
                Long rightSum = rightSubtask.join();
                return leftSum + rightSum;
            }
            else {
                return (long) list.get(0);
            }
        }
    }

    public static void main(String[] args) {
        List<Integer> myList = IntStream.range(0, 100000000).boxed().collect(Collectors.toList());
        SumTask fullTask = new SumTask(myList);

        for (int i = 0; i < 500; i++) {
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
