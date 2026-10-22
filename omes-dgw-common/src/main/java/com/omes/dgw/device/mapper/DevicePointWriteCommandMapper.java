package com.omes.dgw.device.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.omes.dgw.device.dto.DevicePointWriteCommandQuery;
import com.omes.dgw.device.dto.DevicePointWriteTaskHistoryVo;
import com.omes.dgw.device.entity.DevicePointWriteCommand;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DevicePointWriteCommandMapper extends BaseMapper<DevicePointWriteCommand> {

    IPage<DevicePointWriteTaskHistoryVo> selectTaskHistoryPage(
            Page<DevicePointWriteTaskHistoryVo> page,
            @Param("q") DevicePointWriteCommandQuery query
    );
}
