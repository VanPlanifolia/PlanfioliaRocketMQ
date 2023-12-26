package van.planifolia.rocket.domain;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * @Description 基础消息类，所有的rocketMQ消息应该集成这个抽象类
 * @Author Van.Planifolia
 * @Date 2023/12/25
 * @Version 1.0
 */
@Data
@Accessors(chain = true)
public abstract class BaseMessage {
    /**
     * 业务键，用于RocketMQ控制台查看消费情况，唯一
     */
    protected String key;
    /**
     * 发送消息来源，用于排查问题
     */
    protected String source = "";

    /**
     * 发送时间
     */
    protected LocalDateTime sendTime = LocalDateTime.now();

    /**
     * 重试次数，用于判断重试次数，超过重试次数发送异常警告
     */
    protected Integer retryTimes = 0;
}
