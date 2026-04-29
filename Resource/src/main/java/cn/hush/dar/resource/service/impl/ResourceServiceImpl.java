package cn.hush.dar.resource.service.impl;


import cn.hush.dar.resource.dao.entity.AntennaAlloc;
import cn.hush.dar.resource.dao.entity.AntennaResource;
import cn.hush.dar.resource.dao.entity.CPUResource;
import cn.hush.dar.resource.dao.entity.GPUResource;
import cn.hush.dar.resource.dao.entity.PhysicalAntennaResource;
import cn.hush.dar.resource.dao.entity.CpuAlloc;
import cn.hush.dar.resource.dao.entity.GpuAlloc;
import cn.hush.dar.resource.dao.mapper.AntennaAllocMapper;
import cn.hush.dar.resource.dao.mapper.AntennaMapper;
import cn.hush.dar.resource.dao.mapper.CPUMapper;
import cn.hush.dar.resource.dao.mapper.GPUMapper;
import cn.hush.dar.resource.dao.mapper.PhysicalAntennaMapper;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final AntennaAllocMapper antennaAllocMapper;
    private final PhysicalAntennaMapper physicalAntennaMapper;
    private final CPUMapper cpuMapper;
    private final GPUMapper gpuMapper;
    private final CpuAllocMapper cpuAllocMapper;
    private final GpuAllocMapper gpuAllocMapper;


    @Override
    public void initMockData() {
        // 1. 鍒濆鍖栧ぉ绾?(8x8 鏂归樀)
        initAntennas(8, 8);

        // 2. 鍒濆鍖?CPU (妯℃嫙 5 涓绠楄妭鐐?
        initCPUs();

        // 3. 鍒濆鍖?GPU (妯℃嫙 8 寮犲姞閫熷崱)
        initGPUs();
    }


    //鍒濆鍖栧ぉ绾?
    private void initAntennas(int rows, int cols) {
        antennaMapper.deleteByQuery(new QueryWrapper().where("1=1"));
        antennaAllocMapper.deleteByQuery(new QueryWrapper().where("1=1"));
        physicalAntennaMapper.deleteByQuery(new QueryWrapper().where("1=1"));
        List<PhysicalAntennaResource> antennas = new ArrayList<>();
        int antennaRows = Math.max(1, rows / 2);
        int antennaCols = Math.max(1, cols / 2);
        for (int i = 0; i < antennaRows; i++) {
            for (int j = 0; j < antennaCols; j++) {
                antennas.add(PhysicalAntennaResource.builder()
                        .code(String.format("ANT-%02d-%02d", i, j))
                        .name(String.format("Antenna-%02d-%02d", i, j))
                        .surfaceCode(resolveSurfaceCode(i, j, antennaRows, antennaCols))
                        .xPos((double) i * 20)
                        .yPos((double) j * 20)
                        .status(0)
                        .build());
            }
        }
        physicalAntennaMapper.insertBatch(antennas);

        List<AntennaResource> units = new ArrayList<>();
        for (PhysicalAntennaResource antenna : physicalAntennaMapper.selectAll()) {
            for (int unitRow = 0; unitRow < 2; unitRow++) {
                for (int unitCol = 0; unitCol < 2; unitCol++) {
                    units.add(AntennaResource.builder()
                            .antennaId(antenna.getId())
                            .unitCode(String.format("%s-U%d%d", antenna.getCode(), unitRow, unitCol))
                            .xPos((antenna.getXPos() == null ? 0.0 : antenna.getXPos()) + unitRow * 5.0)
                            .yPos((antenna.getYPos() == null ? 0.0 : antenna.getYPos()) + unitCol * 5.0)
                            .phase(0.0)
                            .amplitude(1.0)
                            .reuseCount(0)
                            .surfaceCode(antenna.getSurfaceCode())
                            .status(0)
                            .build());
                }
            }
        }
        antennaMapper.insertBatch(units);
    }

    //鍒濆鍖朇PU
    private void initCPUs() {
        cpuMapper.deleteByQuery(new QueryWrapper().where("1=1"));
        List<CPUResource> list = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            list.add(CPUResource.builder()
                    .hostname("Node-0" + i)
                    .ipAddress("192.168.1." + (100 + i))
                    .totalCores(64) // 鍋囪姣忎釜鑺傜偣64鏍?
                    .usedCores(0) // 鍒濆鏃犺礋杞斤紝閬垮厤褰卞搷瀹為檯璋冨害灞曠ず
                    .status(0)
                    .build());
        }
        cpuMapper.insertBatch(list);
    }

    //鍒濆鍖朑PU
    private void initGPUs() {
        gpuMapper.deleteByQuery(new QueryWrapper().where("1=1"));
        List<GPUResource> list = new ArrayList<>();
        String[] models = {"NVIDIA A100", "NVIDIA RTX 4090", "NVIDIA V100"};
        Random random = new Random();

        for (int i = 1; i <= 8; i++) {
            list.add(GPUResource.builder()
                    .model(models[random.nextInt(models.length)])
                    .totalMemory(24) // 24GB 鏄惧瓨
                    .usedMemory(0)
                    .status(0)
                    .build());
        }
        gpuMapper.insertBatch(list);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetAllResources() {
        // 閲嶇疆澶╃嚎锛氱姸鎬佸綊0锛屼换鍔D娓呯┖锛岀浉浣嶅箙搴﹀浣?
        UpdateChain.of(AntennaResource.class)
                .set(AntennaResource::getStatus, 0)
                .set(AntennaResource::getTaskId, null)
                .set(AntennaResource::getPhase, 0.0)
                .set(AntennaResource::getAmplitude, 1.0)
                .set(AntennaResource::getReuseCount, 0)
                .where("1=1")
                .update();
        UpdateChain.of(PhysicalAntennaResource.class)
                .set(PhysicalAntennaResource::getStatus, 0)
                .where("1=1")
                .update();
        antennaAllocMapper.deleteByQuery(new QueryWrapper().where("1=1"));

        // 閲嶇疆 CPU
        UpdateChain.of(CPUResource.class)
                .set(CPUResource::getUsedCores, 0)
                .set(CPUResource::getStatus, 0)
                .where("1=1")
                .update();

        // 閲嶇疆 GPU
        UpdateChain.of(GPUResource.class)
                .set(GPUResource::getUsedMemory, 0)
                .set(GPUResource::getStatus, 0)
                .where("1=1")
                .update();
    }

    @Override
    public List<PhysicalAntennaResource> getPhysicalAntennas() {
        return physicalAntennaMapper.selectAll();
    }

    @Override
    public PhysicalAntennaResource createPhysicalAntenna(PhysicalAntennaResource antenna) {
        if (antenna.getStatus() == null) antenna.setStatus(0);
        antenna.setSurfaceCode(normalizeSurfaceCode(antenna.getSurfaceCode()));
        physicalAntennaMapper.insert(antenna);
        return antenna;
    }

    @Override
    public boolean updatePhysicalAntenna(Integer id, PhysicalAntennaResource antenna) {
        if (id == null) return false;
        antenna.setId(id);
        antenna.setSurfaceCode(normalizeSurfaceCode(antenna.getSurfaceCode()));
        return physicalAntennaMapper.update(antenna) > 0;
    }

    @Override
    public boolean deletePhysicalAntenna(Integer id) {
        if (id == null) return false;
        long unitCount = antennaMapper.selectCountByQuery(QueryWrapper.create().eq(AntennaResource::getAntennaId, id));
        if (unitCount > 0) return false;
        return physicalAntennaMapper.deleteById(id) > 0;
    }

    @Override
    public boolean updatePhysicalAntennaStatus(Integer id, Integer status) {
        if (id == null || status == null) return false;
        return UpdateChain.of(PhysicalAntennaResource.class)
                .set(PhysicalAntennaResource::getStatus, status)
                .where(PhysicalAntennaResource::getId).eq(id)
                .update();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchCreatePhysicalAntennas(List<PhysicalAntennaResource> antennas) {
        if (antennas == null || antennas.isEmpty()) return 0;
        antennas.forEach(antenna -> {
            if (antenna.getStatus() == null) antenna.setStatus(0);
            antenna.setSurfaceCode(normalizeSurfaceCode(antenna.getSurfaceCode()));
        });
        return physicalAntennaMapper.insertBatch(antennas);
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

    // ================= 鏍稿績璋冨害閫昏緫瀹炵幇 =================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean allocateAntennas(List<Integer> antennaIds, Integer taskId) {
        return allocateAntennas(antennaIds, taskId, null, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean allocateAntennas(List<Integer> antennaIds, Integer taskId, Double beamFrequency, String beamGroup) {
        if (antennaIds == null || antennaIds.isEmpty()) return false;

        Random random = new Random();
        boolean success = true;
        for (Integer antennaId : antennaIds) {
            AntennaResource antenna = antennaMapper.selectOneById(antennaId);
            if (antenna == null || (antenna.getStatus() != null && antenna.getStatus() == 2)) {
                success = false;
                continue;
            }
            int reuseCount = antenna.getReuseCount() == null ? 0 : antenna.getReuseCount();
            antennaAllocMapper.insert(AntennaAlloc.builder()
                    .taskId(taskId)
                    .antennaId(antennaId)
                    .beamFrequency(beamFrequency)
                    .beamGroup(beamGroup)
                    .createTime(new java.util.Date())
                    .build());
            boolean updated = UpdateChain.of(AntennaResource.class)
                    .set(AntennaResource::getStatus, 1)
                    .set(AntennaResource::getTaskId, taskId)
                    .set(AntennaResource::getPhase, random.nextDouble() * 360)
                    .set(AntennaResource::getReuseCount, reuseCount + 1)
                    .where(AntennaResource::getId).eq(antennaId)
                    .update();
            success = success && updated;
        }

        log.info("task[{}] allocated antennas: {}", taskId, antennaIds);
        return success;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void releaseResourcesByTask(Integer taskId) {
        List<AntennaAlloc> allocs = antennaAllocMapper.selectListByQuery(
                QueryWrapper.create().eq(AntennaAlloc::getTaskId, taskId)
        );
        if (allocs.isEmpty()) {
            UpdateChain.of(AntennaResource.class)
                    .set(AntennaResource::getStatus, 0)
                    .set(AntennaResource::getTaskId, null)
                    .set(AntennaResource::getPhase, 0.0)
                    .where(AntennaResource::getTaskId).eq(taskId)
                    .update();
            return;
        }

        List<Integer> antennaIds = allocs.stream()
                .map(AntennaAlloc::getAntennaId)
                .distinct()
                .toList();

        antennaAllocMapper.deleteByQuery(QueryWrapper.create().eq(AntennaAlloc::getTaskId, taskId));

        for (Integer antennaId : antennaIds) {
            List<AntennaAlloc> remainingAllocs = antennaAllocMapper.selectListByQuery(
                    QueryWrapper.create().eq(AntennaAlloc::getAntennaId, antennaId)
            );
            if (remainingAllocs.isEmpty()) {
                UpdateChain.of(AntennaResource.class)
                        .set(AntennaResource::getStatus, 0)
                        .set(AntennaResource::getTaskId, null)
                        .set(AntennaResource::getPhase, 0.0)
                        .where(AntennaResource::getId).eq(antennaId)
                        .update();
            } else {
                Integer latestTaskId = remainingAllocs.stream()
                        .map(AntennaAlloc::getTaskId)
                        .filter(java.util.Objects::nonNull)
                        .reduce((first, second) -> second)
                        .orElse(null);
                UpdateChain.of(AntennaResource.class)
                        .set(AntennaResource::getStatus, 1)
                        .set(AntennaResource::getTaskId, latestTaskId)
                        .where(AntennaResource::getId).eq(antennaId)
                        .update();
            }
        }

        log.info("task[{}] antenna resources released", taskId);
    }

    @Override
    public List<AntennaAlloc> getActiveAntennaAllocs() {
        return antennaAllocMapper.selectAll();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean allocateCpuCores(Integer taskId, Integer cores) {
        if (cores == null || cores <= 0) return true;
        LinkedHashMap<Integer, Integer> cpuPlan = buildBalancedCpuPlan(cores);
        if (cpuPlan.isEmpty()) return false;
        return allocateCpuPlan(taskId, cpuPlan);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean allocateCpuPlan(Integer taskId, LinkedHashMap<Integer, Integer> cpuPlan) {
        if (cpuPlan == null || cpuPlan.isEmpty()) return true;
        for (Map.Entry<Integer, Integer> entry : cpuPlan.entrySet()) {
            Integer cpuId = entry.getKey();
            Integer cores = entry.getValue();
            if (cpuId == null || cores == null || cores <= 0) continue;
            CPUResource cpu = cpuMapper.selectOneById(cpuId);
            if (cpu == null || (cpu.getStatus() != null && cpu.getStatus() == 2)) return false;
            int free = Math.max(0, safeInt(cpu.getTotalCores()) - safeInt(cpu.getUsedCores()));
            if (free < cores) return false;
        }

        for (Map.Entry<Integer, Integer> entry : cpuPlan.entrySet()) {
            Integer cpuId = entry.getKey();
            Integer cores = entry.getValue();
            if (cpuId == null || cores == null || cores <= 0) continue;
            CPUResource cpu = cpuMapper.selectOneById(cpuId);
            UpdateChain.of(CPUResource.class)
                    .set(CPUResource::getUsedCores, safeInt(cpu.getUsedCores()) + cores)
                    .set(CPUResource::getStatus, 1)
                    .where(CPUResource::getId).eq(cpuId)
                    .update();
            cpuAllocMapper.insert(CpuAlloc.builder()
                    .taskId(taskId)
                    .cpuId(cpuId)
                    .cores(cores)
                    .build());
        }
        return true;
    }

    private LinkedHashMap<Integer, Integer> buildBalancedCpuPlan(Integer cores) {
        LinkedHashMap<Integer, Integer> plan = new LinkedHashMap<>();
        if (cores == null || cores <= 0) return plan;
        List<CPUResource> cpus = cpuMapper.selectAll().stream()
                .filter(cpu -> cpu.getId() != null)
                .filter(cpu -> cpu.getStatus() == null || cpu.getStatus() != 2)
                .filter(cpu -> Math.max(0, safeInt(cpu.getTotalCores()) - safeInt(cpu.getUsedCores())) > 0)
                .toList();
        int available = cpus.stream()
                .mapToInt(cpu -> Math.max(0, safeInt(cpu.getTotalCores()) - safeInt(cpu.getUsedCores())))
                .sum();
        if (available < cores) return new LinkedHashMap<>();

        for (int i = 0; i < cores; i++) {
            CPUResource best = null;
            double bestScore = Double.MAX_VALUE;
            for (CPUResource cpu : cpus) {
                int allocated = plan.getOrDefault(cpu.getId(), 0);
                int free = Math.max(0, safeInt(cpu.getTotalCores()) - safeInt(cpu.getUsedCores()) - allocated);
                if (free <= 0) continue;
                double score = computeCpuVariance(cpus, plan, cpu.getId(), 1);
                if (!plan.containsKey(cpu.getId())) {
                    score += 0.02;
                }
                if (score < bestScore) {
                    bestScore = score;
                    best = cpu;
                }
            }
            if (best == null) return new LinkedHashMap<>();
            plan.merge(best.getId(), 1, Integer::sum);
        }
        return plan;
    }

    private double computeCpuVariance(List<CPUResource> cpus,
                                      Map<Integer, Integer> plan,
                                      Integer extraCpuId,
                                      int extraCores) {
        if (cpus == null || cpus.isEmpty()) return 0.0;
        double avg = cpus.stream()
                .mapToDouble(cpu -> projectedCpuLoad(cpu, plan, extraCpuId, extraCores))
                .average()
                .orElse(0.0);
        return cpus.stream()
                .mapToDouble(cpu -> {
                    double load = projectedCpuLoad(cpu, plan, extraCpuId, extraCores);
                    return Math.pow(load - avg, 2);
                })
                .average()
                .orElse(0.0);
    }

    private double projectedCpuLoad(CPUResource cpu,
                                    Map<Integer, Integer> plan,
                                    Integer extraCpuId,
                                    int extraCores) {
        int total = Math.max(1, safeInt(cpu.getTotalCores()));
        int used = safeInt(cpu.getUsedCores()) + plan.getOrDefault(cpu.getId(), 0);
        if (cpu.getId().equals(extraCpuId)) {
            used += extraCores;
        }
        return used / (double) total;
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
        Integer gpuId = selectBalancedGpu(mem);
        if (gpuId == null) return false;
        return allocateGpuCard(taskId, gpuId, mem);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean allocateGpuCard(Integer taskId, Integer gpuId, Integer mem) {
        if (mem == null || mem <= 0) return true;
        if (gpuId == null) return false;
        GPUResource gpu = gpuMapper.selectOneById(gpuId);
        if (gpu == null || (gpu.getStatus() != null && gpu.getStatus() == 2)) return false;
        int free = Math.max(0, safeInt(gpu.getTotalMemory()) - safeInt(gpu.getUsedMemory()));
        if (free < mem) return false;

        UpdateChain.of(GPUResource.class)
                .set(GPUResource::getUsedMemory, safeInt(gpu.getUsedMemory()) + mem)
                .set(GPUResource::getStatus, 1)
                .where(GPUResource::getId).eq(gpuId)
                .update();
        gpuAllocMapper.insert(GpuAlloc.builder()
                .taskId(taskId)
                .gpuId(gpuId)
                .mem(mem)
                .build());
        return true;
    }

    private Integer selectBalancedGpu(Integer mem) {
        List<GPUResource> gpus = gpuMapper.selectAll();
        return gpus.stream()
                .filter(gpu -> gpu.getId() != null)
                .filter(gpu -> gpu.getStatus() == null || gpu.getStatus() != 2)
                .filter(gpu -> Math.max(0, safeInt(gpu.getTotalMemory()) - safeInt(gpu.getUsedMemory())) >= mem)
                .min(Comparator
                        .comparingDouble((GPUResource gpu) -> computeGpuVariance(gpus, gpu.getId(), mem))
                        .thenComparingInt(gpu -> Math.max(0, safeInt(gpu.getTotalMemory()) - safeInt(gpu.getUsedMemory()) - mem))
                        .thenComparing(GPUResource::getId))
                .map(GPUResource::getId)
                .orElse(null);
    }

    private double computeGpuVariance(List<GPUResource> gpus, Integer selectedGpuId, int mem) {
        List<GPUResource> available = gpus.stream()
                .filter(gpu -> gpu.getId() != null)
                .filter(gpu -> gpu.getStatus() == null || gpu.getStatus() != 2)
                .toList();
        if (available.isEmpty()) return 0.0;
        double avg = available.stream()
                .mapToDouble(gpu -> projectedGpuLoad(gpu, selectedGpuId, mem))
                .average()
                .orElse(0.0);
        return available.stream()
                .mapToDouble(gpu -> {
                    double load = projectedGpuLoad(gpu, selectedGpuId, mem);
                    return Math.pow(load - avg, 2);
                })
                .average()
                .orElse(0.0);
    }

    private double projectedGpuLoad(GPUResource gpu, Integer selectedGpuId, int mem) {
        int total = Math.max(1, safeInt(gpu.getTotalMemory()));
        int used = safeInt(gpu.getUsedMemory());
        if (gpu.getId().equals(selectedGpuId)) {
            used += mem;
        }
        return used / (double) total;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
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

    // ================= 璧勬簮绠＄悊锛氬ぉ绾?=================

    @Override
    public AntennaResource createAntenna(AntennaResource antenna) {
        if (antenna.getStatus() == null) antenna.setStatus(0);
        if (antenna.getPhase() == null) antenna.setPhase(0.0);
        if (antenna.getAmplitude() == null) antenna.setAmplitude(1.0);
        if (antenna.getReuseCount() == null) antenna.setReuseCount(0);
        if (antenna.getUnitCode() == null || antenna.getUnitCode().isBlank()) {
            antenna.setUnitCode("UNIT-" + System.currentTimeMillis());
        }
        antenna.setSurfaceCode(normalizeSurfaceCode(antenna.getSurfaceCode()));
        antennaMapper.insert(antenna);
        return antenna;
    }

    @Override
    public boolean updateAntenna(Integer id, AntennaResource antenna) {
        if (id == null) return false;
        antenna.setId(id);
        antenna.setSurfaceCode(normalizeSurfaceCode(antenna.getSurfaceCode()));
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
            if (a.getReuseCount() == null) a.setReuseCount(0);
            if (a.getUnitCode() == null || a.getUnitCode().isBlank()) {
                a.setUnitCode("UNIT-" + System.nanoTime());
            }
            a.setSurfaceCode(normalizeSurfaceCode(a.getSurfaceCode()));
        });
        return antennaMapper.insertBatch(antennas);
    }

    // ================= 璧勬簮绠＄悊锛欳PU =================

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

    // ================= 璧勬簮绠＄悊锛欸PU =================

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

    private String resolveSurfaceCode(int row, int col, int rows, int cols) {
        boolean top = row < Math.max(1, rows / 2);
        boolean left = col < Math.max(1, cols / 2);
        if (top && left) return "SURFACE-A";
        if (top) return "SURFACE-B";
        if (left) return "SURFACE-C";
        return "SURFACE-D";
    }

    private String normalizeSurfaceCode(String surfaceCode) {
        if (surfaceCode == null) {
            return null;
        }
        String trimmed = surfaceCode.trim();
        return trimmed.isEmpty() ? null : trimmed.toUpperCase();
    }

}

