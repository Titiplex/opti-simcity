package org.titiplex;

import org.titiplex.city.City;
import org.titiplex.optimizer.GameOptimizer;

public class Main {
    public static void main(String[] args) {
        int width = 24;
        int height = 24;
        int iterations = 10_000;

        City best = GameOptimizer.optimizeCity(iterations, width, height);
        best.printCity();
    }
}
