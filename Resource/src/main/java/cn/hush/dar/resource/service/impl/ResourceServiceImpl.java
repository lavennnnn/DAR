package cn.hush.dar.resource.service.impl;


import cn.hush.dar.resource.dao.entity.AntennaResource;
import cn.hush.dar.resource.dao.entity.CPUResource;
import cn.hush.dar.resource.dao.entity.GPUResource;
import cn.hush.dar.resource.dao.entity.CpuAlloc;
import cn.hush.dar.resource.dao.entity.GpuAlloc;
import cn.hush.dar.resource.dao.mapper.AntennaMapper;
import cn.hush.dar.resource.dao.mapper.CPUMapper;
import cn.hush.dar.resource.dao.mapper.GPUMapper;
import cn.hush.dar.resource.dao.mapper.CpuAllocMapper;
import cn.hush.dar.resource.dao.mapper.GpuAllocMapper;
import cn.hush.dar.resource.service.ResourceService;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateChain;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * @program: DAR
 * @description:
 * @author: Hush
 * @create: 2026-01-05 21:57
 **/

@Slf4j
@Service
@RequiredArgsConstructor
public class ResourceServiceImpl implements ResourceService {

    private final AntennaMapper antennaMapper;
    private final CPUMapper cpuMapper;
    private final GPUMapper gpuMapper;
    private final CpuAllocMapper cpuAllocMapper;
    private final GpuAllocMapper gpuAllocMapper;


    @Override
    public void initMockData() {
        // 1. 初始化天线 (8x8 方阵)
        initAntennas(8, 8);

        // 2. 初始化 CPU (模拟 5 个计算节点)
        initCPUs();

        // 3. 初始化 GPU (模拟 8 张加速卡)
        initGPUs();
    }


    //初始化天线
    private void initAntennas(int rows, int cols) {
        antennaMapper.deleteByQuery(new QueryWrapper().where("1=1"));
        List<AntennaResource> list = new ArrayList<>();
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                list.add(AntennaResource.builder()
                        .code(String.format("ANT-%d-%d", i, j))
                        .xPos((double) i * 10) // 间距10单位
                        .yPos((double) j * 10)
                        .phase(0.0)       // 初始相位 0
                        .amplitude(1.0)   // 初始幅度 1.0
                        .status(0)
                        .build());
            }
        }
        antennaMapper.insertBatch(list);
    }

    //初始化CPU
    private void initCPUs() {
        cpuMapper.deleteByQuery(new QueryWrapper().where("1=1"));
        List<CPUResource> list = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            list.add(CPUResource.builder()
                    .hostname("Node-0" + i)
                    .ipAddress("192.168.1." + (100 + i))
                    .totalCores(64) // 假设每个节点64核
                    .usedCores(0) // 初始无负载，避免影响实际调度展示
                    .status(0)
                    .build());
        }
        cpuMapper.insertBatch(list);
    }

    //初始化GPU
    private void initGPUs() {
        gpuMapper.deleteByQuery(new QueryWrapper().where("1=1"));
        List<GPUResource> list = new ArrayList<>();
        String[] models = {"NVIDIA A100", "NVIDIA RTX 4090", "NVIDIA V100"};
        Random random = new Random();

        for (int i = 1; i <= 8; i++) {
            list.add(GPUResource.builder()
                    .model(models[random.nextInt(models.length)])
                    .totalMemory(24) // 24GB 显存
                    .usedMemory(0)
                    .status(0)
                    .build());
        }
        gpuMapper.insertBatch(list);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetAllResources() {
        // 重置天线：状态归0，任务ID清空，相位幅度复位
        UpdateChain.of(AntennaResource.class)
                .set(AntennaResource::getStatus, 0)
                .set(AntennaResource::getTaskId, null)
                .set(AntennaResource::getPhase, 0.0)
                .set(AntennaResource::getAmplitude, 1.0)
                .where("1=1")
                .update();

        // 重置 CPU
        UpdateChain.of(CPUResource.class)
                .set(CPUResource::getUsedCores, 0)
                .set(CPUResource::getStatus, 0)
                .where("1=1")
                .update();

        // 重置 GPU
        UpdateChain.of(GPUResource.class)
                .set(GPUResource::getUsedMemory, 0)
                .set(GPUResource::getStatus, 0)
                .where("1=1")
                .update();
    }

    @Override
    public List<AntennaResource> getAllAntennas() {
        return antennaMapper.selectAll();
    }

    @Override
    public List<CPUResource> getAllCPUs() {
        return cpuMapper.selectAll();
    }

    @Override
    public List<GPUResource> getAllGPUs() {
        return gpuMapper.selectAll();
    }

    // ================= 核心调度逻辑实现 =================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean allocateAntennas(List<Integer> antennaIds, Integer taskId) {
        if (antennaIds == null || antennaIds.isEmpty()) return false;

        // 1. 检查所有请求的资源是否都是空闲状态
        // select count(*) from t_antenna where id in (...) and status = 0
        long availableCount = antennaMapper.selectCountByQuery(
                QueryWrapper.create()
                        .in(AntennaResource::getId, antennaIds)
                        .eq(AntennaResource::getStatus, 0)
        );
        if (availableCount != antennaIds.size()) {
            log.warn("任务[{}] 资源分配失败：请求 {} 个，实际可用 {} 个", taskId, antennaIds.size(), availableCount);
            return false; // 资源不足或被抢占，回滚
        }
        //2. 执行分配：更新状态为占用(1)，设置任务ID，并模拟随机相位(让前端展示动起来)
        // update t_antenna set status=1, task_id=taskId, phase=... where id in (...)
        Random random = new Random();
        boolean success = UpdateChain.of(AntennaResource.class)
                .set(AntennaResource::getStatus, 1)
                .set(AntennaResource::getTaskId, taskId)
                .set(AntennaResource::getPhase, random.nextDouble() * 360) // 模拟波束形成时的相位变化
                .where(AntennaResource::getId).in(antennaIds)
                .update();

        log.info("任务[{}] 成功分配天线: {}", taskId, antennaIds);
        return success;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void releaseResourcesByTask(Integer taskId) {
        // 释放该任务占用的所有天线
        UpdateChain.of(AntennaResource.class)
                .set(AntennaResource::getStatus, 0)
                .set(AntennaResource::getTaskId, null)
                .set(AntennaResource::getPhase, 0.0) // 恢复初始位
                .where(AntennaResource::getTaskId).eq(taskId)
                .update();

        log.info("任务[{}] 资源已释放", taskId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean allocateCpuCores(Integer taskId, Integer cores) {
        if (cores == null || cores <= 0) return true;
        List<CPUResource> cpus = cpuMapper.selectAll();
        int available = cpus.stream()
                .mapToInt(c -> Math.max(0, c.getTotalCores() - c.getUsedCores()))
                .sum();
        if (available < cores) return false;

        int remaining = cores;
        // 负载均衡：优先选择当前使用率最低的 CPU
        cpus.sort(Comparator.comparingDouble(c -> {
            int total = c.getTotalCores() == null ? 0 : c.getTotalCores();
            int used = c.getUsedCores() == null ? 0 : c.getUsedCores();
            return total == 0 ? 1.0 : (double) used / total;
        }));
        for (CPUResource cpu : cpus) {
            if (remaining <= 0) break;
            int free = Math.max(0, cpu.getTotalCores() - cpu.getUsedCores());
            if (free == 0) continue;
            int take = Math.min(free, remaining);
            UpdateChain.of(CPUResource.class)
                    .set(CPUResource::getUsedCores, cpu.getUsedCores() + take)
                    .set(CPUResource::getStatus, 1)
                    .where(CPUResource::getId).eq(cpu.getId())
                    .update();
            cpuAllocMapper.insert(CpuAlloc.builder()
                    .taskId(taskId)
                    .cpuId(cpu.getId())
                    .cores(take)
                    .build());
            remaining -= take;
        }
        return remaining == 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void releaseCpuCores(Integer taskId) {
        if (taskId == null) return;
        List<CpuAlloc> allocs = cpuAllocMapper.selectListByQuery(
                QueryWrapper.create().eq(CpuAlloc::getTaskId, taskId)
        );
        for (CpuAlloc alloc : allocs) {
            CPUResource cpu = cpuMapper.selectOneById(alloc.getCpuId());
            if (cpu == null) continue;
            int used = Math.max(0, cpu.getUsedCores() - alloc.getCores());
            UpdateChain.of(CPUResource.class)
                    .set(CPUResource::getUsedCores, used)
                    .set(CPUResource::getStatus, used > 0 ? 1 : 0)
                    .where(CPUResource::getId).eq(cpu.getId())
                    .update();
        }
        cpuAllocMapper.deleteByQuery(QueryWrapper.create().eq(CpuAlloc::getTaskId, taskId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean allocateGpuMem(Integer taskId, Integer mem) {
        if (mem == null || mem <= 0) return true;
        List<GPUResource> gpus = gpuMapper.selectAll();
        int available = gpus.stream()
                .mapToInt(g -> Math.max(0, g.getTotalMemory() - g.getUsedMemory()))
                .sum();
        if (available < mem) return false;

        int remaining = mem;
        // 负载均衡：优先选择当前使用率最低的 GPU
        gpus.sort(Comparator.comparingDouble(g -> {
            int total = g.getTotalMemory() == null ? 0 : g.getTotalMemory();
            int used = g.getUsedMemory() == null ? 0 : g.getUsedMemory();
            return total == 0 ? 1.0 : (double) used / total;
        }));
        for (GPUResource gpu : gpus) {
            if (remaining <= 0) break;
            int free = Math.max(0, gpu.getTotalMemory() - gpu.getUsedMemory());
            if (free == 0) continue;
            int take = Math.min(free, remaining);
            UpdateChain.of(GPUResource.class)
                    .set(GPUResource::getUsedMemory, gpu.getUsedMemory() + take)
                    .set(GPUResource::getStatus, 1)
                    .where(GPUResource::getId).eq(gpu.getId())
                    .update();
            gpuAllocMapper.insert(GpuAlloc.builder()
                    .taskId(taskId)
                    .gpuId(gpu.getId())
                    .mem(take)
                    .build());
            remaining -= take;
        }
        return remaining == 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void releaseGpuMem(Integer taskId) {
        if (taskId == null) return;
        List<GpuAlloc> allocs = gpuAllocMapper.selectListByQuery(
                QueryWrapper.create().eq(GpuAlloc::getTaskId, taskId)
        );
        for (GpuAlloc alloc : allocs) {
            GPUResource gpu = gpuMapper.selectOneById(alloc.getGpuId());
            if (gpu == null) continue;
            int used = Math.max(0, gpu.getUsedMemory() - alloc.getMem());
            UpdateChain.of(GPUResource.class)
                    .set(GPUResource::getUsedMemory, used)
                    .set(GPUResource::getStatus, used > 0 ? 1 : 0)
                    .where(GPUResource::getId).eq(gpu.getId())
                    .update();
        }
        gpuAllocMapper.deleteByQuery(QueryWrapper.create().eq(GpuAlloc::getTaskId, taskId));
    }

    // ================= 资源管理：天线 =================

    @Override
    public AntennaResource createAntenna(AntennaResource antenna) {
        if (antenna.getStatus() == null) antenna.setStatus(0);
        if (antenna.getPhase() == null) antenna.setPhase(0.0);
        if (antenna.getAmplitude() == null) antenna.setAmplitude(1.0);
        antennaMapper.insert(antenna);
        return antenna;
    }

    @Override
    public boolean updateAntenna(Integer id, AntennaResource antenna) {
        if (id == null) return false;
        antenna.setId(id);
        return antennaMapper.update(antenna) > 0;
    }

    @Override
    public boolean deleteAntenna(Integer id) {
        if (id == null) return false;
        return antennaMapper.deleteById(id) > 0;
    }

    @Override
    public boolean updateAntennaStatus(Integer id, Integer status) {
        if (id == null || status == null) return false;
        UpdateChain<AntennaResource> chain = UpdateChain.of(AntennaResource.class)
                .set(AntennaResource::getStatus, status)
                .where(AntennaResource::getId).eq(id);
        if (status == 0 || status == 2) {
            chain.set(AntennaResource::getTaskId, null);
        }
        return chain.update();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchCreateAntennas(List<AntennaResource> antennas) {
        if (antennas == null || antennas.isEmpty()) return 0;
        antennas.forEach(a -> {
            if (a.getStatus() == null) a.setStatus(0);
            if (a.getPhase() == null) a.setPhase(0.0);
            if (a.getAmplitude() == null) a.setAmplitude(1.0);
        });
        return antennaMapper.insertBatch(antennas);
    }

    // ================= 资源管理：CPU =================

    @Override
    public CPUResource createCpu(CPUResource cpu) {
        if (cpu.getStatus() == null) cpu.setStatus(0);
        if (cpu.getUsedCores() == null) cpu.setUsedCores(0);
        cpuMapper.insert(cpu);
        return cpu;
    }

    @Override
    public boolean updateCpu(Integer id, CPUResource cpu) {
        if (id == null) return false;
        cpu.setId(id);
        return cpuMapper.update(cpu) > 0;
    }

    @Override
    public boolean deleteCpu(Integer id) {
        if (id == null) return false;
        return cpuMapper.deleteById(id) > 0;
    }

    @Override
    public boolean updateCpuStatus(Integer id, Integer status) {
        if (id == null || status == null) return false;
        UpdateChain<CPUResource> chain = UpdateChain.of(CPUResource.class)
                .set(CPUResource::getStatus, status)
                .where(CPUResource::getId).eq(id);
        if (status == 0 || status == 2) {
            chain.set(CPUResource::getUsedCores, 0);
        }
        return chain.update();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchCreateCpus(List<CPUResource> cpus) {
        if (cpus == null || cpus.isEmpty()) return 0;
        cpus.forEach(c -> {
            if (c.getStatus() == null) c.setStatus(0);
            if (c.getUsedCores() == null) c.setUsedCores(0);
        });
        return cpuMapper.insertBatch(cpus);
    }

    // ================= 资源管理：GPU =================

    @Override
    public GPUResource createGpu(GPUResource gpu) {
        if (gpu.getStatus() == null) gpu.setStatus(0);
        if (gpu.getUsedMemory() == null) gpu.setUsedMemory(0);
        gpuMapper.insert(gpu);
        return gpu;
    }

    @Override
    public boolean updateGpu(Integer id, GPUResource gpu) {
        if (id == null) return false;
        gpu.setId(id);
        return gpuMapper.update(gpu) > 0;
    }

    @Override
    public boolean deleteGpu(Integer id) {
        if (id == null) return false;
        return gpuMapper.deleteById(id) > 0;
    }

    @Override
    public boolean updateGpuStatus(Integer id, Integer status) {
        if (id == null || status == null) return false;
        UpdateChain<GPUResource> chain = UpdateChain.of(GPUResource.class)
                .set(GPUResource::getStatus, status)
                .where(GPUResource::getId).eq(id);
        if (status == 0 || status == 2) {
            chain.set(GPUResource::getUsedMemory, 0);
        }
        return chain.update();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchCreateGpus(List<GPUResource> gpus) {
        if (gpus == null || gpus.isEmpty()) return 0;
        gpus.forEach(g -> {
            if (g.getStatus() == null) g.setStatus(0);
            if (g.getUsedMemory() == null) g.setUsedMemory(0);
        });
        return gpuMapper.insertBatch(gpus);
    }
}
