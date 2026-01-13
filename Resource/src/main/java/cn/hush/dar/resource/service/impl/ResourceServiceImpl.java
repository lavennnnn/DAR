package cn.hush.dar.resource.service.impl;


import cn.hush.dar.resource.dao.entity.AntennaResource;
import cn.hush.dar.resource.dao.entity.CPUResource;
import cn.hush.dar.resource.dao.entity.GPUResource;
import cn.hush.dar.resource.dao.mapper.AntennaMapper;
import cn.hush.dar.resource.dao.mapper.CPUMapper;
import cn.hush.dar.resource.dao.mapper.GPUMapper;
import cn.hush.dar.resource.service.ResourceService;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateChain;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
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
                    .usedCores(new Random().nextInt(10)) // 随机初始负载
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
                .update();

        // 重置 CPU
        UpdateChain.of(CPUResource.class)
                .set(CPUResource::getUsedCores, 0)
                .set(CPUResource::getStatus, 0)
                .update();

        // 重置 GPU
        UpdateChain.of(GPUResource.class)
                .set(GPUResource::getUsedMemory, 0)
                .set(GPUResource::getStatus, 0)
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
}
