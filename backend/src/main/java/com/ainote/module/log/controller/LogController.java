package com.ainote.module.log.controller;

import com.ainote.common.result.Result;
import com.ainote.common.util.SecurityUtil;
import com.ainote.module.log.entity.OperLog;
import com.ainote.module.log.mapper.OperLogMapper;
import com.ainote.module.log.vo.LogVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 操作日志接口
 */
@Tag(name = "日志模块")
@RestController
@RequestMapping("/log")
@RequiredArgsConstructor
public class LogController {

    private final OperLogMapper operLogMapper;

    @Operation(summary = "我的操作日志（分页）")
    @GetMapping("/list")
    public Result<Page<LogVO>> list(@RequestParam(value = "page", defaultValue = "1") Integer page,
                                    @RequestParam(value = "size", defaultValue = "20") Integer size) {
        Long userId = SecurityUtil.getUserId();
        Page<OperLog> p = operLogMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<OperLog>()
                        .eq(OperLog::getUserId, userId)
                        .orderByDesc(OperLog::getCreateTime));
        Page<LogVO> voPage = new Page<>(p.getCurrent(), p.getSize(), p.getTotal());
        voPage.setRecords(p.getRecords().stream().map(log -> {
            LogVO vo = new LogVO();
            BeanUtils.copyProperties(log, vo);
            return vo;
        }).toList());
        return Result.success(voPage);
    }
}