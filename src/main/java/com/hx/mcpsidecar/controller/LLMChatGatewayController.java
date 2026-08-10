//package com.hx.mcpsidecar.controller;
//
//import com.hx.mcpsidecar.service.IAuthService;
//import com.hx.mcpsidecar.service.LLMChatLimiter;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.http.HttpStatus;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.PostMapping;
//import org.springframework.web.bind.annotation.RequestHeader;
//import org.springframework.web.bind.annotation.RequestMapping;
//import org.springframework.web.bind.annotation.RestController;
//
//@RestController
//@RequestMapping("/llm")
//public class LLMChatGatewayController {
//
//    @Autowired
//    private IAuthService authService;
//
//    @Autowired
//    private LLMChatLimiter llmChatLimiter;
//
//    @PostMapping("/chat")
//    public ResponseEntity<?> check(String token, String userId) {
//        String rawToken = token.replace("Bearer ", "");
//        // 1.验证用户登录
//        boolean validSession = authService.validateToken(token, userId);
//        if (!validSession) {
//            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("未登录用户，不允许对话");
//        }
//        // 2.并发限制
//        boolean ok = llmChatLimiter.tryToChat(userId);
//        if (!ok) {
//            return ResponseEntity.status(429).body("当前使用人数多，请稍后再试");
//        }
//        return ResponseEntity.ok("允许对话");
//    }
//
//    @PostMapping("/session/end")
//    public ResponseEntity<?> end(
//        @RequestHeader("Authorization") String auth, String userId) {
//
//        String token = auth.replace("Bearer ", "");
//        if(authService.validateToken(token, userId)) {
//            llmChatLimiter.completeChat(userId);
//        } else {
//            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("未登录用户，不允许结束对话");
//        }
//
//        return ResponseEntity.ok("结束成功");
//    }
//}
