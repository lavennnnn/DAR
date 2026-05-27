package cn.hush.dar.scheduler.dto;

import lombok.Data;

@Data
public class BenchmarkRequest {
    private Integer neededAntennas;
    private Integer neededCpuCores;
    private Integer neededGpuMem;
    private Integer priority;
    private Integer deadlineMs;
    private Boolean allowCrossSurface;
    private String preferredSurface;
    private Integer targetReuseLimit;
    private Double beamFrequency;
    private String beamGroup;
    private String forceAlgorithm;    // BFS / DIJKSTRA / GREEDY / HEAP / DP
    private String forceComputeMode;  // PACKED / BALANCE
}
