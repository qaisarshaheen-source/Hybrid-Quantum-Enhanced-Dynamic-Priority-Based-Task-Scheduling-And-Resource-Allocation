////package org.iquantum.examples.hq_dptara.util;
////
////public final class SimConfig {
////    private SimConfig() {}
////
////    // ====== System Parameters ======
////    public static final int NUM_IOT_DEVICES   = 50;
//////    public static final int NUM_EDGE_SERVERS  = 10;
////public static final int NUM_EDGE_SERVERS = 8;
////
////    public static final int NUM_CLOUD_SERVERS = 1;
////
//////    public static final double LAMBDA_TASKS_PER_SEC = 5.0;
////    public static final double LAMBDA_TASKS_PER_SEC = 20.0;
////
////    public static final double TASK_SIZE_MB_MIN = 5.0;
////    public static final double TASK_SIZE_MB_MAX = 50.0;
////
////    public static final double DEADLINE_SEC_MIN = 10.0;
////    public static final double DEADLINE_SEC_MAX = 30.0;
////
////    public static final double EDGE_POWER_W  = 50.0;
////    public static final double CLOUD_POWER_W = 200.0;
////
////    public static final double EDGE_NET_MS_MIN  = 5.0;
////    public static final double EDGE_NET_MS_MAX  = 10.0;
////
////    public static final double CLOUD_NET_MS_MIN = 50.0;
////    public static final double CLOUD_NET_MS_MAX = 100.0;
////    /// iadded them....
////    public static final int EDGE_VM_RAM_MB  = 4096;   // 4 GB per VM (safe)
////    public static final int CLOUD_VM_RAM_MB = 8192;   // 8 GB per VM (safe)
////
////    // Hardware ranges
//////    public static final int EDGE_MIPS_MIN  = 500;
//////    public static final int EDGE_MIPS_MAX  = 3000;
//////    public static final int CLOUD_MIPS_MIN = 2000;
//////    public static final int CLOUD_MIPS_MAX = 10000;
//////    public static final int EDGE_HOST_MIPS_MIN  = 500;
//////    public static final int EDGE_HOST_MIPS_MAX  = 3000;
////    public static final int EDGE_HOST_MIPS_MIN = 1000;   // instead of 500
////    public static final int EDGE_HOST_MIPS_MAX = 3000;
////
////    public static final int CLOUD_HOST_MIPS_MIN = 2000;
////    public static final int CLOUD_HOST_MIPS_MAX = 10000;
////    public static final int EDGE_VM_MIPS  = 1000;  // stable MIPS request
//////    public static final int CLOUD_VM_MIPS = 4000;  // stable cloud MIPS request
////    public static final int CLOUD_VM_MIPS = 2000;
////    public static final int EDGE_RAM_MB  = 32 * 1024;
////    public static final int CLOUD_RAM_MB = 64 * 1024;
////
////    // Workload mapping
////    public static final double MI_PER_MB = 80.0;
////
////    // ====== Experiment controls ======
////    public static final int TOTAL_TASKS = 100;     // change to 50,100,200...
////    public static final double SIM_TIME_SEC = 200.0;
////    public static final int MAX_TASKS = TOTAL_TASKS;
////
////    // ====== ONE AND ONLY ONE TAG ======
////    public static final int TAG_TASK_ARRIVAL = 91001;
////
////    // HQ defaults
////    public static final double DEFAULT_ALPHA = 0.55;
////    public static final double DEFAULT_BETA  = 0.30;
////    public static final double DEFAULT_GAMMA = 0.15;
////
////    public static final double W_LAT  = 0.30;
////    public static final double W_ENE  = 0.25;
////    public static final double W_UTIL = 0.20;
////    public static final double W_BIAS = 0.15;
////    public static final double W_PRIO = 0.10;
////
////    public static final double EPS = 1e-9;
////
////    public static final double BASE_RESERVE       = 0.15;
////    public static final double CRITICAL_THRESHOLD = 0.70;
////
////    public static final int K_CANDIDATES = 5;
////
////    public static final double QUANTUM_BATCH_PERIOD_SEC = 10.0;
////
//////    public static final double PLAN_CHANGE_LIMIT_FRAC = 0.30;
////    public static final double PLAN_CHANGE_LIMIT_FRAC = 1.0;
////
////    public static final double ENERGY_TOL_FRAC        = 0.10;
////    public static final double MIGRATION_BUDGET       = 0.20;
////
////    public static final double EMERGENCY_RESERVE_SCALE = 0.5;
////    public static final double FORCED_CLOUD_RESERVE    = 0.05;
////
////    public static final double PR_CLASS_THRESHOLD = 0.80;
////}
////
////
//
//package org.iquantum.examples.hq_dptara.util;
//
//public final class SimConfig {
//    private SimConfig() {}
//
//    // ====== System Parameters (match your thesis tables) ======
//    public static final int NUM_IOT_DEVICES   = 50;
//    public static final int NUM_EDGE_SERVERS  = 10;
//    public static final int NUM_CLOUD_SERVERS = 1;
//
//    // Poisson arrival (GLOBAL λ)
//    public static final double LAMBDA_TASKS_PER_SEC = 5.0;
//    public static final double TASK_ARRIVAL_RATE = 5.0;
//
//    // Task size (MB) and deadlines (sec)
//    public static final double TASK_SIZE_MB_MIN = 5.0;
//    public static final double TASK_SIZE_MB_MAX = 50.0;
//
//    public static final double DEADLINE_SEC_MIN = 10.0;
//    public static final double DEADLINE_SEC_MAX = 100.0;
//
//    // Power (W)
//    public static final double EDGE_POWER_W  = 50.0;
//    public static final double CLOUD_POWER_W = 200.0;
//
//    // Network latency (ms)
//    public static final double EDGE_NET_MS_MIN  = 5.0;
//    public static final double EDGE_NET_MS_MAX  = 10.0;
//
//    public static final double CLOUD_NET_MS_MIN = 50.0;
//    public static final double CLOUD_NET_MS_MAX = 100.0;
//
//    // Hardware ranges (MIPS)
//    public static final int EDGE_HOST_MIPS_MIN  = 500;
//    public static final int EDGE_HOST_MIPS_MAX  = 3000;
//
//    public static final int CLOUD_HOST_MIPS_MIN = 2000;
//    public static final int CLOUD_HOST_MIPS_MAX = 10000;
//
//    // RAM capacity (MB) (hosts)
////    public static final int EDGE_RAM_MB  = 32 * 1024;   // 32GB
////    public static final int CLOUD_RAM_MB = 64 * 1024;   // 64GB
//
//    public static final int EDGE_RAM_MB  = 16 * 1024;   // 16GB
//    public static final int CLOUD_RAM_MB = 32 * 1024;   // 32GB
//    // VM requests (1 VM per host in this experiment -> match host capacity)
//    public static final int EDGE_VM_RAM_MB  = EDGE_RAM_MB;
//    public static final int CLOUD_VM_RAM_MB = CLOUD_RAM_MB;
//
//    // Workload mapping (MI per MB)
////    public static final double MI_PER_MB = 80.0;
//    public static final double MI_PER_MB = 10.0;
//    public static final double CLOUD_SCORE_PENALTY = 0.35;   // start 0.25–0.60
//    public static final double CLOUD_PR_BLOCK = 1.0;
//    // ====== Experiment controls ======
//    public static final int TOTAL_TASKS = 1000;
//    public static final double SIM_TIME_SEC = 200.0; // long enough to generate TOTAL_TASKS
//    public static final int MAX_TASKS = TOTAL_TASKS;
//
//    // ====== ONE AND ONLY ONE TAG ======
//    public static final int TAG_TASK_ARRIVAL = 91001;
//
//    // ====== HQ defaults ======
//    public static final double DEFAULT_ALPHA = 0.50;
//    public static final double DEFAULT_BETA  = 0.30;
//    public static final double DEFAULT_GAMMA = 0.20;
//
//    // Scoring weights (sum=1)
//    public static final double W_LAT  = 0.60;
//    public static final double W_ENE  = 0.15;
//    public static final double W_UTIL = 0.25;
//    public static final double W_BIAS = 0.05;
//    public static final double W_PRIO = 0.20;
//
//    public static final double EPS = 1e-9;
//
//    public static final double BASE_RESERVE       = 0.15;
//    public static final double CRITICAL_THRESHOLD = 0.70;
//
//    // K candidates (your design)
////    public static final int K_CANDIDATES = 3;
//    public static final int K_CANDIDATES = 10;   // 3 se 10 kar do
//
//    // Quantum batch period (sec) – keep in sync with thesis (2 sec works well)
//    public static final double QUANTUM_BATCH_PERIOD_SEC = 2.0;
//
//    // Plan safety / stability
//    public static final double PLAN_CHANGE_LIMIT_FRAC = 0.30;
//    public static final double ENERGY_TOL_FRAC        = 0.10;
//    public static final double MIGRATION_BUDGET       = 0.20;
//
//    // Emergency policy knobs
//    public static final double EMERGENCY_RESERVE_SCALE = 0.5;
//    public static final double FORCED_CLOUD_RESERVE    = 0.05;
//
//    // Priority class split (Pr vs Nr)
//    public static final double PR_CLASS_THRESHOLD = 0.80;
//}













//
//
//package org.iquantum.examples.hq_dptara.util;
//public final class SimConfig {
//    private SimConfig() {}
//    // ====== System Parameters and Their Values (Thesis-Matched) ======
//    public static final int NUM_IOT_DEVICES   = 50;
//    public static final int NUM_EDGE_SERVERS  = 10;
//    public static final int NUM_CLOUD_SERVERS = 1;
//
//    // Poisson arrival rate (GLOBAL λ tasks/sec)
//    public static final double LAMBDA_TASKS_PER_SEC = 5.0;
//
//    // Task size (MB)
//    public static final double TASK_SIZE_MB_MIN = 5.0;
//    public static final double TASK_SIZE_MB_MAX = 50.0;
//
//    // Deadline (sec)
//    public static final double DEADLINE_SEC_MIN = 10.0;
//    public static final double DEADLINE_SEC_MAX = 100.0;
//
//    // Power usage (W)
//    public static final double EDGE_POWER_W  = 50.0;
//    public static final double CLOUD_POWER_W = 200.0;
//
//    // Network & Transmission Latency (ms)
//    public static final double EDGE_NET_MS_MIN  = 5.0;
//    public static final double EDGE_NET_MS_MAX  = 10.0;
//
//    public static final double CLOUD_NET_MS_MIN = 50.0;
//    public static final double CLOUD_NET_MS_MAX = 100.0;
//
//    // Hardware Platform (MIPS ranges)
//    public static final int EDGE_HOST_MIPS_MIN  = 500;
//    public static final int EDGE_HOST_MIPS_MAX  = 3000;
//
//    public static final int CLOUD_HOST_MIPS_MIN = 2000;
//    public static final int CLOUD_HOST_MIPS_MAX = 10000;
//
//    // RAM capacity (MB) (hosts)
//    public static final int EDGE_RAM_MB  = 32 * 1024;   // 32GB
//    public static final int CLOUD_RAM_MB = 64 * 1024;   // 64GB
//
//    // VM requests (1 VM per host in your experiment -> match host capacity)
////    public static final int EDGE_VM_RAM_MB  = EDGE_RAM_MB;
//    public static final int EDGE_VM_RAM_MB = 4096;
////    public static final int CLOUD_VM_RAM_MB = CLOUD_RAM_MB;
//   public static final int CLOUD_VM_RAM_MB= 8192;
//    public static final int EDGE_VM_MIPS  = 2000;   // within [500,3000]
//    public static final int CLOUD_VM_MIPS = 4000;
//    // Workload mapping: MI per MB
//    public static final double MI_PER_MB = 20.0;
//
//    // ====== Experiment controls ======
//    // Note: with λ=5 and TOTAL_TASKS=1000, expected generation horizon ≈ 200s
//    public static final int TOTAL_TASKS = 100;
//    public static final double SIM_TIME_SEC = 220.0; // slight buffer
//    public static final int MAX_TASKS = TOTAL_TASKS;
//
//    // ====== ONE AND ONLY ONE TAG ======
//    public static final int TAG_TASK_ARRIVAL = 91001;
//
//    // ====== HQ defaults ======
//    public static final double DEFAULT_ALPHA = 0.50;
//    public static final double DEFAULT_BETA  = 0.30;
//    public static final double DEFAULT_GAMMA = 0.20;
//
//    // Scoring weights (SUM MUST = 1.0)
//    public static final double W_LAT  = 0.35;
//    public static final double W_ENE  = 0.25;
//    public static final double W_UTIL = 0.20;
//    public static final double W_BIAS = 0.10;
//    public static final double W_PRIO = 0.10;
//
//    public static final double EPS = 1e-9;
//
//    // Reserve policy
//    public static final double BASE_RESERVE       = 0.15;
//    public static final double CRITICAL_THRESHOLD = 0.70;
//
//    // Candidate size K
//    public static final int K_CANDIDATES = 5;
//
//    // Quantum batch period (sec)
//    public static final double QUANTUM_BATCH_PERIOD_SEC = 2.0;
//
//    // Plan safety / stability
//    public static final double PLAN_CHANGE_LIMIT_FRAC = 0.30;
//    public static final double ENERGY_TOL_FRAC        = 0.10;
//    public static final double MIGRATION_BUDGET       = 0.20;
//
//    // Emergency policy knobs
//    public static final double EMERGENCY_RESERVE_SCALE = 0.5;
//    public static final double FORCED_CLOUD_RESERVE    = 0.05;
//
//    // Priority class split (Pr vs Nr)
//    public static final double PR_CLASS_THRESHOLD = 0.80;
//} final codeeeeeeee









package org.iquantum.examples.hq_dptara.util;

public final class SimConfig {
    private SimConfig() {}

    // ====== System Parameters ======
    public static final int NUM_IOT_DEVICES   = 50;
    public static final int NUM_EDGE_SERVERS  = 10;
    public static final int NUM_CLOUD_SERVERS = 1;

    public static final double LAMBDA_TASKS_PER_SEC = 5.0;

    public static final double TASK_SIZE_MB_MIN = 5.0;
    public static final double TASK_SIZE_MB_MAX = 50.0;

    public static final double DEADLINE_SEC_MIN = 10.0;
    public static final double DEADLINE_SEC_MAX = 100.0;

    // Power (W)
    public static final double EDGE_POWER_W  = 50.0;
    public static final double CLOUD_POWER_W = 200.0;

    // Network (ms)
    public static final double EDGE_NET_MS_MIN  = 5.0;
    public static final double EDGE_NET_MS_MAX  = 10.0;

    public static final double CLOUD_NET_MS_MIN = 50.0;
    public static final double CLOUD_NET_MS_MAX = 100.0;

    // Host MIPS
    public static final int EDGE_HOST_MIPS_MIN = 3000;
    public static final int EDGE_HOST_MIPS_MAX = 3000;

    // VM MIPS
    public static final int EDGE_VM_MIPS  = 2000;
    public static final int CLOUD_VM_MIPS = 4000; // VM#10 fails on edge -> created in cloud

    public static final int CLOUD_HOST_MIPS_MIN = 2000;
    public static final int CLOUD_HOST_MIPS_MAX = 10000;

    // RAM (MB)
    public static final int EDGE_RAM_MB  = 32 * 1024;
    public static final int CLOUD_RAM_MB = 64 * 1024;

    // VM RAM (MB)
    public static final int EDGE_VM_RAM_MB  = 4096;
    public static final int CLOUD_VM_RAM_MB = 8192;

    // Workload mapping
    public static final double MI_PER_MB = 90.0;

    // Experiment
    public static final int TOTAL_TASKS = 200;
    public static final int TAG_TASK_ARRIVAL = 91001;

    // Priority threshold
    public static final double CRITICAL_THRESHOLD = 0.70;

    // Numerical stability
    public static final double EPS = 1e-9;

    // ====== NEW: Energy-aware offloading knobs ======
    // NR will offload to cloud only if cloud energy is not too worse than edge
    // (because cloud power is 4x in your table)
    public static final double NR_CLOUD_ENERGY_TOL = 1.20; // allow cloud <= 1.2 * edgeEnergy (strict)

    // Cloud decision margins
    public static final double CLOUD_DECISION_MARGIN_SEC = 0.05;  // 50ms
    public static final double CLOUD_CONGESTED_QUEUE_SEC = 1.00;  // 1s
    // ================== NEW: CAP + exploration knobs ==================
//    public static final double CLOUD_MAX_SHARE   = 0.35;
    public static final double CLOUD_CAP = 0.35;// ✅ default CAP35
    public static final double CLOUD_MIN_SHARE   = 0.20;  // ✅ at least 10% exploration
    public static final double CLOUD_TOLERANCE_S = 0.080; // ✅ cloud allowed if not worse by >80ms during exploration
}



//
//package org.iquantum.examples.hq_dptara.util;
//
//public final class SimConfig {
//    private SimConfig() {}
//
//    // ====== System Parameters ======
//    public static final int NUM_IOT_DEVICES   = 50;
//    public static final int NUM_EDGE_SERVERS  = 7;
//    public static final int NUM_CLOUD_SERVERS = 1;
//
//    public static final double LAMBDA_TASKS_PER_SEC = 5.0;
//
//    public static final double TASK_SIZE_MB_MIN = 5.0;
//    public static final double TASK_SIZE_MB_MAX = 50.0;
//
//    public static final double DEADLINE_SEC_MIN = 10.0;
//    public static final double DEADLINE_SEC_MAX = 100.0;
//
//    public static final double EDGE_POWER_W  = 50.0;
//    public static final double CLOUD_POWER_W = 200.0;
//
//    // These will be sampled ONCE per task and stored in Meta (Common Random Numbers)
//    public static final double EDGE_NET_MS_MIN  = 5.0;
//    public static final double EDGE_NET_MS_MAX  = 10.0;
//
//    public static final double CLOUD_NET_MS_MIN = 50.0;
//    public static final double CLOUD_NET_MS_MAX = 100.0;
//
//    // Host MIPS (keep your current setting)
//    public static final int EDGE_HOST_MIPS_MIN = 3000;
//    public static final int EDGE_HOST_MIPS_MAX = 3000;
//        public static final int CLOUD_HOST_MIPS_MIN = 1000;
//    public static final int CLOUD_HOST_MIPS_MAX = 10000;
//
//    // VM MIPS
//    public static final int EDGE_VM_MIPS  = 2000;
//    public static final int CLOUD_VM_MIPS = 4000;
//
//    // RAM (MB)
//    public static final int EDGE_RAM_MB  = 32 * 1024;
//    public static final int CLOUD_RAM_MB = 64 * 1024;
//
//    // VM RAM
//    public static final int EDGE_VM_RAM_MB  = 4096;
//    public static final int CLOUD_VM_RAM_MB = 8192;
//
//    // Workload mapping
//    public static final double MI_PER_MB = 20.0;
//
//    // Experiment
//    public static final int TOTAL_TASKS = 80;
//    public static final int TAG_TASK_ARRIVAL = 91001;
//
//    // Priority threshold
//    public static final double CRITICAL_THRESHOLD = 0.70;
//
//    // CAP rule (default 35%)
//    public static final double CLOUD_CAP = 0.35;
//
//    // Minimum exploration to cloud (set 0.10 for 10% or 0.0 to disable)
//    public static final double CLOUD_MIN_SHARE = 0.25;
//
//    public static final double EPS = 1e-9;
//}
