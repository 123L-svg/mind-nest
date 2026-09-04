package com.ainote.module.kb.service;

import cn.hutool.core.util.StrUtil;
import com.ainote.common.exception.BusinessException;
import com.ainote.common.result.ResultCode;
import com.ainote.module.kb.dto.KbDTO;
import com.ainote.module.kb.entity.KnowledgeBase;
import com.ainote.module.kb.mapper.KnowledgeBaseMapper;
import com.ainote.module.kb.vo.KbVO;
import com.ainote.module.note.entity.Note;
import com.ainote.module.note.mapper.NoteMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 知识库服务
 */
@Service
@RequiredArgsConstructor
public class KnowledgeBaseService {

    private final KnowledgeBaseMapper kbMapper;
    private final NoteMapper noteMapper;

    /** 我的知识库列表 */
    public List<KbVO> listMine(Long userId) {
        List<KnowledgeBase> list = kbMapper.selectList(
                new LambdaQueryWrapper<KnowledgeBase>()
                        .eq(KnowledgeBase::getUserId, userId)
                        .orderByDesc(KnowledgeBase::getUpdateTime));
        return list.stream().map(this::toVO).toList();
    }

    /** 新建 */
    public Long add(Long userId, KbDTO dto) {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setUserId(userId);
        apply(dto, kb);
        kbMapper.insert(kb);
        return kb.getId();
    }

    /** 编辑（校验归属） */
    public void update(Long userId, KbDTO dto) {
        KnowledgeBase kb = getOwned(userId, dto.getId());
        apply(dto, kb);
        kbMapper.updateById(kb);
    }

    /** 删除（逻辑删除，校验归属） */
    public void delete(Long userId, Long id) {
        KnowledgeBase kb = getOwned(userId, id);
        kbMapper.deleteById(kb.getId());
    }

    /** 详情（校验归属） */
    public KbVO detail(Long userId, Long id) {
        return toVO(getOwned(userId, id));
    }

    /** 公开知识库详情（游客）：仅 is_public=1 可读 */
    public KbVO publicDetail(Long id) {
        KnowledgeBase kb = kbMapper.selectById(id);
        if (kb == null || kb.getIsPublic() == null || kb.getIsPublic() != 1) {
            throw new BusinessException(ResultCode.NOT_FOUND, "知识库不存在或未公开");
        }
        return toVO(kb);
    }

    /** 校验知识库公开可读，返回 kb */
    public KnowledgeBase checkPublic(Long id) {
        KnowledgeBase kb = kbMapper.selectById(id);
        if (kb == null || kb.getIsPublic() == null || kb.getIsPublic() != 1) {
            throw new BusinessException(ResultCode.NOT_FOUND, "知识库不存在或未公开");
        }
        return kb;
    }

    private KnowledgeBase getOwned(Long userId, Long id) {
        KnowledgeBase kb = kbMapper.selectById(id);
        if (kb == null || !kb.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "知识库不存在或无权访问");
        }
        return kb;
    }

    private void apply(KbDTO dto, KnowledgeBase kb) {
        kb.setName(dto.getName());
        if (dto.getDescription() != null) {
            kb.setDescription(dto.getDescription());
        }
        if (dto.getCover() != null) {
            kb.setCover(dto.getCover());
        }
        if (dto.getIsPublic() != null) {
            kb.setIsPublic(dto.getIsPublic());
        }
    }

    private KbVO toVO(KnowledgeBase kb) {
        KbVO vo = new KbVO();
        BeanUtils.copyProperties(kb, vo);
        vo.setNoteCount(noteMapper.selectCount(
                new LambdaQueryWrapper<Note>()
                        .eq(Note::getKbId, kb.getId())
                        .eq(Note::getStatus, 0)));
        return vo;
    }
}