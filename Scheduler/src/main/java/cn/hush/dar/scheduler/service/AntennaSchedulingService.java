package cn.hush.dar.scheduler.service;

import cn.hush.dar.resource.dao.entity.AntennaAlloc;
import cn.hush.dar.resource.dao.entity.AntennaResource;
import cn.hush.dar.resource.service.ResourceService;
import cn.hush.dar.task.dao.entity.TaskEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AntennaSchedulingService {
    private static final double ADJ_TOLERANCE = 1.05;
    private static final int DEFAULT_MAX_ACTIVE_REUSE = 3;
    private static final double DEFAULT_FREQUENCY_CONFLICT_GAP = 5.0;
    private static final int MAX_DP_CANDIDATES = 18;
    private static final double SURFACE_SWITCH_PENALTY = 5000.0;

    private final ResourceService resourceService;

    public boolean canAllocate(TaskEntity task) {
        int required = requiredUnits(task);
        return required <= 0 || selectPlan(task).isFeasible(required);
    }

    public List<Integer> selectAntennaIds(TaskEntity task) {
        return selectPlan(task).getAntennaIds();
    }

    public SelectionResult selectPlan(TaskEntity task) {
        int required = requiredUnits(task);
        if (required <= 0) return SelectionResult.empty("NONE");
        List<AntennaResource> all = resourceService.getAllAntennas();
        if (all == null || all.isEmpty()) return SelectionResult.empty("NONE");

        Map<Integer, List<AntennaAlloc>> allocMap = resourceService.getActiveAntennaAllocs().stream()
                .collect(Collectors.groupingBy(AntennaAlloc::getAntennaId));
        List<AntennaResource> candidates = all.stream()
                .filter(a -> a.getStatus() == null || a.getStatus() != 2)
                .filter(a -> isAntennaCandidate(a, task, allocMap))
                .sorted(candidateComparator(allocMap))
                .collect(Collectors.toList());
        if (candidates.size() < required) return SelectionResult.empty("NONE");

        Map<Integer, List<Integer>> adjacency = buildAdjacency(candidates, all);
        List<SurfaceBucket> buckets = buildSurfaceBuckets(candidates, allocMap, task);
        String preferred = normalize(task.getPreferredSurface());
        boolean allowCross = task.getAllowCrossSurface() == null || Boolean.TRUE.equals(task.getAllowCrossSurface());

        if (preferred != null) {
            for (SurfaceBucket bucket : buckets) {
                if (preferred.equals(bucket.surfaceCode) && bucket.antennas.size() >= required) {
                    SelectionResult plan = dispatch(resolveMode(task, bucket.antennas.size(), 1, required), task, bucket.antennas, adjacency, allocMap, required, bucket.surfaceCode, buckets);
                    if (plan.isFeasible(required)) return plan;
                    break;
                }
            }
            if (!allowCross) return SelectionResult.empty("PREFERRED_SURFACE_BLOCKED");
        }

        SelectionResult bestSingle = SelectionResult.empty("NONE");
        for (SurfaceBucket bucket : buckets) {
            if (bucket.antennas.size() < required) continue;
            if (preferred != null && preferred.equals(bucket.surfaceCode)) continue;
            SelectionResult plan = dispatch(resolveMode(task, bucket.antennas.size(), 1, required), task, bucket.antennas, adjacency, allocMap, required, bucket.surfaceCode, buckets);
            if (plan.isBetterThan(bestSingle, required)) bestSingle = plan;
        }
        if (bestSingle.isFeasible(required)) return bestSingle;
        if (!allowCross) return bestSingle;

        SelectionResult global = dispatch(resolveMode(task, candidates.size(), buckets.size(), required), task, candidates, adjacency, allocMap, required, null, buckets);
        if (global.isFeasible(required)) return global;
        return fallbackPlan(candidates, allocMap, required, "FALLBACK");
    }

    private SelectionResult dispatch(ScheduleMode primary, TaskEntity task, List<AntennaResource> scope,
                                     Map<Integer, List<Integer>> adjacency,
                                     Map<Integer, List<AntennaAlloc>> allocMap,
                                     int required, String forcedSurface, List<SurfaceBucket> buckets) {
        List<ScheduleMode> attempts = new ArrayList<>();
        attempts.add(primary);
        if (scope.stream().map(a -> surfaceKey(a.getSurfaceCode())).distinct().count() <= 1) {
            addAttempt(attempts, ScheduleMode.DIJKSTRA); addAttempt(attempts, ScheduleMode.BFS);
            addAttempt(attempts, ScheduleMode.GREEDY); addAttempt(attempts, ScheduleMode.DP);
        } else {
            addAttempt(attempts, ScheduleMode.HEAP); addAttempt(attempts, ScheduleMode.GREEDY);
            addAttempt(attempts, ScheduleMode.DIJKSTRA); addAttempt(attempts, ScheduleMode.BFS); addAttempt(attempts, ScheduleMode.DP);
        }
        SelectionResult best = SelectionResult.empty(primary.name());
        for (ScheduleMode mode : attempts) {
            SelectionResult candidate = switch (mode) {
                case BFS -> bfsPlan(scope, adjacency, allocMap, required, forcedSurface);
                case DIJKSTRA -> dijkstraPlan(scope, adjacency, allocMap, required, forcedSurface);
                case GREEDY -> greedyPlan(scope, adjacency, allocMap, required, forcedSurface);
                case HEAP -> heapPlan(scope, adjacency, allocMap, required, task, buckets);
                case DP -> dpPlan(scope, adjacency, allocMap, required, forcedSurface);
                default -> SelectionResult.empty("AUTO");
            };
            if (candidate.isFeasible(required)) return candidate;
            if (candidate.isBetterThan(best, required)) best = candidate;
        }
        return best;
    }

    private void addAttempt(List<ScheduleMode> attempts, ScheduleMode mode) {
        if (mode != null && mode != ScheduleMode.AUTO && !attempts.contains(mode)) attempts.add(mode);
    }

    private SelectionResult bfsPlan(List<AntennaResource> scope, Map<Integer, List<Integer>> adjacency,
                                    Map<Integer, List<AntennaAlloc>> allocMap, int required, String forcedSurface) {
        Map<Integer, AntennaResource> map = toMap(scope);
        SelectionResult best = SelectionResult.empty("BFS");
        for (AntennaResource start : orderedStarts(scope, allocMap)) {
            Queue<Integer> queue = new ArrayDeque<>();
            Set<Integer> visited = new HashSet<>();
            List<Integer> cluster = new ArrayList<>();
            queue.offer(start.getId()); visited.add(start.getId());
            while (!queue.isEmpty() && cluster.size() < required) {
                Integer currentId = queue.poll(); cluster.add(currentId);
                List<Integer> neighbors = adjacency.getOrDefault(currentId, Collections.emptyList()).stream()
                        .filter(map::containsKey).filter(id -> !visited.contains(id))
                        .sorted(Comparator.comparingDouble(id -> baseNodeWeight(map.get(id), allocMap))).toList();
                for (Integer id : neighbors) { visited.add(id); queue.offer(id); }
            }
            if (cluster.size() >= required) {
                SelectionResult candidate = buildResult(cluster.subList(0, required), map, allocMap, "BFS", forcedSurface);
                if (candidate.isBetterThan(best, required)) best = candidate;
            }
        }
        return best;
    }

    private SelectionResult dijkstraPlan(List<AntennaResource> scope, Map<Integer, List<Integer>> adjacency,
                                         Map<Integer, List<AntennaAlloc>> allocMap, int required, String forcedSurface) {
        Map<Integer, AntennaResource> map = toMap(scope);
        SelectionResult best = SelectionResult.empty("DIJKSTRA");
        for (AntennaResource start : orderedStarts(scope, allocMap)) {
            PriorityQueue<PathNode> frontier = new PriorityQueue<>(Comparator.comparingDouble(PathNode::score).thenComparing(PathNode::antennaId));
            frontier.offer(new PathNode(start.getId(), 0.0));
            Set<Integer> chosen = new HashSet<>();
            List<Integer> cluster = new ArrayList<>();
            while (!frontier.isEmpty() && cluster.size() < required) {
                PathNode current = frontier.poll();
                if (!chosen.add(current.antennaId())) continue;
                cluster.add(current.antennaId());
                for (Integer id : adjacency.getOrDefault(current.antennaId(), Collections.emptyList())) {
                    if (!chosen.contains(id) && map.containsKey(id)) frontier.offer(new PathNode(id, current.score() + transitionCost(map.get(current.antennaId()), map.get(id), allocMap)));
                }
            }
            if (cluster.size() >= required) {
                SelectionResult candidate = buildResult(cluster.subList(0, required), map, allocMap, "DIJKSTRA", forcedSurface);
                if (candidate.isBetterThan(best, required)) best = candidate;
            }
        }
        return best;
    }
    private SelectionResult greedyPlan(List<AntennaResource> scope, Map<Integer, List<Integer>> adjacency,
                                       Map<Integer, List<AntennaAlloc>> allocMap, int required, String forcedSurface) {
        Map<Integer, AntennaResource> map = toMap(scope);
        SelectionResult best = SelectionResult.empty("GREEDY");
        for (AntennaResource start : orderedStarts(scope, allocMap)) {
            List<Integer> cluster = new ArrayList<>();
            Set<Integer> chosen = new HashSet<>();
            Set<Integer> frontier = new HashSet<>();
            chosen.add(start.getId()); cluster.add(start.getId()); frontier.addAll(adjacency.getOrDefault(start.getId(), Collections.emptyList()));
            while (cluster.size() < required) {
                Integer nextId = frontier.stream().filter(map::containsKey).filter(id -> !chosen.contains(id))
                        .min(Comparator.comparingDouble(id -> greedyNodeScore(id, cluster, map, allocMap))).orElse(null);
                if (nextId == null) {
                    nextId = scope.stream().map(AntennaResource::getId).filter(id -> !chosen.contains(id))
                            .min(Comparator.comparingDouble(id -> greedyNodeScore(id, cluster, map, allocMap) + SURFACE_SWITCH_PENALTY)).orElse(null);
                }
                if (nextId == null) break;
                chosen.add(nextId); cluster.add(nextId); frontier.remove(nextId);
                frontier.addAll(adjacency.getOrDefault(nextId, Collections.emptyList())); frontier.removeAll(chosen);
            }
            if (cluster.size() >= required) {
                SelectionResult candidate = buildResult(cluster.subList(0, required), map, allocMap, "GREEDY", forcedSurface);
                if (candidate.isBetterThan(best, required)) best = candidate;
            }
        }
        return best;
    }

    private SelectionResult heapPlan(List<AntennaResource> scope, Map<Integer, List<Integer>> adjacency,
                                     Map<Integer, List<AntennaAlloc>> allocMap, int required, TaskEntity task, List<SurfaceBucket> providedBuckets) {
        List<SurfaceBucket> buckets = (providedBuckets == null || providedBuckets.isEmpty()) ? buildSurfaceBuckets(scope, allocMap, task) : providedBuckets.stream()
                .filter(bucket -> bucket.antennas.stream().anyMatch(scope::contains)).toList();
        if (buckets.isEmpty()) return SelectionResult.empty("HEAP");
        Map<Integer, AntennaResource> map = toMap(scope);
        Map<String, List<Integer>> orders = new LinkedHashMap<>();
        for (SurfaceBucket bucket : buckets) {
            List<Integer> order = buildWeightedOrdering(bucket.antennas, adjacency, allocMap);
            if (!order.isEmpty()) orders.put(bucket.surfaceCode, order);
        }
        if (orders.isEmpty()) return SelectionResult.empty("HEAP");
        PriorityQueue<SurfaceCursor> queue = new PriorityQueue<>(Comparator.comparingDouble(SurfaceCursor::score).thenComparing(SurfaceCursor::surfaceCode));
        Set<String> usedSurfaces = new HashSet<>();
        Set<Integer> chosen = new HashSet<>();
        List<Integer> cluster = new ArrayList<>();
        for (Map.Entry<String, List<Integer>> entry : orders.entrySet()) queue.offer(new SurfaceCursor(entry.getKey(), 0, heapCursorScore(entry.getValue().get(0), map, allocMap, false)));
        while (!queue.isEmpty() && cluster.size() < required) {
            SurfaceCursor cursor = queue.poll();
            List<Integer> order = orders.get(cursor.surfaceCode());
            if (order == null || cursor.index() >= order.size()) continue;
            Integer antennaId = order.get(cursor.index());
            if (chosen.add(antennaId)) { cluster.add(antennaId); usedSurfaces.add(cursor.surfaceCode()); }
            int next = cursor.index() + 1;
            if (next < order.size()) queue.offer(new SurfaceCursor(cursor.surfaceCode(), next, heapCursorScore(order.get(next), map, allocMap, !usedSurfaces.contains(cursor.surfaceCode())) + next * 5.0));
        }
        return cluster.size() < required ? SelectionResult.empty("HEAP") : buildResult(cluster.subList(0, required), map, allocMap, "HEAP", null);
    }

    private SelectionResult dpPlan(List<AntennaResource> scope, Map<Integer, List<Integer>> adjacency,
                                   Map<Integer, List<AntennaAlloc>> allocMap, int required, String forcedSurface) {
        if (required <= 0 || required > MAX_DP_CANDIDATES) return SelectionResult.empty("DP");
        List<AntennaResource> limited = orderedStarts(scope, allocMap).stream().limit(MAX_DP_CANDIDATES).toList();
        if (limited.size() < required) return SelectionResult.empty("DP");
        Map<Integer, Integer> indexMap = new HashMap<>();
        for (int i = 0; i < limited.size(); i++) indexMap.put(limited.get(i).getId(), i);
        long[] neighborMasks = new long[limited.size()];
        double[] nodeWeights = new double[limited.size()];
        for (int i = 0; i < limited.size(); i++) {
            AntennaResource antenna = limited.get(i); nodeWeights[i] = baseNodeWeight(antenna, allocMap); long mask = 0L;
            for (Integer id : adjacency.getOrDefault(antenna.getId(), Collections.emptyList())) { Integer idx = indexMap.get(id); if (idx != null) mask |= (1L << idx); }
            neighborMasks[i] = mask;
        }
        DpBest best = new DpBest();
        for (int i = 0; i < limited.size(); i++) {
            Map<String, Double> memo = new HashMap<>();
            searchDp(1L << i, neighborMasks[i] & ~(1L << i), required, nodeWeights[i], limited.size(), neighborMasks, nodeWeights, memo, best);
        }
        if (best.mask == 0L) return SelectionResult.empty("DP");
        List<Integer> cluster = new ArrayList<>();
        for (int i = 0; i < limited.size(); i++) if ((best.mask & (1L << i)) != 0) cluster.add(limited.get(i).getId());
        return buildResult(cluster, toMap(limited), allocMap, "DP", forcedSurface);
    }

    private void searchDp(long chosenMask, long frontierMask, int required, double currentWeight, int size,
                          long[] neighborMasks, double[] nodeWeights, Map<String, Double> memo, DpBest best) {
        int chosenCount = Long.bitCount(chosenMask);
        if (chosenCount == required) { if (currentWeight < best.weight) { best.weight = currentWeight; best.mask = chosenMask; } return; }
        if (chosenCount > required || currentWeight >= best.weight) return;
        long remain = ((1L << size) - 1) & ~chosenMask;
        long candidates = frontierMask & remain;
        if (candidates == 0L) return;
        String key = chosenMask + ":" + frontierMask;
        Double seen = memo.get(key);
        if (seen != null && seen <= currentWeight) return;
        memo.put(key, currentWeight);
        long iter = candidates;
        while (iter != 0L) {
            int idx = Long.numberOfTrailingZeros(iter); long bit = 1L << idx; iter &= ~bit;
            searchDp(chosenMask | bit, (frontierMask | neighborMasks[idx]) & ~(chosenMask | bit), required,
                    currentWeight + nodeWeights[idx], size, neighborMasks, nodeWeights, memo, best);
        }
    }

    private List<Integer> buildWeightedOrdering(List<AntennaResource> antennas, Map<Integer, List<Integer>> adjacency,
                                                Map<Integer, List<AntennaAlloc>> allocMap) {
        if (antennas.isEmpty()) return Collections.emptyList();
        SelectionResult greedy = greedyPlan(antennas, adjacency, allocMap, antennas.size(), surfaceKey(antennas.get(0).getSurfaceCode()));
        if (!greedy.getAntennaIds().isEmpty()) return greedy.getAntennaIds();
        return antennas.stream().sorted(candidateComparator(allocMap)).map(AntennaResource::getId).toList();
    }

    private SelectionResult fallbackPlan(List<AntennaResource> candidates, Map<Integer, List<AntennaAlloc>> allocMap, int required, String algorithm) {
        Map<Integer, AntennaResource> map = toMap(candidates);
        List<Integer> ids = candidates.stream().sorted(candidateComparator(allocMap)).limit(required).map(AntennaResource::getId).toList();
        return buildResult(ids, map, allocMap, algorithm, null);
    }

    private SelectionResult buildResult(List<Integer> cluster, Map<Integer, AntennaResource> map,
                                        Map<Integer, List<AntennaAlloc>> allocMap, String algorithm, String forcedSurface) {
        List<Integer> ids = new ArrayList<>(cluster);
        return new SelectionResult(ids, algorithm, forcedSurface != null ? forcedSurface : inferSurface(ids, map), computeClusterScore(ids, map, allocMap));
    }

    private List<SurfaceBucket> buildSurfaceBuckets(List<AntennaResource> candidates, Map<Integer, List<AntennaAlloc>> allocMap, TaskEntity task) {
        String preferred = normalize(task.getPreferredSurface());
        Map<String, List<AntennaResource>> grouped = candidates.stream().collect(Collectors.groupingBy(a -> surfaceKey(a.getSurfaceCode())));
        return grouped.entrySet().stream()
                .map(entry -> new SurfaceBucket(entry.getKey(), entry.getValue(), averageNodeWeight(entry.getValue(), allocMap)))
                .sorted(Comparator.comparing((SurfaceBucket bucket) -> !bucket.surfaceCode.equals(preferred))
                        .thenComparing((SurfaceBucket bucket) -> -bucket.antennas.size())
                        .thenComparingDouble(SurfaceBucket::averageWeight)
                        .thenComparing(SurfaceBucket::surfaceCode))
                .toList();
    }

    private Comparator<AntennaResource> candidateComparator(Map<Integer, List<AntennaAlloc>> allocMap) {
        return Comparator.comparingInt((AntennaResource a) -> getActiveReuse(a.getId(), allocMap))
                .thenComparingInt(a -> safeInt(a.getReuseCount()))
                .thenComparingDouble(a -> safe(a.getXPos()))
                .thenComparingDouble(a -> safe(a.getYPos()))
                .thenComparing(AntennaResource::getId);
    }

    private List<AntennaResource> orderedStarts(List<AntennaResource> scope, Map<Integer, List<AntennaAlloc>> allocMap) {
        return scope.stream().sorted(candidateComparator(allocMap)).toList();
    }

    private ScheduleMode resolveMode(TaskEntity task, int candidateCount, int surfaceCount, int required) {
        int priority = task.getPriority() == null ? 0 : task.getPriority();
        Integer deadlineMs = task.getDeadlineMs();
        if (candidateCount <= MAX_DP_CANDIDATES && required <= 8 && priority >= 80) return ScheduleMode.DP;
        if (surfaceCount <= 1) return deadlineMs != null && deadlineMs <= 100 ? ScheduleMode.BFS : (required <= 6 ? ScheduleMode.BFS : ScheduleMode.DIJKSTRA);
        if (deadlineMs != null && deadlineMs <= 100) return ScheduleMode.GREEDY;
        return required <= 8 ? ScheduleMode.GREEDY : ScheduleMode.HEAP;
    }
    private Map<Integer, List<Integer>> buildAdjacency(List<AntennaResource> candidates, List<AntennaResource> allAntennas) {
        Map<Integer, List<Integer>> adjacency = new HashMap<>();
        double stepX = inferStep(allAntennas.stream().map(AntennaResource::getXPos).toList());
        double stepY = inferStep(allAntennas.stream().map(AntennaResource::getYPos).toList());
        double thresholdX = stepX <= 0 ? 0.0 : stepX * ADJ_TOLERANCE;
        double thresholdY = stepY <= 0 ? 0.0 : stepY * ADJ_TOLERANCE;
        for (AntennaResource antenna : candidates) {
            List<Integer> neighbors = new ArrayList<>();
            for (AntennaResource other : candidates) {
                if (antenna.getId().equals(other.getId())) continue;
                double dx = Math.abs(safe(antenna.getXPos()) - safe(other.getXPos()));
                double dy = Math.abs(safe(antenna.getYPos()) - safe(other.getYPos()));
                boolean xAdjacent = dx > 0 && thresholdX > 0 && dx <= thresholdX && dy < 0.0001;
                boolean yAdjacent = dy > 0 && thresholdY > 0 && dy <= thresholdY && dx < 0.0001;
                if (xAdjacent || yAdjacent) neighbors.add(other.getId());
            }
            adjacency.put(antenna.getId(), neighbors);
        }
        return adjacency;
    }

    private boolean isAntennaCandidate(AntennaResource antenna, TaskEntity task, Map<Integer, List<AntennaAlloc>> allocMap) {
        int activeReuse = getActiveReuse(antenna.getId(), allocMap);
        int reuseLimit = task.getTargetReuseLimit() == null ? DEFAULT_MAX_ACTIVE_REUSE : Math.max(1, task.getTargetReuseLimit());
        if (activeReuse >= reuseLimit) return false;
        return allocMap.getOrDefault(antenna.getId(), Collections.emptyList()).stream().noneMatch(alloc -> isFrequencyConflict(task, alloc));
    }

    private boolean isFrequencyConflict(TaskEntity task, AntennaAlloc alloc) {
        if (task == null || alloc == null) return false;
        Double targetFrequency = task.getBeamFrequency();
        Double occupiedFrequency = alloc.getBeamFrequency();
        if (targetFrequency == null || occupiedFrequency == null) return false;
        String targetGroup = normalize(task.getBeamGroup());
        String occupiedGroup = normalize(alloc.getBeamGroup());
        if (targetGroup == null || occupiedGroup == null) return false;
        return Math.abs(targetFrequency - occupiedFrequency) < DEFAULT_FREQUENCY_CONFLICT_GAP && !targetGroup.equals(occupiedGroup);
    }

    private int getActiveReuse(Integer antennaId, Map<Integer, List<AntennaAlloc>> allocMap) {
        return allocMap.getOrDefault(antennaId, Collections.emptyList()).size();
    }

    private double averageNodeWeight(List<AntennaResource> antennas, Map<Integer, List<AntennaAlloc>> allocMap) {
        return antennas.stream().mapToDouble(a -> baseNodeWeight(a, allocMap)).average().orElse(Double.MAX_VALUE);
    }

    private double baseNodeWeight(AntennaResource antenna, Map<Integer, List<AntennaAlloc>> allocMap) {
        if (antenna == null) return Double.MAX_VALUE;
        return getActiveReuse(antenna.getId(), allocMap) * 1000.0 + safeInt(antenna.getReuseCount()) * 10.0;
    }

    private double transitionCost(AntennaResource source, AntennaResource target, Map<Integer, List<AntennaAlloc>> allocMap) {
        return baseNodeWeight(target, allocMap) + distance(source, target);
    }

    private double greedyNodeScore(Integer antennaId, List<Integer> cluster, Map<Integer, AntennaResource> map, Map<Integer, List<AntennaAlloc>> allocMap) {
        AntennaResource antenna = map.get(antennaId);
        if (antenna == null) return Double.MAX_VALUE;
        double minDistance = cluster.stream().map(map::get).filter(Objects::nonNull).mapToDouble(existing -> distance(existing, antenna)).min().orElse(0.0);
        return baseNodeWeight(antenna, allocMap) + minDistance;
    }

    private double heapCursorScore(Integer antennaId, Map<Integer, AntennaResource> map, Map<Integer, List<AntennaAlloc>> allocMap, boolean switchSurface) {
        return baseNodeWeight(map.get(antennaId), allocMap) + (switchSurface ? SURFACE_SWITCH_PENALTY : 0.0);
    }

    private double computeClusterScore(List<Integer> cluster, Map<Integer, AntennaResource> map, Map<Integer, List<AntennaAlloc>> allocMap) {
        List<AntennaResource> antennas = cluster.stream().map(map::get).filter(Objects::nonNull).toList();
        if (antennas.isEmpty()) return Double.MAX_VALUE;
        double activeReuseScore = antennas.stream().mapToInt(a -> getActiveReuse(a.getId(), allocMap)).sum();
        double reuseScore = antennas.stream().mapToInt(a -> safeInt(a.getReuseCount())).sum();
        double minX = antennas.stream().mapToDouble(a -> safe(a.getXPos())).min().orElse(0.0);
        double maxX = antennas.stream().mapToDouble(a -> safe(a.getXPos())).max().orElse(0.0);
        double minY = antennas.stream().mapToDouble(a -> safe(a.getYPos())).min().orElse(0.0);
        double maxY = antennas.stream().mapToDouble(a -> safe(a.getYPos())).max().orElse(0.0);
        long surfaceCount = antennas.stream().map(a -> surfaceKey(a.getSurfaceCode())).distinct().count();
        return activeReuseScore * 10000.0 + reuseScore * 1000.0 + (maxX - minX) + (maxY - minY) + (surfaceCount - 1) * SURFACE_SWITCH_PENALTY;
    }

    private String inferSurface(List<Integer> cluster, Map<Integer, AntennaResource> map) {
        Set<String> surfaces = cluster.stream().map(map::get).filter(Objects::nonNull).map(a -> surfaceKey(a.getSurfaceCode())).collect(Collectors.toCollection(LinkedHashSet::new));
        if (surfaces.isEmpty()) return null;
        return surfaces.size() == 1 ? surfaces.iterator().next() : String.join(",", surfaces);
    }

    private Map<Integer, AntennaResource> toMap(List<AntennaResource> antennas) {
        return antennas.stream().collect(Collectors.toMap(AntennaResource::getId, a -> a, (left, right) -> left));
    }

    private int requiredUnits(TaskEntity task) { return task == null || task.getNeededAntennas() == null ? 0 : task.getNeededAntennas(); }
    private String surfaceKey(String value) { String normalized = normalize(value); return normalized == null ? "UNASSIGNED" : normalized; }
    private String normalize(String value) { if (value == null) return null; String trimmed = value.trim(); return trimmed.isEmpty() ? null : trimmed.toUpperCase(); }
    private double inferStep(Collection<Double> values) {
        List<Double> sorted = values.stream().filter(Objects::nonNull).distinct().sorted().toList();
        if (sorted.size() < 2) return 0.0;
        double minGap = Double.MAX_VALUE;
        for (int i = 1; i < sorted.size(); i++) { double gap = sorted.get(i) - sorted.get(i - 1); if (gap > 0) minGap = Math.min(minGap, gap); }
        return minGap == Double.MAX_VALUE ? 0.0 : minGap;
    }
    private double distance(AntennaResource left, AntennaResource right) { double dx = safe(left.getXPos()) - safe(right.getXPos()); double dy = safe(left.getYPos()) - safe(right.getYPos()); return Math.sqrt(dx * dx + dy * dy); }
    private double safe(Double value) { return value == null ? 0.0 : value; }
    private int safeInt(Integer value) { return value == null ? 0 : value; }

    private enum ScheduleMode {
        AUTO, BFS, DIJKSTRA, GREEDY, HEAP, DP;
        private static ScheduleMode from(String value) {
            if (value == null || value.isBlank()) return AUTO;
            try { return ScheduleMode.valueOf(value.trim().toUpperCase()); } catch (IllegalArgumentException ex) { return AUTO; }
        }
    }

    public static class SelectionResult {
        private final List<Integer> antennaIds;
        private final String algorithm;
        private final String surfaceCode;
        private final double score;

        public SelectionResult(List<Integer> antennaIds, String algorithm, String surfaceCode, double score) {
            this.antennaIds = antennaIds; this.algorithm = algorithm; this.surfaceCode = surfaceCode; this.score = score;
        }
        public static SelectionResult empty(String algorithm) { return new SelectionResult(Collections.emptyList(), algorithm, null, Double.MAX_VALUE); }
        public boolean isFeasible(int required) { return antennaIds != null && antennaIds.size() >= required; }
        public boolean isBetterThan(SelectionResult other, int required) {
            if (other == null) return true;
            boolean feasible = isFeasible(required), otherFeasible = other.isFeasible(required);
            if (feasible != otherFeasible) return feasible;
            if (score != other.score) return score < other.score;
            return (antennaIds == null ? 0 : antennaIds.size()) > (other.antennaIds == null ? 0 : other.antennaIds.size());
        }
        public List<Integer> getAntennaIds() { return antennaIds; }
        public String getAlgorithm() { return algorithm; }
        public String getSurfaceCode() { return surfaceCode; }
        public double getScore() { return score; }
    }

    /**
     * Benchmark: force a specific algorithm without fallback chain.
     * Read-only operation - does not allocate resources.
     */
    public SelectionResult benchmarkPlan(TaskEntity task, String forceAlgorithm) {
        int required = requiredUnits(task);
        if (required <= 0) return SelectionResult.empty("NONE");
        List<AntennaResource> all = resourceService.getAllAntennas();
        if (all == null || all.isEmpty()) return SelectionResult.empty("NONE");

        Map<Integer, List<AntennaAlloc>> allocMap = resourceService.getActiveAntennaAllocs().stream()
                .collect(Collectors.groupingBy(AntennaAlloc::getAntennaId));
        List<AntennaResource> candidates = all.stream()
                .filter(a -> a.getStatus() == null || a.getStatus() != 2)
                .filter(a -> isAntennaCandidate(a, task, allocMap))
                .sorted(candidateComparator(allocMap))
                .collect(Collectors.toList());
        if (candidates.size() < required) return SelectionResult.empty("INSUFFICIENT");

        Map<Integer, List<Integer>> adjacency = buildAdjacency(candidates, all);
        List<SurfaceBucket> buckets = buildSurfaceBuckets(candidates, allocMap, task);
        String preferred = normalize(task.getPreferredSurface());

        ScheduleMode mode = ScheduleMode.from(forceAlgorithm);
        if (mode == ScheduleMode.AUTO) return selectPlan(task);

        return switch (mode) {
            case BFS -> bfsPlan(candidates, adjacency, allocMap, required, preferred);
            case DIJKSTRA -> dijkstraPlan(candidates, adjacency, allocMap, required, preferred);
            case GREEDY -> greedyPlan(candidates, adjacency, allocMap, required, preferred);
            case HEAP -> heapPlan(candidates, adjacency, allocMap, required, task, buckets);
            case DP -> dpPlan(candidates, adjacency, allocMap, required, preferred);
            default -> SelectionResult.empty("UNKNOWN");
        };
    }

    private record PathNode(Integer antennaId, double score) {}
    private record SurfaceCursor(String surfaceCode, int index, double score) {}
    private record SurfaceBucket(String surfaceCode, List<AntennaResource> antennas, double averageWeight) {}
    private static class DpBest { private double weight = Double.MAX_VALUE; private long mask = 0L; }
}
