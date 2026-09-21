package com.ainote.module.ai.controller;

import com.ainote.common.exception.BusinessException;
import com.ainote.common.ratelimit.RateLimit;
import com.ainote.common.result.Result;
import com.ainote.common.result.ResultCode;
import com.ainote.common.util.SecurityUtil;
import com.ainote.module.ai.config.AiProperties;
import com.ainote.module.ai.dto.AiAsyncRequestDTO;
import com.ainote.module.ai.dto.AiRequestDTO;
import com.ainote.module.ai.entity.AiTask;
import com.ainote.module.ai.service.AiService;
import com.ainote.module.ai.service.AiTaskService;
import com.ainote.module.ai.vo.AiResultVO;
import com.ainote.module.ai.vo.AiTaskVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/**
 * AI 能力接口
 */
@Tag(name = "AI 模块")
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;
    private final AiProperties aiProperties;
    private final AiTaskService aiTaskService;

    /** 允许的异步任务 action */
    private static final Set<String> ALLOWED_ACTIONS =
            Set.of(AiService.OUTLINE, AiService.POLISH, AiService.SUMMARIZE, AiService.CHAT);

    @Operation(summary = "AI 生成笔记大纲")
    @PostMapping("/generate-outline")
    @RateLimit(key = "ai", capacity = 3, refillPerSecond = 1, message = "AI 调用过于频繁，请稍后再试")
    public Result<AiResultVO> outline(@RequestBody AiRequestDTO dto) {
        return Result.success(doExecute(dto, AiService.OUTLINE));
    }

    @Operation(summary = "AI 润色内容")
    @PostMapping("/polish")
    @RateLimit(key = "ai", capacity = 3, refillPerSecond = 1, message = "AI 调用过于频繁，请稍后再试")
    public Result<AiResultVO> polish(@RequestBody AiRequestDTO dto) {
        return Result.success(doExecute(dto, AiService.POLISH));
    }

    @Operation(summary = "AI 生成摘要")
    @PostMapping("/summarize")
    @RateLimit(key = "ai", capacity = 3, refillPerSecond = 1, message = "AI 调用过于频繁，请稍后再试")
    public Result<AiResultVO> summarize(@RequestBody AiRequestDTO dto) {
        return Result.success(doExecute(dto, AiService.SUMMARIZE));
    }

    @Operation(summary = "基于笔记内容问答")
    @PostMapping("/chat")
    @RateLimit(key = "ai", capacity = 3, refillPerSecond = 1, message = "AI 调用过于频繁，请稍后再试")
    public Result<AiResultVO> chat(@RequestBody AiRequestDTO dto) {
        return Result.success(doExecute(dto, AiService.CHAT));
    }

    private AiResultVO doExecute(AiRequestDTO dto, String action) {
        String text = aiService.execute(
                action,
                dto.getTitle(),
                dto.getContent(),
                dto.getQuestion());
        AiResultVO vo = new AiResultVO();
        vo.setAction(action);
        vo.setMock(aiProperties.isMockEnabled() || !aiProperties.isRealReady());
        vo.setModel(aiProperties.isRealReady() ? aiProperties.getModel() : null);
        vo.setResult(text);
        return vo;
    }

    /**
     * AI 异步任务：入队后立即返回 taskId，由 MQ 消费者异步执行（解耦/削峰）。
     */
    @Operation(summary = "AI 异步任务（MQ 解耦，入队即返回 taskId）")
    @PostMapping("/async")
    @RateLimit(key = "ai", capacity = 3, refillPerSecond = 1, message = "AI 调用过于频繁，请稍后再试")
    public Result<AiTaskVO> async(@RequestBody @Valid AiAsyncRequestDTO dto) {
        String action = dto.getAction();
        if (!ALLOWED_ACTIONS.contains(action)) {
            throw new BusinessException("不支持的 AI 动作：" + action);
        }
        AiTask task = new AiTask();
        task.setUserId(SecurityUtil.getUserId());
        task.setAction(action);
        task.setTitle(dto.getTitle());
        task.setContent(dto.getContent());
        task.setQuestion(dto.getQuestion());
        Long taskId = aiTaskService.submit(task);
        return Result.success(toVO(taskId, AiTask.STATUS_PENDING, null, null));
    }

    /**
     * 查询异步任务状态（前端轮询，成功后取 result）。
     */
    @Operation(summary = "查询 AI 异步任务状态")
    @GetMapping("/task/{taskId}")
    public Result<AiTaskVO> task(@PathVariable("taskId") Long taskId) {
        AiTask task = aiTaskService.getById(taskId);
        if (task == null) {
            throw new BusinessException("任务不存在");
        }
        // 越权校验：仅任务发起者可查询
        if (!task.getUserId().equals(SecurityUtil.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权访问该任务");
        }
        return Result.success(toVO(task.getId(), task.getStatus(),
                task.getResult(), task.getErrorMsg()));
    }

    private AiTaskVO toVO(Long taskId, Integer status, String result, String errorMsg) {
        AiTaskVO vo = new AiTaskVO();
        vo.setTaskId(taskId);
        vo.setStatus(status);
        vo.setStatusText(statusText(status));
        vo.setResult(result);
        vo.setErrorMsg(errorMsg);
        return vo;
    }

    private String statusText(Integer status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case 0 -> "待处理";
            case 1 -> "处理中";
            case 2 -> "成功";
            case 3 -> "失败";
            default -> "未知";
        };
    }
}