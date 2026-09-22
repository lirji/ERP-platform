package com.lrj.erp.kernel.outbox;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Outbox 持久层。
 *
 * <p>关闭租户与数据权限拦截：投递由后台调度器执行，那里<b>没有</b> AccessContext；
 * 且 Outbox 必须跨租户投递全部待发消息。租户信息随消息本体携带，不靠 SQL 注入。
 */
@Mapper
@InterceptorIgnore(tenantLine = "true", dataPermission = "true")
public interface OutboxMapper {

    /** 运维汇总，只扫描未发布及死信分支。 */
    OutboxHealthService.Health health();

    int append(@Param("m") OutboxMessage message);

    /**
     * 领取一批待投递消息。
     *
     * <p>{@code FOR UPDATE SKIP LOCKED}：多实例并发投递时，各自领到不同的行，
     * 既不会重复投递，也不会互相阻塞。没有它就只能靠单实例调度，
     * 或者接受重复投递（那要求下游严格幂等，代价更高）。
     */
    List<OutboxMessage> claimPending(@Param("limit") int limit);

    int markPublished(@Param("id") long id);

    /** 记录一次失败：重试次数 +1，按退避设置下次重试时间；达上限则置 DEAD。 */
    int markFailed(@Param("id") long id,
                   @Param("maxRetry") int maxRetry,
                   @Param("backoffSeconds") int backoffSeconds,
                   @Param("error") String error);
}
