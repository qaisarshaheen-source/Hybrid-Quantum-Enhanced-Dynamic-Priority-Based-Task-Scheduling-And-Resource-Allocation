package org.iquantum.examples.hybrid;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
/**
 * Classical DPTARA simulator (paper-based) - self-contained.
 *
 * Key mappings to paper:
 * - Dynamic priority: Pi = α*Porig + β*((t-now - t-arrival)/Di) - γ*Lcurrent  (Algorithm snippet)  :contentReference[oaicite:5]{index=5}
 * - Allocation: choose resource with minimum load among feasible; else cloud; else drop             :contentReference[oaicite:6]{index=6}
 * - Latency: Llocal = Ttr(edge)+Tcomp(edge), Lcloud = Ttr(edge)+Tedge-cloud + Tcomp(cloud)         :contentReference[oaicite:7]{index=7}
 * - System parameters: Table 3/4                                                                    :contentReference[oaicite:8]{index=8}
 */
public class ClassicDptaraSimulator {

    // -------------------- Paper Environment Parameters (Table 3/4) -------------------- :contentReference[oaicite:9]{index=9}
    static final int NUM_IOT_DEVICES = 50;
    static final int NUM_EDGE = 10;
    static final int NUM_CLOUD = 1;

    static final double LAMBDA_TASKS_PER_SEC = 5.0;          // Poisson arrival rate
                          // you can change 100 -> 200/500/1000 easily

    static final double TASK_SIZE_MB_MIN = 5.0;
    static final double TASK_SIZE_MB_MAX = 50.0;

    static final double DEADLINE_S_MIN = 10.0;
    static final double DEADLINE_S_MAX = 100.0;

    static final double EDGE_POWER_W = 50.0;
    static final double CLOUD_POWER_W = 200.0;

    static final double EDGE_NET_MS_MIN = 5.0;
    static final double EDGE_NET_MS_MAX = 10.0;

    static final double CLOUD_NET_MS_MIN = 50.0;   // edge-cloud portion
    static final double CLOUD_NET_MS_MAX = 100.0;

    static final double EDGE_MIPS_MIN = 500.0;
    static final double EDGE_MIPS_MAX = 3000.0;

    static final double CLOUD_MIPS_MIN = 2000.0;
    static final double CLOUD_MIPS_MAX = 10000.0;

    static final double EDGE_RAM_MB = 32.0 * 1024.0;   // 32GB -> MB
    static final double CLOUD_RAM_MB = 64.0 * 1024.0;  // 64GB -> MB

    // -------------------- DPTARA tuning --------------------
    static final double ALPHA = 0.5;
    static final double BETA  = 0.3;
    static final double GAMMA = 0.2;
    static final double PRIORITY_THRESHOLD = 0.70;

    /**
     * IMPORTANT (realism / unit fix):
     * Paper uses Tcomp = δ / ψ (Eq. 6,9) but δ is MB and ψ is MIPS => units don't match directly. :contentReference[oaicite:10]{index=10}
     * So we use a scaler to convert "MB/MIPS" into seconds, calibrated so DPTARA can land near Table 5 scale.
     *
     * If you want exec times closer to ~335ms, keep ~20.
     * If you want heavier healthcare compute, increase this.
     */
//    static final double COMPUTE_SCALER = 25.0;
    static final double COMPUTE_SCALER = 160.0;
    static final int NUM_TASKS = 200;
    // -------------------- Data types --------------------
    enum TaskType { PRIORITY_RP, NORMAL_RN }

    static class Task {
        final int id;
        final int deviceId;
        final double arrivalTimeS;
        final double sizeMB;
        final double deadlineS;      // relative deadline window
        final double absDeadlineS;   // arrival + deadline
        final double pOrig;          // original priority [0,1]
        double pDyn;                 // dynamic priority [0,1]
        TaskType type;

        // results
        double startTimeS;
        double finishTimeS;
        double netTimeS;
        double computeTimeS;
        double responseTimeS;
        double latencyS;
        Resource assigned;

        Task(int id, int deviceId, double arrivalTimeS, double sizeMB, double deadlineS, double pOrig) {
            this.id = id;
            this.deviceId = deviceId;
            this.arrivalTimeS = arrivalTimeS;
            this.sizeMB = sizeMB;
            this.deadlineS = deadlineS;
            this.absDeadlineS = arrivalTimeS + deadlineS;
            this.pOrig = pOrig;
        }
    }

    static abstract class Resource {
        final String name;
        final boolean isCloud;
        final double mips;
        final double ramCapacityMB;
        final double powerW;

        double busyUntilS = 0.0;
        double currentLoadMB = 0.0; // simple capacity gate per Eq (12) :contentReference[oaicite:11]{index=11}

        // For utilization
        double totalBusyTimeS = 0.0;

        // Track running normal tasks for possible preemption (very simplified)
        final Deque<Task> runningNormalQueue = new ArrayDeque<>();

        Resource(String name, boolean isCloud, double mips, double ramCapacityMB, double powerW) {
            this.name = name;
            this.isCloud = isCloud;
            this.mips = mips;
            this.ramCapacityMB = ramCapacityMB;
            this.powerW = powerW;
        }

        boolean canFit(Task t) {
            return (currentLoadMB + t.sizeMB) <= ramCapacityMB;
        }

        double computeTimeSeconds(Task t) {
            // Paper-style compute: δ/ψ, then unit fix via scaler. :contentReference[oaicite:12]{index=12}
            // If δ in MB and ψ in MIPS, scaler makes it practical.
            return (t.sizeMB / mips) * COMPUTE_SCALER;
        }

        @Override public String toString() { return name; }
    }

    static class EdgeNode extends Resource {
        EdgeNode(String name, double mips) {
            super(name, false, mips, EDGE_RAM_MB, EDGE_POWER_W);
        }
    }

    static class CloudNode extends Resource {
        CloudNode(String name, double mips) {
            super(name, true, mips, CLOUD_RAM_MB, CLOUD_POWER_W);
        }
    }

    // -------------------- DPTARA Core --------------------
    static class DptaraScheduler {
        final List<EdgeNode> edges;
        final CloudNode cloud;

        DptaraScheduler(List<EdgeNode> edges, CloudNode cloud) {
            this.edges = edges;
            this.cloud = cloud;
        }

        double avgEdgeUtilProxy() {
            // Lcurrent term (load proxy). Paper uses "current load" in priority formula. :contentReference[oaicite:13]{index=13}
            // We'll use average RAM utilization here (0..1).
            double sum = 0.0;
            for (EdgeNode e : edges) sum += (e.currentLoadMB / e.ramCapacityMB);
            return sum / Math.max(edges.size(), 1);
        }

        void dynamicPrioritization(Task t, double nowS) {
            double aging = (nowS - t.arrivalTimeS) / Math.max(t.deadlineS, 1e-9);
            double Lcurrent = avgEdgeUtilProxy(); // proxy for current load :contentReference[oaicite:14]{index=14}
            double Pi = ALPHA * t.pOrig + BETA * aging - GAMMA * Lcurrent;
            t.pDyn = clamp(Pi, 0.0, 1.0);
            t.type = (t.pDyn >= PRIORITY_THRESHOLD) ? TaskType.PRIORITY_RP : TaskType.NORMAL_RN;
        }

        Resource selectResource(Task t) {
            // Allocation per Eq (12) + min load rule. :contentReference[oaicite:15]{index=15} :contentReference[oaicite:16]{index=16}
            Resource best = null;
            double bestScore = Double.POSITIVE_INFINITY;

            // Search edge resources first
            for (EdgeNode e : edges) {
                if (!e.canFit(t)) continue;

                // fairness/min-load: choose lowest load. :contentReference[oaicite:17]{index=17}
                // predictive load idea (Eq 13) lightly used as "score": :contentReference[oaicite:18]{index=18}
                double predLoad = e.currentLoadMB + (LAMBDA_TASKS_PER_SEC * t.sizeMB * (1.0 - t.pDyn));
                double score = predLoad; // smaller is better
                if (score < bestScore) {
                    bestScore = score;
                    best = e;
                }
            }

            if (best != null) return best;

            // If no edge can fit -> try cloud (backup) :contentReference[oaicite:19]{index=19}
            if (cloud.canFit(t)) return cloud;

            return null; // overloaded (drop/defer) :contentReference[oaicite:20]{index=20}
        }

        /**
         * Very simplified fairness rule:
         * If task is high priority (Rp) and no edge fits, try to "free" some RAM by offloading
         * one normal task from the most-loaded edge to cloud (if cloud fits). This matches the
         * idea: deallocate from normal tasks and offload to cloud. :contentReference[oaicite:21]{index=21}
         */
        boolean tryPreemptNormalForPriority(Task priorityTask) {
            if (priorityTask.type != TaskType.PRIORITY_RP) return false;

            EdgeNode victimEdge = null;
            double maxLoadFrac = -1;
            for (EdgeNode e : edges) {
                double frac = e.currentLoadMB / e.ramCapacityMB;
                if (frac > maxLoadFrac) {
                    maxLoadFrac = frac;
                    victimEdge = e;
                }
            }
            if (victimEdge == null) return false;

            Task victim = victimEdge.runningNormalQueue.pollLast();
            if (victim == null) return false;

            // offload victim to cloud if possible
            if (!cloud.canFit(victim)) {
                // put it back
                victimEdge.runningNormalQueue.addLast(victim);
                return false;
            }

            // "free" victim's RAM from edge, allocate to cloud instead (simplified)
            victimEdge.currentLoadMB = Math.max(0.0, victimEdge.currentLoadMB - victim.sizeMB);
            cloud.currentLoadMB += victim.sizeMB;
            victim.assigned = cloud;

            return true;
        }

        void execute(Task t) {
            Resource r = selectResource(t);

            // If no resource and it's priority task, attempt fairness reallocation then retry
            if (r == null && t.type == TaskType.PRIORITY_RP) {
                if (tryPreemptNormalForPriority(t)) {
                    r = selectResource(t);
                }
            }

            if (r == null) {
                t.assigned = null; // dropped/deferred
                return;
            }

            // Transmission latency: edge vs cloud models :contentReference[oaicite:22]{index=22}
            double edgeNetMs = uniform(EDGE_NET_MS_MIN, EDGE_NET_MS_MAX);
            double netS;
            if (!r.isCloud) {
                netS = edgeNetMs / 1000.0;
            } else {
                double edgeCloudMs = uniform(CLOUD_NET_MS_MIN, CLOUD_NET_MS_MAX);
                netS = (edgeNetMs + edgeCloudMs) / 1000.0; // Eq (8) idea :contentReference[oaicite:23]{index=23}
            }

            double compS = r.computeTimeSeconds(t);

            double start = Math.max(t.arrivalTimeS, r.busyUntilS);
            double finish = start + netS + compS;

            // Update resource
            r.totalBusyTimeS += (netS + compS);
            r.busyUntilS = finish;
            r.currentLoadMB += t.sizeMB;

            // Track normal task as "preemptable"
            if (!r.isCloud && t.type == TaskType.NORMAL_RN) {
                ((EdgeNode) r).runningNormalQueue.addLast(t);
            }

            // Save results
            t.assigned = r;
            t.startTimeS = start;
            t.finishTimeS = finish;
            t.netTimeS = netS;
            t.computeTimeS = compS;
            t.responseTimeS = Math.max(0.0, start - t.arrivalTimeS);
            t.latencyS = (finish - t.arrivalTimeS);
        }
    }

    // -------------------- Simulation driver --------------------
    public static void main(String[] args) {
        // Build resources
        List<EdgeNode> edges = new ArrayList<>();
        for (int i = 0; i < NUM_EDGE; i++) {
            double mips = uniform(EDGE_MIPS_MIN, EDGE_MIPS_MAX);
            edges.add(new EdgeNode("EDGE-" + i, mips));
        }
        CloudNode cloud = new CloudNode("CLOUD-0", uniform(CLOUD_MIPS_MIN, CLOUD_MIPS_MAX));

        // Generate tasks with Poisson arrivals
        List<Task> tasks = generateTasks(NUM_TASKS);

        // Run
        DptaraScheduler scheduler = new DptaraScheduler(edges, cloud);
        for (Task t : tasks) {
            scheduler.dynamicPrioritization(t, t.arrivalTimeS);
            scheduler.execute(t);
        }

        // Metrics
        printMetrics(tasks, edges, cloud);
    }

    static List<Task> generateTasks(int n) {
        List<Task> list = new ArrayList<>(n);

        double now = 0.0;
        for (int i = 0; i < n; i++) {
            // Exponential inter-arrival time for Poisson process
            double u = ThreadLocalRandom.current().nextDouble();
            double inter = -Math.log(1.0 - u) / LAMBDA_TASKS_PER_SEC;
            now += inter;

            int deviceId = ThreadLocalRandom.current().nextInt(NUM_IOT_DEVICES);

            double sizeMB = uniform(TASK_SIZE_MB_MIN, TASK_SIZE_MB_MAX);
            double deadlineS = uniform(DEADLINE_S_MIN, DEADLINE_S_MAX);

            // Original priority distribution: mix urgent + normal
            // 20% urgent tasks start high, 80% normal start lower (realistic healthcare mix)
            double pOrig;
            if (ThreadLocalRandom.current().nextDouble() < 0.20) {
                pOrig = uniform(0.75, 1.0);
            } else {
                pOrig = uniform(0.10, 0.70);
            }

            list.add(new Task(i, deviceId, now, sizeMB, deadlineS, pOrig));
        }
        return list;
    }

    static void printMetrics(List<Task> tasks, List<EdgeNode> edges, CloudNode cloud) {
        int done = 0, dropped = 0;
        double sumLatencyS = 0.0;
        double sumExecS = 0.0;
        double sumRespS = 0.0;

        double energyEdgeJ = 0.0;
        double energyCloudJ = 0.0;

        int deadlineMiss = 0;

        double makespanS = 0.0;
        for (Task t : tasks) {
            if (t.assigned == null) { dropped++; continue; }
            done++;

            sumLatencyS += t.latencyS;
            sumExecS += t.computeTimeS;
            sumRespS += t.responseTimeS;

            if (t.finishTimeS > makespanS) makespanS = t.finishTimeS;
            if (t.finishTimeS > t.absDeadlineS) deadlineMiss++;

            // Energy = power * compute_time (simple, consistent with Table 3 power values) :contentReference[oaicite:24]{index=24}
            if (t.assigned.isCloud) {
                energyCloudJ += CLOUD_POWER_W * t.computeTimeS;
            } else {
                energyEdgeJ += EDGE_POWER_W * t.computeTimeS;
            }
        }

        // Avg edge utilization: average of (busy time / makespan)
        double utilSum = 0.0;
        for (EdgeNode e : edges) {
            utilSum += (makespanS <= 0 ? 0.0 : (e.totalBusyTimeS / makespanS));
        }
        double avgEdgeUtil = utilSum / Math.max(edges.size(), 1);

        double avgLatencyMs = (done == 0) ? 0.0 : (sumLatencyS / done) * 1000.0;
        double avgExecMs    = (done == 0) ? 0.0 : (sumExecS / done) * 1000.0;
        double avgRespMs    = (done == 0) ? 0.0 : (sumRespS / done) * 1000.0;

        double totalEnergyJ = energyEdgeJ + energyCloudJ;
        double dmr = (done == 0) ? 0.0 : ((double) deadlineMiss / done);

        System.out.println("===== Classical DPTARA Simulation Results (" + NUM_TASKS + " tasks) =====");
        System.out.printf ("Completed: %d | Dropped: %d%n", done, dropped);
        System.out.printf ("Avg Latency (ms):           %.3f%n", avgLatencyMs);
        System.out.printf ("Avg Response Time (ms):     %.3f%n", avgRespMs);
        System.out.printf ("Avg Exec Time (ms):         %.3f%n", avgExecMs);
        System.out.printf ("Edge Energy (J):            %.3f%n", energyEdgeJ);
        System.out.printf ("Cloud Energy (J):           %.3f%n", energyCloudJ);
        System.out.printf ("Total Energy (J):           %.3f%n", totalEnergyJ);
        System.out.printf ("Avg Edge Utilization:       %.4f (%.2f%%)%n", avgEdgeUtil, avgEdgeUtil * 100.0);
        System.out.printf ("Deadline Miss Ratio:        %.4f%n", dmr);
        System.out.println("=============================================================");
    }

    // -------------------- helpers --------------------
    static double uniform(double a, double b) {
        return a + (b - a) * ThreadLocalRandom.current().nextDouble();
    }
    static double clamp(double x, double lo, double hi) {
        return Math.max(lo, Math.min(hi, x));
    }
}



