package com.ainote.module.ai.skill;

import com.ainote.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * AI 技能注册表：统一管理所有技能实现，按 action 名称分发。
 * <p>所有 {@link AiSkill} 实现类由 Spring 容器自动装配，
 * 新增技能（如翻译/起标题）只需实现接口并标注 @Component。</p>
 */
@Component
public class AiSkillRegistry {

    private final Map<String, AiSkill> skillMap;

    public AiSkillRegistry(List<AiSkill> skills) {
        this.skillMap = skills.stream()
                .collect(Collectors.toMap(AiSkill::name, Function.identity()));
    }

    /**
     * 按名称查找技能，不存在时抛出业务异常。
     */
    public AiSkill require(String name) {
        AiSkill skill = skillMap.get(name);
        if (skill == null) {
            throw new BusinessException("不支持的 AI 操作：" + name);
        }
        return skill;
    }
}
