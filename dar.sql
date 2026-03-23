create table t_antenna
(
    id          int auto_increment comment '阵元ID'
        primary key,
    code        varchar(32)                        null comment '阵元编号 (e.g., ANT-001)',
    x_pos       double                             not null comment 'X坐标 (用于阵列布局可视化)',
    y_pos       double                             not null comment 'Y坐标',
    status      int      default 0                 null comment '状态: 0-空闲, 1-占用, 2-故障',
    task_id     int                                null comment '当前占用的任务ID',
    update_time datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    phase       double   default 0                 null comment '相位',
    amplitude   double   default 1                 null comment '幅度'
)
    comment '天线阵元资源表';

create table t_cpu
(
    id          int auto_increment
        primary key,
    hostname    varchar(64)   null comment '主机名/节点名',
    ip_address  varchar(32)   null comment 'IP地址',
    total_cores int           null comment '总核心数',
    used_cores  int default 0 null comment '已用核心数',
    status      int default 0 null comment '状态: 0-空闲, 1-繁忙, 2-离线'
)
    comment 'CPU计算资源表';

create table t_gpu
(
    id           int auto_increment
        primary key,
    model        varchar(64)   null comment '型号 (e.g., NVIDIA A100)',
    total_memory int           null comment '总显存 (GB)',
    used_memory  int default 0 null comment '已用显存 (GB)',
    status       int default 0 null comment '状态: 0-空闲, 1-占用, 2-故障'
)
    comment '加速卡资源表';

create table t_task
(
    id               int auto_increment
        primary key,
    name             varchar(64)                        not null comment '任务名称',
    priority         int      default 0                 null comment '优先级 (数值越大越高)',
    needed_antennas  int      default 0                 null comment '所需天线数量',
    needed_cpu_cores int      default 0                 null comment '所需CPU核数',
    needed_gpu_mem   int      default 0                 null comment '所需GPU显存(GB)',
    duration         int      default 10                null comment '任务持续时间(秒)',
    status           int      default 0                 null comment '状态: 0-待调度, 1-运行中, 2-已完成, 3-失败',
    create_time      datetime default CURRENT_TIMESTAMP null,
    start_time       datetime                           null,
    end_time         datetime                           null
)
    comment '任务表';

create table user
(
    id              bigint auto_increment comment '主键ID'
        primary key,
    username        varchar(50)                        not null comment '登录用户名',
    password        varchar(255)                       not null comment '密码',
    nickname        varchar(100)                       null comment '用户昵称或显示名称',
    last_login_time datetime                           null comment '最近登录时间',
    create_time     datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    update_time     datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '修改时间',
    constraint uk_username
        unique (username)
)
    comment '用户表';

create table t_scheduler_config
(
    id          int auto_increment
        primary key,
    strategy    varchar(32) default 'DRF' not null comment '调度策略: DRF / PRIORITY / FCFS',
    update_time datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP
)
    comment '调度策略配置';
