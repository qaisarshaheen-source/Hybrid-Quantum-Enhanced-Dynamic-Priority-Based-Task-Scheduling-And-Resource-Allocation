//package org.iquantum.examples.hq_dptara.broker;
//import java.util.Map;
//import org.iquantum.examples.hq_dptara.util.SimConfig;
///**
// * QuantumController:
// * - Produces candidate Plan updates (Algorithm 2)
// * - DOES NOT schedule tasks directly.
// *
// * NOTE: In your thesis, this module can be replaced by iQuantum quantum routines.
// * Here we implement a safe heuristic "quantum-like" proposer that adapts parameters
// * based on recent metrics and node utilization snapshots.
// */
//public class QuantumController {
//    public Plan suggestUpdate(Metrics recent, Plan current, Map<Integer, Double> vmUtil01) {
//        Plan next = current.copy();
//        double dmr = recent.dmrPercent();
//        double avgLatMs = recent.avgLatencyMs();
//        double avgUtilPct = recent.avgUtilPercent();
//        // ---- Coefficients: α, β, γ ----
//        // If deadline misses rise -> increase β (aging) to prioritize tasks closer to deadline.
//        if (dmr > 0.05) {
//            next.beta = clamp(next.beta + 0.03, 0.10, 0.60);
//        }
//        // If latency is high -> slightly raise α (respect original healthcare priority more).
//        if (avgLatMs > 400.0) {
//            next.alpha = clamp(next.alpha + 0.02, 0.30, 0.80);
//        }
//        // If utilization is too low -> reduce γ a bit (less penalization from load term).
//        if (avgUtilPct > 0.0 && avgUtilPct < 35.0) {
//            next.gamma = clamp(next.gamma - 0.02, 0.05, 0.40);
//        }
//        // ---- Weights ----
//        // If energy per task is high -> increase energy weight slightly.
//        double energyPerTask = recent.completed == 0 ? 0.0 : (recent.totalEnergyJ / recent.completed);
//        if (energyPerTask > 2000.0) {
//            next.wEne = clamp(next.wEne + 0.03, 0.10, 0.60);
//            next.wLat = clamp(next.wLat - 0.02, 0.10, 0.60);
//        }
//        // Normalize
//        next.normalizeWeights();
//        // ---- Bias map: discourage overloaded, encourage underloaded ----
//        for (Map.Entry<Integer, Double> e : vmUtil01.entrySet()) {
//            int vmId = e.getKey();
//            double util = e.getValue(); // 0..1 (estimated)
//            // bias in [-0.30, +0.30]
//            double bias = clamp((0.5 - util) * 0.4, -0.30, +0.30);
//            next.biasMap.put(vmId, bias);
//        }
//        // ---- Reserve override suggestion (will be validated in Algorithm 5) ----
//        if (avgUtilPct > 0.0 && avgUtilPct < 35.0) {
//            next.reserveOverride = 0.10; // use more capacity
//        } else if (dmr > 0.05) {
//            next.reserveOverride = 0.18; // keep more reserve for safety
//        } else {
//            next.reserveOverride = null;
//        }
//        return next;
//    }
//    private static double clamp(double x, double lo, double hi) {
//        return Math.max(lo, Math.min(hi, x));
//    }
//}

/////////////////// final code








package org.iquantum.examples.hq_dptara.broker;

import java.util.Map;

public class QuantumController {

    public Plan suggestUpdate(Metrics recent, Plan current, Map<Integer, Double> vmUtil01) {
        Plan next = current.copy();

        double dmr = recent.dmrPercent();
        double avgLatMs = recent.avgLatencyMs();
        double avgUtilPct = recent.avgUtilPercent();

        // ----- Tune α, β, γ -----
        if (dmr > 5.0) { // DMR percent
            next.beta = clamp(next.beta + 0.03, 0.10, 0.60);
        }
        if (avgLatMs > 400.0) {
            next.alpha = clamp(next.alpha + 0.02, 0.30, 0.80);
        }
        if (avgUtilPct > 0.0 && avgUtilPct < 35.0) {
            next.gamma = clamp(next.gamma - 0.02, 0.05, 0.40);
        }

        // ----- Weights -----
        double energyPerTask = recent.completed == 0 ? 0.0 : (recent.totalEnergyJ / recent.completed);
        if (energyPerTask > 2000.0) {
            next.wEne = clamp(next.wEne + 0.03, 0.10, 0.60);
            next.wLat = clamp(next.wLat - 0.02, 0.10, 0.60);
        }

        next.normalizeWeights();

        // ----- Bias map -----
        for (Map.Entry<Integer, Double> e : vmUtil01.entrySet()) {
            int vmId = e.getKey();
            double util = e.getValue(); // 0..1
            double bias = clamp((0.5 - util) * 0.4, -0.30, +0.30);
            next.biasMap.put(vmId, bias);
        }

        // ----- Reserve override suggestion -----
        if (avgUtilPct > 0.0 && avgUtilPct < 35.0) next.reserveOverride = 0.10;
        else if (dmr > 5.0) next.reserveOverride = 0.18;
        else next.reserveOverride = null;

        return next;
    }

    private static double clamp(double x, double lo, double hi) {
        return Math.max(lo, Math.min(hi, x));
    }
}
