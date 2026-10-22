package com.omes.dgw.device.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MenuVo {

    private String id;
    private String title;
    private String path;
    private String icon;
    @Builder.Default
    private List<MenuVo> children = new ArrayList<>();
}
