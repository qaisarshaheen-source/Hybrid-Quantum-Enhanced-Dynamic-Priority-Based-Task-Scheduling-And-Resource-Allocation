//package org.iquantum.examples.hq_dptara.broker;
//import java.util.HashMap;
//import java.util.Map;
///**
// * Plan = the shared configuration produced/updated by the quantum layer and
// * consumed by the classical scheduler. This directly matches your Chapter 3 design.
// */
//public class Plan {
//    // Priority coefficients (Algorithm 3): Pi = α*Porig + β*aging - γ*Lcurrent
//    public double alpha, beta, gamma;
//    // Multi-objective weights (Equation-style scoring)
//    public double wLat, wEne, wUtil, wBias, wPrio;
//    // Resource bias map (soft preferences). Key = VM id
//    public final Map<Integer, Double> biasMap = new HashMap<>();
//    // Optional override for reserve ratio (validated before apply)
//    public Double reserveOverride = null;
//    // bookkeeping
//    public double timestampSec = 0.0;
//    public Plan(double alpha, double beta, double gamma,
//                double wLat, double wEne, double wUtil, double wBias, double wPrio) {
//        this.alpha = alpha;
//        this.beta = beta;
//        this.gamma = gamma;
//        this.wLat = wLat;
//        this.wEne = wEne;
//        this.wUtil = wUtil;
//        this.wBias = wBias;
//        this.wPrio = wPrio;
//        normalizeWeights();
//    }
//    public Plan copy() {
//        Plan p = new Plan(alpha, beta, gamma, wLat, wEne, wUtil, wBias, wPrio);
//        p.biasMap.clear();
//        p.biasMap.putAll(this.biasMap);
//        p.reserveOverride = this.reserveOverride;
//        p.timestampSec = this.timestampSec;
//        return p;
//    }
//    public void normalizeWeights() {
//        double s = wLat + wEne + wUtil + wBias + wPrio;
//        if (s <= 0) return;
//        wLat  /= s;
//        wEne  /= s;
//        wUtil /= s;
//        wBias /= s;
//        wPrio /= s;
//    }
//}



//////////////// final code








package org.iquantum.examples.hq_dptara.broker;

import java.util.HashMap;
import java.util.Map;

public class Plan {
    public double alpha, beta, gamma;
    public double wLat, wEne, wUtil, wBias, wPrio;

    public final Map<Integer, Double> biasMap = new HashMap<>();
    public Double reserveOverride = null;
    public double timestampSec = 0.0;

    public Plan(double alpha, double beta, double gamma,
                double wLat, double wEne, double wUtil, double wBias, double wPrio) {
        this.alpha = alpha;
        this.beta = beta;
        this.gamma = gamma;
        this.wLat = wLat;
        this.wEne = wEne;
        this.wUtil = wUtil;
        this.wBias = wBias;
        this.wPrio = wPrio;
        normalizeWeights();
    }

    public Plan copy() {
        Plan p = new Plan(alpha, beta, gamma, wLat, wEne, wUtil, wBias, wPrio);
        p.biasMap.clear();
        p.biasMap.putAll(this.biasMap);
        p.reserveOverride = this.reserveOverride;
        p.timestampSec = this.timestampSec;
        return p;
    }

    public void normalizeWeights() {
        double s = wLat + wEne + wUtil + wBias + wPrio;
        if (s <= 0) return;
        wLat  /= s;
        wEne  /= s;
        wUtil /= s;
        wBias /= s;
        wPrio /= s;
    }
}
