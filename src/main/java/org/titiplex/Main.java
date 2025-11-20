package org.titiplex;

import org.titiplex.city.City;
import org.titiplex.optimizer.GameOptimizer;

public class Main {
    public static void main(String[] args) {
        int width = 24;
        int height = 24;
        int iterations = 10_000;

        System.out.println("City dimensions: " + width + "x" + height + " (" + iterations + " iterations)");
        System.out.println("Generating city...");
        City best = GameOptimizer.optimizeCity(iterations, width, height);
        System.out.println("Done.");
        System.out.println("Best city :");
        best.printCity();
    }
}
