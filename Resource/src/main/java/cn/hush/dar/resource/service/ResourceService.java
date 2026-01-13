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
}
