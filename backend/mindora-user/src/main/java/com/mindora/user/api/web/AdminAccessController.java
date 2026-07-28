package com.mindora.user.api.web;

import com.mindora.common.api.ApiResponse;
import com.mindora.common.id.PublicIds;
import com.mindora.user.application.RbacPermissionService;
import com.mindora.user.domain.TokenPrincipal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminAccessController {
    private final RbacPermissionService permissionService;

    public AdminAccessController(RbacPermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @GetMapping("/me")
    public ApiResponse<AdminUserView> me(
            Authentication authentication,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        TokenPrincipal principal = principal(authentication);
        Set<String> permissions = permissionService.permissionsFor(principal);
        return success(new AdminUserView(
                PublicIds.toPublicId(principal.userId()),
                displayName(principal.email()),
                principal.email(),
                null,
                permissionService.roleCodesFor(principal.roles()),
                permissionService.uiButtonsFor(permissions),
                permissions), traceId);
    }

    @GetMapping("/menus")
    public ApiResponse<List<MenuView>> menus(
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return success(List.of(
                contentMenu(),
                systemMenu()), traceId);
    }

    private MenuView contentMenu() {
        return new MenuView(
                "/content",
                "Content",
                "/index/index",
                new MenuMeta("menus.content.title", "ri:article-line", Set.of("R_SUPER", "R_ADMIN")),
                List.of(
                        new MenuChildView(
                                "articles",
                                "ContentArticles",
                                "/content/articles",
                                new MenuMeta(
                                        "menus.content.articles",
                                        null,
                                        Set.of("R_SUPER", "R_ADMIN"),
                                        List.of(
                                                new AuthItem("新增", "add"),
                                                new AuthItem("编辑", "edit"),
                                                new AuthItem("删除", "delete"),
                                                new AuthItem("发布", "publish"),
                                                new AuthItem("下线", "unpublish"))),
                                List.of()),
                        new MenuChildView(
                                "editor",
                                "ContentEditor",
                                "/content/editor",
                                new MenuMeta(
                                        "menus.content.editor",
                                        null,
                                        Set.of("R_SUPER", "R_ADMIN")),
                                List.of()),
                        new MenuChildView(
                                "taxonomy",
                                "ContentTaxonomy",
                                "/content/taxonomy",
                                new MenuMeta(
                                        "menus.content.taxonomy",
                                        null,
                                        Set.of("R_SUPER", "R_ADMIN"),
                                        List.of(
                                                new AuthItem("新增", "add"),
                                                new AuthItem("编辑", "edit"),
                                                new AuthItem("删除", "delete"))),
                                List.of()),
                        new MenuChildView(
                                "assets",
                                "ContentAssets",
                                "/content/assets",
                                new MenuMeta(
                                        "menus.content.assets",
                                        null,
                                        Set.of("R_SUPER", "R_ADMIN"),
                                        List.of(new AuthItem("上传", "upload"))),
                                List.of())));
    }

    private MenuView systemMenu() {
        return new MenuView(
                "/system",
                "System",
                "/index/index",
                new MenuMeta("menus.system.title", "ri:user-3-line", Set.of("R_SUPER", "R_ADMIN")),
                List.of(
                        new MenuChildView(
                                "user",
                                "User",
                                "/system/user",
                                new MenuMeta(
                                        "menus.system.user",
                                        null,
                                        Set.of("R_SUPER", "R_ADMIN"),
                                        List.of(
                                                new AuthItem("新增", "add"),
                                                new AuthItem("编辑", "edit"),
                                                new AuthItem("删除", "delete"))),
                                List.of()),
                        new MenuChildView(
                                "role",
                                "Role",
                                "/system/role",
                                new MenuMeta(
                                        "menus.system.role",
                                        null,
                                        Set.of("R_SUPER"),
                                        List.of(
                                                new AuthItem("新增", "add"),
                                                new AuthItem("编辑", "edit"),
                                                new AuthItem("删除", "delete"),
                                                new AuthItem("权限", "permission"))),
                                List.of()),
                        new MenuChildView(
                                "menu",
                                "Menus",
                                "/system/menu",
                                new MenuMeta(
                                        "menus.system.menu",
                                        null,
                                        Set.of("R_SUPER"),
                                        List.of(
                                                new AuthItem("新增", "add"),
                                                new AuthItem("编辑", "edit"),
                                                new AuthItem("删除", "delete"))),
                                List.of())));
    }

    private TokenPrincipal principal(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof TokenPrincipal principal)) {
            throw new IllegalStateException("Authenticated admin principal is required");
        }
        return principal;
    }

    private String displayName(String email) {
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }

    private <T> ApiResponse<T> success(T data, String traceId) {
        return ApiResponse.success(
                data,
                traceId == null || traceId.isBlank() ? UUID.randomUUID().toString() : traceId);
    }

    public record AdminUserView(
            String userId,
            String userName,
            String email,
            String avatar,
            Set<String> roles,
            Set<String> buttons,
            Set<String> permissions) {
    }

    public record MenuView(
            String path,
            String name,
            String component,
            MenuMeta meta,
            List<MenuChildView> children) {
    }

    public record MenuChildView(
            String path,
            String name,
            String component,
            MenuMeta meta) {
        public MenuChildView(
                String path,
                String name,
                String component,
                MenuMeta meta,
                List<?> ignoredChildren) {
            this(path, name, component, meta);
        }
    }

    public record MenuMeta(
            String title,
            String icon,
            Set<String> roles,
            List<AuthItem> authList) {
        public MenuMeta(String title, String icon, Set<String> roles) {
            this(title, icon, roles, List.of());
        }
    }

    public record AuthItem(
            String title,
            String authMark) {
    }
}
