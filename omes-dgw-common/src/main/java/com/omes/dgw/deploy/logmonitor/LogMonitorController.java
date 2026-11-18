package com.omes.dgw.deploy.logmonitor;

import com.omes.dgw.common.Result;
import com.omes.dgw.deploy.logmonitor.dto.LogContentVo;
import com.omes.dgw.deploy.logmonitor.dto.LogFileVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "日志监控")
@RestController
@RequestMapping("/system/logs")
@PreAuthorize("hasRole('ADMIN')")
public class LogMonitorController {

    private final LogMonitorService logMonitorService;

    public LogMonitorController(LogMonitorService logMonitorService) {
        this.logMonitorService = logMonitorService;
    }

    @Operation(summary = "列出可查看的服务日志文件")
    @GetMapping
    public Result<List<LogFileVo>> list() {
        return Result.ok(logMonitorService.listFiles());
    }

    @Operation(summary = "尾读日志（类似 tail -N，最多 500 行）")
    @GetMapping("/content")
    public Result<LogContentVo> content(
            @RequestParam("name") String name,
            @RequestParam(value = "tailLines", required = false) Integer tailLines,
            @RequestParam(value = "level", required = false) String level,
            @RequestParam(value = "keyword", required = false) String keyword
    ) {
        return Result.ok(logMonitorService.readContent(name, tailLines, level, keyword));
    }
}
