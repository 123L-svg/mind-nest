package com.ainote.module.category.service;

import com.ainote.common.exception.BusinessException;
import com.ainote.common.result.ResultCode;
import com.ainote.module.category.dto.CategoryDTO;
import com.ainote.module.category.entity.Category;
import com.ainote.module.category.mapper.CategoryMapper;
import com.ainote.module.category.vo.CategoryVO;
import com.ainote.module.kb.entity.KnowledgeBase;
import com.ainote.module.kb.mapper.KnowledgeBaseMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 分类服务
 */
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryMapper categoryMapper;
    private final KnowledgeBaseMapper kbMapper;

    /** 知识库下分类列表 */
    public List<CategoryVO> list(Long userId, Long kbId) {
        checkKbOwner(userId, kbId);
        return categoryMapper.selectList(
                        new LambdaQueryWrapper<Category>()
                                .eq(Category::getUserId, userId)
                                .eq(Category::getKbId, kbId)
                                .orderByAsc(Category::getSort))
                .stream().map(this::toVO).toList();
    }

    public Long add(Long userId, CategoryDTO dto) {
        checkKbOwner(userId, dto.getKbId());
        Category c = new Category();
        c.setUserId(userId);
        c.setKbId(dto.getKbId());
        c.setName(dto.getName());
        c.setSort(dto.getSort() == null ? 0 : dto.getSort());
        categoryMapper.insert(c);
        return c.getId();
    }

    public void update(Long userId, CategoryDTO dto) {
        Category c = getOwned(userId, dto.getId());
        c.setName(dto.getName());
        if (dto.getSort() != null) {
            c.setSort(dto.getSort());
        }
        categoryMapper.updateById(c);
    }

    public void delete(Long userId, Long id) {
        categoryMapper.deleteById(getOwned(userId, id).getId());
    }

    private void checkKbOwner(Long userId, Long kbId) {
        KnowledgeBase kb = kbMapper.selectById(kbId);
        if (kb == null || !kb.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "知识库不存在或无权访问");
        }
    }

    private Category getOwned(Long userId, Long id) {
        Category c = categoryMapper.selectById(id);
        if (c == null || !c.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "分类不存在或无权访问");
        }
        return c;
    }

    private CategoryVO toVO(Category c) {
        CategoryVO vo = new CategoryVO();
        BeanUtils.copyProperties(c, vo);
        return vo;
    }
}