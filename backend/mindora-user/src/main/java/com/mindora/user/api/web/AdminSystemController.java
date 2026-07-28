package com.mindora.user.api.web;

import com.mindora.common.api.ApiResponse;
import com.mindora.user.application.AdminIdentityService;
import com.mindora.user.application.AdminIdentityService.PageResult;
import com.mindora.user.application.AdminIdentityService.PermissionRow;
import com.mindora.user.application.AdminIdentityService.PermissionUpdateRequest;
import com.mindora.user.application.AdminIdentityService.RoleRequest;
import com.mindora.user.application.AdminIdentityService.RoleRow;
import com.mindora.user.application.AdminIdentityService.RoleSearch;
import com.mindora.user.application.AdminIdentityService.UserRequest;
import com.mindora.user.application.AdminIdentityService.UserRow;
import com.mindora.user.application.AdminIdentityService.UserSearch;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminSystemController {
    private final AdminIdentityService identityService;

    public AdminSystemController(AdminIdentityService identityService) {
        this.identityService = identityService;
    }

    @GetMapping("/users")
    public ApiResponse<PageResult<UserRow>> users(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) String userName,
            @RequestParam(required = false) String userGender,
            @RequestParam(required = false) String userPhone,
            @RequestParam(required = false) String userEmail,
            @RequestParam(required = false) String status,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return success(identityService.listUsers(new UserSearch(
                current,
                size,
                userName,
                userGender,
                userPhone,
                userEmail,
                status)), traceId);
    }

    @PostMapping("/users")
    public ApiResponse<UserRow> createUser(
            @Valid @RequestBody UserRequest request,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return success(identityService.createUser(request), traceId);
    }

    @PutMapping("/users/{id}")
    public ApiResponse<UserRow> updateUser(
            @PathVariable String id,
            @Valid @RequestBody UserRequest request,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return success(identityService.updateUser(id, request), traceId);
    }

    @DeleteMapping("/users/{id}")
    public ApiResponse<Void> deleteUser(
            @PathVariable String id,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        identityService.deleteUser(id);
        return success(null, traceId);
    }

    @GetMapping("/roles")
    public ApiResponse<PageResult<RoleRow>> roles(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) String roleName,
            @RequestParam(required = false) String roleCode,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return success(identityService.listRoles(new RoleSearch(
                current,
                size,
                roleName,
                roleCode,
                description,
                enabled,
                startTime,
                endTime)), traceId);
    }

    @PostMapping("/roles")
    public ApiResponse<RoleRow> createRole(
            @Valid @RequestBody RoleRequest request,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return success(identityService.createRole(request), traceId);
    }

    @PutMapping("/roles/{id}")
    public ApiResponse<RoleRow> updateRole(
            @PathVariable String id,
            @Valid @RequestBody RoleRequest request,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return success(identityService.updateRole(id, request), traceId);
    }

    @DeleteMapping("/roles/{id}")
    public ApiResponse<Void> deleteRole(
            @PathVariable String id,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        identityService.deleteRole(id);
        return success(null, traceId);
    }

    @GetMapping("/roles/{id}/permissions")
    public ApiResponse<Set<String>> rolePermissions(
            @PathVariable String id,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return success(identityService.rolePermissions(id), traceId);
    }

    @PutMapping("/roles/{id}/permissions")
    public ApiResponse<Set<String>> updateRolePermissions(
            @PathVariable String id,
            @RequestBody PermissionUpdateRequest request,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return success(identityService.updateRolePermissions(id, request), traceId);
    }

    @GetMapping("/permissions")
    public ApiResponse<List<PermissionRow>> permissions(
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return success(identityService.listPermissions(), traceId);
    }

    private <T> ApiResponse<T> success(T data, String traceId) {
        return ApiResponse.success(
                data,
                traceId == null || traceId.isBlank() ? UUID.randomUUID().toString() : traceId);
    }
}
