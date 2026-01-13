package cn.hush.dar.resource.dao.mapper;


import cn.hush.dar.resource.dao.entity.AntennaResource;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * @program: DAR
 * @description:
 * @author: Hush
 * @create: 2026-01-05 20:16
 **/

@Mapper
public interface AntennaMapper extends BaseMapper<AntennaResource> {
    // BaseMapper 已内置基本的 CRUD，如需复杂SQL可在此扩展
}