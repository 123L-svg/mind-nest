package com.ainote.module.ai.controller;

import com.ainote.common.result.Result;
import com.ainote.module.ai.config.AiProperties;
import com.ainote.module.ai.dto.AiRequestDTO;
import com.ainote.module.ai.service.AiService;
import com.ainote.module.ai.vo.AiResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    @Operation(summary = "AI 生成笔记大纲")
    @PostMapping("/generate-outline")
    public Result<AiResultVO> outline(@RequestBody AiRequestDTO dto) {
        return Result.success(doExecute(dto, AiService.OUTLINE));
    }

    @Operation(summary = "AI 润色内容")
    @PostMapping("/polish")
    public Result<AiResultVO> polish(@RequestBody AiRequestDTO dto) {
        return Result.success(doExecute(dto, AiService.POLISH));
    }

    @Operation(summary = "AI 生成摘要")
    @PostMapping("/summarize")
    public Result<AiResultVO> summarize(@RequestBody AiRequestDTO dto) {
        return Result.success(doExecute(dto, AiService.SUMMARIZE));
    }

    @Operation(summary = "基于笔记内容问答")
    @PostMapping("/chat")
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
}