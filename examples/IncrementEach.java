package examples;

import core.Pool;
import core.Task;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class IncrementEach {

    private static class NumberContainer {

        private int value = 0;

        private void increment() {
            value++;
        }

        private int getValue() {
            return value;
        }

    }

    private static class SumTask extends Task<Void> {

        private List<NumberContainer> list;
        private int MIN_SPLIT_SIZE = 1; // in a more sensible application, this would be higher

        SumTask(List<NumberContainer> list) {
            this.list = list;
        }

        @Override
        public Void compute() {

            if (list.size() == 0) {
                return null;
            }

            if (list.size() > MIN_SPLIT_SIZE) {
                SumTask leftSubtask = new SumTask(list.subList(0, list.size() / 2));
                SumTask rightSubtask = new SumTask(list.subList(list.size() / 2, list.size()));
                rightSubtask.fork();
                leftSubtask.compute();
                rightSubtask.join();
                return null;
            }
            else {
                list.forEach(NumberContainer::increment);
                return null;
            }
        }
    }

    public static void main(String[] args) {

        List<NumberContainer> myList = IntStream.range(0, 100000000)
                .boxed()
                .map(i -> new NumberContainer())
                .collect(Collectors.toList());

        SumTask fullTask = new SumTask(myList);

        Pool pool = new Pool(3);

        int numIters = 25;
        for (int i = 0; i < numIters; i++) {
            long startTime = System.currentTimeMillis();
            pool.invoke(fullTask);
            long endTime = System.currentTimeMillis();
            System.out.println(endTime - startTime);
        }

        boolean allCorrect = true;
        for (NumberContainer container : myList) {
            if (container.getValue() != numIters) {
                System.out.println("Problem: found " + container.getValue());
                allCorrect = false;
            }
        }
        if (allCorrect) {
            System.out.println("All correct");
        }

        pool.terminate();
    }

}
