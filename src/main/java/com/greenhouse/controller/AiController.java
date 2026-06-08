package com.greenhouse.controller;

import com.greenhouse.common.Result;
import com.greenhouse.service.DeepSeekService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final DeepSeekService deepSeekService;

    public AiController(DeepSeekService deepSeekService) {
        this.deepSeekService = deepSeekService;
    }

    /**
     * AI 聊天接口
     * POST /api/ai/chat
     * Body: { "message": "一号大棚最近有什么异常吗？" }
     */
    @PostMapping("/chat")
    public Result<?> chat(@RequestBody Map<String, String> body) {
        String message = body.getOrDefault("message", "");
        if (message.isBlank()) {
            return Result.fail("请输入问题");
        }
        String reply = deepSeekService.chat(message);
        return Result.ok(Map.of("reply", reply));
    }
}
