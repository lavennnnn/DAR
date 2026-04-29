package cn.hush.dar.resource.service;

import cn.hush.dar.resource.dao.entity.AntennaAlloc;
import cn.hush.dar.resource.dao.entity.AntennaResource;
import cn.hush.dar.resource.dao.entity.CPUResource;
import cn.hush.dar.resource.dao.entity.GPUResource;
import cn.hush.dar.resource.dao.entity.PhysicalAntennaResource;

import java.util.LinkedHashMap;
import java.util.List;

public interface ResourceService {

    void initMockData();
    void resetAllResources();

    List<PhysicalAntennaResource> getPhysicalAntennas();
    PhysicalAntennaResource createPhysicalAntenna(PhysicalAntennaResource antenna);
    boolean updatePhysicalAntenna(Integer id, PhysicalAntennaResource antenna);
    boolean deletePhysicalAntenna(Integer id);
    boolean updatePhysicalAntennaStatus(Integer id, Integer status);
    int batchCreatePhysicalAntennas(List<PhysicalAntennaResource> antennas);

    List<AntennaResource> getAllAntennas();
    List<CPUResource> getAllCPUs();
    List<GPUResource> getAllGPUs();

    boolean allocateAntennas(List<Integer> antennaIds, Integer taskId);
    boolean allocateAntennas(List<Integer> antennaIds, Integer taskId, Double beamFrequency, String beamGroup);
    void releaseResourcesByTask(Integer taskId);
    List<AntennaAlloc> getActiveAntennaAllocs();

    boolean allocateCpuCores(Integer taskId, Integer cores);
    boolean allocateCpuPlan(Integer taskId, LinkedHashMap<Integer, Integer> cpuPlan);
    void releaseCpuCores(Integer taskId);
    boolean allocateGpuMem(Integer taskId, Integer mem);
    boolean allocateGpuCard(Integer taskId, Integer gpuId, Integer mem);
    void releaseGpuMem(Integer taskId);

    AntennaResource createAntenna(AntennaResource antenna);
    boolean updateAntenna(Integer id, AntennaResource antenna);
    boolean deleteAntenna(Integer id);
    boolean updateAntennaStatus(Integer id, Integer status);
    int batchCreateAntennas(List<AntennaResource> antennas);

    CPUResource createCpu(CPUResource cpu);
    boolean updateCpu(Integer id, CPUResource cpu);
    boolean deleteCpu(Integer id);
    boolean updateCpuStatus(Integer id, Integer status);
    int batchCreateCpus(List<CPUResource> cpus);

    GPUResource createGpu(GPUResource gpu);
    boolean updateGpu(Integer id, GPUResource gpu);
    boolean deleteGpu(Integer id);
    boolean updateGpuStatus(Integer id, Integer status);
    int batchCreateGpus(List<GPUResource> gpus);
}
