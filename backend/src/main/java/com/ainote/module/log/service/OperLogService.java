package com.ainote.module.log.service;

import com.ainote.module.log.entity.OperLog;
import com.ainote.module.log.mapper.OperLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 操作日志服务：异步写入，避免影响主流程
 */
@Service
@RequiredArgsConstructor
public class OperLogService {

    private final OperLogMapper operLogMapper;

    @Async
    public void save(OperLog log) {
        try {
            operLogMapper.insert(log);
        } catch (Exception ignored) {
            // 日志写入失败不影响业务
        }
    }
}