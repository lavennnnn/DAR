package cn.hush.dar.scheduler.service;

import cn.hush.dar.resource.dao.entity.CPUResource;
import cn.hush.dar.resource.dao.entity.GPUResource;
import cn.hush.dar.resource.service.ResourceService;
import cn.hush.dar.task.dao.entity.TaskEntity;
import cn.hush.dar.task.service.TaskService;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ComputeSchedulingService {

    private static final double NEW_NODE_PENALTY = 0.02;
    private static final double PACKED_NODE_BONUS = 0.015;
    private static final String MODE_BALANCE = "BALANCE";
    private static final String MODE_PACKED = "PACKED";

    private final TaskService taskService;
    private final ResourceService resourceService;

    public ComputePlan plan(TaskEntity task) {
        List<TaskEntity> allTasks = taskService.list();
        List<CPUResource> cpus = resourceService.getAllCPUs();
        List<GPUResource> gpus = resourceService.getAllGPUs();
        return plan(task, allTasks, cpus, gpus);
    }

    public ComputePlan plan(TaskEntity task,
                            List<TaskEntity> allTasks,
                            List<CPUResource> cpus,
                            List<GPUResource> gpus) {
        String mode = resolveMode(task);
        Map<Integer, TaskEntity> taskMap = allTasks == null ? Collections.emptyMap() : allTasks.stream()
                .filter(Objects::nonNull)
                .filter(t -> t.getId() != null)
                .collect(Collectors.toMap(TaskEntity::getId, t -> t, (a, b) -> a));

        Set<Integer> dependencyIds = parseTaskIds(task.getDependsOnTaskIds());
        List<Integer> blockedDependencies = dependencyIds.stream()
                .filter(id -> {
                    TaskEntity dep = taskMap.get(id);
                    return dep == null || dep.getStatus() == null || dep.getStatus() != 2;
                })
                .sorted()
                .toList();
        if (!blockedDependencies.isEmpty()) {
            return infeasible("dependency blocked: " + blockedDependencies, mode);
        }

        Set<Integer> repelIds = parseTaskIds(task.getRepelTaskIds());
        List<Integer> runningConflicts = repelIds.stream()
                .filter(id -> {
                    TaskEntity conflict = taskMap.get(id);
                    return conflict != null && conflict.getStatus() != null && conflict.getStatus() == 1;
                })
                .sorted()
                .toList();
        if (!runningConflicts.isEmpty()) {
            return infeasible("repel conflict: " + runningConflicts, mode);
        }

        LinkedHashMap<Integer, Integer> cpuPlan = planCpu(task, cpus, mode);
        int neededCpu = task.getNeededCpuCores() == null ? 0 : task.getNeededCpuCores();
        int plannedCpu = cpuPlan.values().stream().mapToInt(Integer::intValue).sum();
        if (plannedCpu < neededCpu) {
            return infeasible("cpu insufficient", mode);
        }

        Integer gpuId = planGpu(task, gpus, mode);
        int neededGpu = task.getNeededGpuMem() == null ? 0 : task.getNeededGpuMem();
        if (neededGpu > 0 && gpuId == null) {
            return infeasible("gpu insufficient", mode);
        }

        double cpuScore = computeCpuVariance(cpus, cpuPlan, null, 0);
        double gpuScore = computeGpuVariance(gpus, gpuId, neededGpu);
        String detail = String.format("mode=%s,cpuPlan=%s,gpu=%s,depends=%s,repel=%s",
                mode,
                formatCpuPlan(cpuPlan),
                gpuId == null ? "-" : gpuId,
                dependencyIds.isEmpty() ? "-" : dependencyIds,
                repelIds.isEmpty() ? "-" : repelIds);

        return ComputePlan.builder()
                .feasible(true)
                .mode(mode)
                .cpuAllocations(cpuPlan)
                .gpuId(gpuId)
                .gpuMem(neededGpu)
                .cpuScore(cpuScore)
                .gpuScore(gpuScore)
                .detail(detail)
                .build();
    }

    public String formatCpuPlan(Map<Integer, Integer> cpuPlan) {
        if (cpuPlan == null || cpuPlan.isEmpty()) return "-";
        return cpuPlan.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> "CPU-" + entry.getKey() + ":" + entry.getValue())
                .collect(Collectors.joining(","));
    }

    private ComputePlan infeasible(String reason, String mode) {
        return ComputePlan.builder()
                .feasible(false)
                .mode(mode)
                .cpuAllocations(new LinkedHashMap<>())
                .detail(reason)
                .build();
    }

    private LinkedHashMap<Integer, Integer> planCpu(TaskEntity task, List<CPUResource> cpus, String mode) {
        int need = task.getNeededCpuCores() == null ? 0 : task.getNeededCpuCores();
        LinkedHashMap<Integer, Integer> plan = new LinkedHashMap<>();
        if (need <= 0) return plan;

        List<CPUResource> candidates = cpus == null ? new ArrayList<>() : cpus.stream()
                .filter(Objects::nonNull)
                .filter(cpu -> cpu.getId() != null)
                .filter(cpu -> cpu.getStatus() == null || cpu.getStatus() != 2)
                .filter(cpu -> Math.max(0, safeInt(cpu.getTotalCores()) - safeInt(cpu.getUsedCores())) > 0)
                .toList();

        int totalFree = candidates.stream()
                .mapToInt(cpu -> Math.max(0, safeInt(cpu.getTotalCores()) - safeInt(cpu.getUsedCores())))
                .sum();
        if (totalFree < need) return plan;

        if (MODE_BALANCE.equals(mode)) {
            return planCpuByBalance(need, candidates);
        }

        for (int i = 0; i < need; i++) {
            CPUResource best = null;
            double bestScore = Double.MAX_VALUE;
            for (CPUResource cpu : candidates) {
                int allocated = plan.getOrDefault(cpu.getId(), 0);
                int free = Math.max(0, safeInt(cpu.getTotalCores()) - safeInt(cpu.getUsedCores()) - allocated);
                if (free <= 0) continue;
                double score = computeCpuVariance(candidates, plan, cpu.getId(), 1);
                if (!plan.containsKey(cpu.getId())) {
                    score += MODE_BALANCE.equals(mode) ? NEW_NODE_PENALTY : -PACKED_NODE_BONUS;
                }
                if (MODE_PACKED.equals(mode)) {
                    score -= safeLoad(cpu, allocated) * 0.01;
                } else {
                    score += safeLoad(cpu, allocated) * 0.01;
                }
                if (score < bestScore) {
                    bestScore = score;
                    best = cpu;
                }
            }
            if (best == null) return new LinkedHashMap<>();
            plan.merge(best.getId(), 1, Integer::sum);
        }
        return plan;
    }

    private LinkedHashMap<Integer, Integer> planCpuByBalance(int need, List<CPUResource> candidates) {
        LinkedHashMap<Integer, Integer> plan = new LinkedHashMap<>();
        for (int i = 0; i < need; i++) {
            CPUResource best = candidates.stream()
                    .filter(cpu -> {
                        int allocated = plan.getOrDefault(cpu.getId(), 0);
                        return Math.max(0, safeInt(cpu.getTotalCores()) - safeInt(cpu.getUsedCores()) - allocated) > 0;
                    })
                    .min(Comparator
                            .comparingDouble((CPUResource cpu) -> projectedLoadAfter(cpu, plan.getOrDefault(cpu.getId(), 0), 1))
                            .thenComparing((CPUResource cpu) -> -Math.max(0, safeInt(cpu.getTotalCores()) - safeInt(cpu.getUsedCores()) - plan.getOrDefault(cpu.getId(), 0)))
                            .thenComparing(CPUResource::getId))
                    .orElse(null);
            if (best == null) return new LinkedHashMap<>();
            plan.merge(best.getId(), 1, Integer::sum);
        }
        return plan;
    }

    private double projectedLoadAfter(CPUResource cpu, int allocated, int extraCores) {
        int total = Math.max(1, safeInt(cpu.getTotalCores()));
        return (safeInt(cpu.getUsedCores()) + allocated + extraCores) / (double) total;
    }

    private Integer planGpu(TaskEntity task, List<GPUResource> gpus, String mode) {
        int need = task.getNeededGpuMem() == null ? 0 : task.getNeededGpuMem();
        if (need <= 0) return null;

        return (gpus == null ? List.<GPUResource>of() : gpus).stream()
                .filter(Objects::nonNull)
                .filter(gpu -> gpu.getId() != null)
                .filter(gpu -> gpu.getStatus() == null || gpu.getStatus() != 2)
                .filter(gpu -> Math.max(0, safeInt(gpu.getTotalMemory()) - safeInt(gpu.getUsedMemory())) >= need)
                .min(Comparator
                        .comparingDouble((GPUResource gpu) -> {
                            double score = computeGpuVariance(gpus, gpu.getId(), need);
                            int waste = Math.max(0, safeInt(gpu.getTotalMemory()) - safeInt(gpu.getUsedMemory()) - need);
                            score += waste * 0.001;
                            if (MODE_PACKED.equals(mode)) {
                                score -= safeLoad(gpu, 0) * 0.02;
                            }
                            return score;
                        })
                        .thenComparing(GPUResource::getId))
                .map(GPUResource::getId)
                .orElse(null);
    }

    private double computeCpuVariance(List<CPUResource> cpus,
                                      Map<Integer, Integer> plan,
                                      Integer extraCpuId,
                                      int extraCores) {
        List<Double> loads = new ArrayList<>();
        for (CPUResource cpu : cpus) {
            int total = Math.max(1, safeInt(cpu.getTotalCores()));
            int projected = safeInt(cpu.getUsedCores()) + plan.getOrDefault(cpu.getId(), 0);
            if (Objects.equals(cpu.getId(), extraCpuId)) {
                projected += extraCores;
            }
            loads.add(projected / (double) total);
        }
        return variance(loads);
    }

    private double computeGpuVariance(List<GPUResource> gpus, Integer selectedGpuId, int mem) {
        List<Double> loads = new ArrayList<>();
        for (GPUResource gpu : gpus == null ? List.<GPUResource>of() : gpus) {
            if (gpu == null || gpu.getId() == null || (gpu.getStatus() != null && gpu.getStatus() == 2)) continue;
            int total = Math.max(1, safeInt(gpu.getTotalMemory()));
            int projected = safeInt(gpu.getUsedMemory());
            if (Objects.equals(gpu.getId(), selectedGpuId)) {
                projected += mem;
            }
            loads.add(projected / (double) total);
        }
        return variance(loads);
    }

    private double variance(List<Double> values) {
        if (values == null || values.isEmpty()) return 0.0;
        double avg = values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        return values.stream()
                .mapToDouble(value -> Math.pow(value - avg, 2))
                .average()
                .orElse(0.0);
    }

    private double safeLoad(CPUResource cpu, int allocated) {
        int total = Math.max(1, safeInt(cpu.getTotalCores()));
        return (safeInt(cpu.getUsedCores()) + allocated) / (double) total;
    }

    private double safeLoad(GPUResource gpu, int allocated) {
        int total = Math.max(1, safeInt(gpu.getTotalMemory()));
        return (safeInt(gpu.getUsedMemory()) + allocated) / (double) total;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private Set<Integer> parseTaskIds(String raw) {
        if (raw == null || raw.isBlank()) return Collections.emptySet();
        return List.of(raw.split("[,，\\s]+"))
                .stream()
                .map(String::trim)
                .filter(token -> !token.isEmpty())
                .map(token -> {
                    try {
                        return Integer.valueOf(token);
                    } catch (NumberFormatException ex) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private String resolveMode(TaskEntity task) {
        int neededGpu = task.getNeededGpuMem() == null ? 0 : task.getNeededGpuMem();
        int neededCpu = task.getNeededCpuCores() == null ? 0 : task.getNeededCpuCores();
        int priority = task.getPriority() == null ? 0 : task.getPriority();
        Integer deadlineMs = task.getDeadlineMs();

        if (neededGpu > 0) return MODE_PACKED;
        if (deadlineMs != null && deadlineMs > 0 && deadlineMs <= 100 && neededCpu <= 16) return MODE_PACKED;
        if (priority >= 80 && neededCpu <= 16) return MODE_PACKED;
        return MODE_BALANCE;
    }

    /**
     * Benchmark: force PACKED or BALANCE mode, skip dependency/repel checks.
     * Read-only operation - does not allocate resources.
     */
    public ComputePlan benchmarkPlan(TaskEntity task, String forceMode) {
        List<CPUResource> cpus = resourceService.getAllCPUs();
        List<GPUResource> gpus = resourceService.getAllGPUs();

        String mode = MODE_PACKED.equalsIgnoreCase(forceMode) ? MODE_PACKED : MODE_BALANCE;

        LinkedHashMap<Integer, Integer> cpuPlan = planCpu(task, cpus, mode);
        int neededCpu = task.getNeededCpuCores() == null ? 0 : task.getNeededCpuCores();
        int plannedCpu = cpuPlan.values().stream().mapToInt(Integer::intValue).sum();
        if (plannedCpu < neededCpu) {
            return infeasible("cpu insufficient for benchmark", mode);
        }

        Integer gpuId = planGpu(task, gpus, mode);
        int neededGpu = task.getNeededGpuMem() == null ? 0 : task.getNeededGpuMem();
        if (neededGpu > 0 && gpuId == null) {
            return infeasible("gpu insufficient for benchmark", mode);
        }

        double cpuScore = computeCpuVariance(cpus, cpuPlan, null, 0);
        double gpuScore = computeGpuVariance(gpus, gpuId, neededGpu);

        return ComputePlan.builder()
                .feasible(true)
                .mode(mode)
                .cpuAllocations(cpuPlan)
                .gpuId(gpuId)
                .gpuMem(neededGpu)
                .cpuScore(cpuScore)
                .gpuScore(gpuScore)
                .detail("benchmark mode=" + mode)
                .build();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComputePlan {
        private boolean feasible;
        private String mode;
        @Builder.Default
        private LinkedHashMap<Integer, Integer> cpuAllocations = new LinkedHashMap<>();
        private Integer gpuId;
        private Integer gpuMem;
        private Double cpuScore;
        private Double gpuScore;
        private String detail;
    }
}
