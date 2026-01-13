package cn.hush.dar.task.service.impl;


import cn.hush.dar.task.dao.entity.TaskEntity;
import cn.hush.dar.task.dao.mapper.TaskMapper;
import cn.hush.dar.task.service.TaskService;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * @program: DAR
 * @description:
 * @author: Hush
 * @create: 2026-01-06 01:43
 **/
@Service
public class TaskServiceImpl extends ServiceImpl<TaskMapper, TaskEntity> implements TaskService {
}
