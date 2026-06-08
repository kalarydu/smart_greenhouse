package com.greenhouse.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.greenhouse.common.Result;
import com.greenhouse.entity.AlertLog;
import com.greenhouse.service.AlertLogService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/alert-log")
public class AlertLogController {

    private final AlertLogService alertLogService;

    public AlertLogController(AlertLogService alertLogService) {
        this.alertLogService = alertLogService;
    }

    /**
     * 分页查询报警记录（可按大棚ID、报警类型、状态筛选）
     * GET /api/alert-log/list?page=1&size=10&greenhouseId=1&alertType=TEMP_HIGH&status=0
     */
    @GetMapping("/list")
    public Result<Page<AlertLog>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Long greenhouseId,
            @RequestParam(required = false) String alertType,
            @RequestParam(required = false) Integer status) {

        LambdaQueryWrapper<AlertLog> wrapper = new LambdaQueryWrapper<>();
        if (greenhouseId != null) {
            wrapper.eq(AlertLog::getGreenhouseId, greenhouseId);
        }
        if (alertType != null && !alertType.isEmpty()) {
            wrapper.eq(AlertLog::getAlertType, alertType);
        }
        if (status != null) {
            wrapper.eq(AlertLog::getStatus, status);
        }
        wrapper.orderByDesc(AlertLog::getCreateTime);

        Page<AlertLog> result = alertLogService.page(new Page<>(page, size), wrapper);
        return Result.ok(result);
    }

    @GetMapping("/{id}")
    public Result<AlertLog> getById(@PathVariable Long id) {
        AlertLog alertLog = alertLogService.getById(id);
        return alertLog != null ? Result.ok(alertLog) : Result.fail("报警记录不存在");
    }

    @PostMapping
    public Result<?> add(@RequestBody AlertLog alertLog) {
        boolean saved = alertLogService.save(alertLog);
        return saved ? Result.ok("新增成功") : Result.fail("新增失败");
    }

    @PutMapping
    public Result<?> update(@RequestBody AlertLog alertLog) {
        boolean updated = alertLogService.updateById(alertLog);
        return updated ? Result.ok("修改成功") : Result.fail("修改失败");
    }

    /**
     * 处理报警：将状态改为"已处理"
     * PUT /api/alert-log/1/handle
     */
    @PutMapping("/{id}/handle")
    public Result<?> handle(@PathVariable Long id) {
        AlertLog alertLog = alertLogService.getById(id);
        if (alertLog == null) {
            return Result.fail("报警记录不存在");
        }
        alertLog.setStatus(1);
        alertLog.setHandleTime(LocalDateTime.now());
        alertLogService.updateById(alertLog);
        return Result.ok("报警已处理");
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        boolean removed = alertLogService.removeById(id);
        return removed ? Result.ok("删除成功") : Result.fail("删除失败");
    }
}
