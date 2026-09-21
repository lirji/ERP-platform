package com.lrj.erp.document.repository;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/** 单据关系图持久层。 */
@Mapper
public interface DocumentRelationMapper {

    /** 建立父子关系；重复建立不产生多行。 */
    int link(@Param("tenantId") long tenantId,
             @Param("parentType") String parentType, @Param("parentId") String parentId,
             @Param("parentNo") String parentNo,
             @Param("childType") String childType, @Param("childId") String childId,
             @Param("childNo") String childNo,
             @Param("relationType") String relationType);

    /** 正查：这张单产生了哪些下游单据。 */
    List<Map<String, Object>> findDownstream(@Param("tenantId") long tenantId,
                                             @Param("type") String type,
                                             @Param("id") String id);

    /** 反查：这张单来自哪里。 */
    List<Map<String, Object>> findUpstream(@Param("tenantId") long tenantId,
                                           @Param("type") String type,
                                           @Param("id") String id);
}
