//package org.iquantum.examples.hq_dptara.util;
//import java.util.Random;
//
//public final class Dist {
//    private Dist() {}
//
//    private static final Object LOCK = new Object();
//    private static Random rng = new Random(42L);
//
//    public static void setSeed(long seed) {
//        synchronized (LOCK) {
//            rng = new Random(seed);
//        }
//    }
//
//    private static double nextDouble() {
//        synchronized (LOCK) { return rng.nextDouble(); }
//    }
//
//    private static int nextInt(int bound) {
//        synchronized (LOCK) { return rng.nextInt(bound); }
//    }
//
//    public static double uniform(double a, double b) {
//        return a + (b - a) * nextDouble();
//    }
//
//    public static int uniformInt(int a, int bInclusive) {
//        return a + nextInt((bInclusive - a) + 1);
//    }
//
//    public static double expInterArrival(double lambdaPerSec) {
//        double u = Math.max(nextDouble(), SimConfig.EPS);
//        return -Math.log(u) / Math.max(lambdaPerSec, SimConfig.EPS);
//    }
//}

///////////////////////////// final codeeeeeeeeeee





package org.iquantum.examples.hq_dptara.util;
import java.util.Random;
public final class Dist {
    private Dist() {}

    private static final Object LOCK = new Object();
    private static Random rng = new Random(42L);

    public static void setSeed(long seed) {
        synchronized (LOCK) { rng = new Random(seed); }
    }

    private static double nextDouble() {
        synchronized (LOCK) { return rng.nextDouble(); }
    }

    private static int nextInt(int bound) {
        synchronized (LOCK) { return rng.nextInt(bound); }
    }

    public static double uniform(double a, double b) {
        return a + (b - a) * nextDouble();
    }

    public static int uniformInt(int a, int bInclusive) {
        return a + nextInt((bInclusive - a) + 1);
    }

    public static double expInterArrival(double lambdaPerSec) {
        double u = Math.max(nextDouble(), SimConfig.EPS);
        return -Math.log(u) / Math.max(lambdaPerSec, SimConfig.EPS);
    }
}
