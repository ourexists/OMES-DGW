package com.omes.dgw.device.service;

import com.omes.dgw.common.PageResult;
import com.omes.dgw.device.dto.DevicePointWriteBatchRequest;
import com.omes.dgw.device.dto.DevicePointWriteCommandQuery;
import com.omes.dgw.device.dto.DevicePointWriteCommandVo;
import com.omes.dgw.device.dto.DevicePointWriteRequest;
import com.omes.dgw.device.dto.DevicePointWriteTaskHistoryVo;
import com.omes.dgw.device.dto.DevicePointWriteTaskVo;
import com.omes.dgw.device.entity.DevicePointWriteCommand;

import java.util.List;
import java.util.Optional;

public interface DevicePointWriteService {

    DevicePointWriteTaskVo enqueue(DevicePointWriteRequest request);

    DevicePointWriteTaskVo enqueueBatch(DevicePointWriteBatchRequest request);

    PageResult<DevicePointWriteCommandVo> pageHistory(DevicePointWriteCommandQuery query);

    PageResult<DevicePointWriteTaskHistoryVo> pageTaskHistory(DevicePointWriteCommandQuery query);

    List<String> listRunnableTaskIds();

    Optional<DevicePointWriteCommand> claimNextPending(String taskId);

    void execute(DevicePointWriteCommand command);

    /**
     * 停机/重启恢复：将中断时遗留的 RUNNING 重置为 PENDING，便于重新领取执行。
     *
     * @return 重置条数
     */
    int recoverStaleRunningCommands();
}
