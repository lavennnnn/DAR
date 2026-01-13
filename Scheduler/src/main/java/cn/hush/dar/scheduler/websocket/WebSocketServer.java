package cn.hush.dar.scheduler.websocket;


import jakarta.websocket.OnClose;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * @program: DAR
 * @description: WebSocket服务
 * @author: Hush
 * @create: 2026-01-13 19:51
 **/
@Component
@Slf4j
@ServerEndpoint("/ws/monitor")
public class WebSocketServer {

    // 线程安全的 Set，存放所有连接的客户端 Session
    private static final CopyOnWriteArraySet<Session> sessionSet = new CopyOnWriteArraySet<>();

    @OnOpen
    public void onOpen(Session session) {
        sessionSet.add(session);
        log.info("前端已连接 WebSocket, 当前在线人数: {}", sessionSet.size());
    }

    @OnClose
    public void onClose(Session session) {
        sessionSet.remove(session);
        log.info("前端断开连接, 当前在线人数: {}", sessionSet.size());
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        log.info("收到前端消息: {}", message);
    }

    /**
     * 群发消息（广播）
     * 供 TaskSchedulerService 调用
     */
    public static void sendInfo(String message) {
        for (Session session : sessionSet) {
            try {
                session.getBasicRemote().sendText(message);
            } catch (IOException e) {
                log.error("WebSocket消息发送失败", e);
            }
        }
    }

}
