package com.lrj.erp.document.service;

import com.lrj.erp.document.repository.DocumentRelationMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 单据关系图（CAP-P08）。系统必须能回答两个问题（提示词第九章）：
 * 「这张业务单据来自哪里」与「它后续产生了哪些单据」。
 *
 * <p>关系<b>显式登记</b>，不靠外键或约定推断：下游单据可能跨模块产生
 * （入库 → 应付由 erp-finance 消费事件创建），跨模块不共享表，
 * 只有显式登记才能把链路串起来。
 */
@Service
public class DocumentLineageService {

    public static final String DERIVED_FROM = "DERIVED_FROM";

    private final DocumentRelationMapper mapper;

    public DocumentLineageService(DocumentRelationMapper mapper) {
        this.mapper = mapper;
    }

    /** 登记「child 由 parent 派生」。重复登记幂等。 */
    @Transactional
    public void link(long tenantId, String parentType, String parentId, String parentNo,
                     String childType, String childId, String childNo) {
        mapper.link(tenantId, parentType, parentId, parentNo,
                childType, childId, childNo, DERIVED_FROM);
    }

    /** 正查：这张单产生了哪些下游单据。 */
    public List<Map<String, Object>> downstream(long tenantId, String type, String id) {
        return mapper.findDownstream(tenantId, type, id);
    }

    /** 反查：这张单来自哪里。 */
    public List<Map<String, Object>> upstream(long tenantId, String type, String id) {
        return mapper.findUpstream(tenantId, type, id);
    }
}
