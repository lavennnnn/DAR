package cn.hush.dar.resource.controller;


import cn.hush.dar.common.result.Result;
import cn.hush.dar.common.web.Results;
import cn.hush.dar.resource.dao.entity.AntennaResource;
import cn.hush.dar.resource.dao.entity.CPUResource;
import cn.hush.dar.resource.dao.entity.GPUResource;
import cn.hush.dar.resource.service.ResourceService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @program: DAR
 * @description:
 * @author: Hush
 * @create: 2026-01-05 22:13
 **/

@RestController
@RequestMapping("/api/resource")
@Tag(name = "资源相关功能")
@RequiredArgsConstructor
public class ResourceController {

    private final ResourceService resourceService;

    /**
     * 一键初始化所有模拟数据 (Antenna, CPU, GPU)
     */
    @PostMapping("/init")
    public Result<Void> initMockData() {
        resourceService.initMockData();
        return Results.success();
    }


    // 3. 一键重置 (演示出错时很有用)
    @PostMapping("/reset")
    public Result<Void> reset() {
        resourceService.resetAllResources();
        return Results.success();
    }

    // --- 查询接口 ---

    @GetMapping("/antenna/list")
    public Result<List<AntennaResource>> getAntennaList() {
        return Results.success(resourceService.getAllAntennas());
    }

    @GetMapping("/cpu/list")
    public Result<List<CPUResource>> getCPUList() {
        return Results.success(resourceService.getAllCPUs());
    }

    @GetMapping("/gpu/list")
    public Result<List<GPUResource>> getGPUList() {
        return Results.success(resourceService.getAllGPUs());
    }

    // --- 新增：分配测试接口 ---

    @PostMapping("/allocate")
    public Result<Boolean> allocate(@RequestBody AllocateRequest request) {
        boolean success = resourceService.allocateAntennas(request.getAntennaIds(), request.getTaskId());
        if (success) {
            return Results.success(true);
        } else {
            return Results.failure("RESOURCE_BUSY", "资源已被占用或不足");
        }
    }

    @PostMapping("/release")
    public Result<Void> release(@RequestParam Integer taskId) {
        resourceService.releaseResourcesByTask(taskId);
        return Results.success();
    }

    // 内部 DTO 类，用于接收分配请求
    @Data
    public static class AllocateRequest {
        private List<Integer> antennaIds;
        private Integer taskId;
    }
}

