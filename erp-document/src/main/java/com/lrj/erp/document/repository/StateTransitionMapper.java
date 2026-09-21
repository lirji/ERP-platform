package com.lrj.erp.document.repository;

import com.lrj.erp.document.model.StateTransitionRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 状态流转日志持久层。SQL 见 resources/mapper/StateTransitionMapper.xml。 */
@Mapper
public interface StateTransitionMapper {

    /**
     * 记录一次迁移。撞 uk_doc_transition_version 唯一索引即表示同一版本已被他人迁移过。
     *
     * <p>用 {@code ON CONFLICT DO NOTHING} + 影响行数判断，而不是先查后插：
     * 先查后插在并发下必然出现两个线程都认为"没人迁移过"。
     *
     * @return 实际插入行数：1 表示本次迁移胜出，0 表示并发冲突
     */
    int insertIfVersionUnused(@Param("r") StateTransitionRecord record);

    List<StateTransitionRecord> findHistory(@Param("tenantId") long tenantId,
                                            @Param("businessType") String businessType,
                                            @Param("businessId") String businessId);
}
