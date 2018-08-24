package examples;

import java.util.ArrayList;
import java.util.List;

public class Example {

    static class NumberContainer {

        int value = 0;

        void increment() {
            value++;
        }

        int getValue() {
            return value;
        }
    }

    public static void main(String[] args) {

        List<NumberContainer> array = new ArrayList<>();
        int numElements = 100000;
        for (int i = 0; i < numElements; i++) {
            array.add(new NumberContainer());
        }

        int numIterations = 10000;
        for (int j = 0; j < numIterations; j++) {
            array.parallelStream().forEach(NumberContainer::increment);
        }

        array.forEach(container -> {
            if (container.getValue() != numIterations) {
                System.out.println("Problem!!!");
            }
        });
    }
}

