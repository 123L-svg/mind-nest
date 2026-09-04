package com.ainote.module.search.document;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

/**
 * 笔记索引文档（仅索引 status=0 的可见笔记）。
 * 字段名保留 Java 驼峰，检索用 title/summary/content 及 user/kb 过滤字段。
 * <p>中文检索使用 smartcn 分析器（analysis-smartcn 官方插件），提升中文分词精度。
 */
@Data
@Document(indexName = "note")
public class NoteDocument {

    @Id
    @Field
    private Long id;

    @Field
    private Long userId;

    @Field
    private Long kbId;

    @Field
    private Long categoryId;

    @Field(type = FieldType.Text, analyzer = "smartcn", searchAnalyzer = "smartcn")
    private String title;

    @Field(type = FieldType.Text, analyzer = "smartcn", searchAnalyzer = "smartcn")
    private String summary;

    @Field(type = FieldType.Text, analyzer = "smartcn", searchAnalyzer = "smartcn")
    private String content;
}