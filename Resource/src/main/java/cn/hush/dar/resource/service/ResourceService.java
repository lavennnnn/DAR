package cn.hush.dar.resource.service;

import cn.hush.dar.resource.dao.entity.AntennaResource;
import cn.hush.dar.resource.dao.entity.CPUResource;
import cn.hush.dar.resource.dao.entity.GPUResource;

import java.util.List;

public interface ResourceService {

    // --- 初始化/重置 ---
    void initMockData(); // 一键初始化所有资源（天线、CPU、GPU）
    void resetAllResources(); // 重置所有状态为空闲

    // --- 查询接口 ---
    List<AntennaResource> getAllAntennas();
    List<CPUResource> getAllCPUs();
    List<GPUResource> getAllGPUs();

    // --- 核心调度接口  ---
    /**
     * 尝试为任务分配一组天线阵元
     * @param antennaIds 申请的阵元ID列表
     * @param taskId 申请任务的ID
     * @return true=分配成功, false=部分资源已被抢占，分配失败
     */
    boolean allocateAntennas(List<Integer> antennaIds, Integer taskId);

    /**
     * 释放指定任务占用的所有资源 (天线)
     * @param taskId 任务ID
     */
    void releaseResourcesByTask(Integer taskId);

    /**
     * 申请 CPU 核心
     * @param taskId 任务ID
     * @param cores 需要的核心数
     * @return true=分配成功
     */
    boolean allocateCpuCores(Integer taskId, Integer cores);

    /**
     * 释放 CPU 核心
     * @param cores 需要释放的核心数
     */
    void releaseCpuCores(Integer taskId);

    /**
     * 申请 GPU 显存
     * @param mem 需要的显存(GB)
     * @return true=分配成功
     */
    boolean allocateGpuMem(Integer taskId, Integer mem);

    /**
     * 释放 GPU 显存
     * @param mem 需要释放的显存(GB)
     */
    void releaseGpuMem(Integer taskId);

    // --- 资源管理：天线 ---
    AntennaResource createAntenna(AntennaResource antenna);
    boolean updateAntenna(Integer id, AntennaResource antenna);
    boolean deleteAntenna(Integer id);
    boolean updateAntennaStatus(Integer id, Integer status);
    int batchCreateAntennas(List<AntennaResource> antennas);

    // --- 资源管理：CPU ---
    CPUResource createCpu(CPUResource cpu);
    boolean updateCpu(Integer id, CPUResource cpu);
    boolean deleteCpu(Integer id);
    boolean updateCpuStatus(Integer id, Integer status);
    int batchCreateCpus(List<CPUResource> cpus);

    // --- 资源管理：GPU ---
    GPUResource createGpu(GPUResource gpu);
    boolean updateGpu(Integer id, GPUResource gpu);
    boolean deleteGpu(Integer id);
    boolean updateGpuStatus(Integer id, Integer status);
    int batchCreateGpus(List<GPUResource> gpus);
}
