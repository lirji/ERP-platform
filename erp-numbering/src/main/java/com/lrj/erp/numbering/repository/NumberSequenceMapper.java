package com.lrj.erp.numbering.repository;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;

/** 编号中心持久层。SQL 见 resources/mapper/NumberSequenceMapper.xml。 */
@Mapper
public interface NumberSequenceMapper {

    /**
     * 原子取号：不存在则插入为 1，存在则 +1，并返回新值。
     *
     * <p>用单条 {@code INSERT ... ON CONFLICT DO UPDATE ... RETURNING} 完成，
     * 而不是"先 SELECT 再 UPDATE"：后者在并发下会重号，加悲观锁又会让取号串行化并
     * 延长事务持锁时间。单语句由数据库保证原子性，是这里唯一正确且不牺牲吞吐的做法。
     */
    Long nextValue(@Param("tenantId") long tenantId,
                   @Param("businessType") String businessType,
                   @Param("bizDate") LocalDate bizDate);

    /** 读取编号规则；未配置或已停用返回 null。 */
    NumberRuleRecord findEnabledRule(@Param("tenantId") long tenantId,
                                     @Param("businessType") String businessType);

    /** 编号规则投影。刻意用 record 而非实体：本模块不需要可变持久化对象。 */
    record NumberRuleRecord(String prefix, int seqWidth) { }
}
