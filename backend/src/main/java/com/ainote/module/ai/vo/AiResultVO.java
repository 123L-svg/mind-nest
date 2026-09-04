package com.ainote.module.ai.vo;

import lombok.Data;

/**
 * AI 结果返回
 */
@Data
public class AiResultVO {

    private String action;

    /** 模型名（mock 时为 null） */
    private String model;

    /** 是否 mock 结果 */
    private boolean mock;

    /** AI 生成结果 */
    private String result;
}