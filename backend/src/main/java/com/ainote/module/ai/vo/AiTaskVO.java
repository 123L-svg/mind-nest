package com.ainote.module.ai.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * AI 异步任务状态返回（前端据此轮询）
 */
@Data
@Schema(description = "AI 异步任务状态")
public class AiTaskVO {

    @Schema(description = "任务ID")
    private Long taskId;

    /** 0待处理 1处理中 2成功 3失败 */
    @Schema(description = "状态 0待处理 1处理中 2成功 3失败")
    private Integer status;

    /** 状态中文描述 */
    @Schema(description = "状态描述")
    private String statusText;

    @Schema(description = "AI 生成结果（成功后返回）")
    private String result;

    @Schema(description = "失败原因")
    private String errorMsg;
}