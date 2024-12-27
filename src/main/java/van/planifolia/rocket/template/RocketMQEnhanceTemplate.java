package van.planifolia.rocket.template;

import com.alibaba.fastjson.JSONObject;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.StringUtils;
import van.planifolia.rocket.configure.RocketEnhanceProperties;
import van.planifolia.rocket.domain.BaseMessage;

import javax.annotation.Resource;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class RocketMQEnhanceTemplate {
    /**
     * -- GETTER --
     *  获取原生RocketMQTemplate
     */
    @Getter
    private final RocketMQTemplate template;

    @Resource
    private RocketEnhanceProperties rocketEnhanceProperties;


    /**
     * 根据系统上下文自动构建隔离后的topic
     * 构建目的地
     */
    public String buildDestination(String topic, String tag) {
        topic = reBuildTopic(topic);
        return topic + ":" + tag;
    }

    /**
     * 根据环境重新隔离topic
     *
     * @param topic 原始topic
     */
    private String reBuildTopic(String topic) {
        if (rocketEnhanceProperties.isEnabledIsolation() && StringUtils.hasText(rocketEnhanceProperties.getEnvironment())) {
            return topic + "_" + rocketEnhanceProperties.getEnvironment();
        }
        return topic;
    }

    /**
     * 发送同步消息，对发送消息的二层封装，使用topic加上tag来构建出真实的主题
     *
     * @param message 消息实体，需要继承BaseMessage
     * @param topic   消息主题
     * @param tag     标签，用于构建主题
     */
    public <T extends BaseMessage> SendResult send(String topic, String tag, T message) {
        // 注意分隔符
        return send(buildDestination(topic, tag), message);
    }

    /**
     * 发送同步消息，对发送同步消息的一层封装。
     *
     * @param destination 消息主题
     * @param message     消息内容
     * @param <T>         实体的泛型
     * @return 发送结果
     */
    public <T extends BaseMessage> SendResult send(String destination, T message) {
        // 设置业务键，此处根据公共的参数进行处理
        // 更多的其它基础业务处理...
        Message<T> sendMessage = MessageBuilder.withPayload(message).setHeader(RocketMQHeaders.KEYS, message.getKey()).build();
        SendResult sendResult = template.syncSend(destination, sendMessage);
        // 此处为了方便查看给日志转了json，根据选择选择日志记录方式，例如ELK采集
        log.info("[{}]同步消息[{}]发送结果[{}]", destination, JSONObject.toJSON(message), JSONObject.toJSON(sendResult));
        return sendResult;
    }

    /**
     * 发送延迟消息的二层封装，使用topic加上tag来构建出真实的topic
     *
     * @param topic      消息主题
     * @param tag        消息标签
     * @param message    消息体
     * @param delayLevel 延迟等级
     * @param <T>        消息实体的泛型
     * @return 发送结果
     */
    public <T extends BaseMessage> SendResult sendDelay(String topic, String tag, T message, int delayLevel) {
        return sendDelay(buildDestination(topic, tag), message, delayLevel);
    }

    /**
     * 发送延迟消息的一层封装，使用destination直接作为真实的topic
     *
     * @param destination 主题
     * @param message     消息
     * @param delayLevel  延迟等级
     * @param <T>         实体的泛型
     * @return 执行结构
     */
    public <T extends BaseMessage> SendResult sendDelay(String destination, T message, int delayLevel) {
        Message<T> sendMessage = MessageBuilder.withPayload(message).setHeader(RocketMQHeaders.KEYS, message.getKey()).build();
        SendResult sendResult = template.syncSend(destination, sendMessage, 3000, delayLevel);
        log.info("[{}]延迟等级[{}]消息[{}]发送结果[{}]", destination, delayLevel, JSONObject.toJSON(message), JSONObject.toJSON(sendResult));
        return sendResult;
    }
}
