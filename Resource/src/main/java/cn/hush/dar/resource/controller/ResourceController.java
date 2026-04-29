package cn.hush.dar.resource.controller;

import cn.hush.dar.common.utils.jwtutils.JwtUtil;
import cn.hush.dar.common.result.Result;
import cn.hush.dar.common.web.Results;
import cn.hush.dar.resource.dao.entity.AntennaResource;
import cn.hush.dar.resource.dao.entity.CPUResource;
import cn.hush.dar.resource.dao.entity.GPUResource;
import cn.hush.dar.resource.dao.entity.PhysicalAntennaResource;
import cn.hush.dar.resource.service.ResourceService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/resource")
@Tag(name = "资源相关功能")
@RequiredArgsConstructor
public class ResourceController {

    private final ResourceService resourceService;
    private final JwtUtil jwtUtil;

    @PostMapping("/init")
    public Result<Void> initMockData(HttpServletRequest request) {
        requireAuthorized(request);
        resourceService.initMockData();
        return Results.success();
    }

    @PostMapping("/reset")
    public Result<Void> reset(HttpServletRequest request) {
        requireAuthorized(request);
        resourceService.resetAllResources();
        return Results.success();
    }

    @GetMapping("/antenna/list")
    public Result<List<AntennaResource>> getAntennaList() {
        return Results.success(resourceService.getAllAntennas());
    }

    @GetMapping("/antenna/physical/list")
    public Result<List<PhysicalAntennaResource>> getPhysicalAntennaList() {
        return Results.success(resourceService.getPhysicalAntennas());
    }

    @PostMapping("/antenna/physical")
    public Result<PhysicalAntennaResource> createPhysicalAntenna(@RequestBody PhysicalAntennaResource antenna) {
        return Results.success(resourceService.createPhysicalAntenna(antenna));
    }

    @PutMapping("/antenna/physical/{id}")
    public Result<Boolean> updatePhysicalAntenna(@PathVariable Integer id, @RequestBody PhysicalAntennaResource antenna) {
        return Results.success(resourceService.updatePhysicalAntenna(id, antenna));
    }

    @DeleteMapping("/antenna/physical/{id}")
    public Result<Boolean> deletePhysicalAntenna(@PathVariable Integer id) {
        return Results.success(resourceService.deletePhysicalAntenna(id));
    }

    @PatchMapping("/antenna/physical/{id}/status")
    public Result<Boolean> updatePhysicalAntennaStatus(@PathVariable Integer id, @RequestParam Integer status) {
        return Results.success(resourceService.updatePhysicalAntennaStatus(id, status));
    }

    @PostMapping("/antenna/physical/batch")
    public Result<Integer> batchCreatePhysicalAntenna(@RequestBody List<PhysicalAntennaResource> antennas) {
        return Results.success(resourceService.batchCreatePhysicalAntennas(antennas));
    }

    @GetMapping("/cpu/list")
    public Result<List<CPUResource>> getCPUList() {
        return Results.success(resourceService.getAllCPUs());
    }

    @GetMapping("/gpu/list")
    public Result<List<GPUResource>> getGPUList() {
        return Results.success(resourceService.getAllGPUs());
    }

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

    @PostMapping("/allocate")
    public Result<Boolean> allocate(@RequestBody AllocateRequest request) {
        boolean success = resourceService.allocateAntennas(request.getAntennaIds(), request.getTaskId());
        if (success) {
            return Results.success(true);
        }
        return Results.failure("RESOURCE_BUSY", "资源已被占用或不足");
    }

    @PostMapping("/release")
    public Result<Void> release(@RequestParam Integer taskId) {
        resourceService.releaseResourcesByTask(taskId);
        return Results.success();
    }

    @Data
    public static class AllocateRequest {
        private List<Integer> antennaIds;
        private Integer taskId;
    }

    private void requireAuthorized(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        try {
            jwtUtil.parseToken(token);
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
    }
}
