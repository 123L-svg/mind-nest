package com.ainote.module.ai.controller;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.ainote.common.exception.BusinessException;
import com.ainote.common.ratelimit.RateLimit;
import com.ainote.common.result.Result;
import com.ainote.common.result.ResultCode;
import com.ainote.common.util.SecurityUtil;
import com.ainote.module.ai.config.AiProperties;
import com.ainote.module.ai.dto.AiAsyncRequestDTO;
import com.ainote.module.ai.dto.AiRequestDTO;
import com.ainote.module.ai.entity.AiTask;
import com.ainote.module.ai.service.AiChatMemoryService;
import com.ainote.module.ai.service.AiService;
import com.ainote.module.ai.service.AiTaskService;
import com.ainote.module.ai.vo.AiResultVO;
import com.ainote.module.ai.vo.AiTaskVO;
import com.ainote.module.ai.vo.ChatMessageVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    private final AiChatMemoryService chatMemory;

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

    /** chat 流式执行线程池（Java 21 虚拟线程：每个流式连接占一个，生成期间挂起不占平台线程） */
    private final ExecutorService chatStreamExecutor = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * AI 对话（SSE 流式逐字输出）。
     * <p>事件协议：delta {"t":"片段"} / done {"content":"全文"} / error {"message":"..."}；
     * 流结束后写回 Redis 多轮记忆（mock 模式跳过）。</p>
     */
    @Operation(summary = "AI 对话（SSE 流式逐字输出）")
    @PostMapping(value = "/chat/stream")
    @RateLimit(key = "ai", capacity = 3, refillPerSecond = 1, message = "AI 调用过于频繁，请稍后再试")
    public SseEmitter chatStream(@RequestBody AiRequestDTO dto, HttpServletResponse response) {
        // 关闭 Nginx 对该响应的代理缓冲，避免流被攒成一整块下发
        response.setHeader("X-Accel-Buffering", "no");
        // userId 必须在请求线程取出：异步线程拿不到 SecurityContext ThreadLocal
        Long userId = SecurityUtil.getUserId();
        SseEmitter emitter = new SseEmitter(aiProperties.getTimeoutMillis() + 30_000L);

        chatStreamExecutor.execute(() -> {
            try {
                StringBuilder full = new StringBuilder();
                String answer = aiService.chatStream(userId, dto.getNoteId(), dto.getTitle(),
                        dto.getContent(), dto.getQuestion(), delta -> {
                            full.append(delta);
                            sendSse(emitter, "delta", new JSONObject().set("t", delta));
                        });
                if (answer.length() > full.length()) {
                    full.setLength(0);
                    full.append(answer);
                }
                // 流式完成：写回多轮记忆（mock 模式跳过，避免污染真实上下文）
                boolean mock = aiProperties.isMockEnabled() || !aiProperties.isRealReady();
                if (!mock && userId != null && dto.getQuestion() != null && !dto.getQuestion().isBlank()) {
                    chatMemory.append(userId, dto.getNoteId(), dto.getQuestion(), answer);
                }
                sendSse(emitter, "done", new JSONObject().set("content", answer));
                emitter.complete();
            } catch (Exception e) {
                trySendSse(emitter, "error", new JSONObject().set("message", e.getMessage()));
                try {
                    emitter.complete();
                } catch (Exception ignore) { /* 已完成/断开 */ }
            }
        });
        return emitter;
    }

    /** 发送 SSE 事件：失败时抛出异常以中断上游 LLM 流读取（客户端已断开） */
    private void sendSse(SseEmitter emitter, String event, JSONObject data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data.toString()));
        } catch (Exception e) {
            throw new IllegalStateException("SSE 发送失败（客户端可能已断开）", e);
        }
    }

    /** 发送 SSE 事件（静默）：仅用于收尾（error 通知/complete），失败不再传播 */
    private void trySendSse(SseEmitter emitter, String event, JSONObject data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data.toString()));
        } catch (Exception ignore) { /* 客户端断开 */ }
    }

    @Operation(summary = "AI 对话历史（Redis 多轮记忆，按笔记隔离）")
    @GetMapping("/chat/history")
    public Result<List<ChatMessageVO>> chatHistory(
            @RequestParam(value = "noteId", required = false) Long noteId) {
        return Result.success(chatMemory.getHistory(SecurityUtil.getUserId(), noteId));
    }

    @Operation(summary = "清空 AI 对话记忆（按笔记隔离）")
    @DeleteMapping("/chat/history")
    public Result<Void> clearChatHistory(
            @RequestParam(value = "noteId", required = false) Long noteId) {
        chatMemory.clear(SecurityUtil.getUserId(), noteId);
        return Result.success(null);
    }

    private AiResultVO doExecute(AiRequestDTO dto, String action) {
        String paramsStr = dto.getParams() != null ? JSONUtil.toJsonStr(dto.getParams()) : null;
        String text = aiService.execute(
                SecurityUtil.getUserId(),
                dto.getNoteId(),
                action,
                paramsStr,
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
        task.setNoteId(dto.getNoteId());
        task.setAction(action);
        task.setTitle(dto.getTitle());
        task.setContent(dto.getContent());
        task.setQuestion(dto.getQuestion());
        task.setParams(dto.getParams() != null ? JSONUtil.toJsonStr(dto.getParams()) : null);
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