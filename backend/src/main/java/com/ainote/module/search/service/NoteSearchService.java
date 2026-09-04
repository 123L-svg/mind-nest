package com.ainote.module.search.service;

import com.ainote.common.exception.BusinessException;
import com.ainote.module.note.entity.Note;
import com.ainote.module.note.mapper.NoteMapper;
import com.ainote.module.search.document.NoteDocument;
import com.ainote.module.search.dto.SearchResult;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.HighlightQuery;
import org.springframework.data.elasticsearch.core.query.highlight.Highlight;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightField;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightParameters;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 笔记全文检索服务（Elasticsearch 实现）。
 * <p>索引只保存 status=0 的可见笔记；移入回收站/永久删除时同步删除索引。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NoteSearchService {

    private final ElasticsearchOperations operations;
    private final NoteMapper noteMapper;

    @Value("${note.search.engine:mysql}")
    private String engine;

    @Value("${es.reindex-on-start:true}")
    private boolean reindexOnStart;

    private static final String PRE_TAG = "<em>";
    private static final String POST_TAG = "</em>";

    private boolean esEnabled() {
        return "elasticsearch".equalsIgnoreCase(engine);
    }

    /**
     * ES 搜索，返回命中笔记 id 分页 + 高亮片段（userId 为 null 表示不按用户过滤，供公开搜索）。
     * 高亮字段限定 title/summary（纯文本），避免正文 HTML 片段带来的渲染与转义问题。
     */
    public SearchResult searchNoteIds(Long userId, Long kbId, Long categoryId, String keyword,
                                      int page, int size) {
        // ES 内部 page 从 0 开始
        int esPage = Math.max(page - 1, 0);
        HighlightParameters params = HighlightParameters.builder()
                .withPreTags(new String[]{PRE_TAG})
                .withPostTags(new String[]{POST_TAG})
                .build();
        Highlight highlight = new Highlight(params,
                List.of(new HighlightField("title"), new HighlightField("summary")));
        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.bool(b -> {
                    b.should(s -> s.multiMatch(mm -> mm.query(keyword)
                            .fields("title^3", "summary^2", "content")));
                    if (userId != null) {
                        b.must(m -> m.term(t -> t.field("userId").value(userId)));
                    }
                    if (kbId != null) {
                        b.must(m -> m.term(t -> t.field("kbId").value(kbId)));
                    }
                    if (categoryId != null) {
                        b.must(m -> m.term(t -> t.field("categoryId").value(categoryId)));
                    }
                    b.minimumShouldMatch("1");
                    return b;
                }))
                .withPageable(PageRequest.of(esPage, size))
                .build();
        query.setHighlightQuery(new HighlightQuery(highlight, NoteDocument.class));

        SearchHits<NoteDocument> hits = operations.search(query, NoteDocument.class);
        Map<Long, String> highlights = new HashMap<>();
        for (SearchHit<NoteDocument> hit : hits.getSearchHits()) {
            Long id = hit.getContent().getId();
            if (id == null) continue;
            Map<String, List<String>> fields = hit.getHighlightFields();
            String hl = firstOrNull(fields.get("title"));
            if (hl == null) hl = firstOrNull(fields.get("summary"));
            if (hl != null) {
                highlights.put(id, hl);
            }
        }

        List<Long> ids = hits.getSearchHits().stream()
                .map(h -> h.getContent().getId())
                .filter(java.util.Objects::nonNull)
                .toList();
        Page<Long> result = new Page<>(page, size, hits.getTotalHits());
        result.setRecords(ids);
        return new SearchResult(result, highlights);
    }

    private static String firstOrNull(List<String> list) {
        return (list == null || list.isEmpty()) ? null : list.get(0);
    }

    /** 写操作：新增或更新（update 时若已非可见状态先移除） */
    public void save(Note note) {
        if (!esEnabled()) return;
        try {
            if (note == null || note.getStatus() == null || note.getStatus() != 0) {
                deleteById(note == null ? 0L : note.getId());
                return;
            }
            NoteDocument doc = toDoc(note);
            operations.save(doc);
        } catch (Exception e) {
            throw new BusinessException("索引写入失败：" + e.getMessage());
        }
    }

    /** 写操作：删除（移入回收站 / 永久删除） */
    public void deleteById(Long id) {
        if (!esEnabled() || id == null) return;
        try {
            operations.delete(String.valueOf(id), NoteDocument.class);
        } catch (Exception e) {
            throw new BusinessException("索引删除失败：" + e.getMessage());
        }
    }

    /** 重建 note 索引：删除旧索引后全量同步 status=0 的笔记 */
    public void reindex() {
        if (!esEnabled()) {
            log.info("检索引擎为 mysql，跳过 ES 重建");
            return;
        }
        try {
            boolean existed = operations.indexOps(NoteDocument.class).exists();
            if (existed) {
                operations.indexOps(NoteDocument.class).delete();
            }
            // 用 @Field(analyzer=smartcn) 生成显式 mapping，而非空/动态索引
            operations.indexOps(NoteDocument.class).createWithMapping();
            List<Note> notes = noteMapper.selectList(
                    new LambdaQueryWrapper<Note>().eq(Note::getStatus, 0));
            for (Note n : notes) {
                operations.save(toDoc(n));
            }
            log.info("note 索引重建完成，共 {} 篇", notes.size());
        } catch (Exception e) {
            log.error("note 索引重建失败，后续搜索将回退 MySQL：{}", e.getMessage());
        }
    }

    /** 应用就绪后（ES 可用时）自动重建一次，保证存量数据可检索 */
    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (reindexOnStart) {
            try {
                reindex();
            } catch (Exception e) {
                log.warn("启动重建索引失败，忽略：{}", e.getMessage());
            }
        }
    }

    private NoteDocument toDoc(Note note) {
        NoteDocument doc = new NoteDocument();
        doc.setId(note.getId());
        doc.setUserId(note.getUserId());
        doc.setKbId(note.getKbId());
        doc.setCategoryId(note.getCategoryId());
        doc.setTitle(note.getTitle());
        doc.setSummary(note.getSummary());
        doc.setContent(note.getContent());
        return doc;
    }
}