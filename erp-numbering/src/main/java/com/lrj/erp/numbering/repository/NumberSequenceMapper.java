package com.lrj.erp.numbering.repository;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;

/**
 * 编号中心持久层。SQL 见 resources/mapper/NumberSequenceMapper.xml。
 *
 * <p><b>为什么整体关闭租户与数据权限拦截</b>：取号语句是
 * {@code INSERT ... ON CONFLICT DO UPDATE ... RETURNING}，在 MyBatis 中以
 * {@code <select>} 形式声明（PostgreSQL 的常见写法）。MP 的拦截器会把所有 select
 * 交给 JSqlParser 按 Select 解析，遇到这种形态直接抛 UnsupportedOperationException——
 * 解析发生在 handler 被调用<b>之前</b>，因此把表加进豁免集合并不能避免。
 *
 * <p>关闭是安全的：{@code num_*} 两张表都以 tenant_id 为主键组成部分，且只能经
 * {@link com.lrj.erp.numbering.service.NumberGenerator} 访问，而该接口把 tenantId
 * 作为显式入参——租户隔离由调用契约保证，不依赖 SQL 自动注入。
 */
@Mapper
@InterceptorIgnore(tenantLine = "true", dataPermission = "true")
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
