package com.lrj.erp.kernel.command;
import org.apache.ibatis.annotations.*;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
/** 显式租户绑定，ON CONFLICT由数据库串行化同一命令，避免先查后写竞争。 */
@Mapper
@InterceptorIgnore(tenantLine="true",dataPermission="true")
public interface HttpCommandMapper {
    record Entry(long actorId,String requestHash,String responseJson,int httpStatus) {}
    int claim(@Param("tenant") long tenant,@Param("endpoint") String endpoint,@Param("key") String key,
        @Param("actor") long actor,@Param("hash") String hash,@Param("status") int status);
    Entry find(@Param("tenant") long tenant,@Param("endpoint") String endpoint,@Param("key") String key);
    int finish(@Param("tenant") long tenant,@Param("endpoint") String endpoint,@Param("key") String key,@Param("json") String json);
}
