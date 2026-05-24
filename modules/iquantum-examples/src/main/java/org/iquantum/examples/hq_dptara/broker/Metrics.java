//package org.iquantum.examples.hq_dptara.broker;
///**
// * Metrics aggregator for HQ_DPTARA (healthcare).
// * Tracks All / Pr / Nr latency, execution, energy, and deadline miss.
// */
//public class Metrics {
//    public long totalGenerated = 0;
//    public long completed = 0;
//    public long rejected = 0;
//
//    public long completedPr = 0;
//    public long completedNr = 0;
//
//    public long deadlineMiss = 0;
//
//    public double totalLatencySec = 0.0;
//    public double totalLatencySecPr = 0.0;
//    public double totalLatencySecNr = 0.0;
//
//    public double totalExecSec = 0.0;
//    public double totalEnergyJ = 0.0;
//
//    // utilization/balancing sampling (optional)
//    public long utilSamples = 0;
//    public double utilSum = 0.0;
//
//    public long brSamples = 0;
//    public double brSum = 0.0;
//
//    public void onGenerated() { totalGenerated++; }
//
//    public void onRejected() { rejected++; }
//
//    public void addCompleted(double latencySec, double execSec, double energyJ,
//                             boolean missedDeadline, boolean isPr) {
//        completed++;
//        totalLatencySec += latencySec;
//        totalExecSec += execSec;
//        totalEnergyJ += energyJ;
//
//        if (isPr) {
//            completedPr++;
//            totalLatencySecPr += latencySec;
//        } else {
//            completedNr++;
//            totalLatencySecNr += latencySec;
//        }
//
//        if (missedDeadline) deadlineMiss++;
//    }
//
//    public void sampleUtil(double util01) {
//        utilSamples++;
//        utilSum += util01;
//    }
//
//    public void sampleBr(double br01) {
//        brSamples++;
//        brSum += br01;
//    }
//
//    public double avgLatencyMs() {
//        return completed == 0 ? 0.0 : (totalLatencySec / completed) * 1000.0;
//    }
//
//    public double avgLatencyMsPr() {
//        return completedPr == 0 ? 0.0 : (totalLatencySecPr / completedPr) * 1000.0;
//    }
//
//    public double avgLatencyMsNr() {
//        return completedNr == 0 ? 0.0 : (totalLatencySecNr / completedNr) * 1000.0;
//    }
//
//    public double avgExecMs() {
//        return completed == 0 ? 0.0 : (totalExecSec / completed) * 1000.0;
//    }
//
//    public double avgEnergyJ() {
//        return completed == 0 ? 0.0 : (totalEnergyJ / completed);
//    }
//
//    public double dmr() {
//        return completed == 0 ? 0.0 : (double) deadlineMiss / (double) completed;
//    }
//
////    public double avgUtilPct() {
////        return utilSamples == 0 ? 0.0 : (utilSum / utilSamples) * 100.0;
////    }
//    public double avgUtilPct() {
//        return (utilSamples > 0) ? (utilSum / utilSamples) * 100.0 : 0.0;
//    }
//
//    public double avgBrPct() {
//        return brSamples == 0 ? 0.0 : (brSum / brSamples) * 100.0;
//    }
//}

//
//package org.iquantum.examples.hq_dptara.broker;
//public class Metrics {
//    // counts
//    public long totalGenerated = 0;
//    public long completed = 0;
//    public long rejected = 0;
//    // optional (still keep for future PR/NR split; we won't print now)
//    public long completedPr = 0;
//    public long completedNr = 0;
//    // sums (seconds / joules)
//    public double totalLatencySec = 0.0;
//    public double totalExecSec = 0.0;
//    public double totalEnergyJ = 0.0;
//    // optional PR/NR sums (for later)
//    public double totalLatencySecPr = 0.0;
//    public double totalLatencySecNr = 0.0;
//    // deadline miss count (overall)
//    public long deadlineMiss = 0;
//    // utilization sampling (overall indicator; you can sample RAM util or anything)
//    public long utilSamples = 0;
//    public double utilSum = 0.0;
//    // balancing rate sampling (0..1 values)
//    public long brSamples = 0;
//    public double brSum = 0.0;
//    /* ================== events ================== */
//    public void onGenerated() {
//        totalGenerated++;
//    }
//    public void onRejected() {
//        rejected++;
//    }
//    public void addCompleted(double latencySec, double execSec, double energyJ, boolean missedDeadline, boolean isPr) {
//        completed++;
//        totalLatencySec += Math.max(0.0, latencySec);
//        totalExecSec += Math.max(0.0, execSec);
//        totalEnergyJ += Math.max(0.0, energyJ);
//        if (missedDeadline) deadlineMiss++;
//        // keep for later PR/NR (not printed now)
//        if (isPr) {
//            completedPr++;
//            totalLatencySecPr += Math.max(0.0, latencySec);
//        } else {
//            completedNr++;
//            totalLatencySecNr += Math.max(0.0, latencySec);
//        }
//    }
//    /* ================== sampling ================== */
//    public void sampleUtil(double util01) {
//        utilSamples++;
//        utilSum += clamp(util01, 0.0, 1.0);
//    }
//    public void sampleBr(double br01) {
//        brSamples++;
//        brSum += clamp(br01, 0.0, 1.0);
//    }
//    /* ================== derived ================== */
//    public double avgLatencyMs() {
//        if (completed <= 0) return 0.0;
//        return (totalLatencySec / completed) * 1000.0;
//    }
//    public double avgExecMs() {
//        if (completed <= 0) return 0.0;
//        return (totalExecSec / completed) * 1000.0;
//    }
//    public double dmrPercent() {
//        if (completed <= 0) return 0.0;
//        return (deadlineMiss * 100.0) / completed;
//    }
//    public double avgUtilPercent() {
//        if (utilSamples <= 0) return 0.0;
//        return (utilSum / utilSamples) * 100.0;
//    }
//    public double avgBrPercent() {
//        if (brSamples <= 0) return 0.0;
//        return (brSum / brSamples) * 100.0;
//    }
//    private static double clamp(double x, double lo, double hi) {
//        return Math.max(lo, Math.min(hi, x));
//    }
//}
/// /////////// final code






package org.iquantum.examples.hq_dptara.broker;

public class Metrics {
    public long totalGenerated = 0;
    public long completed = 0;
    public long rejected = 0;

    public long completedPr = 0;
    public long completedNr = 0;

    public double totalLatencySec = 0.0;
    public double totalExecSec = 0.0;
    public double totalEnergyJ = 0.0;

    public double totalLatencySecPr = 0.0;
    public double totalLatencySecNr = 0.0;

    public long deadlineMiss = 0;

    public long utilSamples = 0;
    public double utilSum = 0.0;

    public long brSamples = 0;
    public double brSum = 0.0;

    public void onGenerated() { totalGenerated++; }
    public void onRejected() { rejected++; }

    public void addCompleted(double latencySec, double execSec, double energyJ, boolean missedDeadline, boolean isPr) {
        completed++;
        totalLatencySec += Math.max(0.0, latencySec);
        totalExecSec += Math.max(0.0, execSec);
        totalEnergyJ += Math.max(0.0, energyJ);
        if (missedDeadline) deadlineMiss++;

        if (isPr) {
            completedPr++;
            totalLatencySecPr += Math.max(0.0, latencySec);
        } else {
            completedNr++;
            totalLatencySecNr += Math.max(0.0, latencySec);
        }
    }

    public void sampleUtil(double util01) {
        utilSamples++;
        utilSum += clamp(util01, 0.0, 1.0);
    }

    public void sampleBr(double br01) {
        brSamples++;
        brSum += clamp(br01, 0.0, 1.0);
    }

    public double avgLatencyMs() {
        if (completed <= 0) return 0.0;
        return (totalLatencySec / completed) * 1000.0;
    }

    public double avgExecMs() {
        if (completed <= 0) return 0.0;
        return (totalExecSec / completed) * 1000.0;
    }

    public double dmrPercent() {
        if (completed <= 0) return 0.0;
        return (deadlineMiss * 100.0) / completed;
    }

    public double avgUtilPercent() {
        if (utilSamples <= 0) return 0.0;
        return (utilSum / utilSamples) * 100.0;
    }

    public double avgBrPercent() {
        if (brSamples <= 0) return 0.0;
        return (brSum / brSamples) * 100.0;
    }

    private static double clamp(double x, double lo, double hi) {
        return Math.max(lo, Math.min(hi, x));
    }
}




