package org.iquantum.examples.hybrid;

import java.util.*;

/**
 * HQ-DPTARA Realistic Discrete-Event Simulator (Thesis-clean, CPU-util metric).
 *
 * FIXED to match your document better:
 * ✅ (1) Network happens BEFORE compute (paper latency model)
 * ✅ (2) Planning uses MEAN network (stable decisions); execution uses SAMPLED network once
 * ✅ (3) CPU Utilization factors from Table-4 applied:
 *        Edge CPU util = 0.5  => effectiveMips = mips * 0.5
 *        Cloud CPU util = 2.0 => effectiveMips = mips * 2.0
 * ✅ (4) Priority aging uses waiting-aware predicted startHat (includes mean net)
 * ✅ (5) Resource-utilization metric = CPU-only (busyExecAccumSec / horizon)
 * ✅ (6) Edge + Cloud both used (no local layer; cloud is always a candidate)
 *
 * Algorithms mapping (as you wrote):
 * - Alg 1: main scheduler loop (task arrival order)
 * - Alg 2: quantum batch update (inline every T_BATCH_SEC)
 * - Alg 3: dynamic priority (waiting-aware aging via startHatMin)
 * - Alg 4: candidate selection (K smallest predicted finish times)
 * - Alg 5: validate plan (safety check)
 * - Alg 6: emergency handler (deadline protection)
 */
public class HQDptaraSimulatorRealistic70 {

    /* -----------------------------
       Parameters (from your tables)
       ----------------------------- */
    static final int NUM_IOT_DEVICES = 50;
    static final int NUM_EDGE = 10;
    static final int NUM_TASKS = 100;
    static final double LAMBDA_TASKS_PER_SEC = 5.0;

    static final double TASK_SIZE_MB_MIN = 5.0;
    static final double TASK_SIZE_MB_MAX = 50.0;

    static final double DEADLINE_SEC_MIN = 10.0;
    static final double DEADLINE_SEC_MAX = 100.0;

    static final double EDGE_NET_MS_MIN = 5.0;
    static final double EDGE_NET_MS_MAX = 10.0;

    static final double CLOUD_NET_MS_MIN = 50.0;
    static final double CLOUD_NET_MS_MAX = 100.0;

    static final double EDGE_POWER_W = 50.0;
    static final double CLOUD_POWER_W = 200.0;

    static final int EDGE_MIPS_MIN = 500;
    static final int EDGE_MIPS_MAX = 3000;

    static final int CLOUD_MIPS_MIN = 2000;
    static final int CLOUD_MIPS_MAX = 10000;

    static final double EDGE_RAM_MB = 32 * 1024.0;
    static final double CLOUD_RAM_MB = 64 * 1024.0;

    /* -----------------------------
       CPU utilization factors (Table-4)
       ----------------------------- */
    static final double EDGE_CPU_UTIL_FACTOR = 0.5;  // Table-4
    static final double CLOUD_CPU_UTIL_FACTOR = 2.0; // Table-4 (VMs)

    /* -----------------------------
       HQ-DPTARA constants
       ----------------------------- */
    static final double DEFAULT_ALPHA = 0.5;
    static final double DEFAULT_BETA  = 0.3;
    static final double DEFAULT_GAMMA = 0.2;

    static final double BASE_RESERVE = 0.15;
    static final double PRIORITY_THRESHOLD = 0.7;
    static final int K = 3;

    // Quantum batch interval (simulation seconds)
    static final double T_BATCH_SEC = 2.0;

    // Queue normalization for priority/load proxy + scoring (modeling choice)
    static final double QUEUE_NORM_S = 1.0;

    /* -----------------------------
       Compute model (size-driven)
       Work (MI) = size(MB) * MI_PER_MB_BASE * typeMultiplier
       ----------------------------- */
    static final double MI_PER_MB_BASE = 25.0;

    enum TaskType {
        MONITORING(0.7, 0.6),
        ANALYTICS(0.2, 1.4),
        IMAGING_AI(0.1, 3.0);

        final double prob;
        final double multiplier;
        TaskType(double prob, double multiplier) {
            this.prob = prob;
            this.multiplier = multiplier;
        }
    }

    // Safety / plan validation (Algorithm 5)
    static final double UTIL_SAFETY_LIMIT = 0.85;

    // Reproducibility
    static final long SEED = 42L;

    /* -----------------------------
       Data structures
       ----------------------------- */
    static class Task {
        final int id;
        final int deviceId;
        final double arrivalTimeSec;
        final double sizeMB;
        final double deadlineSec;
        final double absDeadlineSec;
        final double originalPriority;
        final TaskType type;

        double priority;

        double startComputeSec;  // CPU start time (after net + queue)
        double finishComputeSec; // CPU finish time
        double execTimeSec;      // compute only
        double netTimeSec;       // transmission before compute
        double energyJ;

        Resource assigned;
        boolean cloudForced = false;

        Task(int id, int deviceId, double arrivalTimeSec, double sizeMB,
             double deadlineSec, double originalPriority, TaskType type) {
            this.id = id;
            this.deviceId = deviceId;
            this.arrivalTimeSec = arrivalTimeSec;
            this.sizeMB = sizeMB;
            this.deadlineSec = deadlineSec;
            this.absDeadlineSec = arrivalTimeSec + deadlineSec;
            this.originalPriority = originalPriority;
            this.type = type;
            this.priority = originalPriority;
        }
    }

    static class Resource {
        final String name;
        final boolean isCloud;
        final int mips;
        final double powerW;
        final double capacityMB;

        // in-use "RAM-like" load; released at CPU finish time
        double currentLoadMB = 0.0;

        // CPU queue state (busyUntil is CPU finish time of last scheduled task)
        double busyUntilSec = 0.0;

        // CPU utilization metric (EXEC only)
        double busyExecAccumSec = 0.0;

        Resource(String name, boolean isCloud, int mips, double powerW, double capacityMB) {
            this.name = name;
            this.isCloud = isCloud;
            this.mips = mips;
            this.powerW = powerW;
            this.capacityMB = capacityMB;
        }

        double effectiveMips() {
            return isCloud ? (mips * CLOUD_CPU_UTIL_FACTOR) : (mips * EDGE_CPU_UTIL_FACTOR);
        }
    }

    static class OptimizationPlan {
        double alpha = DEFAULT_ALPHA;
        double beta  = DEFAULT_BETA;
        double gamma = DEFAULT_GAMMA;

        // score weights (wL,wE,wU,wB,wP)
        double wL = 0.60, wE = 0.15, wU = 0.25, wB = 0.05, wP = 0.20;

        Map<String, Double> biasMap = new HashMap<>();
        Double reserveOverride = null;
        double timestampSec = 0.0;
    }

    static class Metrics {
        double avgLatencyMs;         // net + queue + exec
        double avgQueueMs;           // queue only (startCompute - arrivalAtCompute)
        double avgExecMs;
        double totalEnergyJ;
        double avgCpuUtilization;    // CPU-only (busyExec/horizon)
        double deadlineMissRatio;
    }

    /** release event to free load at CPU finish time */
    static class ReleaseEvent {
        final double timeSec;
        final Resource r;
        final double loadMB;
        ReleaseEvent(double timeSec, Resource r, double loadMB) {
            this.timeSec = timeSec;
            this.r = r;
            this.loadMB = loadMB;
        }
    }

    /* -----------------------------
       Quantum-like controller stubs
       ----------------------------- */
    static class QuantumController {
        private final Random rnd;
        QuantumController(Random rnd) { this.rnd = rnd; }

        List<Double> QFLP(List<Resource> resources) throws Exception {
            if (rnd.nextDouble() < 0.10) throw new Exception("QFLP unavailable");
            List<Double> loads = new ArrayList<>();
            for (Resource r : resources) loads.add(r.currentLoadMB);
            return loads;
        }

        RawPlan QGO(List<Resource> resources) throws Exception {
            if (rnd.nextDouble() < 0.10) throw new Exception("QGO unavailable");
            RawPlan p = new RawPlan();

            double avgEdgeLoad = resources.stream()
                    .filter(r -> !r.isCloud)
                    .mapToDouble(r -> r.currentLoadMB)
                    .average().orElse(0.0);

            for (Resource r : resources) {
                double bias = 0.0;
                if (!r.isCloud && avgEdgeLoad > 1e-9) {
                    double loadNorm = r.currentLoadMB / avgEdgeLoad;
                    if (loadNorm > 1.2) bias = +0.08;
                    else if (loadNorm < 0.8) bias = -0.04;
                }
                if (r.isCloud) bias += 0.05; // mild cloud bias to allow usage when beneficial
                p.biasMap.put(r.name, bias);
            }

            double avgUtil = avgEdgeRamUtil(resources); // keep as simple load indicator
            p.reserveOverride = (avgUtil > 0.70) ? 0.18 : null;

            if (avgUtil > 0.70) {
                p.wL = 0.65; p.wE = 0.12; p.wU = 0.25; p.wB = 0.05; p.wP = 0.20;
            } else {
                p.wL = 0.60; p.wE = 0.15; p.wU = 0.25; p.wB = 0.05; p.wP = 0.20;
            }
            return p;
        }

        double[] QWT(List<Resource> resources) throws Exception {
            if (rnd.nextDouble() < 0.10) throw new Exception("QWT unavailable");
            // tune gamma slightly when edges are "full" (RAM proxy)
            double util = avgEdgeRamUtil(resources);
            double a = DEFAULT_ALPHA, b = DEFAULT_BETA, g = DEFAULT_GAMMA;
            if (util > 0.70) g = 0.28;
            return new double[]{a, b, g};
        }
    }

    static class RawPlan {
        Map<String, Double> biasMap = new HashMap<>();
        Double reserveOverride = null;
        double wL, wE, wU, wB, wP;
    }

    static class ClassicalPredictor {
        List<Double> predict(List<Resource> resources) {
            List<Double> loads = new ArrayList<>();
            for (Resource r : resources) loads.add(r.currentLoadMB);
            return loads;
        }
    }

    /* -----------------------------
       Main (Algorithm 1)
       ----------------------------- */
    public static void main(String[] args) {
        Random rnd = new Random(SEED);

        // Resources
        List<Resource> edges = new ArrayList<>();
        for (int i = 0; i < NUM_EDGE; i++) {
            int mips = randInt(rnd, EDGE_MIPS_MIN, EDGE_MIPS_MAX);
            edges.add(new Resource("EDGE-" + i, false, mips, EDGE_POWER_W, EDGE_RAM_MB));
        }
        Resource cloud = new Resource("CLOUD-0", true,
                randInt(rnd, CLOUD_MIPS_MIN, CLOUD_MIPS_MAX),
                CLOUD_POWER_W, CLOUD_RAM_MB);

        List<Resource> all = new ArrayList<>(edges);
        all.add(cloud);

        OptimizationPlan plan = new OptimizationPlan();
        QuantumController quantum = new QuantumController(rnd);
        ClassicalPredictor classical = new ClassicalPredictor();

        // Tasks
        List<Task> tasks = generateTasksPoisson(rnd, NUM_TASKS, LAMBDA_TASKS_PER_SEC);

        // Release events
        PriorityQueue<ReleaseEvent> releases = new PriorityQueue<>(Comparator.comparingDouble(e -> e.timeSec));

        // Normalization refs (score)
        final double L_REF_SEC = 1.0;
        final double E_REF_J   = 50.0;

        // Quantum batch timing
        double nextBatchTime = 0.0;

        List<Task> completed = new ArrayList<>();

        for (Task t : tasks) {
            double now = t.arrivalTimeSec;

            // release loads up to 'now' (finish times can be before next arrivals)
            while (!releases.isEmpty() && releases.peek().timeSec <= now) {
                ReleaseEvent ev = releases.poll();
                ev.r.currentLoadMB = Math.max(0.0, ev.r.currentLoadMB - ev.loadMB);
            }

            // Algorithm 2 (inline): update plan at each batch boundary
            while (nextBatchTime <= now) {
                quantumBatchUpdateInline(quantum, classical, all, plan, nextBatchTime);
                nextBatchTime += T_BATCH_SEC;
            }

            // snapshot plan
            double alpha = plan.alpha, beta = plan.beta, gamma = plan.gamma;
            double wL = plan.wL, wE = plan.wE, wU = plan.wU, wB = plan.wB, wP = plan.wP;
            Map<String, Double> biasMap = plan.biasMap;
            Double reserveOverride = plan.reserveOverride;

            // -------- Algorithm 3: dynamic priority (waiting-aware) --------
            // Use best predicted CPU start among edge nodes INCLUDING mean edge net.
            double meanEdgeNet = meanNetworkSec(false);
            double startHatMin = Double.POSITIVE_INFINITY;
            for (Resource r : edges) {
                double arrivalAtComputeHat = now + meanEdgeNet;
                double startHat = Math.max(arrivalAtComputeHat, r.busyUntilSec);
                startHatMin = Math.min(startHatMin, startHat);
            }
            if (!Double.isFinite(startHatMin)) startHatMin = now + meanEdgeNet;

            // aging fraction: (predicted waiting) / deadline
            double waitingHat = Math.max(0.0, startHatMin - (now + meanEdgeNet));
            double aging = waitingHat / Math.max(t.deadlineSec, 1e-9);

            // load proxy: avg edge queue pressure (CPU queue), normalized
            double loadProxy = avgEdgeQueueNorm(edges, now, meanEdgeNet);

            double Pi = alpha * t.originalPriority + beta * aging - gamma * loadProxy;
            t.priority = clamp(Pi, 0.0, 1.0);

            // reserve fraction
            double reserveFraction = (t.priority >= PRIORITY_THRESHOLD) ? 0.05 : BASE_RESERVE;
            if (reserveOverride != null) reserveFraction = clamp(reserveOverride, 0.0, 0.50);

            // -------- Algorithm 4: candidate selection (K best predicted finish edges + cloud) --------
            // IMPORTANT: prediction uses MEAN network, not random samples.
            Map<Resource, Double> estFinish = new HashMap<>();
            for (Resource r : edges) {
                double finishHat = predictFinishTimeMeanNet(t, r, now);
                estFinish.put(r, finishHat);
            }
            List<Resource> candidates = pickKSmallest(estFinish, K);
            candidates.add(cloud); // always include cloud fallback

            // -------- Score + select --------
            Resource selected = null;
            double bestScore = Double.POSITIVE_INFINITY;

            for (Resource r : candidates) {
                double available = r.capacityMB * (1.0 - reserveFraction);
                if (r.currentLoadMB + t.sizeMB > available) continue;

                double netHat = meanNetworkSec(r.isCloud);
                double arrivalAtComputeHat = now + netHat;

                double startHat = Math.max(arrivalAtComputeHat, r.busyUntilSec);
                double execHat  = estimateComputeSec(t, r);
                double finishHat = startHat + execHat;

                double latencyHat = finishHat - now; // net + queue + exec
                double energyHat  = r.powerW * execHat;

                // utilization term in score: CPU queue pressure (not RAM)
                double queueHat = Math.max(0.0, r.busyUntilSec - arrivalAtComputeHat);
                double utilHat = clamp(queueHat / QUEUE_NORM_S, 0.0, 1.0);

                double bias = biasMap.getOrDefault(r.name, 0.0);

                double latencyNorm = latencyHat / Math.max(L_REF_SEC, 1e-9);
                double energyNorm  = energyHat / Math.max(E_REF_J, 1e-9);

                double score = wL * latencyNorm + wE * energyNorm + wU * utilHat + wB * bias - wP * t.priority;

                if (score < bestScore) {
                    bestScore = score;
                    selected = r;
                }
            }

            // -------- Algorithm 6: proactive deadline protection --------
            if (selected != null && t.priority >= PRIORITY_THRESHOLD) {
                double predictedFinish = predictFinishTimeMeanNet(t, selected, now);
                if (predictedFinish > t.absDeadlineSec) {
                    selected = emergencyHandlerEnhanced(t, edges, cloud, reserveFraction, now);
                }
            }

            // overload fallback
            if (selected == null) {
                selected = emergencyHandlerEnhanced(t, edges, cloud, reserveFraction, now);
            }

            if (selected == null) continue;

            // -------- Execute (sample network ONCE; net BEFORE compute) --------
            double net = sampleNetworkSec(selected.isCloud, rnd);
            double arrivalAtCompute = now + net;

            double exec = estimateComputeSec(t, selected);
            double start = Math.max(arrivalAtCompute, selected.busyUntilSec);
            double finish = start + exec;

            // Update CPU queue state
            selected.busyUntilSec = finish;

            // Utilization accumulation (EXEC only)
            selected.busyExecAccumSec += exec;

            // Load occupancy: allocate now, release at CPU finish
            selected.currentLoadMB = Math.min(selected.capacityMB, selected.currentLoadMB + t.sizeMB);
            releases.add(new ReleaseEvent(finish, selected, t.sizeMB));

            // Task metrics
            t.startComputeSec = start;
            t.finishComputeSec = finish;
            t.execTimeSec = exec;
            t.netTimeSec = net;
            t.energyJ = selected.powerW * exec;
            t.assigned = selected;

            completed.add(t);
        }

        Metrics m = computeMetrics(completed, edges, cloud);

        System.out.println("===== HQ-DPTARA Simulation Results (" + NUM_TASKS + " tasks) =====");
        System.out.printf(Locale.US, "Completed: %d | Dropped: %d%n", completed.size(), (NUM_TASKS - completed.size()));
        System.out.printf(Locale.US, "Avg Latency (ms):           %.3f%n", m.avgLatencyMs);
        System.out.printf(Locale.US, "Avg Queueing (ms):          %.3f%n", m.avgQueueMs);
        System.out.printf(Locale.US, "Avg Exec Time (ms):         %.3f%n", m.avgExecMs);
        System.out.printf(Locale.US, "Total Energy (J):           %.3f%n", m.totalEnergyJ);
        System.out.printf(Locale.US, "Avg CPU Utilization:        %.4f%n", m.avgCpuUtilization);
        System.out.printf(Locale.US, "Deadline Miss Ratio:        %.4f%n", m.deadlineMissRatio);
        System.out.println("===============================================================");

        // quick sanity: edge vs cloud usage counts
        long edgeTasks = completed.stream().filter(t -> t.assigned != null && !t.assigned.isCloud).count();
        long cloudTasks = completed.stream().filter(t -> t.assigned != null && t.assigned.isCloud).count();
        System.out.printf("Sanity: Edge tasks=%d | Cloud tasks=%d%n", edgeTasks, cloudTasks);
    }

    /* -----------------------------
       Algorithm 2: Quantum batch update (INLINE)
       ----------------------------- */
    static void quantumBatchUpdateInline(
            QuantumController quantum,
            ClassicalPredictor classical,
            List<Resource> allResources,
            OptimizationPlan sharedPlan,
            double simTime
    ) {
        // QFLP with fallback
        List<Double> predictedLoads;
        try {
            predictedLoads = quantum.QFLP(allResources);
        } catch (Exception e) {
            predictedLoads = classical.predict(allResources);
        }

        // QGO with fallback
        RawPlan raw;
        try {
            raw = quantum.QGO(allResources);
        } catch (Exception e) {
            raw = neutralRawPlan(allResources);
        }

        // QWT with fallback
        double[] tuned;
        try {
            tuned = quantum.QWT(allResources);
        } catch (Exception e) {
            tuned = new double[]{sharedPlan.alpha, sharedPlan.beta, sharedPlan.gamma};
        }

        OptimizationPlan candidate = new OptimizationPlan();
        candidate.alpha = tuned[0];
        candidate.beta  = tuned[1];
        candidate.gamma = tuned[2];

        candidate.biasMap = raw.biasMap;
        candidate.reserveOverride = raw.reserveOverride;
        candidate.wL = raw.wL; candidate.wE = raw.wE; candidate.wU = raw.wU;
        candidate.wB = raw.wB; candidate.wP = raw.wP;
        candidate.timestampSec = simTime;

        if (validatePlan(candidate, allResources, predictedLoads)) {
            sharedPlan.alpha = candidate.alpha;
            sharedPlan.beta  = candidate.beta;
            sharedPlan.gamma = candidate.gamma;

            sharedPlan.biasMap = candidate.biasMap;
            sharedPlan.reserveOverride = candidate.reserveOverride;

            sharedPlan.wL = candidate.wL; sharedPlan.wE = candidate.wE; sharedPlan.wU = candidate.wU;
            sharedPlan.wB = candidate.wB; sharedPlan.wP = candidate.wP;

            sharedPlan.timestampSec = candidate.timestampSec;
        }
    }

    /* -----------------------------
       Algorithm 5: validate plan
       ----------------------------- */
    static boolean validatePlan(OptimizationPlan p, List<Resource> resources, List<Double> predictedLoads) {
        double maxUtil = 0.0;
        for (int i = 0; i < resources.size(); i++) {
            Resource r = resources.get(i);
            double util = predictedLoads.get(i) / Math.max(r.capacityMB, 1e-9);
            maxUtil = Math.max(maxUtil, util);
        }
        if (maxUtil > UTIL_SAFETY_LIMIT) return false;

        if (p.alpha < 0.1 || p.alpha > 0.9) return false;
        if (p.beta  < 0.0 || p.beta  > 0.6) return false;
        if (p.gamma < 0.0 || p.gamma > 0.6) return false;

        if (p.reserveOverride != null && (p.reserveOverride < 0.0 || p.reserveOverride > 0.5)) return false;

        if (p.wE < 0.10) return false;

        return true;
    }

    static RawPlan neutralRawPlan(List<Resource> resources) {
        RawPlan p = new RawPlan();
        for (Resource r : resources) p.biasMap.put(r.name, 0.0);
        p.reserveOverride = null;
        p.wL = 0.60; p.wE = 0.15; p.wU = 0.25; p.wB = 0.05; p.wP = 0.20;
        return p;
    }

    /* -----------------------------
       Algorithm 6: emergency handler (deadline-aware)
       ----------------------------- */
    static Resource emergencyHandlerEnhanced(Task t, List<Resource> edges, Resource cloud,
                                             double reserveFraction, double now) {
        if (t.priority < PRIORITY_THRESHOLD) return null;

        List<Resource> all = new ArrayList<>(edges);
        all.add(cloud);

        // try to find any resource that can meet deadline under mean network
        for (Resource r : all) {
            double emergencyCap = r.capacityMB * (1.0 - reserveFraction * 0.5);
            if (r.currentLoadMB + t.sizeMB <= emergencyCap) {
                double finishHat = predictFinishTimeMeanNet(t, r, now);
                if (finishHat <= t.absDeadlineSec) return r;
            }
        }

        // last-resort cloud (best effort)
        if (cloud.currentLoadMB + t.sizeMB <= cloud.capacityMB * 0.95) {
            t.cloudForced = true;
            return cloud;
        }
        return null;
    }

    /* -----------------------------
       Metrics (CPU-only utilization)
       ----------------------------- */
    static Metrics computeMetrics(List<Task> done, List<Resource> edges, Resource cloud) {
        Metrics m = new Metrics();
        if (done.isEmpty()) return m;

        double sumLatency = 0.0, sumQueue = 0.0, sumExec = 0.0, sumEnergy = 0.0;
        int miss = 0;

        double start = done.stream().mapToDouble(t -> t.arrivalTimeSec).min().orElse(0.0);
        double end   = done.stream().mapToDouble(t -> t.finishComputeSec).max().orElse(0.0);
        double horizon = Math.max(1e-9, end - start);

        for (Task t : done) {
            double latency = (t.finishComputeSec - t.arrivalTimeSec); // net + queue + exec
            double queue = Math.max(0.0, t.startComputeSec - (t.arrivalTimeSec + t.netTimeSec));
            sumLatency += latency;
            sumQueue += queue;
            sumExec += t.execTimeSec;
            sumEnergy += t.energyJ;
            if (t.finishComputeSec > t.absDeadlineSec) miss++;
        }

        m.avgLatencyMs = (sumLatency / done.size()) * 1000.0;
        m.avgQueueMs   = (sumQueue / done.size()) * 1000.0;
        m.avgExecMs    = (sumExec / done.size()) * 1000.0;
        m.totalEnergyJ = sumEnergy;
        m.deadlineMissRatio = (double) miss / (double) done.size();

        // CPU utilization: average across all resources (edges + cloud)
        List<Resource> all = new ArrayList<>(edges);
        all.add(cloud);

        double utilSum = 0.0;
        for (Resource r : all) {
            double u = clamp(r.busyExecAccumSec / horizon, 0.0, 1.0);
            utilSum += u;
        }
        m.avgCpuUtilization = utilSum / all.size();

        return m;
    }

    /* -----------------------------
       Task generation (Poisson)
       ----------------------------- */
    static List<Task> generateTasksPoisson(Random rnd, int n, double lambdaPerSec) {
        List<Task> tasks = new ArrayList<>();
        double time = 0.0;

        for (int i = 0; i < n; i++) {
            double u = Math.max(1e-12, rnd.nextDouble());
            double inter = -Math.log(u) / lambdaPerSec;
            time += inter;

            int deviceId = rnd.nextInt(NUM_IOT_DEVICES);
            double sizeMB = randDouble(rnd, TASK_SIZE_MB_MIN, TASK_SIZE_MB_MAX);
            double deadline = randDouble(rnd, DEADLINE_SEC_MIN, DEADLINE_SEC_MAX);

            // originalPriority: keep in [0.2..1.0] like your code
            double p = clamp(0.2 + 0.8 * rnd.nextDouble(), 0.0, 1.0);

            TaskType type = sampleTaskType(rnd);
            tasks.add(new Task(i, deviceId, time, sizeMB, deadline, p, type));
        }
        return tasks;
    }

    static TaskType sampleTaskType(Random rnd) {
        double x = rnd.nextDouble();
        double c = 0.0;
        for (TaskType t : TaskType.values()) {
            c += t.prob;
            if (x <= c) return t;
        }
        return TaskType.MONITORING;
    }

    /* -----------------------------
       Compute + Network models
       ----------------------------- */
    static double estimateComputeSec(Task t, Resource r) {
        double mi = t.sizeMB * MI_PER_MB_BASE * t.type.multiplier;
        double effMips = Math.max(1e-9, r.effectiveMips());
        return mi / effMips;
    }

    // mean network (for planning)
    static double meanNetworkSec(boolean isCloud) {
        double edgeMeanMs = (EDGE_NET_MS_MIN + EDGE_NET_MS_MAX) / 2.0;
        if (!isCloud) return edgeMeanMs / 1000.0;
        double cloudMeanMs = (CLOUD_NET_MS_MIN + CLOUD_NET_MS_MAX) / 2.0;
        return (edgeMeanMs + cloudMeanMs) / 1000.0;
    }

    // sample network (for execution)
    static double sampleNetworkSec(boolean isCloud, Random rnd) {
        double edgeMs = randDouble(rnd, EDGE_NET_MS_MIN, EDGE_NET_MS_MAX);
        if (!isCloud) return edgeMs / 1000.0;
        double cloudMs = randDouble(rnd, CLOUD_NET_MS_MIN, CLOUD_NET_MS_MAX);
        return (edgeMs + cloudMs) / 1000.0;
    }

    // Predict finish using mean net, and network-before-compute
    static double predictFinishTimeMeanNet(Task t, Resource r, double now) {
        double netHat = meanNetworkSec(r.isCloud);
        double arrivalAtComputeHat = now + netHat;
        double execHat = estimateComputeSec(t, r);
        double startHat = Math.max(arrivalAtComputeHat, r.busyUntilSec);
        return startHat + execHat;
    }

    /* -----------------------------
       Candidate selection helpers
       ----------------------------- */
    static List<Resource> pickKSmallest(Map<Resource, Double> map, int k) {
        List<Map.Entry<Resource, Double>> entries = new ArrayList<>(map.entrySet());
        entries.sort(Comparator.comparingDouble(Map.Entry::getValue));
        List<Resource> out = new ArrayList<>();
        for (int i = 0; i < Math.min(k, entries.size()); i++) out.add(entries.get(i).getKey());
        return out;
    }

    // RAM proxy used only for quantum stub logic (not for CPU util metric)
    static double avgEdgeRamUtil(List<Resource> resources) {
        double sum = 0.0;
        int count = 0;
        for (Resource r : resources) {
            if (r.isCloud) continue;
            sum += r.currentLoadMB / Math.max(r.capacityMB, 1e-9);
            count++;
        }
        return count == 0 ? 0.0 : sum / count;
    }

    // CPU queue proxy for priority load term (better aligned with scheduling reality)
    static double avgEdgeQueueNorm(List<Resource> edges, double now, double meanEdgeNet) {
        double sum = 0.0;
        for (Resource r : edges) {
            double arrivalAtComputeHat = now + meanEdgeNet;
            double q = Math.max(0.0, r.busyUntilSec - arrivalAtComputeHat);
            sum += clamp(q / QUEUE_NORM_S, 0.0, 1.0);
        }
        return edges.isEmpty() ? 0.0 : (sum / edges.size());
    }

    /* -----------------------------
       Utils
       ----------------------------- */
    static double clamp(double x, double lo, double hi) {
        return Math.max(lo, Math.min(hi, x));
    }

    static double randDouble(Random rnd, double lo, double hi) {
        return lo + (hi - lo) * rnd.nextDouble();
    }

    static int randInt(Random rnd, int lo, int hi) {
        return lo + rnd.nextInt((hi - lo) + 1);
    }
}

