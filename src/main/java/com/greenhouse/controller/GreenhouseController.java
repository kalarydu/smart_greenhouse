package com.greenhouse.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.greenhouse.common.Result;
import com.greenhouse.entity.Greenhouse;
import com.greenhouse.service.GreenhouseService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/greenhouse")
public class GreenhouseController {

    private final GreenhouseService greenhouseService;

    // 构造方法注入（Spring 推荐方式，比 @Autowired 更安全）
    public GreenhouseController(GreenhouseService greenhouseService) {
        this.greenhouseService = greenhouseService;
    }

    // ==================== 查询 ====================

    /**
     * 分页查询大棚列表
     * GET /api/greenhouse/list?page=1&size=10&name=一号棚
     */
    @GetMapping("/list")
    public Result<Page<Greenhouse>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String name) {

        // LambdaQueryWrapper：用 Java lambda 方式构建查询条件，避免手写字段名
        LambdaQueryWrapper<Greenhouse> wrapper = new LambdaQueryWrapper<>();
        if (name != null && !name.isEmpty()) {
            wrapper.like(Greenhouse::getName, name);  // WHERE name LIKE '%一号棚%'
        }
        wrapper.orderByDesc(Greenhouse::getCreateTime);

        Page<Greenhouse> result = greenhouseService.page(new Page<>(page, size), wrapper);
        return Result.ok(result);
    }

    /**
     * 根据ID查单个大棚
     * GET /api/greenhouse/1
     */
    @GetMapping("/{id}")
    public Result<Greenhouse> getById(@PathVariable Long id) {
        Greenhouse greenhouse = greenhouseService.getById(id);
        if (greenhouse == null) {
            return Result.fail("大棚不存在");
        }
        return Result.ok(greenhouse);
    }

    // ==================== 新增 ====================

    /**
     * 新增大棚
     * POST /api/greenhouse
     * Body: { "name": "一号棚", "location": "A区", "area": 200.0, ... }
     */
    @PostMapping
    public Result<?> add(@RequestBody Greenhouse greenhouse) {
        boolean saved = greenhouseService.save(greenhouse);
        return saved ? Result.ok("新增成功") : Result.fail("新增失败");
    }

    // ==================== 修改 ====================

    /**
     * 修改大棚信息
     * PUT /api/greenhouse
     * Body: { "id": 1, "name": "一号棚(已改造)", ... }
     */
    @PutMapping
    public Result<?> update(@RequestBody Greenhouse greenhouse) {
        boolean updated = greenhouseService.updateById(greenhouse);
        return updated ? Result.ok("修改成功") : Result.fail("修改失败");
    }

    // ==================== 删除 ====================

    /**
     * 删除大棚（逻辑删除：deleted 字段变为 1）
     * DELETE /api/greenhouse/1
     */
    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        boolean removed = greenhouseService.removeById(id);
        return removed ? Result.ok("删除成功") : Result.fail("删除失败");
    }
}
