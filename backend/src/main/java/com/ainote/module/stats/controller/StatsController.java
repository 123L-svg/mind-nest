package com.ainote.module.stats.controller;

import com.ainote.common.result.Result;
import com.ainote.common.util.SecurityUtil;
import com.ainote.module.category.entity.Category;
import com.ainote.module.category.mapper.CategoryMapper;
import com.ainote.module.kb.entity.KnowledgeBase;
import com.ainote.module.kb.mapper.KnowledgeBaseMapper;
import com.ainote.module.note.entity.Note;
import com.ainote.module.note.mapper.NoteMapper;
import com.ainote.module.stats.vo.CategoryStatVO;
import com.ainote.module.stats.vo.HotNoteVO;
import com.ainote.module.stats.vo.StatsVO;
import com.ainote.module.stats.vo.TrendVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 数据统计接口
 */
@Tag(name = "统计模块")
@RestController
@RequestMapping("/stats")
@RequiredArgsConstructor
public class StatsController {

    private final NoteMapper noteMapper;
    private final KnowledgeBaseMapper kbMapper;
    private final CategoryMapper categoryMapper;

    @Operation(summary = "我的数据统计")
    @GetMapping("/overview")
    public Result<StatsVO> overview() {
        Long userId = SecurityUtil.getUserId();
        StatsVO vo = new StatsVO();

        vo.setKbCount(kbMapper.selectCount(
                new LambdaQueryWrapper<KnowledgeBase>().eq(KnowledgeBase::getUserId, userId)));

        vo.setNoteCount(noteMapper.selectCount(
                new LambdaQueryWrapper<Note>()
                        .eq(Note::getUserId, userId)
                        .eq(Note::getStatus, 0)));

        vo.setRecycleCount(noteMapper.selectCount(
                new LambdaQueryWrapper<Note>()
                        .eq(Note::getUserId, userId)
                        .eq(Note::getStatus, 1)));

        // 总浏览 = 该用户所有正常笔记 view_count 之和
        long total = noteMapper.selectList(
                        new LambdaQueryWrapper<Note>()
                                .eq(Note::getUserId, userId)
                                .eq(Note::getStatus, 0))
                .stream().mapToLong(n -> n.getViewCount() == null ? 0 : n.getViewCount()).sum();
        vo.setTotalViewCount(total);

        Note recent = noteMapper.selectOne(
                new LambdaQueryWrapper<Note>()
                        .eq(Note::getUserId, userId)
                        .eq(Note::getStatus, 0)
                        .orderByDesc(Note::getUpdateTime)
                        .last("LIMIT 1"));
        if (recent != null) {
            vo.setRecentTitle(recent.getTitle());
            vo.setRecentUpdateTime(recent.getUpdateTime());
        }

        fillTrend(vo, userId);
        fillHotNotes(vo, userId);
        fillCategoryStats(vo, userId);
        return Result.success(vo);
    }

    /** 近 30 日新增笔记趋势 */
    private void fillTrend(StatsVO vo, Long userId) {
        List<Note> notes = noteMapper.selectList(
                new LambdaQueryWrapper<Note>()
                        .eq(Note::getUserId, userId)
                        .eq(Note::getStatus, 0));
        LocalDate today = LocalDate.now();
        Map<LocalDate, Long> byDate = notes.stream()
                .filter(n -> n.getCreateTime() != null)
                .map(n -> n.getCreateTime().toLocalDate())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        List<TrendVO> trend = new ArrayList<>();
        for (int i = 29; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            TrendVO t = new TrendVO();
            t.setDate(d.toString());
            t.setCount(byDate.getOrDefault(d, 0L));
            trend.add(t);
        }
        vo.setTrend(trend);
    }

    /** 热门笔记 TOP5（按浏览量，附最近更新时间） */
    private void fillHotNotes(StatsVO vo, Long userId) {
        List<Note> top = noteMapper.selectList(
                new LambdaQueryWrapper<Note>()
                        .eq(Note::getUserId, userId)
                        .eq(Note::getStatus, 0)
                        .orderByDesc(Note::getViewCount)
                        .last("LIMIT 5"));
        vo.setHotNotes(top.stream().map(n -> {
            HotNoteVO h = new HotNoteVO();
            h.setId(n.getId());
            h.setTitle(n.getTitle());
            h.setViewCount(n.getViewCount() == null ? 0 : n.getViewCount());
            h.setUpdateTime(n.getUpdateTime());
            return h;
        }).toList());
    }

    /** 分类占比 */
    private void fillCategoryStats(StatsVO vo, Long userId) {
        List<Note> notes = noteMapper.selectList(
                new LambdaQueryWrapper<Note>()
                        .eq(Note::getUserId, userId)
                        .eq(Note::getStatus, 0));
        Map<Long, Long> perCategory = notes.stream()
                .filter(n -> n.getCategoryId() != null)
                .collect(Collectors.groupingBy(Note::getCategoryId, Collectors.counting()));
        long unCategorized = notes.size() - perCategory.values().stream().mapToLong(Long::longValue).sum();

        List<Category> categories = perCategory.isEmpty() ? List.of() : categoryMapper.selectBatchIds(perCategory.keySet());
        Map<Long, String> nameMap = categories.stream()
                .collect(Collectors.toMap(Category::getId, Category::getName));

        List<CategoryStatVO> list = new ArrayList<>();
        perCategory.forEach((cid, cnt) -> {
            CategoryStatVO c = new CategoryStatVO();
            c.setCategoryId(cid);
            c.setCategoryName(nameMap.getOrDefault(cid, "未命名"));
            c.setNoteCount(cnt);
            list.add(c);
        });
        if (unCategorized > 0) {
            CategoryStatVO c = new CategoryStatVO();
            c.setCategoryId(null);
            c.setCategoryName("未分类");
            c.setNoteCount(unCategorized);
            list.add(c);
        }
        vo.setCategoryStats(list.stream()
                .sorted((a, b) -> Long.compare(b.getNoteCount(), a.getNoteCount()))
                .toList());
    }
}