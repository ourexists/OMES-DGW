package com.omes.dgw.auth.service;

import com.omes.dgw.config.AppProperties;
import com.omes.dgw.device.dto.MenuVo;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class MenuService {

    private final AppProperties appProperties;

    public MenuService(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    public List<MenuVo> menusForRoles(Set<String> roles) {
        return appProperties.getMenus().stream()
                .filter(menu -> hasAnyRole(menu.getRoles(), roles))
                .map(menu -> toVo(menu, roles))
                .collect(Collectors.toList());
    }

    private MenuVo toVo(AppProperties.MenuItem item, Set<String> roles) {
        List<MenuVo> children = item.getChildren().stream()
                .filter(child -> hasAnyRole(child.getRoles(), roles))
                .map(child -> toVo(child, roles))
                .collect(Collectors.toList());
        return MenuVo.builder()
                .id(item.getId())
                .title(item.getTitle())
                .path(item.getPath())
                .icon(item.getIcon())
                .children(children)
                .build();
    }

    private boolean hasAnyRole(List<String> requiredRoles, Set<String> userRoles) {
        if (requiredRoles == null || requiredRoles.isEmpty()) {
            return true;
        }
        return requiredRoles.stream().anyMatch(userRoles::contains);
    }
}
