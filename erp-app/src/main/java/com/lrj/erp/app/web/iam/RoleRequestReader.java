package com.lrj.erp.app.web.iam;
import com.fasterxml.jackson.databind.*;
import com.lrj.erp.kernel.error.*;
import org.springframework.stereotype.Component;
import java.util.List;
/** 本批IAM请求严格解码，不改变历史API的Jackson配置。 */
@Component
public class RoleRequestReader {
    private final ObjectMapper json;
    public RoleRequestReader(ObjectMapper json){this.json=json;}
    /** 标识/版本必须是字符串，拒绝未知字段与Jackson隐式类型转换。 */
    public <T>T read(JsonNode body,Class<T> type){try{
        if(!body.isObject())throw new IllegalArgumentException();
        for(String field:List.of("code","name","expectedVersion")){if(body.has(field)&&!body.get(field).isTextual())throw new IllegalArgumentException();}
        if(body.has("enabled")&&!body.get("enabled").isBoolean())throw new IllegalArgumentException();
        if(body.has("permissions")&&(!body.get("permissions").isArray()||java.util.stream.StreamSupport.stream(body.get("permissions").spliterator(),false).anyMatch(v->!v.isTextual())))throw new IllegalArgumentException();
        if(body.has("scope")){
            var scope=body.get("scope");if(!scope.isObject())throw new IllegalArgumentException();
            for(String field:List.of("type","warehouseMode"))if(!scope.has(field)||!scope.get(field).isTextual())throw new IllegalArgumentException();
            for(String field:List.of("orgIds","companyIds","warehouseIds"))if(!scope.has(field)||!scope.get(field).isArray()||java.util.stream.StreamSupport.stream(scope.get(field).spliterator(),false).anyMatch(v->!v.isTextual()))throw new IllegalArgumentException();
        }
        return json.readerFor(type).with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(body);
    }catch(Exception e){throw new DomainException(SystemErrorCode.MALFORMED_BODY);}}
}
