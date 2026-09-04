package com.ainote.module.tag.service;

import com.ainote.common.exception.BusinessException;
import com.ainote.common.result.ResultCode;
import com.ainote.module.tag.dto.TagDTO;
import com.ainote.module.tag.entity.Tag;
import com.ainote.module.tag.mapper.TagMapper;
import com.ainote.module.tag.vo.TagVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 标签服务
 */
@Service
@RequiredArgsConstructor
public class TagService {

    private final TagMapper tagMapper;

    /** 我的标签列表 */
    public List<TagVO> list(Long userId) {
        return tagMapper.selectList(
                        new LambdaQueryWrapper<Tag>()
                                .eq(Tag::getUserId, userId)
                                .orderByDesc(Tag::getCreateTime))
                .stream().map(this::toVO).toList();
    }

    public Long add(Long userId, TagDTO dto) {
        Long count = tagMapper.selectCount(
                new LambdaQueryWrapper<Tag>()
                        .eq(Tag::getUserId, userId)
                        .eq(Tag::getName, dto.getName()));
        if (count != null && count > 0) {
            throw new BusinessException("标签已存在");
        }
        Tag tag = new Tag();
        tag.setUserId(userId);
        tag.setName(dto.getName());
        tagMapper.insert(tag);
        return tag.getId();
    }

    public void delete(Long userId, Long id) {
        Tag tag = tagMapper.selectById(id);
        if (tag == null || !tag.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "标签不存在或无权访问");
        }
        tagMapper.deleteById(id);
    }

    private TagVO toVO(Tag tag) {
        TagVO vo = new TagVO();
        BeanUtils.copyProperties(tag, vo);
        return vo;
    }
}