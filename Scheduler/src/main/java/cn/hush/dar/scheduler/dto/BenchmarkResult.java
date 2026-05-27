package cn.hush.dar.scheduler.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BenchmarkResult {
    private String algorithm;
    private Long executionTimeNs;
    private Double score;
    private Boolean feasible;
    private List<Integer> antennaIds;
    private Integer antennaCount;
    private String surfaceCode;
    private Map<String, Integer> surfaceDistribution;
    private Double averageReuse;

    private String computeMode;
    private Double cpuVariance;
    private Double gpuVariance;
    private Map<Integer, Integer> cpuAllocations;
    private Integer gpuId;
}
