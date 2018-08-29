package examples;

import core.*;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class SlowSum {

    private static class PrintAndSumListTask extends Task<Integer> {

        private List<Integer> list;

        PrintAndSumListTask(List<Integer> list) {
            this.list = list;
        }

        @Override
        public Integer compute() {

            if (list.size() == 0) {
                return 0;
            }

            System.out.println(Thread.currentThread().getName() + " : " + list);
            try {
                Thread.sleep(1);
            } catch (InterruptedException ex) {
                throw new RuntimeException(ex);
            }

            if (list.size() > 1) {
                PrintAndSumListTask leftSubtask = new PrintAndSumListTask(list.subList(0, list.size() / 2));
                PrintAndSumListTask rightSubtask = new PrintAndSumListTask(list.subList(list.size() / 2, list.size()));
                rightSubtask.fork();
                Integer leftSum = leftSubtask.compute();
                Integer rightSum = rightSubtask.join();
                return leftSum + rightSum;
            }
            else {
                return list.get(0);
            }
        }
    }

    public static void main(String[] args) {
        List<Integer> myList = IntStream.range(0, 64).boxed().collect(Collectors.toList());
        PrintAndSumListTask fullTask = new PrintAndSumListTask(myList);
        Pool pool = new Pool(3);
        Integer sum = pool.invoke(fullTask);
        pool.terminate();
        System.out.println("Sum = " + sum);
    }

}
