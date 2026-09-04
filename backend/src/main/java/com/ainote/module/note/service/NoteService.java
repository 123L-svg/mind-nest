package com.ainote.module.note.service;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.ainote.common.exception.BusinessException;
import com.ainote.common.result.ResultCode;
import com.ainote.common.util.HtmlSanitizer;
import com.ainote.module.kb.entity.KnowledgeBase;
import com.ainote.module.kb.mapper.KnowledgeBaseMapper;
import com.ainote.module.note.dto.NoteDTO;
import com.ainote.module.note.entity.Note;
import com.ainote.module.note.entity.NoteTag;
import com.ainote.module.note.entity.NoteVersion;
import com.ainote.module.note.mapper.NoteMapper;
import com.ainote.module.note.mapper.NoteTagMapper;
import com.ainote.module.note.mapper.NoteVersionMapper;
import com.ainote.module.note.vo.NoteVersionVO;
import com.ainote.module.note.vo.NoteVO;
import com.ainote.module.search.dto.SearchResult;
import com.ainote.module.search.service.NoteSearchService;
import com.ainote.module.tag.entity.Tag;
import com.ainote.module.tag.mapper.TagMapper;
import com.ainote.module.tag.vo.TagVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 笔记服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NoteService {

    private final NoteMapper noteMapper;
    private final NoteTagMapper noteTagMapper;
    private final TagMapper tagMapper;
    private final KnowledgeBaseMapper kbMapper;
    private final NoteSearchService noteSearchService;
    private final NoteVersionMapper noteVersionMapper;

    /** 全文检索引擎：mysql | elasticsearch */
    @Value("${note.search.engine:mysql}")
    private String searchEngine;

    private boolean esEnabled() {
        return "elasticsearch".equalsIgnoreCase(searchEngine);
    }

    /** 分页查询（status 0 正常 / 1 回收站） */
    public Page<NoteVO> page(Long userId, Integer page, Integer size, Long kbId,
                             Long categoryId, Integer status, String keyword) {
        // ES 全文检索：有关键词且启用 ES 时走 ES 命中 id，失败自动回退 MySQL LIKE
        if (StrUtil.isNotBlank(keyword) && esEnabled()) {
            try {
                SearchResult sr = noteSearchService.searchNoteIds(userId, kbId, categoryId, keyword, page, size);
                Page<NoteVO> voPage = assembleByIds(sr.getIdPage());
                enrichTags(voPage.getRecords());
                applyHighlight(voPage.getRecords(), sr.getHighlights());
                return voPage;
            } catch (Exception e) {
                log.warn("ES 检索失败，回退 MySQL LIKE：{}", e.getMessage());
            }
        }
        Page<Note> p = noteMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Note>()
                        .eq(Note::getUserId, userId)
                        .eq(kbId != null, Note::getKbId, kbId)
                        .eq(categoryId != null, Note::getCategoryId, categoryId)
                        .eq(status != null, Note::getStatus, status)
                        .and(StrUtil.isNotBlank(keyword), w -> w
                                .like(Note::getTitle, keyword)
                                .or().like(Note::getSummary, keyword)
                                .or().like(Note::getContent, keyword))
                        .orderByDesc(Note::getUpdateTime));

        Page<NoteVO> voPage = new Page<>(p.getCurrent(), p.getSize(), p.getTotal());
        List<NoteVO> vos = p.getRecords().stream().map(this::toVO).toList();
        enrichTags(vos);
        voPage.setRecords(vos);
        return voPage;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long add(Long userId, NoteDTO dto) {
        Note note = new Note();
        note.setUserId(userId);
        apply(dto, note);
        note.setViewCount(0);
        note.setStatus(0);
        noteMapper.insert(note);
        saveTags(note.getId(), userId, dto.getTagIds());
        syncIndexSave(note);
        recordVersion(note);
        return note.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long userId, NoteDTO dto) {
        Note note = getOwned(userId, dto.getId());
        apply(dto, note);
        noteMapper.updateById(note);
        saveTags(note.getId(), userId, dto.getTagIds());
        syncIndexSave(note);
        recordVersion(note);
    }

    /** 详情（校验归属，自增浏览数，含 content 与标签） */
    public NoteVO detail(Long userId, Long id) {
        Note note = getOwned(userId, id);
        NoteVO vo = toVO(note);
        vo.setContent(note.getContent());
        enrichTags(List.of(vo));
        noteMapper.incrViewCount(note.getId());
        return vo;
    }

    /** 移入回收站（status=1） */
    public void delete(Long userId, Long id) {
        Note note = getOwned(userId, id);
        note.setStatus(1);
        noteMapper.updateById(note);
        syncIndexDelete(id);
    }

    /** 恢复（status=0） */
    public void restore(Long userId, Long id) {
        Note note = getOwned(userId, id);
        note.setStatus(0);
        noteMapper.updateById(note);
        syncIndexSave(note);
    }

    /** 永久删除（逻辑删除，连带清理标签关联） */
    @Transactional(rollbackFor = Exception.class)
    public void permanentlyDelete(Long userId, Long id) {
        Note note = getOwned(userId, id);
        noteTagMapper.delete(new LambdaQueryWrapper<NoteTag>().eq(NoteTag::getNoteId, note.getId()));
        noteMapper.deleteById(note.getId());
        syncIndexDelete(id);
    }

    /**
     * 回收站自动清理：永久删除进入回收站超过 keepDays 天的笔记
     * （连带清理标签关联、ES 索引；返回清理数量）
     */
    @Transactional(rollbackFor = Exception.class)
    public int cleanupRecycleBin(int keepDays) {
        LocalDateTime threshold = LocalDateTime.now().minusDays(keepDays);
        List<Note> expired = noteMapper.selectList(
                new LambdaQueryWrapper<Note>()
                        .eq(Note::getStatus, 1)
                        .lt(Note::getUpdateTime, threshold));
        if (expired.isEmpty()) {
            return 0;
        }
        List<Long> ids = expired.stream().map(Note::getId).toList();
        noteTagMapper.delete(new LambdaQueryWrapper<NoteTag>().in(NoteTag::getNoteId, ids));
        noteMapper.delete(new LambdaQueryWrapper<Note>().in(Note::getId, ids));
        for (Long id : ids) {
            syncIndexDelete(id);
        }
        return ids.size();
    }

    private Note getOwned(Long userId, Long id) {
        Note note = noteMapper.selectById(id);
        if (note == null || !note.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "笔记不存在或无权访问");
        }
        return note;
    }

    // -----------------------------------------------------------------------
    // 公开（分享）只读访问：知识库 is_public=1 时游客可读
    // -----------------------------------------------------------------------

    /** 公开知识库下的笔记列表（不校验登录；内部校验知识库已公开） */
    public List<NoteVO> publicList(Long kbId, String keyword) {
        checkKbPublic(kbId);
        if (StrUtil.isNotBlank(keyword) && esEnabled()) {
            try {
                SearchResult sr = noteSearchService.searchNoteIds(null, kbId, null, keyword, 1, 200);
                if (!sr.getIdPage().getRecords().isEmpty()) {
                    List<Note> notes = noteMapper.selectBatchIds(sr.getIdPage().getRecords());
                    Map<Long, Note> map = notes.stream()
                            .collect(Collectors.toMap(Note::getId, Function.identity()));
                    List<NoteVO> vos = sr.getIdPage().getRecords().stream()
                            .map(map::get).filter(Objects::nonNull).map(this::toVO).toList();
                    enrichTags(vos);
                    applyHighlight(vos, sr.getHighlights());
                    return vos;
                }
                return List.of();
            } catch (Exception e) {
                log.warn("ES 公开检索失败，回退 MySQL LIKE：{}", e.getMessage());
            }
        }
        List<Note> list = noteMapper.selectList(
                new LambdaQueryWrapper<Note>()
                        .eq(Note::getKbId, kbId)
                        .eq(Note::getStatus, 0)
                        .and(StrUtil.isNotBlank(keyword), w -> w
                                .like(Note::getTitle, keyword)
                                .or().like(Note::getSummary, keyword)
                                .or().like(Note::getContent, keyword))
                        .orderByDesc(Note::getUpdateTime));
        List<NoteVO> vos = list.stream().map(this::toVO).toList();
        enrichTags(vos);
        return vos;
    }

    /** 公开笔记详情（不校验登录；内部校验所属知识库已公开） */
    public NoteVO publicDetail(Long id) {
        Note note = noteMapper.selectById(id);
        if (note == null || note.getStatus() != 0) {
            throw new BusinessException(ResultCode.NOT_FOUND, "笔记不存在");
        }
        checkKbPublic(note.getKbId());
        NoteVO vo = toVO(note);
        vo.setContent(note.getContent());
        enrichTags(List.of(vo));
        noteMapper.incrViewCount(note.getId());
        return vo;
    }

    /** 校验某笔记所属知识库是否公开可读；不可读抛异常，可读返回 note */
    public Note checkPublicReadable(Long id) {
        Note note = noteMapper.selectById(id);
        if (note == null || note.getStatus() != 0) {
            throw new BusinessException(ResultCode.NOT_FOUND, "笔记不存在");
        }
        checkKbPublic(note.getKbId());
        return note;
    }

    /** 校验知识库存在且已公开，否则 404（公开读接口的内部防御） */
    private void checkKbPublic(Long kbId) {
        KnowledgeBase kb = kbMapper.selectById(kbId);
        if (kb == null || kb.getIsPublic() == null || kb.getIsPublic() != 1) {
            throw new BusinessException(ResultCode.NOT_FOUND, "知识库不存在或未公开");
        }
    }

    private void apply(NoteDTO dto, Note note) {
        note.setKbId(dto.getKbId());
        note.setCategoryId(dto.getCategoryId());
        note.setTitle(dto.getTitle());
        if (dto.getContent() != null) {
            // 白名单净化富文本，防存储型 XSS
            note.setContent(HtmlSanitizer.sanitize(dto.getContent()));
        }
        if (dto.getSummary() != null) {
            note.setSummary(dto.getSummary());
        }
        if (dto.getWordCount() != null) {
            note.setWordCount(dto.getWordCount());
        }
    }

    /** 替换笔记标签（先清后插），校验标签归属 */
    private void saveTags(Long noteId, Long userId, List<Long> tagIds) {
        if (CollUtil.isEmpty(tagIds)) {
            return;
        }
        Long valid = tagMapper.selectCount(
                new LambdaQueryWrapper<Tag>()
                        .in(Tag::getId, tagIds)
                        .eq(Tag::getUserId, userId));
        if (valid == null || valid != tagIds.size()) {
            throw new BusinessException("存在无效标签");
        }
        noteTagMapper.delete(new LambdaQueryWrapper<NoteTag>().eq(NoteTag::getNoteId, noteId));
        for (Long tagId : tagIds) {
            NoteTag nt = new NoteTag();
            nt.setNoteId(noteId);
            nt.setTagId(tagId);
            noteTagMapper.insert(nt);
        }
    }

    private void enrichTags(List<NoteVO> vos) {
        if (CollUtil.isEmpty(vos)) {
            return;
        }
        List<Long> noteIds = vos.stream().map(NoteVO::getId).toList();
        Map<Long, List<Long>> noteTagMap = noteTagMapper.selectList(
                        new LambdaQueryWrapper<NoteTag>().in(NoteTag::getNoteId, noteIds))
                .stream().collect(Collectors.groupingBy(NoteTag::getNoteId,
                        Collectors.mapping(NoteTag::getTagId, Collectors.toList())));

        List<Long> allTagIds = noteTagMap.values().stream().flatMap(List::stream).distinct().toList();
        Map<Long, Tag> tagMap = allTagIds.isEmpty() ? Map.of()
                : tagMapper.selectBatchIds(allTagIds).stream()
                        .collect(Collectors.toMap(Tag::getId, Function.identity()));

        Map<Long, NoteVO> voMap = vos.stream().collect(Collectors.toMap(NoteVO::getId, Function.identity()));
        noteTagMap.forEach((noteId, tagIds) -> {
            List<TagVO> tags = tagIds.stream().map(tagMap::get)
                    .filter(java.util.Objects::nonNull)
                    .map(t -> {
                        TagVO tv = new TagVO();
                        tv.setId(t.getId());
                        tv.setName(t.getName());
                        return tv;
                    }).toList();
            voMap.get(noteId).setTags(tags);
        });
    }

    private NoteVO toVO(Note note) {
        NoteVO vo = new NoteVO();
        BeanUtils.copyProperties(note, vo);
        return vo;
    }

    /** 将 ES 命中的 id 分页组装为 NoteVO 分页（保持 ES 相关度顺序） */
    private Page<NoteVO> assembleByIds(Page<Long> idPage) {
        Page<NoteVO> voPage = new Page<>(idPage.getCurrent(), idPage.getSize(), idPage.getTotal());
        List<Long> ids = idPage.getRecords();
        if (ids.isEmpty()) {
            voPage.setRecords(List.of());
            return voPage;
        }
        Map<Long, Note> map = noteMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Note::getId, Function.identity()));
        voPage.setRecords(ids.stream().map(map::get).filter(Objects::nonNull).map(this::toVO).toList());
        return voPage;
    }

    /** 回填 ES 高亮片段（仅对命中笔记；无高亮则保持为空） */
    private void applyHighlight(List<NoteVO> vos, Map<Long, String> highlights) {
        if (CollUtil.isEmpty(highlights)) {
            return;
        }
        for (NoteVO vo : vos) {
            if (vo == null) continue;
            String hl = highlights.get(vo.getId());
            if (hl != null) {
                vo.setHighlight(hl);
            }
        }
    }

    /** 索引同步（ES 引擎）——失败仅告警，不阻断主流程 */
    private void syncIndexSave(Note note) {
        if (!esEnabled()) return;
        try {
            noteSearchService.save(note);
        } catch (Exception e) {
            log.warn("ES 索引写入失败：{}", e.getMessage());
        }
    }

    private void syncIndexDelete(Long id) {
        if (!esEnabled()) return;
        try {
            noteSearchService.deleteById(id);
        } catch (Exception e) {
            log.warn("ES 索引删除失败：{}", e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // 版本历史
    // -----------------------------------------------------------------------

    /** 保存一条版本快照（限制每个笔记最多 50 条，超出删最旧） */
    private void recordVersion(Note note) {
        try {
            NoteVersion v = new NoteVersion();
            v.setNoteId(note.getId());
            v.setTitle(note.getTitle());
            v.setContent(note.getContent());
            v.setSummary(note.getSummary());
            v.setWordCount(note.getWordCount());
            noteVersionMapper.insert(v);
            // 精简：每个笔记保留最近 50 条
            List<Long> ids = noteVersionMapper.selectList(
                            new LambdaQueryWrapper<NoteVersion>()
                                    .eq(NoteVersion::getNoteId, note.getId())
                                    .orderByDesc(NoteVersion::getCreateTime)
                                    .last("LIMIT 50 OFFSET 50"))
                    .stream().map(NoteVersion::getId).toList();
            if (!ids.isEmpty()) {
                noteVersionMapper.delete(new LambdaQueryWrapper<NoteVersion>().in(NoteVersion::getId, ids));
            }
        } catch (Exception e) {
            log.warn("记录笔记版本失败：{}", e.getMessage());
        }
    }

    /** 我的笔记版本列表（不含 content，避免列表过大） */
    public List<NoteVersionVO> versions(Long userId, Long noteId) {
        getOwned(userId, noteId);
        return noteVersionMapper.selectList(
                        new LambdaQueryWrapper<NoteVersion>()
                                .eq(NoteVersion::getNoteId, noteId)
                                .orderByDesc(NoteVersion::getCreateTime))
                .stream().map(v -> {
                    NoteVersionVO vo = new NoteVersionVO();
                    vo.setId(v.getId());
                    vo.setNoteId(v.getNoteId());
                    vo.setTitle(v.getTitle());
                    vo.setSummary(v.getSummary());
                    vo.setWordCount(v.getWordCount());
                    vo.setCreateTime(v.getCreateTime());
                    return vo;
                }).toList();
    }

    /** 版本详情（含 content，校验本人） */
    public NoteVersion versionDetail(Long userId, Long versionId) {
        NoteVersion v = noteVersionMapper.selectById(versionId);
        if (v == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "版本不存在");
        }
        getOwned(userId, v.getNoteId());
        return v;
    }

    /** 回滚到指定版本（写回笔记并记录新版本、同步索引） */
    @Transactional(rollbackFor = Exception.class)
    public void rollback(Long userId, Long versionId) {
        NoteVersion v = versionDetail(userId, versionId);
        Note note = getOwned(userId, v.getNoteId());
        if (v.getTitle() != null) note.setTitle(v.getTitle());
        if (v.getContent() != null) note.setContent(v.getContent());
        if (v.getSummary() != null) note.setSummary(v.getSummary());
        if (v.getWordCount() != null) note.setWordCount(v.getWordCount());
        noteMapper.updateById(note);
        syncIndexSave(note);
        recordVersion(note);
    }
}