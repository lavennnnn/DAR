package cn.hush.dar.scheduler.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompareBenchmarkResult {
    private List<BenchmarkResult> antennaResults;
    private List<BenchmarkResult> computeResults;
    private BenchmarkRequest request;
}
