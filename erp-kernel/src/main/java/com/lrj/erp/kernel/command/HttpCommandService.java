package com.lrj.erp.kernel.command;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.lrj.erp.kernel.context.AccessContextHolder;
import com.lrj.erp.kernel.error.DomainException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Supplier;
/** 命令登记与业务共享事务，失败回滚登记，成功重放不重复执行业务。 */
@Service
public class HttpCommandService {
    private final HttpCommandMapper mapper; private final ObjectMapper json;
    public HttpCommandService(HttpCommandMapper mapper,ObjectMapper json){this.mapper=mapper;this.json=json;}
    /** 重放前同样调用当前授权校验；调用方在此事务内锁定授权边界及业务资源。 */
    @Transactional(timeout=10)
    public <T> T execute(String endpoint,String key,Object request,int status,Class<T> type,Runnable authorize,Supplier<T> action){
        if(key==null || !key.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
            throw new DomainException(CommandErrorCode.KEY_REQUIRED);
        var ctx=AccessContextHolder.require();
        authorize.run();
        String hash=hash(request);
        int inserted=mapper.claim(ctx.tenantId(),endpoint,key,ctx.userId(),hash,status);
        if(inserted==0){
            var entry=mapper.find(ctx.tenantId(),endpoint,key);
            if(entry.actorId()!=ctx.userId() || !entry.requestHash().equals(hash)) throw new DomainException(CommandErrorCode.KEY_CONFLICT);
            if(entry.responseJson()==null) throw new DomainException(CommandErrorCode.IN_PROGRESS);
            try{return json.readValue(entry.responseJson(),type);}catch(Exception e){throw new IllegalStateException("命令结果无法读取",e);}
        }
        T result=action.get();
        try{
            if(mapper.finish(ctx.tenantId(),endpoint,key,json.writeValueAsString(result))!=1) throw new IllegalStateException("命令结果保存冲突");
        }catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException("命令结果无法保存",e);}
        return result;
    }
    private String hash(Object value){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsString(canonical(json.valueToTree(value))).getBytes(StandardCharsets.UTF_8)));}
        catch(Exception e){throw new IllegalStateException("命令摘要失败",e);}
    }
    private JsonNode canonical(JsonNode value){
        if(value.isObject()){
            ObjectNode result=json.createObjectNode(); var keys=new TreeSet<String>();value.fieldNames().forEachRemaining(keys::add);
            keys.forEach(k->result.set(k,canonical(value.get(k))));return result;
        }
        if(value.isArray()){ArrayNode result=json.createArrayNode();value.forEach(v->result.add(canonical(v)));return result;}
        return value;
    }
}
