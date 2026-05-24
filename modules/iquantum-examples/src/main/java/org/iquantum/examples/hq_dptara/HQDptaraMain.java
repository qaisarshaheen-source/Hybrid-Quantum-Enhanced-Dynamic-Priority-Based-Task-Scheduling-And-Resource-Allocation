//package org.iquantum.examples.hq_dptara;
//
//import java.util.*;
//import org.iquantum.core.iQuantum;
//import org.iquantum.datacenters.CDatacenter;
//import org.iquantum.datacenters.CDatacenterCharacteristics;
//import org.iquantum.backends.classical.*;
//import org.iquantum.models.UtilizationModel;
//import org.iquantum.models.UtilizationModelFull;
////import org.iquantum.policies.ctasks.CloudletSchedulerTimeShared;
//import org.iquantum.backends.classical.container.schedulers.ContainerCloudletSchedulerTimeShared;
//import org.iquantum.policies.vm.VmAllocationPolicySimple;
//import org.iquantum.policies.vm.VmSchedulerTimeShared;
//import org.iquantum.provisioners.*;
//import org.iquantum.tasks.CTask;
//import org.iquantum.utils.Log;
//
//import org.iquantum.examples.hq_dptara.broker.HQDptaraBroker;
//import org.iquantum.examples.hq_dptara.util.Dist;
//import org.iquantum.examples.hq_dptara.util.SimConfig;
//import org.iquantum.examples.hq_dptara.broker.Metrics;
//
//
//public class HQDptaraMain {
//
//    private static final class Draft {
//        final double arrival;
//        final double sizeMb;
//        final double deadline;
//        final double porig;
//
//        Draft(double arrival, double sizeMb, double deadline, double porig) {
//            this.arrival = arrival;
//            this.sizeMb = sizeMb;
//            this.deadline = deadline;
//            this.porig = porig;
//        }
//    }
//
//    public static void main(String[] args) {
//        // change seed here
//        long seed = 42L;
//
//        runOne("DPTARA (Quantum OFF)", false, seed);
//        runOne("HQ_DPTARA (Quantum ON)", true, seed);
//    }
//
//    private static void runOne(String title, boolean quantumEnabled, long seed) {
//        Log.printLine("\n==============================");
//        Log.printLine(title + " | seed=" + seed + " | tasks=" + SimConfig.TOTAL_TASKS);
//        Log.printLine("==============================");
//
//        try {
//            Dist.setSeed(seed);
//
//            int num_user = 1;
//            Calendar calendar = Calendar.getInstance();
//            boolean trace_flag = false;
//
//            iQuantum.init(num_user, calendar, trace_flag);
//
//            CDatacenter edgeDc  = createDatacenter("Edge_Datacenter", true);
//            CDatacenter cloudDc = createDatacenter("Cloud_Datacenter", false);
//
//            HQDptaraBroker broker = new HQDptaraBroker("HQDPTARA_Broker", quantumEnabled);
//            int brokerId = broker.getId();
//
//            List<Vm> vmlist = new ArrayList<>();
//            vmlist.addAll(createEdgeVms(brokerId, SimConfig.NUM_EDGE_SERVERS));
//            vmlist.addAll(createCloudVms(brokerId, SimConfig.NUM_CLOUD_SERVERS, SimConfig.NUM_EDGE_SERVERS));
//            broker.submitVmList(vmlist);
//
//            List<CTask> taskList = generateTasksPoissonByDevices(brokerId);
//
//            for (CTask t : taskList) {
//                int tid = t.getCloudletId();
//                double arrivalSec = HQDptaraBroker.Meta.arrival(tid);
//                iQuantum.send(brokerId, brokerId, arrivalSec, SimConfig.TAG_TASK_ARRIVAL, t);
//            }
//
//            iQuantum.startSimulation();
//            iQuantum.stopSimulation();
//
//            // simple final metrics
////            var m = broker.getMetrics();
//            Metrics m = broker.getMetrics();
//            Log.printLine("Generated: " + m.totalGenerated);
//            Log.printLine("Completed: " + m.completed);
//            Log.printLine("Rejected : " + m.rejected);
//            Log.printLine(String.format("Avg Latency (All) ms: %.3f", m.avgLatencyMs()));
//            Log.printLine(String.format("Avg Latency (Pr)  ms: %.3f", m.avgLatencyMsPr()));
//            Log.printLine(String.format("Avg Latency (Nr)  ms: %.3f", m.avgLatencyMsNr()));
//            Log.printLine(String.format("Avg Exec Time ms    : %.3f", m.avgExecMs()));
//            Log.printLine(String.format("Avg Energy J        : %.3f", m.avgEnergyJ()));
//            Log.printLine(String.format("Utilization %%       : %.2f", m.avgUtilPct()));
//            Log.printLine(String.format("Balancing Rate %%    : %.2f", m.avgBrPct()));
//            Log.printLine(String.format("DMR                 : %.4f", m.dmr()));
//
//        } catch (Exception e) {
//            e.printStackTrace();
//            Log.printLine("Simulation terminated due to unexpected error.");
//        }
//    }
//
//    private static CDatacenter createDatacenter(String name, boolean isEdge) {
//        List<Host> hostList = new ArrayList<>();
//        int hosts = isEdge ? SimConfig.NUM_EDGE_SERVERS : SimConfig.NUM_CLOUD_SERVERS;
//
//        for (int h = 0; h < hosts; h++) {
//            List<Pe> peList = new ArrayList<>();
//
////            for (int p = 0; p < 4; p++)
//            int peCount = isEdge ? 4 : 8;   // cloud stronger
//            for (int p = 0; p < peCount; p++)  {
////                int mips = isEdge
////                        ? Dist.uniformInt(SimConfig.EDGE_MIPS_MIN, SimConfig.EDGE_MIPS_MAX)
////                        : Dist.uniformInt(SimConfig.CLOUD_MIPS_MIN, SimConfig.CLOUD_MIPS_MAX);
//                int mips = isEdge
//                        ? Dist.uniformInt(SimConfig.EDGE_HOST_MIPS_MIN, SimConfig.EDGE_HOST_MIPS_MAX)
//                        : Dist.uniformInt(SimConfig.CLOUD_HOST_MIPS_MIN, SimConfig.CLOUD_HOST_MIPS_MAX);
//
//                peList.add(new Pe(p, new PeProvisionerSimple(mips)));
//            }
//
//            int ram = isEdge ? SimConfig.EDGE_RAM_MB : SimConfig.CLOUD_RAM_MB;
//            long storage = 1_000_000;
//            int bw = 10_000;
//
//            hostList.add(new Host(
//                    h,
//                    new RamProvisionerSimple(ram),
//                    new BwProvisionerSimple(bw),
//                    storage,
//                    peList,
//                    new VmSchedulerTimeShared(peList)
//            ));
//        }
//
//        CDatacenterCharacteristics characteristics =
//                new CDatacenterCharacteristics("x86", "Linux", "Xen",
//                        hostList, 10.0, 3.0, 0.05, 0.001, 0.0);
//
//        try {
//            return new CDatacenter(name, characteristics,
//                    new VmAllocationPolicySimple(hostList),
//                    new LinkedList<>(), 0);
//        } catch (Exception e) {
//            throw new RuntimeException(e);
//        }
//    }
//
////    private static List<Vm> createEdgeVms(int brokerId, int count) {
////        List<Vm> vms = new ArrayList<>();
////        for (int i = 0; i < count; i++) {
////            int vmid = i;
////            int mips = Dist.uniformInt(SimConfig.EDGE_MIPS_MIN, SimConfig.EDGE_MIPS_MAX);
////            vms.add(new Vm(vmid, brokerId, mips, 1, SimConfig.EDGE_RAM_MB, 1000, 10_000,
////                    "EDGE", new CloudletSchedulerTimeShared()));
////        }
////        return vms;
////    }
//private static List<Vm> createEdgeVms(int brokerId, int count) {
//    List<Vm> vms = new ArrayList<>();
//    for (int i = 0; i < count; i++) {
//        int vmid = i;
//
//        int mips = SimConfig.EDGE_VM_MIPS;      // FIXED
//        long size = 10_000;
//        int ram = SimConfig.EDGE_VM_RAM_MB;     // FIXED
//        long bw = 1000;
//        int pesNumber = 1;
//        String vmm = "EDGE";
//
//        vms.add(new Vm(vmid, brokerId, mips, pesNumber, ram, bw, size,
//                vmm, new CloudletSchedulerTimeShared()));
//    }
//    return vms;
//}
//
//    private static List<Vm> createCloudVms(int brokerId, int count, int edgeVmCount) {
//        List<Vm> vms = new ArrayList<>();
//        for (int i = 0; i < count; i++) {
//            int vmid = edgeVmCount + i;
//
//            int mips = SimConfig.CLOUD_VM_MIPS;     // FIXED
//            long size = 50_000;
//            int ram = SimConfig.CLOUD_VM_RAM_MB;    // FIXED
//            long bw = 5000;
//            int pesNumber = 1;
//            String vmm = "CLOUD";
//
//            vms.add(new Vm(vmid, brokerId, mips, pesNumber, ram, bw, size,
//                    vmm, new CloudletSchedulerTimeShared()));
//        }
//        return vms;
//    }
////    private static List<Vm> createCloudVms(int brokerId, int count, int edgeVmCount) {
////        List<Vm> vms = new ArrayList<>();
////        for (int i = 0; i < count; i++) {
////            int vmid = edgeVmCount + i;
////            int mips = Dist.uniformInt(SimConfig.CLOUD_MIPS_MIN, SimConfig.CLOUD_MIPS_MAX);
////            vms.add(new Vm(vmid, brokerId, mips, 1, SimConfig.CLOUD_RAM_MB, 5000, 50_000,
////                    "CLOUD", new CloudletSchedulerTimeShared()));
////        }
////        return vms;
////    }
//
//    private static List<CTask> generateTasksPoissonByDevices(int brokerId) {
//        UtilizationModel um = new UtilizationModelFull();
//        List<Draft> drafts = new ArrayList<>();
//
//        double lambdaDevice = SimConfig.LAMBDA_TASKS_PER_SEC / Math.max(1.0, SimConfig.NUM_IOT_DEVICES);
//
//        for (int dev = 0; dev < SimConfig.NUM_IOT_DEVICES; dev++) {
//            double t = 0.0;
//            while (t <= SimConfig.SIM_TIME_SEC && drafts.size() < SimConfig.MAX_TASKS) {
//                t += Dist.expInterArrival(lambdaDevice);
//                if (t > SimConfig.SIM_TIME_SEC) break;
//
//                double sizeMb = Dist.uniform(SimConfig.TASK_SIZE_MB_MIN, SimConfig.TASK_SIZE_MB_MAX);
//                double deadline = Dist.uniform(SimConfig.DEADLINE_SEC_MIN, SimConfig.DEADLINE_SEC_MAX);
//                double porig = samplePorig();
//
//                drafts.add(new Draft(t, sizeMb, deadline, porig));
//            }
//        }
//
//        drafts.sort(Comparator.comparingDouble(x -> x.arrival));
//
//        List<CTask> list = new ArrayList<>();
//        for (int id = 0; id < Math.min(SimConfig.MAX_TASKS, drafts.size()); id++) {
//            Draft d = drafts.get(id);
//
//            long lengthMI = Math.max(1L, Math.round(d.sizeMb * SimConfig.MI_PER_MB));
//            CTask task = new CTask(id, lengthMI, 1, 300, 300, um, um, um);
//            task.setUserId(brokerId);
//
//            HQDptaraBroker.Meta.put(id, d.arrival, d.deadline, d.porig, d.sizeMb);
//            list.add(task);
//        }
//
//        Log.printLine("Generated tasks: " + list.size());
//        return list;
//    }
//    private static double samplePorig() {
//        double x = Dist.uniform(0.0, 1.0);
//        if (x < 0.20) return 0.95; // Pr
//        if (x < 0.80) return 0.60; // Nr
//        return 0.30;               // Nr
//    }
//}



//package org.iquantum.examples.hq_dptara;
//import java.util.*;
//import org.iquantum.core.iQuantum;
//import org.iquantum.datacenters.CDatacenter;
//import org.iquantum.datacenters.CDatacenterCharacteristics;
//import org.iquantum.backends.classical.*;
//import org.iquantum.models.UtilizationModel;
//import org.iquantum.models.UtilizationModelFull;
//
//// ✅ FIXED IMPORT (correct scheduler)
////import org.iquantum.backends.classical.container.schedulers.ContainerCloudletSchedulerTimeShared;
//import org.iquantum.policies.ctasks.CloudletSchedulerTimeShared;
//
//import org.iquantum.policies.vm.VmAllocationPolicySimple;
//import org.iquantum.policies.vm.VmSchedulerTimeShared;
//import org.iquantum.provisioners.*;
//import org.iquantum.tasks.CTask;
//import org.iquantum.utils.Log;
//import org.iquantum.examples.hq_dptara.broker.HQDptaraBroker;
//import org.iquantum.examples.hq_dptara.util.Dist;
//import org.iquantum.examples.hq_dptara.util.SimConfig;
//import org.iquantum.examples.hq_dptara.broker.Metrics;
//
//public class HQDptaraMain {
//
//    private static final class Draft {
//        final double arrival;
//        final double sizeMb;
//        final double deadline;
//        final double porig;
//
//        Draft(double arrival, double sizeMb, double deadline, double porig) {
//            this.arrival = arrival;
//            this.sizeMb = sizeMb;
//            this.deadline = deadline;
//            this.porig = porig;
//        }
//    }
//
//    public static void main(String[] args) {
//        // change seed here
//        long seed = 42L;
//
//        runOne("DPTARA (Quantum OFF)", false, seed);
//        runOne("HQ_DPTARA (Quantum ON)", true, seed);
//    }
//
//    private static void runOne(String title, boolean quantumEnabled, long seed) {
//        Log.printLine("\n==============================");
//        Log.printLine(title + " | seed=" + seed + " | tasks=" + SimConfig.TOTAL_TASKS);
//        Log.printLine("==============================");
//
//        try {
//            Dist.setSeed(seed);
//
//            int num_user = 1;
//            Calendar calendar = Calendar.getInstance();
//            boolean trace_flag = false;
//
//            iQuantum.init(num_user, calendar, trace_flag);
//
//            CDatacenter edgeDc  = createDatacenter("Edge_Datacenter", true);
//            CDatacenter cloudDc = createDatacenter("Cloud_Datacenter", false);
//
//            HQDptaraBroker broker = new HQDptaraBroker("HQDPTARA_Broker", quantumEnabled);
//            int brokerId = broker.getId();
//
//            List<Vm> vmlist = new ArrayList<>();
//            vmlist.addAll(createEdgeVms(brokerId, SimConfig.NUM_EDGE_SERVERS));
//            vmlist.addAll(createCloudVms(brokerId, SimConfig.NUM_CLOUD_SERVERS, SimConfig.NUM_EDGE_SERVERS));
//            broker.submitVmList(vmlist);
//
//            List<CTask> taskList = generateTasksPoissonByDevices(brokerId);
//
//            for (CTask t : taskList) {
//                int tid = t.getCloudletId();
//                double arrivalSec = HQDptaraBroker.Meta.arrival(tid);
//                iQuantum.send(brokerId, brokerId, arrivalSec, SimConfig.TAG_TASK_ARRIVAL, t);
//            }
//
//            iQuantum.startSimulation();
//            iQuantum.stopSimulation();
//
//            // simple final metrics
//            Metrics m = broker.getMetrics();
//            Log.printLine("Generated: " + m.totalGenerated);
//            Log.printLine("Completed: " + m.completed);
//            Log.printLine("Rejected : " + m.rejected);
//            Log.printLine(String.format("Avg Latency (All) ms: %.3f", m.avgLatencyMs()));
//            Log.printLine(String.format("Avg Latency (Pr)  ms: %.3f", m.avgLatencyMsPr()));
//            Log.printLine(String.format("Avg Latency (Nr)  ms: %.3f", m.avgLatencyMsNr()));
//            Log.printLine(String.format("Avg Exec Time ms    : %.3f", m.avgExecMs()));
//            Log.printLine(String.format("Avg Energy J        : %.3f", m.avgEnergyJ()));
//            Log.printLine(String.format("Utilization %%       : %.2f", m.avgUtilPct()));
//            Log.printLine(String.format("Balancing Rate %%    : %.2f", m.avgBrPct()));
//            Log.printLine(String.format("DMR                 : %.4f", m.dmr()));
//
//        } catch (Exception e) {
//            e.printStackTrace();
//            Log.printLine("Simulation terminated due to unexpected error.");
//        }
//    }
//
//    private static CDatacenter createDatacenter(String name, boolean isEdge) {
//        List<Host> hostList = new ArrayList<>();
//        int hosts = isEdge ? SimConfig.NUM_EDGE_SERVERS : SimConfig.NUM_CLOUD_SERVERS;
//
//        for (int h = 0; h < hosts; h++) {
//            List<Pe> peList = new ArrayList<>();
//
//            int peCount = isEdge ? 4 : 8;   // cloud stronger
//            for (int p = 0; p < peCount; p++) {
//                int mips = isEdge
//                        ? Dist.uniformInt(SimConfig.EDGE_HOST_MIPS_MIN, SimConfig.EDGE_HOST_MIPS_MAX)
//                        : Dist.uniformInt(SimConfig.CLOUD_HOST_MIPS_MIN, SimConfig.CLOUD_HOST_MIPS_MAX);
//
//                peList.add(new Pe(p, new PeProvisionerSimple(mips)));
//            }
//
//            int ram = isEdge ? SimConfig.EDGE_RAM_MB : SimConfig.CLOUD_RAM_MB;
//            long storage = 1_000_000;
//            int bw = 10_000;
//
//            hostList.add(new Host(
//                    h,
//                    new RamProvisionerSimple(ram),
//                    new BwProvisionerSimple(bw),
//                    storage,
//                    peList,
//                    new VmSchedulerTimeShared(peList)
//            ));
//        }
//
//        CDatacenterCharacteristics characteristics =
//                new CDatacenterCharacteristics("x86", "Linux", "Xen",
//                        hostList, 10.0, 3.0, 0.05, 0.001, 0.0);
//
//        try {
//            return new CDatacenter(name, characteristics,
//                    new VmAllocationPolicySimple(hostList),
//                    new LinkedList<>(), 0);
//        } catch (Exception e) {
//            throw new RuntimeException(e);
//        }
//    }
//
//    private static List<Vm> createEdgeVms(int brokerId, int count) {
//        List<Vm> vms = new ArrayList<>();
//        for (int i = 0; i < count; i++) {
//            int vmid = i;
//
//            int mips = SimConfig.EDGE_VM_MIPS;
//            long size = 10_000;
//            int ram = SimConfig.EDGE_VM_RAM_MB;
//            long bw = 1000;
//            int pesNumber = 1;
//            String vmm = "EDGE";
//
//            // ✅ FIXED: scheduler class
////            vms.add(new Vm(vmid, brokerId, mips, pesNumber, ram, bw, size,
////                    vmm, new ContainerCloudletSchedulerTimeShared()));
//            vms.add(new Vm(vmid, brokerId, mips, pesNumber, ram, bw, size,
//                    vmm, new CloudletSchedulerTimeShared()));
//
//        }
//        return vms;
//    }
//
//    private static List<Vm> createCloudVms(int brokerId, int count, int edgeVmCount) {
//        List<Vm> vms = new ArrayList<>();
//        for (int i = 0; i < count; i++) {
//            int vmid = edgeVmCount + i;
//
//            int mips = SimConfig.CLOUD_VM_MIPS;
//            long size = 50_000;
//            int ram = SimConfig.CLOUD_VM_RAM_MB;
//            long bw = 5000;
//            int pesNumber = 1;
//            String vmm = "CLOUD";
//
//            // ✅ FIXED: scheduler class
////            vms.add(new Vm(vmid, brokerId, mips, pesNumber, ram, bw, size,
////                    vmm, new ContainerCloudletSchedulerTimeShared()));
//            vms.add(new Vm(vmid, brokerId, mips, pesNumber, ram, bw, size,
//                    vmm, new CloudletSchedulerTimeShared()));
//
//        }
//        return vms;
//    }
//
//    private static List<CTask> generateTasksPoissonByDevices(int brokerId) {
//        UtilizationModel um = new UtilizationModelFull();
//        List<Draft> drafts = new ArrayList<>();
//
//        double lambdaDevice = SimConfig.LAMBDA_TASKS_PER_SEC / Math.max(1.0, SimConfig.NUM_IOT_DEVICES);
//
//        for (int dev = 0; dev < SimConfig.NUM_IOT_DEVICES; dev++) {
//            double t = 0.0;
//            while (t <= SimConfig.SIM_TIME_SEC && drafts.size() < SimConfig.MAX_TASKS) {
//                t += Dist.expInterArrival(lambdaDevice);
//                if (t > SimConfig.SIM_TIME_SEC) break;
//
//                double sizeMb = Dist.uniform(SimConfig.TASK_SIZE_MB_MIN, SimConfig.TASK_SIZE_MB_MAX);
//                double deadline = Dist.uniform(SimConfig.DEADLINE_SEC_MIN, SimConfig.DEADLINE_SEC_MAX);
//                double porig = samplePorig();
//
//                drafts.add(new Draft(t, sizeMb, deadline, porig));
//            }
//        }
//
//        drafts.sort(Comparator.comparingDouble(x -> x.arrival));
//
//        List<CTask> list = new ArrayList<>();
//        for (int id = 0; id < Math.min(SimConfig.MAX_TASKS, drafts.size()); id++) {
//            Draft d = drafts.get(id);
//
//            long lengthMI = Math.max(1L, Math.round(d.sizeMb * SimConfig.MI_PER_MB));
//            CTask task = new CTask(id, lengthMI, 1, 300, 300, um, um, um);
//            task.setUserId(brokerId);
//
//            HQDptaraBroker.Meta.put(id, d.arrival, d.deadline, d.porig, d.sizeMb);
//            list.add(task);
//        }
//
//        Log.printLine("Generated tasks: " + list.size());
//        return list;
//    }
//
//    private static double samplePorig() {
//        double x = Dist.uniform(0.0, 1.0);
//        if (x < 0.20) return 0.95; // Pr
//        if (x < 0.80) return 0.60; // Nr
//        return 0.30;               // Nr
//    }
//}

//
//package org.iquantum.examples.hq_dptara;
//
//import java.util.*;
//
//import org.iquantum.core.iQuantum;
//import org.iquantum.datacenters.CDatacenter;
//import org.iquantum.datacenters.CDatacenterCharacteristics;
//
//import org.iquantum.backends.classical.Host;
//import org.iquantum.backends.classical.Pe;
//import org.iquantum.backends.classical.Vm;
//
//import org.iquantum.models.UtilizationModel;
//import org.iquantum.models.UtilizationModelFull;
//
////import org.iquantum.backends.classical.container.schedulers.ContainerCloudletSchedulerTimeShared;
//import org.iquantum.policies.vm.VmAllocationPolicySimple;
//// ✅ ADD this
//import org.iquantum.policies.ctasks.CloudletSchedulerTimeShared;
//
//import org.iquantum.policies.vm.VmSchedulerTimeShared;
//import org.iquantum.provisioners.BwProvisionerSimple;
//import org.iquantum.provisioners.PeProvisionerSimple;
//import org.iquantum.provisioners.RamProvisionerSimple;
//
//import org.iquantum.tasks.CTask;
//import org.iquantum.utils.Log;
//
//import org.iquantum.examples.hq_dptara.broker.HQDptaraBroker;
//import org.iquantum.examples.hq_dptara.broker.Metrics;
//import org.iquantum.examples.hq_dptara.util.Dist;
//import org.iquantum.examples.hq_dptara.util.SimConfig;
//
//public class HQDptaraMain {
//
//    public static void main(String[] args) {
//
//        long seed = 42L; // reproducible
//        Dist.setSeed(seed);
//
//        // ✅ choose ON/OFF here
//        boolean quantumEnabled = true;
//
//        Log.printLine("\n==============================");
//        Log.printLine("HQ_DPTARA | Quantum=" + (quantumEnabled ? "ON" : "OFF") + " | seed=" + seed + " | tasks=" + SimConfig.TOTAL_TASKS);
//        Log.printLine("==============================");
//
//        try {
//            int users = 1;
//            Calendar cal = Calendar.getInstance();
//            boolean trace = false;
//
//            // ✅ IMPORTANT: init simulator
//            iQuantum.init(users, cal, trace);
//
//            // ✅ Datacenters (Edge + Cloud)
//            CDatacenter edgeDc  = createDatacenter("Edge_Datacenter", true);
//            CDatacenter cloudDc = createDatacenter("Cloud_Datacenter", false);
//
//            // ✅ Broker (your constructor needs 2 args)
//            HQDptaraBroker broker = new HQDptaraBroker("HQDPTARA_Broker", quantumEnabled);
//            int brokerId = broker.getId();
//
//            // ✅ VMs
//            List<Vm> vms = new ArrayList<>();
//            vms.addAll(createEdgeVms(brokerId, SimConfig.NUM_EDGE_SERVERS));
//            vms.addAll(createCloudVms(brokerId, SimConfig.NUM_CLOUD_SERVERS, SimConfig.NUM_EDGE_SERVERS));
//            broker.submitVmList(vms);
//
//            // ✅ Tasks + schedule arrivals (event-driven)
//            List<CTask> tasks = generateTasksPoisson(brokerId);
//
//            for (CTask t : tasks) {
//                int tid = t.getCloudletId();
//                double arrivalSec = HQDptaraBroker.Meta.arrival(tid);
//
//                // schedule arrival event at time = arrivalSec
//                iQuantum.send(brokerId, brokerId, arrivalSec, SimConfig.TAG_TASK_ARRIVAL, t);
//            }
//
//            Log.printLine("Starting simulation...");
//            iQuantum.startSimulation();
//            iQuantum.stopSimulation();
//            Log.printLine("Simulation finished.");
//
//            // ✅ Print metrics
////            Metrics m = broker.getMetrics();
////            Log.printLine("Generated: " + m.totalGenerated);
////            Log.printLine("Completed: " + m.completed);
////            Log.printLine("Rejected : " + m.rejected);
////
////            Log.printLine(String.format(Locale.US, "Avg Latency (All) ms: %.3f", m.avgLatencyMs()));
////            Log.printLine(String.format(Locale.US, "Avg Exec Time ms    : %.3f", m.avgExecMs()));
////            Log.printLine(String.format(Locale.US, "Avg Energy J        : %.3f", m.avgEnergyJ()));
////            Log.printLine(String.format(Locale.US, "Utilization %%       : %.2f", m.avgUtilPct()));
////            Log.printLine(String.format(Locale.US, "DMR                 : %.4f", m.dmr()));
//            Log.printLine("Broker finished. Metrics will be printed by Broker (finishExecution).");
//        } catch (Exception e) {
//            e.printStackTrace();
//            Log.printLine("Simulation terminated due to error.");
//        }
//    }
//
//    /* ===================== DATACENTER ===================== */
//    private static CDatacenter createDatacenter(String name, boolean isEdge) {
//
//        List<Host> hostList = new ArrayList<>();
//        int hostCount = isEdge ? SimConfig.NUM_EDGE_SERVERS : SimConfig.NUM_CLOUD_SERVERS;
//
//        for (int h = 0; h < hostCount; h++) {
//
//            List<Pe> peList = new ArrayList<>();
//            int peCount = isEdge ? 4 : 8;
//
//            for (int p = 0; p < peCount; p++) {
//                int mips = isEdge
//                        ? Dist.uniformInt(SimConfig.EDGE_HOST_MIPS_MIN, SimConfig.EDGE_HOST_MIPS_MAX)
//                        : Dist.uniformInt(SimConfig.CLOUD_HOST_MIPS_MIN, SimConfig.CLOUD_HOST_MIPS_MAX);
//
//                peList.add(new Pe(p, new PeProvisionerSimple(mips)));
//            }
//
//            int ram = isEdge ? SimConfig.EDGE_RAM_MB : SimConfig.CLOUD_RAM_MB;
//            long storage = 1_000_000;
//            int bw = 10_000;
//
//            Host host = new Host(
//                    h,
//                    new RamProvisionerSimple(ram),
//                    new BwProvisionerSimple(bw),
//                    storage,
//                    peList,
//                    new VmSchedulerTimeShared(peList)
//            );
//
//            hostList.add(host);
//        }
//
//        CDatacenterCharacteristics characteristics =
//                new CDatacenterCharacteristics(
//                        "x86", "Linux", "Xen",
//                        hostList,
//                        10.0, 3.0, 0.05, 0.001, 0.0
//                );
//
//        try {
//            return new CDatacenter(
//                    name,
//                    characteristics,
//                    new VmAllocationPolicySimple(hostList),
//                    new LinkedList<>(),
//                    0
//            );
//        } catch (Exception e) {
//            throw new RuntimeException(e);
//        }
//    }
//
//    /* ===================== VMs ===================== */
//    private static List<Vm> createEdgeVms(int brokerId, int count) {
//        List<Vm> vms = new ArrayList<>();
//        for (int i = 0; i < count; i++) {
//
//            int mips = Dist.uniformInt(SimConfig.EDGE_HOST_MIPS_MIN, SimConfig.EDGE_HOST_MIPS_MAX);
//            int pes = 1;
//            int ram = SimConfig.EDGE_VM_RAM_MB;
//            long bw = 1000;
//            long size = 10_000;
//            String vmm = "EDGE";
//
//            vms.add(new Vm(
//                    i,
//                    brokerId,
//                    mips,
//                    pes,
//                    ram,
//                    bw,
//                    size,
//                    vmm,
////                    new ContainerCloudletSchedulerTimeShared()
//            new CloudletSchedulerTimeShared()
//            ));
//        }
//        return vms;
//    }
//
//    private static List<Vm> createCloudVms(int brokerId, int count, int idOffset) {
//        List<Vm> vms = new ArrayList<>();
//        for (int i = 0; i < count; i++) {
//
//            int mips = Dist.uniformInt(SimConfig.CLOUD_HOST_MIPS_MIN, SimConfig.CLOUD_HOST_MIPS_MAX);
//            int pes = 1;
//            int ram = SimConfig.CLOUD_VM_RAM_MB;
//            long bw = 1000;
//            long size = 10_000;
//            String vmm = "CLOUD";
//
//            vms.add(new Vm(
//                    idOffset + i,
//                    brokerId,
//                    mips,
//                    pes,
//                    ram,
//                    bw,
//                    size,
//                    vmm,
////                    new ContainerCloudletSchedulerTimeShared()
//                    new CloudletSchedulerTimeShared()
//            ));
//        }
//        return vms;
//    }
//
//    /* ===================== TASKS (Poisson arrivals) ===================== */
//    private static List<CTask> generateTasksPoisson(int brokerId) {
//
//        List<CTask> list = new ArrayList<>();
//        UtilizationModel um = new UtilizationModelFull();
//
//        double time = 0.0;
//
//        for (int tid = 0; tid < SimConfig.TOTAL_TASKS; tid++) {
//
//            double inter = Dist.expInterArrival(SimConfig.LAMBDA_TASKS_PER_SEC);
//            time += inter;
//
//            double sizeMb   = Dist.uniform(SimConfig.TASK_SIZE_MB_MIN, SimConfig.TASK_SIZE_MB_MAX);
//            double deadline = Dist.uniform(SimConfig.DEADLINE_SEC_MIN, SimConfig.DEADLINE_SEC_MAX);
//            double porig    = Dist.uniform(0.2, 1.0);
//
//            long lengthMI = (long) (sizeMb * SimConfig.MI_PER_MB);
//            long fileSizeBytes = (long) (sizeMb * 1024.0 * 1024.0);
//            long outputSizeBytes = fileSizeBytes;
//
//            CTask task = new CTask(
//                    tid,
//                    lengthMI,
//                    1,
//                    fileSizeBytes,
//                    outputSizeBytes,
//                    um, um, um
//            );
//            task.setUserId(brokerId);          // ✅ FIX #1
//            task.setSubmissionTime(time);
//            // store meta for broker
//            HQDptaraBroker.Meta.put(tid, time, deadline, porig, sizeMb);
//
//            list.add(task);
//        }
//
//        return list;
//    }
//}




///////////////////////////////////////////
//
//package org.iquantum.examples.hq_dptara;
//import java.util.*;
//import org.iquantum.core.iQuantum;
//import org.iquantum.datacenters.CDatacenter;
//import org.iquantum.datacenters.CDatacenterCharacteristics;
//import org.iquantum.backends.classical.Host;
//import org.iquantum.backends.classical.Pe;
//import org.iquantum.backends.classical.Vm;
//import org.iquantum.models.UtilizationModel;
//import org.iquantum.models.UtilizationModelFull;
//import org.iquantum.policies.ctasks.CloudletSchedulerTimeShared;
//import org.iquantum.policies.vm.VmAllocationPolicySimple;
//import org.iquantum.policies.vm.VmSchedulerTimeShared;
//import org.iquantum.provisioners.BwProvisionerSimple;
//import org.iquantum.provisioners.PeProvisionerSimple;
//import org.iquantum.provisioners.RamProvisionerSimple;
//import org.iquantum.tasks.CTask;
//import org.iquantum.utils.Log;
//import org.iquantum.examples.hq_dptara.broker.HQDptaraBroker;
//import org.iquantum.examples.hq_dptara.util.Dist;
//import org.iquantum.examples.hq_dptara.util.SimConfig;
//public class HQDptaraMain {
//    public static void main(String[] args) {
//        try {
//            Dist.setSeed(42L);
//            int numUsers = 1;
//            Calendar calendar = Calendar.getInstance();
//            boolean traceFlag = false;
//            iQuantum.init(numUsers, calendar, traceFlag);
//            // ✅ Edge: 10 hosts, 1 PE each (as edge servers)
//            CDatacenter edgeDc = createDatacenter(
//                    "Edge_Datacenter",
//                    SimConfig.NUM_EDGE_SERVERS,
//                    SimConfig.EDGE_HOST_MIPS_MIN,
//                    SimConfig.EDGE_HOST_MIPS_MAX,
//                    SimConfig.EDGE_RAM_MB,
//                    1 // pesPerHost
//            );
//            // ✅ Cloud: 1 host, MULTI-PE to avoid "failed by MIPS" if any fallback happens
//            CDatacenter cloudDc = createDatacenter(
//                    "Cloud_Datacenter",
//                    SimConfig.NUM_CLOUD_SERVERS,
//                    SimConfig.CLOUD_HOST_MIPS_MIN,
//                    SimConfig.CLOUD_HOST_MIPS_MAX,
//                    SimConfig.CLOUD_RAM_MB,
//                    4 // pesPerHost (recommended)
//            );
//            boolean quantumEnabled = true;
//            HQDptaraBroker broker = new HQDptaraBroker("HQDPTARA_Broker", quantumEnabled);
//            int brokerId = broker.getId();
//
//            List<Vm> vmList = new ArrayList<>();
//            // ===================== VMs =====================
//            // ✅ IMPORTANT: VM MIPS must be STABLE (NOT random), otherwise VM allocation fails by MIPS.
//            // Make sure SimConfig has:
////             public static final int EDGE_VM_MIPS = 1000;
////             public static final int CLOUD_VM_MIPS = 4000;
//
//            // Edge VMs (IDs 0..9)
//            for (int i = 0; i < SimConfig.NUM_EDGE_SERVERS; i++) {
//                int mips = SimConfig.EDGE_VM_MIPS;
//                Vm vm = new Vm(
//                        i,
//                        brokerId,
//                        mips,
//                        1, // pes
//                        SimConfig.EDGE_VM_RAM_MB,
//                        1000,
//                        10000,
//                        "EDGE",
//                        new CloudletSchedulerTimeShared()
//                );
//                vmList.add(vm);
//            }
//            // Cloud VMs (ID 10)
//            for (int i = 0; i < SimConfig.NUM_CLOUD_SERVERS; i++) {
//                int vmId = SimConfig.NUM_EDGE_SERVERS + i;
//                int mips = SimConfig.CLOUD_VM_MIPS;
//
//                Vm vm = new Vm(
//                        vmId,
//                        brokerId,
//                        mips,
//                        1, // pes
//                        SimConfig.CLOUD_VM_RAM_MB,
//                        5000,
//                        50000,
//                        "CLOUD",
//                        new CloudletSchedulerTimeShared()
//                );
//                vmList.add(vm);
//            }
//
//            broker.submitVmList(vmList);
//            // ===================== Tasks (Poisson arrivals) =====================
//            List<CTask> tasks = generateTasksGlobalPoisson(brokerId);
//
//            for (CTask t : tasks) {
//                int tid = t.getCloudletId();
//                double arrivalSec = HQDptaraBroker.Meta.arrival(tid);
//
//                // sourceId, destId, delay, tag, data
//                iQuantum.send(brokerId, brokerId, arrivalSec, SimConfig.TAG_TASK_ARRIVAL, t);
//            }
//            // ✅ Broker will stop the simulation after (completed + rejected) == TOTAL_TASKS
//            iQuantum.startSimulation();
//            // ✅ IMPORTANT: Do NOT print a second “FINAL METRICS” block here.
//            // Broker already prints a clean final report (avoid duplicate/confusing output).
//
//        } catch (Exception e) {
//            e.printStackTrace();
//            Log.printLine("Simulation terminated due to error");
//        }
//    }
//
//    private static CDatacenter createDatacenter(
//            String name,
//            int numHosts,
//            int mipsMin,
//            int mipsMax,
//            int ramMb,
//            int pesPerHost
//    ) {
//        List<Host> hostList = new ArrayList<>();
//
//        for (int h = 0; h < numHosts; h++) {
//            List<Pe> peList = new ArrayList<>();
//
//            // ✅ Use uniform host MIPS in the given range (matches thesis table)
//            int mips = Dist.uniformInt(mipsMin, mipsMax);
//
//            for (int p = 0; p < pesPerHost; p++) {
//                peList.add(new Pe(p, new PeProvisionerSimple(mips)));
//            }
//
//            long storage = 1_000_000;
//            int bw = 10_000;
//
//            Host host = new Host(
//                    h,
//                    new RamProvisionerSimple(ramMb),
//                    new BwProvisionerSimple(bw),
//                    storage,
//                    peList,
//                    new VmSchedulerTimeShared(peList)
//            );
//
//            hostList.add(host);
//        }
//
//        CDatacenterCharacteristics characteristics =
//                new CDatacenterCharacteristics(
//                        "x86",
//                        "Linux",
//                        "Xen",
//                        hostList,
//                        10.0,
//                        3.0,
//                        0.05,
//                        0.001,
//                        0.0
//                );
//
//        try {
//            return new CDatacenter(
//                    name,
//                    characteristics,
//                    new VmAllocationPolicySimple(hostList),
//                    new LinkedList<>(),
//                    0
//            );
//        } catch (Exception e) {
//            throw new RuntimeException(e);
//        }
//    }
//
//    private static List<CTask> generateTasksGlobalPoisson(int brokerId) {
//        UtilizationModel um = new UtilizationModelFull();
//
//        List<CTask> list = new ArrayList<>();
//        double time = 0.0;
//
//        for (int id = 0; id < SimConfig.TOTAL_TASKS; id++) {
//            time += Dist.expInterArrival(SimConfig.LAMBDA_TASKS_PER_SEC);
//
//            double sizeMb   = Dist.uniform(SimConfig.TASK_SIZE_MB_MIN, SimConfig.TASK_SIZE_MB_MAX);
//            double deadline = Dist.uniform(SimConfig.DEADLINE_SEC_MIN, SimConfig.DEADLINE_SEC_MAX);
//            double porig    = Dist.uniform(0.2, 1.0);
//
//            long lengthMI = Math.max(1L, Math.round(sizeMb * SimConfig.MI_PER_MB));
//
//            CTask task = new CTask(
//                    id,
//                    lengthMI,
//                    1,
//                    300,
//                    300,
//                    um, um, um
//            );
//            task.setUserId(brokerId);
//
//            HQDptaraBroker.Meta.put(id, time, deadline, porig, sizeMb);
//            list.add(task);
//        }
//
//        Log.printLine("Generated tasks: " + list.size() + " (Poisson λ=" + SimConfig.LAMBDA_TASKS_PER_SEC + ")");
//        return list;
//    }
//}
///////////////////////////////////////////////////// final codeeeeeeeeeeeeeeeeee


package org.iquantum.examples.hq_dptara;
import java.util.*;
import org.iquantum.core.iQuantum;
import org.iquantum.datacenters.CDatacenter;
import org.iquantum.datacenters.CDatacenterCharacteristics;
import org.iquantum.backends.classical.Host;
import org.iquantum.backends.classical.Pe;
import org.iquantum.backends.classical.Vm;
import org.iquantum.models.UtilizationModel;
import org.iquantum.models.UtilizationModelFull;
import org.iquantum.policies.ctasks.CloudletSchedulerTimeShared;
import org.iquantum.policies.vm.VmAllocationPolicySimple;
import org.iquantum.policies.vm.VmSchedulerTimeShared;
import org.iquantum.provisioners.BwProvisionerSimple;
import org.iquantum.provisioners.PeProvisionerSimple;
import org.iquantum.provisioners.RamProvisionerSimple;
import org.iquantum.tasks.CTask;
import org.iquantum.utils.Log;
import org.iquantum.examples.hq_dptara.broker.HQDptaraBroker;
import org.iquantum.examples.hq_dptara.util.Dist;
import org.iquantum.examples.hq_dptara.util.SimConfig;

public class HQDptaraMain {

    public static void main(String[] args) {
        try {
            // Keep fixed seed for reproducible & comparable runs
            Dist.setSeed(42L);

            iQuantum.init(1, Calendar.getInstance(), false);

            CDatacenter edgeDc = createDatacenter(
                    "Edge_Datacenter",
                    SimConfig.NUM_EDGE_SERVERS,
                    SimConfig.EDGE_HOST_MIPS_MIN,
                    SimConfig.EDGE_HOST_MIPS_MAX,
                    SimConfig.EDGE_RAM_MB,
                    1
            );

            CDatacenter cloudDc = createDatacenter(
                    "Cloud_Datacenter",
                    SimConfig.NUM_CLOUD_SERVERS,
                    SimConfig.CLOUD_HOST_MIPS_MIN,
                    SimConfig.CLOUD_HOST_MIPS_MAX,
                    SimConfig.CLOUD_RAM_MB,
                    4
            );

            HQDptaraBroker broker = new HQDptaraBroker("HQDPTARA_Broker");
            int brokerId = broker.getId();

            List<Vm> vmList = new ArrayList<>();

            // Edge VMs (0..9)
            for (int i = 0; i < SimConfig.NUM_EDGE_SERVERS; i++) {
                Vm vm = new Vm(
                        i, brokerId,
                        SimConfig.EDGE_VM_MIPS,
                        1,
                        SimConfig.EDGE_VM_RAM_MB,
                        1000, 10000,
                        "EDGE",
                        new CloudletSchedulerTimeShared()
                );
                vmList.add(vm);
            }

            // Cloud VM (10)
            int cloudVmId = SimConfig.NUM_EDGE_SERVERS;
            Vm cloudVm = new Vm(
                    cloudVmId, brokerId,
                    SimConfig.CLOUD_VM_MIPS,
                    1,
                    SimConfig.CLOUD_VM_RAM_MB,
                    5000, 50000,
                    "CLOUD",
                    new CloudletSchedulerTimeShared()
            );
            vmList.add(cloudVm);

            broker.submitVmList(vmList);

            // Tasks (Poisson) + store common random values in Meta
            List<CTask> tasks = generateTasksGlobalPoisson(brokerId);

            for (CTask t : tasks) {
                int tid = t.getCloudletId();
                double arrivalSec = HQDptaraBroker.Meta.arrival(tid);
                iQuantum.send(brokerId, brokerId, arrivalSec, SimConfig.TAG_TASK_ARRIVAL, t);
            }

            iQuantum.startSimulation();

        } catch (Exception e) {
            e.printStackTrace();
            Log.printLine("Simulation terminated due to error");
        }
    }

    private static CDatacenter createDatacenter(
            String name, int numHosts, int mipsMin, int mipsMax, int ramMb, int pesPerHost
    ) {
        List<Host> hostList = new ArrayList<>();

        for (int h = 0; h < numHosts; h++) {
            List<Pe> peList = new ArrayList<>();
            int mips = Dist.uniformInt(mipsMin, mipsMax);

            for (int p = 0; p < pesPerHost; p++) {
                peList.add(new Pe(p, new PeProvisionerSimple(mips)));
            }

            Host host = new Host(
                    h,
                    new RamProvisionerSimple(ramMb),
                    new BwProvisionerSimple(10_000),
                    1_000_000,
                    peList,
                    new VmSchedulerTimeShared(peList)
            );

            hostList.add(host);
        }

        CDatacenterCharacteristics characteristics =
                new CDatacenterCharacteristics(
                        "x86", "Linux", "Xen",
                        hostList,
                        10.0, 3.0, 0.05, 0.001, 0.0
                );

        try {
            return new CDatacenter(
                    name,
                    characteristics,
                    new VmAllocationPolicySimple(hostList),
                    new LinkedList<>(),
                    0
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static List<CTask> generateTasksGlobalPoisson(int brokerId) {
        UtilizationModel um = new UtilizationModelFull();
        List<CTask> list = new ArrayList<>();
        double time = 0.0;

        for (int id = 0; id < SimConfig.TOTAL_TASKS; id++) {
            time += Dist.expInterArrival(SimConfig.LAMBDA_TASKS_PER_SEC);

            double sizeMb   = Dist.uniform(SimConfig.TASK_SIZE_MB_MIN, SimConfig.TASK_SIZE_MB_MAX);
            double deadline = Dist.uniform(SimConfig.DEADLINE_SEC_MIN, SimConfig.DEADLINE_SEC_MAX);

            // Priority distribution
            double porig = Dist.uniform(0.0, 1.0);

            // ✅ Pre-sample network ONCE per task (common random numbers)
            double edgeNetMs  = Dist.uniform(SimConfig.EDGE_NET_MS_MIN, SimConfig.EDGE_NET_MS_MAX);
            double cloudNetMs = Dist.uniform(SimConfig.CLOUD_NET_MS_MIN, SimConfig.CLOUD_NET_MS_MAX);

            long lengthMI = Math.max(1L, Math.round(sizeMb * SimConfig.MI_PER_MB));

            CTask task = new CTask(id, lengthMI, 1, 300, 300, um, um, um);
            task.setUserId(brokerId);

            HQDptaraBroker.Meta.put(id, time, deadline, porig, sizeMb, edgeNetMs, cloudNetMs);
            list.add(task);
        }

        Log.printLine("Generated tasks: " + list.size() +
                " (Poisson λ=" + SimConfig.LAMBDA_TASKS_PER_SEC + ")");
        return list;
    }
}
