CREATE TABLE t_antenna (
    id INT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(32) NULL COMMENT '物理天线编号',
    name VARCHAR(64) NULL COMMENT '物理天线名称',
    surface_code VARCHAR(64) NULL COMMENT '所属阵面',
    x_pos DOUBLE NULL COMMENT '天线X坐标',
    y_pos DOUBLE NULL COMMENT '天线Y坐标',
    status INT DEFAULT 0 NULL COMMENT '状态: 0-在线, 1-繁忙, 2-故障'
) COMMENT '物理天线表';

CREATE TABLE t_antenna_unit (
    id INT AUTO_INCREMENT PRIMARY KEY,
    antenna_id INT NOT NULL COMMENT '所属物理天线ID',
    unit_code VARCHAR(64) NULL COMMENT '阵元编号',
    x_pos DOUBLE NOT NULL COMMENT '阵元X坐标',
    y_pos DOUBLE NOT NULL COMMENT '阵元Y坐标',
    phase DOUBLE DEFAULT 0 NULL COMMENT '相位',
    amplitude DOUBLE DEFAULT 1 NULL COMMENT '幅度',
    reuse_count INT DEFAULT 0 NULL COMMENT '历史复用次数',
    surface_code VARCHAR(64) NULL COMMENT '阵面分区',
    status INT DEFAULT 0 NULL COMMENT '状态: 0-空闲, 1-占用, 2-故障',
    task_id INT NULL COMMENT '最近占用任务ID'
) COMMENT '阵元资源表';

CREATE TABLE t_antenna_unit_alloc (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id INT NOT NULL COMMENT '任务ID',
    antenna_id INT NOT NULL COMMENT '阵元ID',
    beam_frequency DOUBLE NULL COMMENT '波束频率',
    beam_group VARCHAR(64) NULL COMMENT '波束/阵面分组',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP NULL COMMENT '创建时间'
) COMMENT '任务-阵元分配记录';

CREATE TABLE t_cpu (
    id INT AUTO_INCREMENT PRIMARY KEY,
    hostname VARCHAR(64) NULL COMMENT '主机名',
    ip_address VARCHAR(32) NULL COMMENT 'IP地址',
    total_cores INT NULL COMMENT '总核心数',
    used_cores INT DEFAULT 0 NULL COMMENT '已用核心数',
    status INT DEFAULT 0 NULL COMMENT '状态: 0-空闲, 1-繁忙, 2-离线'
) COMMENT 'CPU 资源表';

CREATE TABLE t_cpu_alloc (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id INT NOT NULL COMMENT '任务ID',
    cpu_id INT NOT NULL COMMENT 'CPU ID',
    cores INT NOT NULL COMMENT '分配核心数',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP NULL COMMENT '创建时间'
) COMMENT '任务-CPU 分配记录';

CREATE TABLE t_gpu (
    id INT AUTO_INCREMENT PRIMARY KEY,
    model VARCHAR(64) NULL COMMENT '型号',
    total_memory INT NULL COMMENT '总显存(GB)',
    used_memory INT DEFAULT 0 NULL COMMENT '已用显存(GB)',
    status INT DEFAULT 0 NULL COMMENT '状态: 0-空闲, 1-占用, 2-故障'
) COMMENT 'GPU 资源表';

CREATE TABLE t_gpu_alloc (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id INT NOT NULL COMMENT '任务ID',
    gpu_id INT NOT NULL COMMENT 'GPU ID',
    mem INT NOT NULL COMMENT '分配显存(GB)',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP NULL COMMENT '创建时间'
) COMMENT '任务-GPU 分配记录';

CREATE TABLE t_task (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(64) NOT NULL COMMENT '任务名称',
    priority INT DEFAULT 0 NULL COMMENT '优先级',
    needed_antennas INT DEFAULT 0 NULL COMMENT '所需阵元数',
    needed_cpu_cores INT DEFAULT 0 NULL COMMENT '所需 CPU 核数',
    needed_gpu_mem INT DEFAULT 0 NULL COMMENT '所需 GPU 显存(GB)',
    beam_frequency DOUBLE NULL COMMENT '波束频率',
    beam_group VARCHAR(64) NULL COMMENT '波束/阵面分组',
    preferred_surface VARCHAR(64) NULL COMMENT '优选阵面',
    antenna_schedule_mode VARCHAR(32) NULL COMMENT '阵元调度模式: AUTO/BFS/DIJKSTRA/GREEDY/HEAP/DP',
    deadline_ms INT NULL COMMENT '调度时延目标(ms)',
    allow_cross_surface TINYINT(1) DEFAULT 1 NULL COMMENT '是否允许跨阵面分配',
    target_reuse_limit INT DEFAULT 3 NULL COMMENT '单阵元最大并发复用上限',
    duration INT DEFAULT 10 NULL COMMENT '任务持续时间(秒)',
    remaining_seconds INT DEFAULT 10 NULL COMMENT '剩余运行时间(秒)',
    virtual_share DOUBLE DEFAULT 0 NULL COMMENT '公平份额累计值',
    status INT DEFAULT 0 NULL COMMENT '状态: 0-待调度, 1-运行中, 2-已完成, 3-已取消/失败',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP NULL,
    start_time DATETIME NULL,
    end_time DATETIME NULL
) COMMENT '任务表';

CREATE TABLE t_schedule_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id INT NOT NULL COMMENT '任务ID',
    action VARCHAR(64) NOT NULL COMMENT '调度动作',
    detail VARCHAR(512) NULL COMMENT '调度详情',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP NULL COMMENT '创建时间'
) COMMENT '调度过程日志';

CREATE TABLE t_scheduler_config (
    id INT AUTO_INCREMENT PRIMARY KEY,
    strategy VARCHAR(32) DEFAULT 'DRF' NOT NULL COMMENT '计算资源调度策略: DRF / PRIORITY / FCFS',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP NULL ON UPDATE CURRENT_TIMESTAMP
) COMMENT '调度策略配置';

CREATE TABLE user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL COMMENT '登录用户名',
    password VARCHAR(255) NOT NULL COMMENT '密码',
    nickname VARCHAR(100) NULL COMMENT '昵称',
    last_login_time DATETIME NULL COMMENT '最近登录时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    CONSTRAINT uk_username UNIQUE (username)
) COMMENT '用户表';

-- Compute scheduling extensions
ALTER TABLE t_task
ADD COLUMN compute_schedule_mode VARCHAR(32) NULL COMMENT '计算资源调度模式: BALANCE / PACKED',
ADD COLUMN depends_on_task_ids VARCHAR(255) NULL COMMENT '依赖任务ID列表,逗号分隔',
ADD COLUMN repel_task_ids VARCHAR(255) NULL COMMENT '互斥任务ID列表,逗号分隔';
