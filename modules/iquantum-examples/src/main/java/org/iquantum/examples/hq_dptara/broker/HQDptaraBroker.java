//package org.iquantum.examples.hq_dptara.broker;
//import java.util.*;
//import org.iquantum.brokers.CBroker;
//import org.iquantum.backends.classical.Vm;
//import org.iquantum.tasks.CTask;
//import org.iquantum.utils.Log;
//import org.iquantum.examples.hq_dptara.util.Dist;
//import org.iquantum.examples.hq_dptara.util.SimConfig;
//public class HQDptaraBroker extends CBroker {
//    // ========== META ==========
//    public static final class Meta {
//        private static final Map<Integer, Double> ARRIVAL  = new HashMap<>();
//        private static final Map<Integer, Double> DEADLINE = new HashMap<>();
//        private static final Map<Integer, Double> PORIG    = new HashMap<>();
//        private static final Map<Integer, Double> SIZE_MB  = new HashMap<>();
//
//        public static void put(int tid, double arrival, double deadline, double porig, double sizeMb) {
//            ARRIVAL.put(tid, arrival);
//            DEADLINE.put(tid, deadline);
//            PORIG.put(tid, porig);
//            SIZE_MB.put(tid, sizeMb);
//        }
//        public static double arrival(int tid)  { return ARRIVAL.getOrDefault(tid, 0.0); }
//        public static double deadline(int tid) { return DEADLINE.getOrDefault(tid, 50.0); }
//        public static double porig(int tid)    { return PORIG.getOrDefault(tid, 0.5); }
//        public static double sizeMb(int tid)   { return SIZE_MB.getOrDefault(tid, 10.0); }
//
//        public static boolean isPr(int tid) {
//            return porig(tid) >= SimConfig.PR_CLASS_THRESHOLD;
//        }
//    }
//
//    // ========== VM STATE ==========
//    private static final class VmState {
//        final Vm vm;
//        final boolean isCloud;
//
//        // queue finish time tracker (seconds)
//        double busyUntilSec = 0.0;
//
//        // normalized queue pressure in [0..1]
//        double loadUnits = 0.0;
//
//        VmState(Vm vm) {
//            this.vm = vm;
//            this.isCloud = "CLOUD".equalsIgnoreCase(vm.getVmm());
//        }
//    }
//
//    private boolean vmStatesReady = false;
//    private final List<VmState> edges = new ArrayList<>();
//    private VmState cloud = null;
//
//    // HQ controls
//    private final boolean quantumEnabled;
//    private final QuantumController Q = new QuantumController();
//
//    private Plan plan;
//    private Plan lastSafePlan;
//
//    // Metrics
//    private final Metrics metrics = new Metrics();
//
//    // ===== Quantum Debug Counters =====
//    private int qBatchesRun = 0;
//    private int qPlansAccepted = 0;
//    private int qPlansRejected = 0;
//
//    // Avg delta tracking (only for ACCEPTED plans)
//    private double qDeltaAlphaSum = 0.0;
//    private double qDeltaBetaSum  = 0.0;
//    private double qDeltaGammaSum = 0.0;
//    private double qDeltaWLatSum  = 0.0;
//    private double qDeltaWEneSum  = 0.0;
//    private double qDeltaWUtilSum = 0.0;
//
//    private double lastBatchUpdateSec = 0.0;
//    private Metrics snapshot = new Metrics();
//
//    // Store analytical end-to-end finish time (includes net latency)
//    private final Map<Integer, Double> finishHatByTask = new HashMap<>();
//
//    public HQDptaraBroker(String name, boolean quantumEnabled) throws Exception {
//        super(name);
//        this.quantumEnabled = quantumEnabled;
//
//        this.plan = new Plan(
//                SimConfig.DEFAULT_ALPHA, SimConfig.DEFAULT_BETA, SimConfig.DEFAULT_GAMMA,
//                SimConfig.W_LAT, SimConfig.W_ENE, SimConfig.W_UTIL, SimConfig.W_BIAS, SimConfig.W_PRIO
//        );
//        this.lastSafePlan = plan.copy();
//    }
//
//    public Metrics getMetrics() { return metrics; }
//
//    @Override
//    protected void processOtherEvent(org.iquantum.core.SimEvent ev) {
//        int tag = ev.getTag();
//
//        // ONE TAG only
//        if (tag == SimConfig.TAG_TASK_ARRIVAL) {
//            Object data = ev.getData();
//            if (data instanceof CTask) {
//                handleSingleArrival((CTask) data);
//            }
//            return;
//        }
//
//        super.processOtherEvent(ev);
//    }
//
//    private void initVmStatesIfNeeded() {
//        if (vmStatesReady) return;
//
//        List<Vm> vms = getVmsCreatedList();
//        if (vms == null || vms.isEmpty()) return;
//
//        edges.clear();
//        cloud = null;
//
//        for (Vm vm : vms) {
//            VmState s = new VmState(vm);
//            if (s.isCloud) cloud = s;
//            else edges.add(s);
//        }
//        if (cloud == null) cloud = new VmState(vms.get(vms.size() - 1));
//
//        vmStatesReady = true;
//    }
//
//    private void handleSingleArrival(CTask t) {
//        initVmStatesIfNeeded();
//        if (!vmStatesReady) return;
//
//        int tid = t.getCloudletId();
//        double now = Meta.arrival(tid);
//
//        metrics.onGenerated();
//
//        // quantum update (periodic)
//        if (quantumEnabled) maybeRunQuantumBatch(now);
//
//        int vmId = hqSelectVm(t, now);
//        if (vmId < 0) {
//            metrics.onRejected();
//            return;
//        }
//
//        submitCloudletList(Collections.singletonList(t));
//        bindCloudletToVm(tid, vmId);
//
//        VmState chosen = (cloud != null && cloud.vm.getId() == vmId) ? cloud : find(edges, vmId);
//        if (chosen != null) {
//            double finishHat = estimateFinish(chosen, t, now);
//            finishHatByTask.put(tid, finishHat);
//
//            updateVmStateAfterAssign(chosen, t, now);
//        }
//
//        super.submitCloudlets();
//    }
//
//    // ---------------- Algorithm 3: dynamic priority ----------------
//    private double dynamicPriority(int tid, double nowSec) {
//        double arrival  = Meta.arrival(tid);
//        double deadline = Meta.deadline(tid);
//        double porig    = Meta.porig(tid);
//
//        double aging = (nowSec - arrival) / Math.max(deadline, SimConfig.EPS);
//        aging = clamp(aging, 0.0, 2.0);
//
//        double Lcur = avgEdgeUtil(edges);
//        Lcur = clamp(Lcur, 0.0, 2.0);
//
//        double Pi = plan.alpha * porig + plan.beta * aging - plan.gamma * Lcur;
//        return clamp(Pi, 0.0, 1.0);
//    }
//
//    // ---------------- Algorithm 4: balancing rate ----------------
//    private double balancingRate(VmState s, List<VmState> all) {
//        double sum = 0.0;
//        for (VmState x : all) sum += x.loadUnits;
//        double avg = sum / Math.max(1, all.size());
//        double br = 1.0 - Math.abs(s.loadUnits - avg) / (avg + SimConfig.EPS);
//        return clamp(br, 0.0, 1.0);
//    }
//
//    // ---------------- Algorithm 5: validate plan ----------------
//    private boolean validatePlan(Plan candidate, List<VmState> edges, VmState cloud) {
//        double reserve = candidate.reserveOverride != null ? candidate.reserveOverride : SimConfig.BASE_RESERVE;
//
//        if (reserve < 0.0 || reserve > 0.30) {
//            Log.printLine("[Q][FAIL] reserve out of range: " + reserve);
//            return false;
//        }
//
//        // stability limits
//        if (candidate.wEne < plan.wEne * (1.0 - SimConfig.ENERGY_TOL_FRAC) - 1e-9) {
//            Log.printLine("[Q][FAIL] wEne too low: cand=" + candidate.wEne + " cur=" + plan.wEne);
//            return false;
//        }
//        if (candidate.beta < plan.beta * 0.85) {
//            Log.printLine("[Q][FAIL] beta too low: cand=" + candidate.beta + " cur=" + plan.beta);
//            return false;
//        }
//
//        // migration/bias budget
//        double meanAbsBias = 0.0;
//        int cnt = 0;
//        for (double b : candidate.biasMap.values()) { meanAbsBias += Math.abs(b); cnt++; }
//        meanAbsBias = (cnt == 0) ? 0.0 : (meanAbsBias / cnt);
//
//        if (meanAbsBias > SimConfig.MIGRATION_BUDGET) {
//            Log.printLine("[Q][FAIL] bias budget exceeded: meanAbsBias=" + meanAbsBias
//                    + " budget=" + SimConfig.MIGRATION_BUDGET);
//            return false;
//        }
//
//        // change fraction (bias map stability)
//        List<VmState> all = new ArrayList<>(edges);
//        if (cloud != null) all.add(cloud);
//
//        int changed = 0;
//        int total = all.size();
//        for (VmState s : all) {
//            double oldb = plan.biasMap.getOrDefault(s.vm.getId(), 0.0);
//            double newb = candidate.biasMap.getOrDefault(s.vm.getId(), 0.0);
////            if (Math.abs(newb - oldb) > 0.10) changed++;
//            if (Math.abs(newb - oldb) > 0.30) changed++;
//
//        }
//
//        double frac = (double) changed / Math.max(1, total);
//        if (frac > SimConfig.PLAN_CHANGE_LIMIT_FRAC) {
//            Log.printLine("[Q][FAIL] plan change too large: frac=" + frac
//                    + " limit=" + SimConfig.PLAN_CHANGE_LIMIT_FRAC);
//            return false;
//        }
//
//        return true;
//    }
//
//    // ---------------- Algorithm 6: emergency handler ----------------
//    private int emergencyHandler(CTask task, double nowSec, double Pi) {
//        int tid = task.getCloudletId();
//        double absDeadline = Meta.arrival(tid) + Meta.deadline(tid);
//
//        VmState best = null;
//        double bestFinish = Double.POSITIVE_INFINITY;
//
//        for (VmState e : edges) {
//            double fin = estimateFinish(e, task, nowSec);
//            if (fin <= absDeadline && fin < bestFinish) {
//                bestFinish = fin;
//                best = e;
//            }
//        }
//        if (best != null) return best.vm.getId();
//
//        if (cloud != null) return cloud.vm.getId();
//
//        VmState least = null;
//        for (VmState e : edges) if (least == null || e.loadUnits < least.loadUnits) least = e;
//        return (least != null) ? least.vm.getId() : -1;
//    }
//
//    // ---------------- Core selection (Algorithm 1) ----------------
//    private int hqSelectVm(CTask task, double nowSec) {
//        if (cloud == null && edges.isEmpty()) return -1;
//
//        int tid = task.getCloudletId();
//        double Pi = dynamicPriority(tid, nowSec);
//
//        List<VmState> topEdges = topKEdgesByFinish(task, nowSec, edges);
//        List<VmState> candidates = new ArrayList<>(topEdges);
//        if (cloud != null) candidates.add(cloud);
//
//        VmState best = null;
//        double bestScore = Double.POSITIVE_INFINITY;
//        double bestFinish = Double.POSITIVE_INFINITY;
//
//        // references (fixed, reasonable for your scale)
//        double Lref = 10.0;    // seconds
//        double Eref = 1500.0;  // J scale close to your observations
//
//        List<VmState> all = new ArrayList<>(edges);
//        if (cloud != null) all.add(cloud);
//
//        double absDeadline = Meta.arrival(tid) + Meta.deadline(tid);
//
//        for (VmState s : candidates) {
//            double finish = estimateFinish(s, task, nowSec);
//            double comp = computeSec(task, s);
//
//            double latency = (finish - nowSec);
//            double energy = powerW(s) * comp;
//            double util01 = util01(s);
//            double bias = plan.biasMap.getOrDefault(s.vm.getId(), 0.0);
//            double br01 = balancingRate(s, all);
//
//            // soft deadline penalty
//            double dlPenalty = 0.0;
//            if (finish > absDeadline) {
//                double over = (finish - absDeadline);
//                dlPenalty = clamp(over / Math.max(1e-6, Meta.deadline(tid)), 0.0, 2.0);
//            }
//
//            double score =
//                    plan.wLat  * (latency / Math.max(SimConfig.EPS, Lref)) +
//                            plan.wEne  * (energy  / Math.max(SimConfig.EPS, Eref)) +
//                            plan.wUtil * util01 +
//                            plan.wBias * (-bias) +
//                            plan.wPrio * (-Pi) +
//                            0.50 * dlPenalty;
//
//            metrics.sampleBr(br01);
//
//            if (score < bestScore) {
//                bestScore = score;
//                best = s;
//                bestFinish = finish;
//            }
//        }
//
//        if (best == null) return -1;
//
//        if (Pi >= SimConfig.CRITICAL_THRESHOLD && bestFinish > absDeadline) {
//            int alt = emergencyHandler(task, nowSec, Pi);
//            if (alt >= 0) return alt;
//        }
//
//        metrics.sampleUtil(avgEdgeUtil(edges));
//        return best.vm.getId();
//    }
//
//    // ---------------- Quantum batch worker (Algorithm 2) ----------------
//    private void maybeRunQuantumBatch(double nowSec) {
//        if (nowSec - lastBatchUpdateSec < SimConfig.QUANTUM_BATCH_PERIOD_SEC) return;
//
//        Metrics recent = delta(metrics, snapshot);
//
//        Map<Integer, Double> utilMap = new HashMap<>();
//        for (VmState e : edges) utilMap.put(e.vm.getId(), util01(e));
//        if (cloud != null) utilMap.put(cloud.vm.getId(), util01(cloud));
//
//        qBatchesRun++;
//
//        Plan candidate = Q.suggestUpdate(recent, plan, utilMap);
//        candidate.timestampSec = nowSec;
//
//// ✅ BLEND bias gradually (FINAL-safe)
//        Map<Integer, Double> blended =
//                blendBias(plan.biasMap, candidate.biasMap, 0.2);
//        candidate.biasMap.clear();
//        candidate.biasMap.putAll(blended);
//
//// ---- DEBUG print candidate vs current ----
//        Log.printLine(String.format(
//                "[Q] t=%.2f cand(a=%.3f b=%.3f g=%.3f | wLat=%.3f wEne=%.3f wUtil=%.3f)  cur(a=%.3f b=%.3f g=%.3f | wLat=%.3f wEne=%.3f wUtil=%.3f)",
//                nowSec,
//                candidate.alpha, candidate.beta, candidate.gamma,
//                candidate.wLat, candidate.wEne, candidate.wUtil,
//                plan.alpha, plan.beta, plan.gamma,
//                plan.wLat, plan.wEne, plan.wUtil
//        ));
//
//        boolean ok = validatePlan(candidate, edges, cloud);
//
//
//        if (ok) {
//            qPlansAccepted++;
//
//            // ✅ deltas BEFORE updating plan
//            qDeltaAlphaSum += Math.abs(candidate.alpha - plan.alpha);
//            qDeltaBetaSum  += Math.abs(candidate.beta  - plan.beta);
//            qDeltaGammaSum += Math.abs(candidate.gamma - plan.gamma);
//            qDeltaWLatSum  += Math.abs(candidate.wLat  - plan.wLat);
//            qDeltaWEneSum  += Math.abs(candidate.wEne  - plan.wEne);
//            qDeltaWUtilSum += Math.abs(candidate.wUtil - plan.wUtil);
//
//            plan = candidate;
//            lastSafePlan = candidate.copy();
//            Log.printLine("[Q] ACCEPTED plan @t=" + nowSec);
//        } else {
//            qPlansRejected++;
//            plan = lastSafePlan.copy();
//            Log.printLine("[Q] REJECTED plan @t=" + nowSec + " accepted=" + qPlansAccepted + "/" + qBatchesRun);
//        }
//
//        snapshot = copyMetrics(metrics);
//        lastBatchUpdateSec = nowSec;
//    }
//
//    private static Metrics copyMetrics(Metrics m) {
//        Metrics c = new Metrics();
//        c.totalGenerated = m.totalGenerated;
//        c.completed = m.completed;
//        c.rejected = m.rejected;
//        c.completedPr = m.completedPr;
//        c.completedNr = m.completedNr;
//        c.deadlineMiss = m.deadlineMiss;
//        c.totalLatencySec = m.totalLatencySec;
//        c.totalLatencySecPr = m.totalLatencySecPr;
//        c.totalLatencySecNr = m.totalLatencySecNr;
//        c.totalExecSec = m.totalExecSec;
//        c.totalEnergyJ = m.totalEnergyJ;
//        c.utilSamples = m.utilSamples;
//        c.utilSum = m.utilSum;
//        c.brSamples = m.brSamples;
//        c.brSum = m.brSum;
//        return c;
//    }
//
//    private static Metrics delta(Metrics cur, Metrics base) {
//        Metrics d = new Metrics();
//        d.totalGenerated = cur.totalGenerated - base.totalGenerated;
//        d.completed = cur.completed - base.completed;
//        d.rejected = cur.rejected - base.rejected;
//        d.completedPr = cur.completedPr - base.completedPr;
//        d.completedNr = cur.completedNr - base.completedNr;
//        d.deadlineMiss = cur.deadlineMiss - base.deadlineMiss;
//        d.totalLatencySec = cur.totalLatencySec - base.totalLatencySec;
//        d.totalLatencySecPr = cur.totalLatencySecPr - base.totalLatencySecPr;
//        d.totalLatencySecNr = cur.totalLatencySecNr - base.totalLatencySecNr;
//        d.totalExecSec = cur.totalExecSec - base.totalExecSec;
//        d.totalEnergyJ = cur.totalEnergyJ - base.totalEnergyJ;
//        d.utilSamples = cur.utilSamples - base.utilSamples;
//        d.utilSum = cur.utilSum - base.utilSum;
//        d.brSamples = cur.brSamples - base.brSamples;
//        d.brSum = cur.brSum - base.brSum;
//        return d;
//    }
//
//    private VmState find(List<VmState> edges, int vmId) {
//        for (VmState s : edges) if (s.vm.getId() == vmId) return s;
//        return null;
//    }
//
//    private void updateVmStateAfterAssign(VmState s, CTask task, double nowSec) {
//        double comp = computeSec(task, s);
//        double net  = netLatencySec(s.isCloud);
//
//        double start = Math.max(nowSec, s.busyUntilSec);
//        double finish = start + net + comp;
//
//        s.busyUntilSec = finish;
//
//        // normalized queue pressure in [0..1]
//        double window = Math.max(1e-6, SimConfig.DEADLINE_SEC_MAX);
//        double queue = Math.max(0.0, s.busyUntilSec - nowSec);
//        s.loadUnits = clamp(queue / window, 0.0, 1.0);
//    }
//
//    private double estimateFinish(VmState s, CTask task, double nowSec) {
//        double comp = computeSec(task, s);
//        double net  = netLatencySec(s.isCloud);
//        double start = Math.max(nowSec, s.busyUntilSec);
//        return start + net + comp;
//    }
//
//    private List<VmState> topKEdgesByFinish(CTask task, double nowSec, List<VmState> edges) {
//        List<VmFinish> list = new ArrayList<>();
//        for (VmState e : edges) {
//            double finish = estimateFinish(e, task, nowSec);
//            list.add(new VmFinish(e, finish));
//        }
//        list.sort(Comparator.comparingDouble(x -> x.finish));
//        int k = Math.min(SimConfig.K_CANDIDATES, list.size());
//        List<VmState> top = new ArrayList<>(k);
//        for (int i = 0; i < k; i++) top.add(list.get(i).s);
//        return top;
//    }
//
//    private static final class VmFinish {
//        VmState s; double finish;
//        VmFinish(VmState s, double finish) { this.s = s; this.finish = finish; }
//    }
//
//    private double netLatencySec(boolean isCloud) {
//        return isCloud
//                ? Dist.uniform(SimConfig.CLOUD_NET_MS_MIN, SimConfig.CLOUD_NET_MS_MAX) / 1000.0
//                : Dist.uniform(SimConfig.EDGE_NET_MS_MIN, SimConfig.EDGE_NET_MS_MAX) / 1000.0;
//    }
//
//    private double computeSec(CTask task, VmState s) {
//        return task.getCloudletLength() / Math.max(s.vm.getMips(), SimConfig.EPS);
//    }
//
//    private double util01(VmState s) {
//        return clamp(s.loadUnits, 0.0, 1.0);
//    }
//
//    private double avgEdgeUtil(List<VmState> edges) {
//        if (edges.isEmpty()) return 0.0;
//        double sum = 0.0;
//        for (VmState e : edges) sum += util01(e);
//        return sum / Math.max(1, edges.size());
//    }
//
//    private double powerW(VmState s) {
//        return s.isCloud ? SimConfig.CLOUD_POWER_W : SimConfig.EDGE_POWER_W;
//    }
//
//    private static double clamp(double x, double lo, double hi) {
//        return Math.max(lo, Math.min(hi, x));
//    }
//
//    private static Map<Integer, Double> blendBias(Map<Integer, Double> cur, Map<Integer, Double> cand, double eta) {
//        Map<Integer, Double> out = new HashMap<>();
//        Set<Integer> keys = new HashSet<>();
//        keys.addAll(cur.keySet());
//        keys.addAll(cand.keySet());
//
//        for (int k : keys) {
//            double c = cur.getOrDefault(k, 0.0);
//            double d = cand.getOrDefault(k, 0.0);
//            out.put(k, c + eta * (d - c));   // gradual move
//        }
//        return out;
//    }
//
//
//    // ---------------- Results metrics ----------------
//    @Override
//    protected void processCloudletReturn(org.iquantum.core.SimEvent ev) {
//        Object data = ev.getData();
//        if (!(data instanceof CTask)) return;
//
//        CTask t = (CTask) data;
//
//        // Add to received list manually (avoid super early finish)
//        getCloudletReceivedList().add(t);
//
//        int tid = t.getCloudletId();
//        double arrival = Meta.arrival(tid);
//        double deadline = Meta.deadline(tid);
//        double absDeadline = arrival + deadline;
//
//        double exec = t.getActualCPUTime();
//
//        double finishHat = finishHatByTask.getOrDefault(tid, t.getFinishTime());
//        double latency = Math.max(0.0, finishHat - arrival);
//
//        boolean isCloud = (t.getVmId() >= SimConfig.NUM_EDGE_SERVERS);
//        double power = isCloud ? SimConfig.CLOUD_POWER_W : SimConfig.EDGE_POWER_W;
//        double energy = power * exec;
//
//        boolean missed = (finishHat > absDeadline);
//        boolean isPr = Meta.isPr(tid);
//
//        metrics.addCompleted(latency, exec, energy, missed, isPr);
//    }
//
//    @Override
//    protected void finishExecution() {
//        if (metrics.totalGenerated < SimConfig.TOTAL_TASKS) return;
//
//        if (quantumEnabled) {
//            Log.printLine("---- Quantum Debug Summary ----");
//            Log.printLine("Quantum batches run     : " + qBatchesRun);
//            Log.printLine("Plans accepted          : " + qPlansAccepted);
//            Log.printLine("Plans rejected          : " + qPlansRejected);
//
//            int acc = Math.max(1, qPlansAccepted);
//            Log.printLine(String.format("Avg |Δalpha| : %.6f", qDeltaAlphaSum / acc));
//            Log.printLine(String.format("Avg |Δbeta|  : %.6f", qDeltaBetaSum  / acc));
//            Log.printLine(String.format("Avg |Δgamma| : %.6f", qDeltaGammaSum / acc));
//            Log.printLine(String.format("Avg |ΔwLat|  : %.6f", qDeltaWLatSum  / acc));
//            Log.printLine(String.format("Avg |ΔwEne|  : %.6f", qDeltaWEneSum  / acc));
//            Log.printLine(String.format("Avg |ΔwUtil| : %.6f", qDeltaWUtilSum / acc));
//        }
//
//        super.finishExecution();
//    }
//}

//
//package org.iquantum.examples.hq_dptara.broker;
//import java.util.*;
//import org.iquantum.brokers.CBroker;
//import org.iquantum.backends.classical.Vm;
//import org.iquantum.tasks.CTask;
//import org.iquantum.utils.Log;
//import org.iquantum.examples.hq_dptara.util.SimConfig;
///**
// * HQ-DPTARA Broker (iQuantum)
// *
// * - Receives Poisson task arrivals via TAG_TASK_ARRIVAL
// * - Assigns each arriving task to a VM using HQ-DPTARA scoring
// * - Collects metrics on cloudlet return
// * - HARD stops simulation exactly after (completed + rejected) == TOTAL_TASKS
// *
// * Key fixes:
// * 1) PR tasks are EDGE-first; cloud only as fallback (avoid reject).
// * 2) Cloud penalty added (base + queue-based) to prevent cloud congestion.
// * 3) Utilization fixed:
// *    - Scoring utilization = RAM utilization (currentLoadMb / capacityMb)
// *    - Printed utilization = sum(busyTimeSec)/(N*horizon)
// * 4) busyUntilSec = CPU-busy horizon ONLY (network latency must NOT block CPU queue)
// */
//public class HQDptaraBroker extends CBroker {
//    /* =================== TUNABLES =================== */
//    private static final double CLOUD_SCORE_PENALTY_BASE = 2.50;
//    private static final double CLOUD_QUEUE_PENALTY_PER_SEC = 0.20;
//    private static final double EDGE_QUEUE_PENALTY_PER_SEC = 0.15;
//    /* ================= META ================= */
//    public static final class Meta {
//        private static final Map<Integer, Double> ARRIVAL  = new HashMap<>();
//        private static final Map<Integer, Double> DEADLINE = new HashMap<>();
//        private static final Map<Integer, Double> PORIG    = new HashMap<>();
//        private static final Map<Integer, Double> SIZE_MB  = new HashMap<>();
//        public static void put(int tid, double arrival, double deadline, double porig, double sizeMb) {
//            ARRIVAL.put(tid, arrival);
//            DEADLINE.put(tid, deadline);
//            PORIG.put(tid, porig);
//            SIZE_MB.put(tid, sizeMb);
//        }
//        public static double arrival(int tid)  { return ARRIVAL.getOrDefault(tid, 0.0); }
//        public static double deadline(int tid) { return DEADLINE.getOrDefault(tid, 50.0); }
//        public static double porig(int tid)    { return PORIG.getOrDefault(tid, 0.5); }
//        public static double sizeMb(int tid)   { return SIZE_MB.getOrDefault(tid, 10.0); }
//        public static boolean isPr(int tid) {
//            return porig(tid) >= SimConfig.PR_CLASS_THRESHOLD;
//        }
//    }
//
//    /* ================= VM STATE ================= */
//    private static final class VmState {
//        final Vm vm;
//        final boolean isCloud;
//
//        // CPU queue model (seconds): when CPU becomes available again
//        double busyUntilSec = 0.0;
//
//        // Queue pressure [0..1] (still useful for queue penalty)
//        double loadUnits = 0.0;
//
//        // RAM occupancy model (MB)
//        double currentLoadMb = 0.0;
//
//        // Real busy time accumulator (CPU compute seconds) for utilization
//        double busyTimeSec = 0.0;
//
//        VmState(Vm vm) {
//            this.vm = vm;
//            this.isCloud = "CLOUD".equalsIgnoreCase(vm.getVmm());
//        }
//
//        double capacityMb() {
//            return Math.max(1.0, vm.getRam());
//        }
//    }
//
//    private boolean vmStatesReady = false;
//    private final List<VmState> edges = new ArrayList<>();
//    private VmState cloud = null;
//
//    /* ================= HQ CONTROLS ================= */
//    private final boolean quantumEnabled;
//    private final QuantumController Q = new QuantumController();
//
//    private Plan plan;
//    private Plan lastSafePlan;
//
//    /* ================= METRICS ================= */
//    private final Metrics metrics = new Metrics();
//    private final Map<Integer, Integer> vmAssignCount = new HashMap<>();
//
//    /* ================= STOP GUARANTEE ================= */
//    private boolean finishedOnce = false;
//
//    // Optional: predicted finish for stable latency
//    private final Map<Integer, Double> finishHatByTask = new HashMap<>();
//
//    // Quantum counters
//    @SuppressWarnings("unused") private int qBatchesRun = 0;
//    @SuppressWarnings("unused") private int qPlansAccepted = 0;
//    @SuppressWarnings("unused") private int qPlansRejected = 0;
//
//    private double lastBatchUpdateSec = 0.0;
//    private Metrics snapshot = new Metrics();
//
//    public HQDptaraBroker(String name, boolean quantumEnabled) throws Exception {
//        super(name);
//        this.quantumEnabled = quantumEnabled;
//
//        this.plan = new Plan(
//                SimConfig.DEFAULT_ALPHA,
//                SimConfig.DEFAULT_BETA,
//                SimConfig.DEFAULT_GAMMA,
//                SimConfig.W_LAT,
//                SimConfig.W_ENE,
//                SimConfig.W_UTIL,
//                SimConfig.W_BIAS,
//                SimConfig.W_PRIO
//        );
//        this.lastSafePlan = plan.copy();
//    }
//
//    public Metrics getMetrics() { return metrics; }
//
//    /* ================= EVENT HANDLING ================= */
//    @Override
//    protected void processOtherEvent(org.iquantum.core.SimEvent ev) {
//        if (ev.getTag() == SimConfig.TAG_TASK_ARRIVAL) {
//            Object data = ev.getData();
//            if (data instanceof CTask) handleSingleArrival((CTask) data);
//            return;
//        }
//        super.processOtherEvent(ev);
//    }
//
//    private void initVmStatesIfNeeded() {
//        if (vmStatesReady) return;
//
//        List<Vm> vms = getVmsCreatedList();
//        if (vms == null || vms.isEmpty()) return;
//
//        edges.clear();
//        cloud = null;
//
//        for (Vm vm : vms) {
//            VmState s = new VmState(vm);
//            if (s.isCloud) cloud = s;
//            else edges.add(s);
//        }
//        if (cloud == null) cloud = new VmState(vms.get(vms.size() - 1));
//
//        vmStatesReady = true;
//    }
//
//    private void handleSingleArrival(CTask t) {
//        initVmStatesIfNeeded();
//        if (!vmStatesReady) return;
//
//        t.setUserId(getId());
//
//        int tid = t.getCloudletId();
//        double now = Meta.arrival(tid);
//
//        metrics.onGenerated();
//
//        if (quantumEnabled) maybeRunQuantumBatch(now);
//
//        int vmId = hqSelectVm(t, now);
//        if (vmId < 0) {
//            metrics.onRejected();
//            maybeStopAndPrint();
//            return;
//        }
//
//        submitCloudletList(Collections.singletonList(t));
//        bindCloudletToVm(tid, vmId);
//
//        VmState chosen = (cloud != null && cloud.vm.getId() == vmId) ? cloud : find(edges, vmId);
//        if (chosen != null) {
//            double finishHat = estimateFinish(chosen, t, now);
//            finishHatByTask.put(tid, finishHat);
//            updateVmStateAfterAssign(chosen, t, now);
//        }
//
//        super.submitCloudlets();
//    }
//
//    /* ================= Algorithm 3: dynamic priority ================= */
//    private double dynamicPriority(int tid, double nowSec) {
//        double deadline = Meta.deadline(tid);
//        double porig    = Meta.porig(tid);
//
//        double earliestStart = Double.POSITIVE_INFINITY;
//        for (VmState e : edges) {
//            earliestStart = Math.min(earliestStart, Math.max(nowSec, e.busyUntilSec));
//        }
//        if (!Double.isFinite(earliestStart)) earliestStart = nowSec;
//
//        double wait = Math.max(0.0, earliestStart - nowSec);
//        double aging = clamp(wait / Math.max(deadline, SimConfig.EPS), 0.0, 2.0);
//
//        // current load indicator: average RAM util across edges (0..1)
//        double Lcur = clamp(avgEdgeRamUtil(edges), 0.0, 1.0);
//
//        double Pi = plan.alpha * porig + plan.beta * aging - plan.gamma * Lcur;
//        return clamp(Pi, 0.0, 1.0);
//    }
//
//    /* ================= Core selection ================= */
//    private int hqSelectVm(CTask task, double nowSec) {
//        if (cloud == null && edges.isEmpty()) return -1;
//
//        int tid = task.getCloudletId();
//        double Pi = dynamicPriority(tid, nowSec);
//
//        // Reserve gate
//        double reserve = (Pi >= SimConfig.CRITICAL_THRESHOLD) ? 0.05 : SimConfig.BASE_RESERVE;
//        if (plan.reserveOverride != null) reserve = clamp(plan.reserveOverride, 0.0, 0.30);
//
//        List<VmState> candidates = new ArrayList<>(topKEdgesByFinish(task, nowSec, edges));
//        boolean isHighPriority = (Pi >= SimConfig.CRITICAL_THRESHOLD);
//
//        // Best edge finish among candidates
//        double bestEdgeFinish = Double.POSITIVE_INFINITY;
//        for (VmState e : candidates) {
//            bestEdgeFinish = Math.min(bestEdgeFinish, estimateFinish(e, task, nowSec));
//        }
//        double absDeadline = Meta.arrival(tid) + Meta.deadline(tid);
//
//        // Allow cloud ONLY for NR when edge likely misses deadline or edges are heavily loaded
//        boolean allowCloudForNR = (!isHighPriority) &&
//                (bestEdgeFinish > absDeadline || avgEdgeRamUtil(edges) > 0.70);
//
//        if (cloud != null && allowCloudForNR) candidates.add(cloud);
//
//        VmState best = null;
//        double bestScore = Double.POSITIVE_INFINITY;
//
//        double Lref = 10.0;
//        double Eref = 1500.0;
//
//        List<VmState> all = new ArrayList<>(edges);
//        if (cloud != null) all.add(cloud);
//
//        for (VmState s : candidates) {
//            double taskMb = Meta.sizeMb(tid);
//            double availableMb = s.capacityMb() * (1.0 - reserve);
//            if (s.currentLoadMb + taskMb > availableMb) continue;
//
//            double finish = estimateFinish(s, task, nowSec);
//            double comp   = computeSec(task, s);
//            double latency = finish - nowSec;
//            double energy = powerW(s) * comp;
//
//            // ✅ Utilization in scoring = RAM utilization (0..1)
//            double util01 = ramUtil01(s);
//
//            double bias   = plan.biasMap.getOrDefault(s.vm.getId(), 0.0);
//            double br01   = balancingRate(s, all);
//
//            double dlPenalty = 0.0;
//            if (finish > absDeadline) {
//                double over = finish - absDeadline;
//                dlPenalty = clamp(over / Math.max(1e-6, Meta.deadline(tid)), 0.0, 2.0);
//            }
//
//            // Cloud penalty: base + queue-based
//            double cloudPenalty = 0.0;
//            if (s.isCloud) {
//                double q = Math.max(0.0, s.busyUntilSec - nowSec); // CPU-queue seconds only
//                cloudPenalty = CLOUD_SCORE_PENALTY_BASE + CLOUD_QUEUE_PENALTY_PER_SEC * q;
//            }
//
//            double queueSec = Math.max(0.0, s.busyUntilSec - nowSec);
//            double edgeQueuePenalty = (!s.isCloud) ? (EDGE_QUEUE_PENALTY_PER_SEC * queueSec) : 0.0;
//
//            double score =
//                    plan.wLat  * (latency / Math.max(SimConfig.EPS, Lref)) +
//                            plan.wEne  * (energy  / Math.max(SimConfig.EPS, Eref)) +
//                            plan.wUtil * util01 +
//                            plan.wBias * (-bias) +
//                            plan.wPrio * (-Pi) +
//                            0.50 * dlPenalty +
//                            cloudPenalty +
//                            edgeQueuePenalty;
//
//            metrics.sampleBr(br01);
//
//            if (score < bestScore) {
//                bestScore = score;
//                best = s;
//            }
//        }
//
//        // Fallback for PR: if no edge fits, allow cloud to avoid reject
//        if (best == null && isHighPriority && cloud != null) {
//            VmState s = cloud;
//            double taskMb = Meta.sizeMb(tid);
//            double availableMb = s.capacityMb() * (1.0 - reserve);
//            if (s.currentLoadMb + taskMb <= availableMb) best = s;
//        }
//
//        if (best == null) return -1;
//
//        // sample RAM-util based edge utilization for graphs (optional)
//        metrics.sampleUtil(avgEdgeRamUtil(edges));
//        vmAssignCount.merge(best.vm.getId(), 1, Integer::sum);
//        return best.vm.getId();
//    }
//
//    /* ================= Algorithm 4: balancing rate ================= */
//    private double balancingRate(VmState s, List<VmState> all) {
//        double sum = 0.0;
//        for (VmState x : all) sum += x.loadUnits;
//        double avg = sum / Math.max(1, all.size());
//        double br = 1.0 - Math.abs(s.loadUnits - avg) / (avg + SimConfig.EPS);
//        return clamp(br, 0.0, 1.0);
//    }
//
//    /* ================= Quantum batch (optional) ================= */
//    private void maybeRunQuantumBatch(double nowSec) {
//        if (nowSec - lastBatchUpdateSec < SimConfig.QUANTUM_BATCH_PERIOD_SEC) return;
//
//        Metrics recent = delta(metrics, snapshot);
//
//        // utilMap for quantum: use RAM util (meaningful)
//        Map<Integer, Double> utilMap = new HashMap<>();
//        for (VmState e : edges) utilMap.put(e.vm.getId(), ramUtil01(e));
//        if (cloud != null) utilMap.put(cloud.vm.getId(), ramUtil01(cloud));
//
//        qBatchesRun++;
//        Plan candidate = Q.suggestUpdate(recent, plan, utilMap);
//        candidate.timestampSec = nowSec;
//
//        boolean ok = validatePlan(candidate);
//        if (ok) {
//            qPlansAccepted++;
//            plan = candidate;
//            lastSafePlan = candidate.copy();
//        } else {
//            qPlansRejected++;
//            plan = lastSafePlan.copy();
//        }
//
//        snapshot = copyMetrics(metrics);
//        lastBatchUpdateSec = nowSec;
//    }
//
//    private boolean validatePlan(Plan candidate) {
//        double reserve = candidate.reserveOverride != null ? candidate.reserveOverride : SimConfig.BASE_RESERVE;
//        if (reserve < 0.0 || reserve > 0.30) return false;
//        if (candidate.wEne < 0.10) return false;
//        return true;
//    }
//
//    /* ================= Cloudlet return ================= */
//    @Override
//    protected void processCloudletReturn(org.iquantum.core.SimEvent ev) {
//        Object data = ev.getData();
//        if (!(data instanceof CTask)) return;
//
//        CTask t = (CTask) data;
//        getCloudletReceivedList().add(t);
//
//        int tid = t.getCloudletId();
//        double arrival = Meta.arrival(tid);
//        double deadline = Meta.deadline(tid);
//        double absDeadline = arrival + deadline;
//
//        double exec = t.getActualCPUTime();
//
//        // use predicted finish if available for stability
//        double finishHat = finishHatByTask.getOrDefault(tid, t.getFinishTime());
//
//        double latency = Math.max(0.0, finishHat - arrival);
//
//        boolean isCloud = (cloud != null && t.getVmId() == cloud.vm.getId());
//        double power = isCloud ? SimConfig.CLOUD_POWER_W : SimConfig.EDGE_POWER_W;
//        double energy = power * exec;
//
//        boolean missed = (finishHat > absDeadline);
//        boolean isPr = Meta.isPr(tid);
//
//        metrics.addCompleted(latency, exec, energy, missed, isPr);
//
//        VmState s = (cloud != null && cloud.vm.getId() == t.getVmId()) ? cloud : find(edges, t.getVmId());
//        if (s != null) {
//            s.currentLoadMb = Math.max(0.0, s.currentLoadMb - Meta.sizeMb(tid));
//        }
//
//        maybeStopAndPrint();
//    }
//
//    /* ================= HARD STOP + METRICS PRINT ================= */
//    private void maybeStopAndPrint() {
//        if (finishedOnce) return;
//
//        long done = metrics.completed + metrics.rejected;
//        if (done < SimConfig.TOTAL_TASKS) return;
//
//        finishedOnce = true;
//
//        // overall averages (already inside Metrics too, but ok)
//        double avgLatencyMs = metrics.avgLatencyMs();
//        double avgExecMs    = metrics.avgExecMs();
//        double dmrPct       = metrics.dmrPercent();
//        double brPct        = metrics.avgBrPercent();
//
//        // ✅ REAL utilization: sum(busyTimeSec) / (N * horizon)
//        double horizon = 0.0;
//        try {
//            horizon = org.iquantum.core.iQuantum.clock();
//        } catch (Throwable ignored) {
//            // fallback: max busyUntil across edges/cloud
//            for (VmState e : edges) horizon = Math.max(horizon, e.busyUntilSec);
//            if (cloud != null) horizon = Math.max(horizon, cloud.busyUntilSec);
//        }
//        horizon = Math.max(1e-9, horizon);
//
//        double edgeBusySum = 0.0;
//        for (VmState e : edges) edgeBusySum += e.busyTimeSec;
//
//        double utilPctReal = (edges.isEmpty())
//                ? 0.0
//                : (edgeBusySum / (edges.size() * horizon)) * 100.0;
//
//        Log.printLine("\n===== HQ-DPTARA METRICS (" + SimConfig.TOTAL_TASKS + " requests) =====");
//        Log.printLine("Generated: " + metrics.totalGenerated);
//        Log.printLine("Completed: " + metrics.completed);
//        Log.printLine("Rejected : " + metrics.rejected);
//
//        Log.printLine(String.format(Locale.US, "Avg Latency (ms)         : %.3f", avgLatencyMs));
//        Log.printLine(String.format(Locale.US, "Avg Task Exec Time (ms)  : %.3f", avgExecMs));
//        Log.printLine(String.format(Locale.US, "Total Energy (J)         : %.3f", metrics.totalEnergyJ));
//        Log.printLine(String.format(Locale.US, "Resource Utilization (%%) : %.2f", utilPctReal));
//
//        // ✅ overall DMR + BR (overall only)
//        Log.printLine(String.format(Locale.US, "Deadline Miss Ratio (%%)  : %.2f", dmrPct));
//        Log.printLine(String.format(Locale.US, "Load Balancing Rate (%%)  : %.2f", brPct));
//
//        Log.printLine("======================================================\n");
//        Log.printLine("VM assignment counts: " + vmAssignCount);
//
//        org.iquantum.core.iQuantum.stopSimulation();
//    }
//
//
//    /* ================= Helpers ================= */
//    private VmState find(List<VmState> edges, int vmId) {
//        for (VmState s : edges) if (s.vm.getId() == vmId) return s;
//        return null;
//    }
//
//    private void updateVmStateAfterAssign(VmState s, CTask task, double nowSec) {
//        double comp = computeSec(task, s);
//        double net  = netLatencySec(s.isCloud);
//
//        // ✅ CPU busy accumulator for real utilization
//        s.busyTimeSec += comp;
//
//        // ✅ CPU queue: only compute blocks CPU (network is transmission, not CPU busy)
//        double startCpu = Math.max(nowSec, s.busyUntilSec);
//        double endCpu   = startCpu + comp;
//        s.busyUntilSec  = endCpu;
//
//        // RAM occupancy
//        s.currentLoadMb = Math.min(s.capacityMb(), s.currentLoadMb + Meta.sizeMb(task.getCloudletId()));
//
//        // queue pressure (based on CPU queue only)
//        double window = Math.max(1e-6, SimConfig.DEADLINE_SEC_MAX);
//        double queue = Math.max(0.0, s.busyUntilSec - nowSec);
//        s.loadUnits = clamp(queue / window, 0.0, 1.0);
//
//        // store predicted finish incl net+comp (for latency)
//        int tid = task.getCloudletId();
//        double finishHat = startCpu + comp + net;
//        finishHatByTask.put(tid, finishHat);
//    }
//
//    private double estimateFinish(VmState s, CTask task, double nowSec) {
//        double comp = computeSec(task, s);
//        double net  = netLatencySec(s.isCloud);
//        double startCpu = Math.max(nowSec, s.busyUntilSec);
//        return startCpu + comp + net;
//    }
//
//    private List<VmState> topKEdgesByFinish(CTask task, double nowSec, List<VmState> edges) {
//        List<VmFinish> list = new ArrayList<>();
//        for (VmState e : edges) list.add(new VmFinish(e, estimateFinish(e, task, nowSec)));
//        list.sort(Comparator.comparingDouble(x -> x.finish));
//        int k = Math.min(SimConfig.K_CANDIDATES, list.size());
//        List<VmState> top = new ArrayList<>(k);
//        for (int i = 0; i < k; i++) top.add(list.get(i).s);
//        return top;
//    }
//
//    private static final class VmFinish {
//        VmState s; double finish;
//        VmFinish(VmState s, double finish) { this.s = s; this.finish = finish; }
//    }
//
//    private double netLatencySec(boolean isCloud) {
//        double edgeMeanMs  = (SimConfig.EDGE_NET_MS_MIN  + SimConfig.EDGE_NET_MS_MAX)  / 2.0;
//        double cloudMeanMs = (SimConfig.CLOUD_NET_MS_MIN + SimConfig.CLOUD_NET_MS_MAX) / 2.0;
//        double ms = isCloud ? (edgeMeanMs + cloudMeanMs) : edgeMeanMs;
//        return ms / 1000.0;
//    }
//
//    private double computeSec(CTask task, VmState s) {
//        return task.getCloudletLength() / Math.max(s.vm.getMips(), SimConfig.EPS);
//    }
//
//    // ✅ Util in scoring (0..1) = RAM utilization
//    private double ramUtil01(VmState s) {
//        return clamp(s.currentLoadMb / Math.max(SimConfig.EPS, s.capacityMb()), 0.0, 1.0);
//    }
//
//    private double avgEdgeRamUtil(List<VmState> edges) {
//        if (edges.isEmpty()) return 0.0;
//        double sum = 0.0;
//        for (VmState e : edges) sum += ramUtil01(e);
//        return sum / Math.max(1, edges.size());
//    }
//
//    private double powerW(VmState s) {
//        return s.isCloud ? SimConfig.CLOUD_POWER_W : SimConfig.EDGE_POWER_W;
//    }
//
//    private static double clamp(double x, double lo, double hi) {
//        return Math.max(lo, Math.min(hi, x));
//    }
//
//    /* ================= Metrics copy/delta ================= */
//    private static Metrics copyMetrics(Metrics m) {
//        Metrics c = new Metrics();
//        c.totalGenerated = m.totalGenerated;
//        c.completed = m.completed;
//        c.rejected = m.rejected;
//        c.completedPr = m.completedPr;
//        c.completedNr = m.completedNr;
//        c.deadlineMiss = m.deadlineMiss;
//        c.totalLatencySec = m.totalLatencySec;
//        c.totalLatencySecPr = m.totalLatencySecPr;
//        c.totalLatencySecNr = m.totalLatencySecNr;
//        c.totalExecSec = m.totalExecSec;
//        c.totalEnergyJ = m.totalEnergyJ;
//        c.utilSamples = m.utilSamples;
//        c.utilSum = m.utilSum;
//        c.brSamples = m.brSamples;
//        c.brSum = m.brSum;
//        return c;
//    }
//
//    private static Metrics delta(Metrics cur, Metrics base) {
//        Metrics d = new Metrics();
//        d.totalGenerated = cur.totalGenerated - base.totalGenerated;
//        d.completed = cur.completed - base.completed;
//        d.rejected = cur.rejected - base.rejected;
//        d.completedPr = cur.completedPr - base.completedPr;
//        d.completedNr = cur.completedNr - base.completedNr;
//        d.deadlineMiss = cur.deadlineMiss - base.deadlineMiss;
//        d.totalLatencySec = cur.totalLatencySec - base.totalLatencySec;
//        d.totalLatencySecPr = cur.totalLatencySecPr - base.totalLatencySecPr;
//        d.totalLatencySecNr = cur.totalLatencySecNr - base.totalLatencySecNr;
//        d.totalExecSec = cur.totalExecSec - base.totalExecSec;
//        d.totalEnergyJ = cur.totalEnergyJ - base.totalEnergyJ;
//        d.utilSamples = cur.utilSamples - base.utilSamples;
//        d.utilSum = cur.utilSum - base.utilSum;
//        d.brSamples = cur.brSamples - base.brSamples;
//        d.brSum = cur.brSum - base.brSum;
//        return d;
//    }
//}     final codeeeeeeeeeeeeeeeeee


//
//package org.iquantum.examples.hq_dptara.broker;
//
//import java.util.*;
//import org.iquantum.brokers.CBroker;
//import org.iquantum.backends.classical.Vm;
//import org.iquantum.core.SimEvent;
//import org.iquantum.tasks.CTask;
//import org.iquantum.utils.Log;
//import org.iquantum.examples.hq_dptara.util.Dist;
//import org.iquantum.examples.hq_dptara.util.SimConfig;
//
///**
// * HQ-DPTARA Broker (Pr->EDGE, Nr->ADAPTIVE + SCORE + CAP35)
// *
// * Metrics printed (architecture):
// * - Avg Latency (ms)
// * - Avg Task Exec Time (ms)
// * - Avg Network Time (ms)
// * - Total Energy (J) = Compute + Network
// * - Resource Utilization (%) [EDGE ONLY]
// * - Deadline Miss Ratio (%)
// * - Load Balancing Rate (%)  [Jain fairness]
// * - Cloud Share (%)          [CAP35 + exploration]
// * - Weights (wL,wE,wQ)
// */
//public class HQDptaraBroker extends CBroker {
//
//    /* ================= META ================= */
//    public static final class Meta {
//        private static final Map<Integer, Double> ARRIVAL  = new HashMap<>();
//        private static final Map<Integer, Double> DEADLINE = new HashMap<>();
//        private static final Map<Integer, Double> PORIG    = new HashMap<>();
//        private static final Map<Integer, Double> SIZE_MB  = new HashMap<>();
//
//        public static void put(int tid, double arrival, double deadline, double porig, double sizeMb) {
//            ARRIVAL.put(tid, arrival);
//            DEADLINE.put(tid, deadline);
//            PORIG.put(tid, porig);
//            SIZE_MB.put(tid, sizeMb);
//        }
//        public static double arrival(int tid)  { return ARRIVAL.getOrDefault(tid, 0.0); }
//        public static double deadline(int tid) { return DEADLINE.getOrDefault(tid, 50.0); }
//        public static double porig(int tid)    { return PORIG.getOrDefault(tid, 0.5); }
//        public static double sizeMb(int tid)   { return SIZE_MB.getOrDefault(tid, 10.0); }
//    }
//
//    /* ================= VM STATE ================= */
//    private static final class VmState {
//        final Vm vm;
//        final boolean isCloud;
//
//        double cpuBusyUntil = 0.0;     // CPU queue horizon
//        double busyCpuSumSec = 0.0;    // CPU busy accumulator
//
//        VmState(Vm vm) {
//            this.vm = vm;
//            this.isCloud = "CLOUD".equalsIgnoreCase(vm.getVmm());
//        }
//    }
//
//    /* ================= POLICY KNOBS ================= */
//    private static final int EDGE_TOP_K = 3;
//    private static final double EDGE_QUEUE_TIEBREAK_W = 0.10;
//
//    private static final double CLOUD_CONGESTED_QUEUE_SEC = 1.00;
//
//    // Energy model (TX powers)
//    private static final double P_TX_EDGE_W  = 2.0;
//    private static final double P_TX_CLOUD_W = 4.0;
//
//    // Score weights (start)
//    private double wL = 0.60;
//    private double wE = 0.30;
//    private double wQ = 0.10;
//
//    /* ================= VM LISTS ================= */
//    private boolean ready = false;
//    private final List<VmState> edgeVms = new ArrayList<>();
//    private VmState cloudVm = null;
//
//    /* ================= PER-TASK STORES ================= */
//    private final Map<Integer, Double> arrivalByTask   = new HashMap<>();
//    private final Map<Integer, Double> finishHatByTask = new HashMap<>();
//    private final Map<Integer, Double> netSecByTask    = new HashMap<>();
//
//    /* ================= METRICS ================= */
//    private long generated = 0, completed = 0, rejected = 0;
//    private long deadlineMiss = 0;
//
//    private double sumLatencySec = 0.0;
//    private double sumExecSec    = 0.0;
//    private double sumNetSec     = 0.0;
//
//    private double totalEnergyJ    = 0.0;
//    private double computeEnergyJ  = 0.0;
//    private double networkEnergyJ  = 0.0;
//
//    // ✅ Correct cloud share counters
//    private long assignedEdge  = 0;
//    private long assignedCloud = 0;
//
//    private final Map<Integer, Integer> vmAssignCount = new HashMap<>();
//    private boolean finishedOnce = false;
//
//    // horizon for utilization/latency stability
//    private double maxFinishHatSec = 0.0;
//
//    public HQDptaraBroker(String name) throws Exception {
//        super(name);
//    }
//
//    @Override
//    protected void processOtherEvent(SimEvent ev) {
//        if (ev.getTag() == SimConfig.TAG_TASK_ARRIVAL) {
//            Object data = ev.getData();
//            if (data instanceof CTask) onArrival((CTask) data);
//            return;
//        }
//        super.processOtherEvent(ev);
//    }
//
//    private void initVmStates() {
//        if (ready) return;
//
//        List<Vm> vms = getVmsCreatedList();
//        if (vms == null || vms.isEmpty()) return;
//
//        edgeVms.clear();
//        cloudVm = null;
//
//        for (Vm vm : vms) {
//            VmState s = new VmState(vm);
//            if (s.isCloud) cloudVm = s;
//            else edgeVms.add(s);
//        }
//
//        ready = true;
//        Log.printLine("VMs Ready: edge=" + edgeVms.size() + " cloud=" + (cloudVm != null));
//    }
//
//    private void onArrival(CTask t) {
//        initVmStates();
//        if (!ready) return;
//
//        generated++;
//        int tid = t.getCloudletId();
//        double now = Meta.arrival(tid);
//
//        arrivalByTask.put(tid, now);
//
//        int vmId = selectVm_PrEdge_NrAdaptiveScoreCap(t, now);
//        if (vmId < 0) {
//            rejected++;
//            stopIfDone();
//            return;
//        }
//
//        submitCloudletList(Collections.singletonList(t));
//        bindCloudletToVm(tid, vmId);
//
//        VmState chosen = findVm(vmId);
//        if (chosen != null) {
//            // ✅ update cloud share counters here
//            if (chosen.isCloud) assignedCloud++;
//            else assignedEdge++;
//
//            double execSec = computeSec(t, chosen);
//
//            // store ONE network delay for this task
//            double netSec = netLatencySec(chosen.isCloud);
//            netSecByTask.put(tid, netSec);
//
//            // predict finishHat = startCpu + exec + net
//            double startCpu = Math.max(now, chosen.cpuBusyUntil);
//            double finishHat = startCpu + execSec + netSec;
//
//            finishHatByTask.put(tid, finishHat);
//            maxFinishHatSec = Math.max(maxFinishHatSec, finishHat);
//
//            // update CPU queue (network doesn't block CPU)
//            chosen.busyCpuSumSec += execSec;
//            chosen.cpuBusyUntil = startCpu + execSec;
//        }
//
//        vmAssignCount.merge(vmId, 1, Integer::sum);
//        super.submitCloudlets();
//    }
//
//    /* ================= Prediction Struct ================= */
//    private static final class Pred {
//        final double execSec;
//        final double netSec;
//        final double queueSec;
//        final double finishHatSec;
//        final double latencySec;
//        final double energyEqSec; // energy normalized into "sec-equivalent" @edge-power baseline
//
//        Pred(double execSec, double netSec, double queueSec, double finishHatSec, double latencySec, double energyEqSec) {
//            this.execSec = execSec;
//            this.netSec = netSec;
//            this.queueSec = queueSec;
//            this.finishHatSec = finishHatSec;
//            this.latencySec = latencySec;
//            this.energyEqSec = energyEqSec;
//        }
//    }
//
//    /* ================= Selection: PR->EDGE, NR->ADAPTIVE + SCORE + CAP35 ================= */
//    private int selectVm_PrEdge_NrAdaptiveScoreCap(CTask task, double nowSec) {
//        if (edgeVms.isEmpty() && cloudVm == null) return -1;
//
//        int tid = task.getCloudletId();
//        double porig = Meta.porig(tid);
//        boolean isPr = (porig >= SimConfig.CRITICAL_THRESHOLD);
//
//        VmState bestEdge = pickBestEdgeTopK(task, nowSec);
//        VmState c = cloudVm;
//
//        // if no edge -> use cloud (if exists)
//        if (bestEdge == null) return (c != null) ? c.vm.getId() : -1;
//
//        // PR -> EDGE always
//        if (isPr) return bestEdge.vm.getId();
//
//        // NR: if no cloud -> edge
//        if (c == null) return bestEdge.vm.getId();
//
//        // CAP & exploration status
//        double totalAssigned = Math.max(1.0, (assignedEdge + assignedCloud));
//        double cloudShareNow = assignedCloud / totalAssigned;
//
//        boolean cloudCapReached   = cloudShareNow >= SimConfig.CLOUD_MAX_SHARE;   // 0.35
//        boolean needExploreCloud  = cloudShareNow <  SimConfig.CLOUD_MIN_SHARE;   // 0.10
//
//        // deadline
//        double arrival = arrivalByTask.getOrDefault(tid, Meta.arrival(tid));
//        double absDeadline = arrival + Meta.deadline(tid);
//
//        // predict edge & cloud using mean-net (no extra random for selection)
//        Pred pe = predictMeanNet(bestEdge, task, nowSec, arrival);
//        Pred pc = predictMeanNet(c, task, nowSec, arrival);
//
//        boolean edgeRisk = pe.finishHatSec > absDeadline;
//
//        // cloud congestion gate
//        double cloudQueue = Math.max(0.0, c.cpuBusyUntil - nowSec);
//        boolean cloudCongested = cloudQueue > CLOUD_CONGESTED_QUEUE_SEC;
//        if (cloudCongested) return bestEdge.vm.getId();
//
//        // scores
//        double scoreEdge  = score(pe.latencySec, pe.energyEqSec, pe.queueSec);
//        double scoreCloud = score(pc.latencySec, pc.energyEqSec, pc.queueSec);
//
//        // ✅ If cap reached: cloud only if edgeRisk AND cloud not worse
//        if (cloudCapReached) {
//            if (edgeRisk && pc.finishHatSec <= pe.finishHatSec) return c.vm.getId();
//            return bestEdge.vm.getId();
//        }
//
//        // ✅ Exploration: keep cloud alive but never let it be "too worse"
//        if (needExploreCloud) {
//            if (pc.finishHatSec <= pe.finishHatSec + SimConfig.CLOUD_TOLERANCE_S) {
//                return c.vm.getId();
//            }
//        }
//
//        // ✅ Normal adaptive: if edgeRisk, prefer cloud if it helps
//        if (edgeRisk) {
//            if (pc.finishHatSec <= pe.finishHatSec) return c.vm.getId();
//            if (scoreCloud + 0.02 < scoreEdge) return c.vm.getId();
//            return bestEdge.vm.getId();
//        }
//
//        // Otherwise choose by score (energy-aware)
//        return (scoreCloud < scoreEdge) ? c.vm.getId() : bestEdge.vm.getId();
//    }
//
//    private VmState pickBestEdgeTopK(CTask task, double nowSec) {
//        List<VmState> sorted = new ArrayList<>(edgeVms);
//        sorted.sort(Comparator.comparingDouble(e -> estimateFinishMeanNet(e, task, nowSec)));
//
//        int k = Math.min(EDGE_TOP_K, sorted.size());
//        VmState best = null;
//        double bestScore = Double.POSITIVE_INFINITY;
//
//        for (int i = 0; i < k; i++) {
//            VmState e = sorted.get(i);
//            double finish = estimateFinishMeanNet(e, task, nowSec);
//            double queue = Math.max(0.0, e.cpuBusyUntil - nowSec);
//
//            double score = finish + EDGE_QUEUE_TIEBREAK_W * queue;
//            if (score < bestScore) {
//                bestScore = score;
//                best = e;
//            }
//        }
//        return best;
//    }
//
//    private VmState findVm(int vmId) {
//        if (cloudVm != null && cloudVm.vm.getId() == vmId) return cloudVm;
//        for (VmState e : edgeVms) if (e.vm.getId() == vmId) return e;
//        return null;
//    }
//
//    private double estimateFinishMeanNet(VmState s, CTask task, double nowSec) {
//        double exec = computeSec(task, s);
//
//        double edgeMeanMs  = (SimConfig.EDGE_NET_MS_MIN + SimConfig.EDGE_NET_MS_MAX) / 2.0;
//        double cloudMeanMs = (SimConfig.CLOUD_NET_MS_MIN + SimConfig.CLOUD_NET_MS_MAX) / 2.0;
//
//        double netMs = s.isCloud ? (edgeMeanMs + cloudMeanMs) : edgeMeanMs;
//        double netSec = netMs / 1000.0;
//
//        double start = Math.max(nowSec, s.cpuBusyUntil);
//        return start + exec + netSec;
//    }
//
//    private Pred predictMeanNet(VmState s, CTask task, double nowSec, double arrivalSec) {
//        double exec = computeSec(task, s);
//
//        double edgeMeanMs  = (SimConfig.EDGE_NET_MS_MIN + SimConfig.EDGE_NET_MS_MAX) / 2.0;
//        double cloudMeanMs = (SimConfig.CLOUD_NET_MS_MIN + SimConfig.CLOUD_NET_MS_MAX) / 2.0;
//        double netMs = s.isCloud ? (edgeMeanMs + cloudMeanMs) : edgeMeanMs;
//        double netSec = netMs / 1000.0;
//
//        double startCpu = Math.max(nowSec, s.cpuBusyUntil);
//        double queueSec = Math.max(0.0, s.cpuBusyUntil - nowSec);
//
//        double finishHat = startCpu + exec + netSec;
//        double latency = Math.max(0.0, finishHat - arrivalSec);
//
//        // Energy (mean-net based) => convert to "sec-equivalent" at edge baseline power
//        double compE = powerW(s.isCloud) * exec;
//        double netE  = txPowerW(s.isCloud) * netSec;
//        double energyEqSec = (compE + netE) / Math.max(SimConfig.EPS, SimConfig.EDGE_POWER_W);
//
//        return new Pred(exec, netSec, queueSec, finishHat, latency, energyEqSec);
//    }
//
//    private double score(double latencySec, double energyEqSec, double queueSec) {
//        return (wL * latencySec) + (wE * energyEqSec) + (wQ * queueSec);
//    }
//
//    private double computeSec(CTask task, VmState s) {
//        return task.getCloudletLength() / Math.max(s.vm.getMips(), SimConfig.EPS);
//    }
//
//    private double netLatencySec(boolean isCloud) {
//        double edgeMs = Dist.uniform(SimConfig.EDGE_NET_MS_MIN, SimConfig.EDGE_NET_MS_MAX);
//        if (!isCloud) return edgeMs / 1000.0;
//        double cloudMs = Dist.uniform(SimConfig.CLOUD_NET_MS_MIN, SimConfig.CLOUD_NET_MS_MAX);
//        return (edgeMs + cloudMs) / 1000.0;
//    }
//
//    private double powerW(boolean isCloud) {
//        return isCloud ? SimConfig.CLOUD_POWER_W : SimConfig.EDGE_POWER_W;
//    }
//
//    private double txPowerW(boolean isCloud) {
//        return isCloud ? (P_TX_EDGE_W + P_TX_CLOUD_W) : P_TX_EDGE_W;
//    }
//
//    /* ================= Cloudlet Return -> Architecture Metrics ================= */
//    @Override
//    protected void processCloudletReturn(SimEvent ev) {
//        Object data = ev.getData();
//        if (!(data instanceof CTask)) return;
//
//        CTask t = (CTask) data;
//        getCloudletReceivedList().add(t);
//
//        int tid = t.getCloudletId();
//
//        double arrival = arrivalByTask.getOrDefault(tid, Meta.arrival(tid));
//        double absDeadline = arrival + Meta.deadline(tid);
//
//        // CPU time from simulator
//        double execSec = t.getActualCPUTime();
//
//        // stable latency using predicted finishHat
//        double finishHat = finishHatByTask.getOrDefault(tid, t.getFinishTime());
//        double latencySec = Math.max(0.0, finishHat - arrival);
//
//        // stored network delay
//        double netSec = netSecByTask.getOrDefault(tid, 0.0);
//
//        boolean isCloud = (cloudVm != null && t.getVmId() == cloudVm.vm.getId());
//
//        // energy
//        double compE = powerW(isCloud) * execSec;
//        double netE  = txPowerW(isCloud) * netSec;
//        double totalE = compE + netE;
//
//        completed++;
//        sumLatencySec += latencySec;
//        sumExecSec    += execSec;
//        sumNetSec     += netSec;
//
//        computeEnergyJ += compE;
//        networkEnergyJ += netE;
//        totalEnergyJ   += totalE;
//
//        if (finishHat > absDeadline) deadlineMiss++;
//
//        stopIfDone();
//    }
//
//    /* ================= Load Balancing Rate (%) via Jain Fairness ================= */
//    private double loadBalancingRatePercent() {
//        List<VmState> all = new ArrayList<>(edgeVms);
//        if (cloudVm != null) all.add(cloudVm);
//        if (all.isEmpty()) return 0.0;
//
//        double sum = 0.0, sumSq = 0.0;
//        for (VmState s : all) {
//            double x = s.busyCpuSumSec;
//            sum += x;
//            sumSq += (x * x);
//        }
//        int n = all.size();
//        if (sumSq <= SimConfig.EPS) return 100.0;
//
//        double jain = (sum * sum) / (n * sumSq + SimConfig.EPS); // [0..1]
//        return jain * 100.0;
//    }
//
//    /* ================= Stop + Print metrics ================= */
//    private void stopIfDone() {
//        if (finishedOnce) return;
//        if (completed + rejected < SimConfig.TOTAL_TASKS) return;
//
//        finishedOnce = true;
//
//        double avgLatMs  = completed == 0 ? 0 : (sumLatencySec / completed) * 1000.0;
//        double avgExecMs = completed == 0 ? 0 : (sumExecSec    / completed) * 1000.0;
//        double avgNetMs  = completed == 0 ? 0 : (sumNetSec     / completed) * 1000.0;
//
//        double dmrPct = completed == 0 ? 0 : (deadlineMiss * 100.0) / completed;
//
//        // Utilization (EDGE ONLY) = SUM(edge busy CPU) / (N_edge * horizon)
//        double horizon = Math.max(1e-9, maxFinishHatSec);
//        double edgeBusySum = 0.0;
//        for (VmState e : edgeVms) edgeBusySum += e.busyCpuSumSec;
//        double utilPct = edgeVms.isEmpty() ? 0.0 : (edgeBusySum / (edgeVms.size() * horizon)) * 100.0;
//
//        double brPct = loadBalancingRatePercent();
//
//        double totalAssigned = Math.max(1.0, (assignedEdge + assignedCloud));
//        double cloudSharePct = (assignedCloud / totalAssigned) * 100.0;
//
//        Log.printLine("\n===== HQ-DPTARA (Pr->EDGE, Nr->ADAPTIVE + SCORE + CAP35) =====");
//        Log.printLine("Generated: " + generated);
//        Log.printLine("Completed: " + completed);
//        Log.printLine("Rejected : " + rejected);
//
//        Log.printLine(String.format(Locale.US, "Avg Latency (ms)         : %.3f", avgLatMs));
//        Log.printLine(String.format(Locale.US, "Avg Task Exec Time (ms)  : %.3f", avgExecMs));
//        Log.printLine(String.format(Locale.US, "Avg Network Time (ms)    : %.3f", avgNetMs));
//
//        Log.printLine(String.format(Locale.US, "Total Energy (J)         : %.3f", totalEnergyJ));
//        Log.printLine(String.format(Locale.US, " - Compute Energy (J)    : %.3f", computeEnergyJ));
//        Log.printLine(String.format(Locale.US, " - Network Energy (J)    : %.3f", networkEnergyJ));
//
//        Log.printLine(String.format(Locale.US, "Resource Utilization (%%) : %.2f", utilPct));
//        Log.printLine(String.format(Locale.US, "Deadline Miss Ratio (%%)  : %.2f", dmrPct));
//        Log.printLine(String.format(Locale.US, "Load Balancing Rate (%%)  : %.2f", brPct));
//
//        Log.printLine(String.format(Locale.US, "Cloud Share (%%)          : %.2f", cloudSharePct));
//        Log.printLine(String.format(Locale.US, "Weights (wL,wE,wQ)       : (%.2f, %.2f, %.2f)", wL, wE, wQ));
//
//        Log.printLine("VM assignment counts: " + vmAssignCount);
//        Log.printLine("===============================================\n");
//
//        org.iquantum.core.iQuantum.stopSimulation();
//    }
//}
















package org.iquantum.examples.hq_dptara.broker;

import java.util.*;
import org.iquantum.brokers.CBroker;
import org.iquantum.backends.classical.Vm;
import org.iquantum.tasks.CTask;
import org.iquantum.utils.Log;
import org.iquantum.examples.hq_dptara.util.SimConfig;

public class HQDptaraBroker extends CBroker {

    /* ================= META ================= */
    public static final class Meta {
        private static final Map<Integer, Double> ARRIVAL   = new HashMap<>();
        private static final Map<Integer, Double> DEADLINE  = new HashMap<>();
        private static final Map<Integer, Double> PORIG     = new HashMap<>();
        private static final Map<Integer, Double> SIZE_MB   = new HashMap<>();
        private static final Map<Integer, Double> EDGE_NET_MS  = new HashMap<>();
        private static final Map<Integer, Double> CLOUD_NET_MS = new HashMap<>();

        public static void put(int tid, double arrival, double deadline, double porig, double sizeMb,
                               double edgeNetMs, double cloudNetMs) {
            ARRIVAL.put(tid, arrival);
            DEADLINE.put(tid, deadline);
            PORIG.put(tid, porig);
            SIZE_MB.put(tid, sizeMb);
            EDGE_NET_MS.put(tid, edgeNetMs);
            CLOUD_NET_MS.put(tid, cloudNetMs);
        }

        public static double arrival(int tid)   { return ARRIVAL.getOrDefault(tid, 0.0); }
        public static double deadline(int tid)  { return DEADLINE.getOrDefault(tid, 50.0); }
        public static double porig(int tid)     { return PORIG.getOrDefault(tid, 0.5); }
        public static double sizeMb(int tid)    { return SIZE_MB.getOrDefault(tid, 10.0); }
        public static double edgeNetMs(int tid) { return EDGE_NET_MS.getOrDefault(tid, 7.5); }
        public static double cloudNetMs(int tid){ return CLOUD_NET_MS.getOrDefault(tid, 75.0); }
    }

    private static final class VmState {
        final Vm vm;
        final boolean isCloud;
        double cpuBusyUntil = 0.0;
        double busyCpuSumSec = 0.0;

        VmState(Vm vm) {
            this.vm = vm;
            this.isCloud = "CLOUD".equalsIgnoreCase(vm.getVmm());
        }
    }

    // Policy knobs
    private static final int EDGE_TOP_K = 5;
    private static final double EDGE_QUEUE_TIEBREAK_W = 0.25;

    private static final double CLOUD_DECISION_MARGIN_SEC = 0.05;
    private static final double CLOUD_CONGESTED_QUEUE_SEC = 1.00;

    // Energy (simple)
    private static final double P_TX_EDGE_W  = 2.0;
    private static final double P_TX_CLOUD_W = 4.0;

    private boolean ready = false;
    private final List<VmState> edgeVms = new ArrayList<>();
    private VmState cloudVm = null;

    private final Map<Integer, Double> finishHatByTask = new HashMap<>();
    private final Map<Integer, Double> netSecByTask    = new HashMap<>();

    private long generated = 0, completed = 0, rejected = 0;
    private long deadlineMiss = 0;

    private double sumLatencySec = 0.0;
    private double sumExecSec    = 0.0;
    private double sumNetSec     = 0.0;

    private double totalEnergyJ   = 0.0;
    private double computeEnergyJ = 0.0;
    private double networkEnergyJ = 0.0;

    private final Map<Integer, Integer> vmAssignCount = new HashMap<>();
    private boolean finishedOnce = false;

    private double maxFinishHatSec = 0.0;

    // Cloud share controls
    private long cloudAssignedCount = 0;

    public HQDptaraBroker(String name) throws Exception {
        super(name);
    }

    @Override
    protected void processOtherEvent(org.iquantum.core.SimEvent ev) {
        if (ev.getTag() == SimConfig.TAG_TASK_ARRIVAL) {
            Object data = ev.getData();
            if (data instanceof CTask) onArrival((CTask) data);
            return;
        }
        super.processOtherEvent(ev);
    }

    private void initVmStates() {
        if (ready) return;

        List<Vm> vms = getVmsCreatedList();
        if (vms == null || vms.isEmpty()) return;

        edgeVms.clear();
        cloudVm = null;

        for (Vm vm : vms) {
            VmState s = new VmState(vm);
            if (s.isCloud) cloudVm = s;
            else edgeVms.add(s);
        }

        ready = true;
        Log.printLine("VMs Ready: edge=" + edgeVms.size() + " cloud=" + (cloudVm != null));
    }

    private void onArrival(CTask t) {
        initVmStates();
        if (!ready) return;

        generated++;
        int tid = t.getCloudletId();
        double now = Meta.arrival(tid);

        int vmId = selectVm_PrEdge_NrAdaptive_ScoreCap(t, now);
        if (vmId < 0) {
            rejected++;
            stopIfDone();
            return;
        }

        submitCloudletList(Collections.singletonList(t));
        bindCloudletToVm(tid, vmId);

        VmState chosen = findVm(vmId);
        if (chosen != null) {
            double execSec = computeSec(t, chosen);

            // ✅ deterministic net per task
            double netSec = netLatencySecDeterministic(tid, chosen.isCloud);
            netSecByTask.put(tid, netSec);

            double startCpu = Math.max(now, chosen.cpuBusyUntil);
            double finishHat = startCpu + execSec + netSec;

            finishHatByTask.put(tid, finishHat);
            maxFinishHatSec = Math.max(maxFinishHatSec, finishHat);

            chosen.busyCpuSumSec += execSec;
            chosen.cpuBusyUntil = startCpu + execSec;

            if (chosen.isCloud) cloudAssignedCount++;
        }

        vmAssignCount.merge(vmId, 1, Integer::sum);
        super.submitCloudlets();
    }

    /* ================= Selection with CAP + min share (stable) ================= */
    private int selectVm_PrEdge_NrAdaptive_ScoreCap(CTask task, double nowSec) {
        if (edgeVms.isEmpty() && cloudVm == null) return -1;

        int tid = task.getCloudletId();
        double porig = Meta.porig(tid);
        boolean isPr = (porig >= SimConfig.CRITICAL_THRESHOLD);

        VmState bestEdge = pickBestEdgeTopK(task, nowSec);
        VmState c = cloudVm;

        if (bestEdge == null) return (c != null) ? c.vm.getId() : -1;

        // PR -> Edge
        if (isPr) return bestEdge.vm.getId();

        // CAP + MIN-SHARE
        double currentCloudShare = (generated <= 0) ? 0.0 : (cloudAssignedCount * 1.0 / generated);
        boolean allowCloudByCap = currentCloudShare < SimConfig.CLOUD_CAP;
        boolean forceCloudByMin = currentCloudShare < SimConfig.CLOUD_MIN_SHARE;

        if (c == null) return bestEdge.vm.getId();

        // If we must push some NR to cloud (exploration), do it but respect CAP
        if (forceCloudByMin && allowCloudByCap) return c.vm.getId();

        // Otherwise adaptive as usual
        double arrival = Meta.arrival(tid);
        double absDeadline = arrival + Meta.deadline(tid);

        double edgeFinish = estimateFinishDeterministic(bestEdge, task, nowSec, tid);
        boolean edgeRisk = edgeFinish > absDeadline;

        double cloudFinish = estimateFinishDeterministic(c, task, nowSec, tid);
        double cloudQueue  = Math.max(0.0, c.cpuBusyUntil - nowSec);
        boolean cloudCongested = cloudQueue > CLOUD_CONGESTED_QUEUE_SEC;

        boolean cloudClearlyBetter = cloudFinish + CLOUD_DECISION_MARGIN_SEC < edgeFinish;

        if (allowCloudByCap && !cloudCongested && edgeRisk && (cloudFinish <= edgeFinish || cloudClearlyBetter)) {
            return c.vm.getId();
        }

        return bestEdge.vm.getId();
    }

    private VmState pickBestEdgeTopK(CTask task, double nowSec) {
        List<VmState> sorted = new ArrayList<>(edgeVms);
        sorted.sort(Comparator.comparingDouble(e -> estimateFinishMeanNet(e, task, nowSec)));

        int k = Math.min(EDGE_TOP_K, sorted.size());
        VmState best = null;
        double bestScore = Double.POSITIVE_INFINITY;

        int tid = task.getCloudletId();

        for (int i = 0; i < k; i++) {
            VmState e = sorted.get(i);

            double finish = estimateFinishDeterministic(e, task, nowSec, tid);
            double queue = Math.max(0.0, e.cpuBusyUntil - nowSec);

            double score = finish + EDGE_QUEUE_TIEBREAK_W * queue;
            if (score < bestScore) {
                bestScore = score;
                best = e;
            }
        }
        return best;
    }

    private VmState findVm(int vmId) {
        if (cloudVm != null && cloudVm.vm.getId() == vmId) return cloudVm;
        for (VmState e : edgeVms) if (e.vm.getId() == vmId) return e;
        return null;
    }

    // Used only for sorting quickly (mean net)
    private double estimateFinishMeanNet(VmState s, CTask task, double nowSec) {
        double exec = computeSec(task, s);

        double edgeMeanMs  = (SimConfig.EDGE_NET_MS_MIN + SimConfig.EDGE_NET_MS_MAX) / 2.0;
        double cloudMeanMs = (SimConfig.CLOUD_NET_MS_MIN + SimConfig.CLOUD_NET_MS_MAX) / 2.0;
        double netMs = s.isCloud ? (edgeMeanMs + cloudMeanMs) : edgeMeanMs;

        double start = Math.max(nowSec, s.cpuBusyUntil);
        return start + exec + (netMs / 1000.0);
    }

    // ✅ deterministic finish using stored per-task net samples
    private double estimateFinishDeterministic(VmState s, CTask task, double nowSec, int tid) {
        double exec = computeSec(task, s);
        double netSec = netLatencySecDeterministic(tid, s.isCloud);
        double start = Math.max(nowSec, s.cpuBusyUntil);
        return start + exec + netSec;
    }

    private double computeSec(CTask task, VmState s) {
        return task.getCloudletLength() / Math.max(s.vm.getMips(), SimConfig.EPS);
    }

    private double netLatencySecDeterministic(int tid, boolean isCloud) {
        double edgeMs = Meta.edgeNetMs(tid);
        if (!isCloud) return edgeMs / 1000.0;
        double cloudMs = Meta.cloudNetMs(tid);
        return (edgeMs + cloudMs) / 1000.0;
    }

    private double powerW(boolean isCloud) {
        return isCloud ? SimConfig.CLOUD_POWER_W : SimConfig.EDGE_POWER_W;
    }

    private double txPowerW(boolean isCloud) {
        return isCloud ? (P_TX_EDGE_W + P_TX_CLOUD_W) : P_TX_EDGE_W;
    }

    @Override
    protected void processCloudletReturn(org.iquantum.core.SimEvent ev) {
        Object data = ev.getData();
        if (!(data instanceof CTask)) return;

        CTask t = (CTask) data;
        getCloudletReceivedList().add(t);

        int tid = t.getCloudletId();
        double arrival = Meta.arrival(tid);
        double absDeadline = arrival + Meta.deadline(tid);

        double execSec = t.getActualCPUTime();
        double finishHat = finishHatByTask.getOrDefault(tid, t.getFinishTime());
        double latencySec = Math.max(0.0, finishHat - arrival);
        double netSec = netSecByTask.getOrDefault(tid, 0.0);

        boolean isCloud = (cloudVm != null && t.getVmId() == cloudVm.vm.getId());

        double compE = powerW(isCloud) * execSec;
        double netE  = txPowerW(isCloud) * netSec;

        completed++;
        sumLatencySec += latencySec;
        sumExecSec    += execSec;
        sumNetSec     += netSec;

        computeEnergyJ += compE;
        networkEnergyJ += netE;
        totalEnergyJ   += (compE + netE);

        if (finishHat > absDeadline) deadlineMiss++;

        stopIfDone();
    }

    private double loadBalancingRatePercent() {
        List<VmState> all = new ArrayList<>(edgeVms);
        if (cloudVm != null) all.add(cloudVm);
        if (all.isEmpty()) return 0.0;

        double sum = 0.0;
        for (VmState s : all) sum += s.busyCpuSumSec;

        double avg = sum / Math.max(1, all.size());
        if (avg <= SimConfig.EPS) return 100.0;

        double devSum = 0.0;
        for (VmState s : all) devSum += Math.abs(s.busyCpuSumSec - avg);

        double avgDev = devSum / Math.max(1, all.size());
        double br01 = 1.0 - (avgDev / (avg + SimConfig.EPS));
        br01 = Math.max(0.0, Math.min(1.0, br01));
        return br01 * 100.0;
    }

    private void stopIfDone() {
        if (finishedOnce) return;
        if (completed + rejected < SimConfig.TOTAL_TASKS) return;

        finishedOnce = true;

        double avgLatMs  = completed == 0 ? 0 : (sumLatencySec / completed) * 1000.0;
        double avgExecMs = completed == 0 ? 0 : (sumExecSec    / completed) * 1000.0;
        double avgNetMs  = completed == 0 ? 0 : (sumNetSec     / completed) * 1000.0;

        double dmrPct = completed == 0 ? 0 : (deadlineMiss * 100.0) / completed;

        double horizon = Math.max(1e-9, maxFinishHatSec);
        double edgeBusySum = 0.0;
        for (VmState e : edgeVms) edgeBusySum += e.busyCpuSumSec;
        double utilPct = edgeVms.isEmpty() ? 0.0 : (edgeBusySum / (edgeVms.size() * horizon)) * 100.0;

        double brPct = loadBalancingRatePercent();
        double cloudSharePct = (generated == 0) ? 0.0 : (cloudAssignedCount * 100.0 / generated);

        Log.printLine("\n===== HQ-DPTARA (Pr->EDGE, Nr->ADAPTIVE + SCORE + CAP35) =====");
        Log.printLine("Generated: " + generated);
        Log.printLine("Completed: " + completed);
        Log.printLine("Rejected : " + rejected);

        Log.printLine(String.format(Locale.US, "Avg Latency (ms)         : %.3f", avgLatMs));
        Log.printLine(String.format(Locale.US, "Avg Task Exec Time (ms)  : %.3f", avgExecMs));
        Log.printLine(String.format(Locale.US, "Avg Network Time (ms)    : %.3f", avgNetMs));

        Log.printLine(String.format(Locale.US, "Total Energy (J)         : %.3f", totalEnergyJ));
        Log.printLine(String.format(Locale.US, " - Compute Energy (J)    : %.3f", computeEnergyJ));
        Log.printLine(String.format(Locale.US, " - Network Energy (J)    : %.3f", networkEnergyJ));

        Log.printLine(String.format(Locale.US, "Resource Utilization (%%) : %.2f", utilPct));
        Log.printLine(String.format(Locale.US, "Deadline Miss Ratio (%%)  : %.2f", dmrPct));
        Log.printLine(String.format(Locale.US, "Load Balancing Rate (%%)  : %.2f", brPct));
        Log.printLine(String.format(Locale.US, "Cloud Share (%%)          : %.2f", cloudSharePct));

        Log.printLine("VM assignment counts: " + vmAssignCount);
        Log.printLine("===============================================\n");

        org.iquantum.core.iQuantum.stopSimulation();
    }
}





