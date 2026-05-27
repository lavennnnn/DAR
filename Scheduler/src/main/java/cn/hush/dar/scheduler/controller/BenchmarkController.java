package cn.hush.dar.scheduler.controller;

import cn.hush.dar.common.result.Result;
import cn.hush.dar.common.web.Results;
import cn.hush.dar.resource.dao.entity.AntennaResource;
import cn.hush.dar.resource.service.ResourceService;
import cn.hush.dar.scheduler.dto.BenchmarkRequest;
import cn.hush.dar.scheduler.dto.BenchmarkResult;
import cn.hush.dar.scheduler.dto.CompareBenchmarkResult;
import cn.hush.dar.scheduler.service.AntennaSchedulingService;
import cn.hush.dar.scheduler.service.ComputeSchedulingService;
import cn.hush.dar.task.dao.entity.TaskEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/benchmark")
@RequiredArgsConstructor
public class BenchmarkController {

    private final AntennaSchedulingService antennaSchedulingService;
    private final ComputeSchedulingService computeSchedulingService;
    private final ResourceService resourceService;

    private static final List<String> ANTENNA_ALGORITHMS = List.of("BFS", "DIJKSTRA", "GREEDY", "HEAP", "DP");
    private static final List<String> COMPUTE_MODES = List.of("PACKED", "BALANCE");

    @PostMapping("/antenna")
    public Result<BenchmarkResult> benchmarkAntenna(@RequestBody BenchmarkRequest request) {
        TaskEntity task = toTaskEntity(request);
        String algorithm = request.getForceAlgorithm();
        if (algorithm == null || algorithm.isBlank()) algorithm = "BFS";

        long startNs = System.nanoTime();
        var result = antennaSchedulingService.benchmarkPlan(task, algorithm);
        long elapsedNs = System.nanoTime() - startNs;

        return Results.success(toAntennaResult(result, algorithm, elapsedNs));
    }

    @PostMapping("/antenna/compare")
    public Result<CompareBenchmarkResult> compareAntenna(@RequestBody BenchmarkRequest request) {
        TaskEntity task = toTaskEntity(request);
        List<BenchmarkResult> results = new ArrayList<>();

        for (String algo : ANTENNA_ALGORITHMS) {
            long startNs = System.nanoTime();
            var result = antennaSchedulingService.benchmarkPlan(task, algo);
            long elapsedNs = System.nanoTime() - startNs;
            results.add(toAntennaResult(result, algo, elapsedNs));
        }

        return Results.success(CompareBenchmarkResult.builder()
                .antennaResults(results)
                .request(request)
                .build());
    }

    @PostMapping("/compute/compare")
    public Result<CompareBenchmarkResult> compareCompute(@RequestBody BenchmarkRequest request) {
        TaskEntity task = toTaskEntity(request);
        List<BenchmarkResult> results = new ArrayList<>();

        for (String mode : COMPUTE_MODES) {
            long startNs = System.nanoTime();
            var plan = computeSchedulingService.benchmarkPlan(task, mode);
            long elapsedNs = System.nanoTime() - startNs;
            results.add(toComputeResult(plan, mode, elapsedNs));
        }

        return Results.success(CompareBenchmarkResult.builder()
                .computeResults(results)
                .request(request)
                .build());
    }

    @PostMapping("/full")
    public Result<CompareBenchmarkResult> fullBenchmark(@RequestBody BenchmarkRequest request) {
        TaskEntity task = toTaskEntity(request);

        List<BenchmarkResult> antennaResults = new ArrayList<>();
        for (String algo : ANTENNA_ALGORITHMS) {
            long startNs = System.nanoTime();
            var result = antennaSchedulingService.benchmarkPlan(task, algo);
            long elapsedNs = System.nanoTime() - startNs;
            antennaResults.add(toAntennaResult(result, algo, elapsedNs));
        }

        List<BenchmarkResult> computeResults = new ArrayList<>();
        for (String mode : COMPUTE_MODES) {
            long startNs = System.nanoTime();
            var plan = computeSchedulingService.benchmarkPlan(task, mode);
            long elapsedNs = System.nanoTime() - startNs;
            computeResults.add(toComputeResult(plan, mode, elapsedNs));
        }

        return Results.success(CompareBenchmarkResult.builder()
                .antennaResults(antennaResults)
                .computeResults(computeResults)
                .request(request)
                .build());
    }

    private TaskEntity toTaskEntity(BenchmarkRequest req) {
        return TaskEntity.builder()
                .neededAntennas(req.getNeededAntennas() != null ? req.getNeededAntennas() : 4)
                .neededCpuCores(req.getNeededCpuCores() != null ? req.getNeededCpuCores() : 0)
                .neededGpuMem(req.getNeededGpuMem() != null ? req.getNeededGpuMem() : 0)
                .priority(req.getPriority() != null ? req.getPriority() : 50)
                .deadlineMs(req.getDeadlineMs())
                .allowCrossSurface(req.getAllowCrossSurface() != null ? req.getAllowCrossSurface() : true)
                .preferredSurface(req.getPreferredSurface())
                .targetReuseLimit(req.getTargetReuseLimit() != null ? req.getTargetReuseLimit() : 3)
                .beamFrequency(req.getBeamFrequency())
                .beamGroup(req.getBeamGroup())
                .build();
    }

    private BenchmarkResult toAntennaResult(AntennaSchedulingService.SelectionResult result,
                                            String algorithm, long elapsedNs) {
        List<Integer> ids = result.getAntennaIds();
        int required = ids != null ? ids.size() : 0;
        boolean feasible = ids != null && !ids.isEmpty();

        Map<String, Integer> surfaceDist = new HashMap<>();
        double avgReuse = 0.0;

        if (feasible) {
            List<AntennaResource> allAntennas = resourceService.getAllAntennas();
            Map<Integer, AntennaResource> antennaMap = allAntennas.stream()
                    .collect(Collectors.toMap(AntennaResource::getId, a -> a, (a, b) -> a));

            for (Integer id : ids) {
                AntennaResource ant = antennaMap.get(id);
                if (ant != null) {
                    String surface = ant.getSurfaceCode() != null ? ant.getSurfaceCode() : "UNKNOWN";
                    surfaceDist.merge(surface, 1, Integer::sum);
                    avgReuse += (ant.getReuseCount() != null ? ant.getReuseCount() : 0);
                }
            }
            if (!ids.isEmpty()) avgReuse /= ids.size();
        }

        return BenchmarkResult.builder()
                .algorithm(algorithm)
                .executionTimeNs(elapsedNs)
                .score(result.getScore() == Double.MAX_VALUE ? null : result.getScore())
                .feasible(feasible)
                .antennaIds(ids)
                .antennaCount(required)
                .surfaceCode(result.getSurfaceCode())
                .surfaceDistribution(surfaceDist)
                .averageReuse(avgReuse)
                .build();
    }

    private BenchmarkResult toComputeResult(ComputeSchedulingService.ComputePlan plan,
                                            String mode, long elapsedNs) {
        return BenchmarkResult.builder()
                .computeMode(mode)
                .executionTimeNs(elapsedNs)
                .feasible(plan.isFeasible())
                .cpuVariance(plan.getCpuScore())
                .gpuVariance(plan.getGpuScore())
                .cpuAllocations(plan.getCpuAllocations())
                .gpuId(plan.getGpuId())
                .build();
    }
}
