package com.ainote.module.ai.mapper;

import com.ainote.module.ai.entity.AiTask;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * AI 异步任务 Mapper
 */
public interface AiTaskMapper extends BaseMapper<AiTask> {

    /**
     * 抢占任务为处理中（幂等/互斥）：仅允许从「待处理」抢占，靠 UPDATE 行锁保证并发下
     * 同一任务只有一个消费者能抢占成功，其余抢占失败即视为重复投递而跳过。
     * <p>「重试」路径需先把任务恢复为待处理，保证下次投递仍能从这里抢占。</p>
     */
    @Update("UPDATE ai_task SET status = 1 WHERE id = #{id} AND status = 0")
    int tryProcess(@Param("id") Long id);
}