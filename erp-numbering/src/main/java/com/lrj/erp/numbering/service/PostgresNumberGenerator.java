package com.lrj.erp.numbering.service;

import com.lrj.erp.kernel.error.DomainException;
import com.lrj.erp.numbering.repository.NumberSequenceMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 基于 PostgreSQL 原子递增的编号生成器。
 */
@Service
public class PostgresNumberGenerator implements NumberGenerator {

    private static final DateTimeFormatter DATE_PART = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final NumberSequenceMapper mapper;

    public PostgresNumberGenerator(NumberSequenceMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * {@code REQUIRES_NEW}：取号必须在<b>自己的短事务</b>中提交。
     *
     * <p>若沿用调用方的业务事务，号段会被持有到业务事务结束——高并发下所有取号排在
     * 同一行的行锁后面，编号中心就变成了全局串行点。独立短事务让锁只存在于一条语句期间。
     *
     * <p>代价是业务事务回滚时该号不会回收（见 {@link NumberGenerator} 的契约说明）。
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String next(long tenantId, String businessType, LocalDate businessDate) {
        NumberSequenceMapper.NumberRuleRecord rule = mapper.findEnabledRule(tenantId, businessType);
        if (rule == null) {
            throw new DomainException(NumberingErrorCode.RULE_NOT_FOUND,
                    Map.of("businessType", businessType));
        }

        Long seq = mapper.nextValue(tenantId, businessType, businessDate);

        long max = (long) Math.pow(10, rule.seqWidth()) - 1;
        if (seq > max) {
            // 超限必须显式失败：截断会产生重号，进位会让单号变长而破坏既有解析逻辑
            throw new DomainException(NumberingErrorCode.SEQUENCE_EXHAUSTED,
                    Map.of("businessType", businessType, "max", max));
        }

        return rule.prefix()
                + businessDate.format(DATE_PART)
                + String.format("%0" + rule.seqWidth() + "d", seq);
    }
}
