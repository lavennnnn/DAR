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

    // --- 资源管理：天线 ---
    @PostMapping("/antenna")
    public Result<AntennaResource> createAntenna(@RequestBody AntennaResource antenna) {
        return Results.success(resourceService.createAntenna(antenna));
    }

    @PutMapping("/antenna/{id}")
    public Result<Boolean> updateAntenna(@PathVariable Integer id, @RequestBody AntennaResource antenna) {
        return Results.success(resourceService.updateAntenna(id, antenna));
    }

    @DeleteMapping("/antenna/{id}")
    public Result<Boolean> deleteAntenna(@PathVariable Integer id) {
        return Results.success(resourceService.deleteAntenna(id));
    }

    @PatchMapping("/antenna/{id}/status")
    public Result<Boolean> updateAntennaStatus(@PathVariable Integer id, @RequestParam Integer status) {
        return Results.success(resourceService.updateAntennaStatus(id, status));
    }

    @PostMapping("/antenna/batch")
    public Result<Integer> batchCreateAntenna(@RequestBody List<AntennaResource> antennas) {
        return Results.success(resourceService.batchCreateAntennas(antennas));
    }

    // --- 资源管理：CPU ---
    @PostMapping("/cpu")
    public Result<CPUResource> createCpu(@RequestBody CPUResource cpu) {
        return Results.success(resourceService.createCpu(cpu));
    }

    @PutMapping("/cpu/{id}")
    public Result<Boolean> updateCpu(@PathVariable Integer id, @RequestBody CPUResource cpu) {
        return Results.success(resourceService.updateCpu(id, cpu));
    }

    @DeleteMapping("/cpu/{id}")
    public Result<Boolean> deleteCpu(@PathVariable Integer id) {
        return Results.success(resourceService.deleteCpu(id));
    }

    @PatchMapping("/cpu/{id}/status")
    public Result<Boolean> updateCpuStatus(@PathVariable Integer id, @RequestParam Integer status) {
        return Results.success(resourceService.updateCpuStatus(id, status));
    }

    @PostMapping("/cpu/batch")
    public Result<Integer> batchCreateCpu(@RequestBody List<CPUResource> cpus) {
        return Results.success(resourceService.batchCreateCpus(cpus));
    }

    // --- 资源管理：GPU ---
    @PostMapping("/gpu")
    public Result<GPUResource> createGpu(@RequestBody GPUResource gpu) {
        return Results.success(resourceService.createGpu(gpu));
    }

    @PutMapping("/gpu/{id}")
    public Result<Boolean> updateGpu(@PathVariable Integer id, @RequestBody GPUResource gpu) {
        return Results.success(resourceService.updateGpu(id, gpu));
    }

    @DeleteMapping("/gpu/{id}")
    public Result<Boolean> deleteGpu(@PathVariable Integer id) {
        return Results.success(resourceService.deleteGpu(id));
    }

    @PatchMapping("/gpu/{id}/status")
    public Result<Boolean> updateGpuStatus(@PathVariable Integer id, @RequestParam Integer status) {
        return Results.success(resourceService.updateGpuStatus(id, status));
    }

    @PostMapping("/gpu/batch")
    public Result<Integer> batchCreateGpu(@RequestBody List<GPUResource> gpus) {
        return Results.success(resourceService.batchCreateGpus(gpus));
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

