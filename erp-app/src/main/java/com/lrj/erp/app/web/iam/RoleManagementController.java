package com.lrj.erp.app.web.iam;
import com.fasterxml.jackson.databind.*;
import com.lrj.erp.iam.service.RoleManagementService;
import com.lrj.erp.iam.service.RoleModels.*;
import com.lrj.erp.iam.security.RequiresPermission;
import com.lrj.erp.kernel.error.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.List;
/** 新管理API使用严格DTO；旧/me与/roles响应不收紧或改变。 */
@RestController
@RequestMapping("/api/v1/iam")
public class RoleManagementController {
    private final RoleManagementService service;private final RoleRequestReader reader;
    public RoleManagementController(RoleManagementService service,RoleRequestReader reader){this.service=service;this.reader=reader;}
    /** 分页参数显式校验，避免类型转换错误成为500。 */
    @GetMapping("/role-directory") @RequiresPermission("iam:role:read")
    public ResponseEntity<Page<Role>> directory(@RequestParam(defaultValue="1") String page,@RequestParam(defaultValue="20") String size,
        @RequestParam(required=false) String q,@RequestParam(defaultValue="id") String sort){
        return reply(service.directory(integer(page),integer(size),q,sort));
    }
    /** 详情及命令响应禁止缓存，避免角色撤权后展示旧权限。 */
    @GetMapping("/roles/{id}") @RequiresPermission("iam:role:read")
    public ResponseEntity<Role> detail(@PathVariable String id){return reply(service.detail(id(id)));}
    /** 固定可授目录不包括安全管理员。 */
    @GetMapping("/permissions") @RequiresPermission("iam:role:read")
    public ResponseEntity<List<Permission>> permissions(){return reply(service.permissions());}
    /** 请求身份取自安全上下文，拒绝tenant/operator等额外字段。 */
    @PostMapping("/roles") @RequiresPermission("iam:role:write")
    public ResponseEntity<Role> create(@RequestHeader(value="Idempotency-Key",required=false) String key,@RequestBody JsonNode body){
        Role role=service.create(key,reader.read(body,Create.class));return ResponseEntity.created(URI.create("/api/v1/iam/roles/"+role.id())).cacheControl(CacheControl.noStore()).body(role);
    }
    /** 普通角色元数据更新，编码保持稳定。 */
    @PutMapping("/roles/{id}") @RequiresPermission("iam:role:write")
    public ResponseEntity<Role> update(@PathVariable String id,@RequestHeader(value="Idempotency-Key",required=false) String key,@RequestBody JsonNode body){return reply(service.update(id(id),key,reader.read(body,Update.class)));}
    /** 权限替换不信任前端按钮是否可见。 */
    @PutMapping("/roles/{id}/permissions") @RequiresPermission("iam:role:write")
    public ResponseEntity<Role> permissions(@PathVariable String id,@RequestHeader(value="Idempotency-Key",required=false) String key,@RequestBody JsonNode body){return reply(service.assignPermissions(id(id),key,reader.read(body,Permissions.class)));}
    /** 畸形JSON在进入DTO前拒绝，不以系统错误掩盖调用方输入问题。 */
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<com.lrj.erp.app.web.ErrorResponse> malformed(){
        return ResponseEntity.badRequest().body(new com.lrj.erp.app.web.ErrorResponse(SystemErrorCode.MALFORMED_BODY.code(),SystemErrorCode.MALFORMED_BODY.message(),
            com.lrj.erp.kernel.context.AccessContextHolder.require().traceId(),java.util.Map.of()));
    }
    private static long id(String value){try{if(!value.matches("[1-9][0-9]*"))throw new NumberFormatException();return Long.parseLong(value);}catch(NumberFormatException e){throw new DomainException(SystemErrorCode.MALFORMED_BODY);}}
    private static int integer(String value){try{return Integer.parseInt(value);}catch(NumberFormatException e){throw new DomainException(SystemErrorCode.INVALID_PAGINATION);}}
    private static <T>ResponseEntity<T> reply(T value){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);}
}
