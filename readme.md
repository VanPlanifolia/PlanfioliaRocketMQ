### `Planifolia-RocketMQ`使用教程

##### 1.本地安装

`Planifolia-RocketMQ`为封装完成的`RocketMQ`的`SpringBoot-starter`，引入设置好配置文件即可自动装配开箱即用。

1. 使用`IDEA`从`Github`拉取源码

 git地址:https://github.com/VanPlanifolia/PlanfioliaRocketMQ.git

2.等待`Maven`下载完毕后执行`maven install`打包入本地maven

##### 2.项目引入

1.在需要使用`RocketMQ`的项目`POM`文件中引入下面依赖

~~~xml
		<dependency>
			<groupId>van.planifolia</groupId>
			<artifactId>planifolia-rocketmq</artifactId>
			<version>1.1.0</version>
		</dependency>
~~~

2.`application`文件修改`RocketMQ`配置

```yaml
rocketmq:
  name-server: 你的RocketMQ服务器IP:你的RocketMQ服务器端口
  producer:
    group: 你自定义的消息组
```

##### 3.具体使用

 1.如何构建消息？

 构建的任何消息都需要继承依赖中的基础消息类`BaseMessage`，里面封装了基础属性与KEY，其中KEY必须不为空！

~~~java
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

~~~

2.如何发送消息

 依赖引入成功之后即可使用`Spring`自动注入依赖`RocketMQEnhanceTemplate`，该方法封装了发送消息的系列方法包含发送普通  消息(`send`)，发送延迟消息(`sendDelay`)

~~~java
@Component
@Slf4j
public class BeeOrderProducer {
    @Resource
    private RocketMQEnhanceTemplate rocketMQEnhanceTemplate;

    /**
     * 下订单的数据进入MQ
     *
     * @param placeOrderVO 下订单基础信息
     * @return 订单基础信息
     */
    public BeePlaceOrderVO sendOrderToMQ(BeePlaceOrderVO placeOrderVO) {
        // 订单号你可以选择发送前构建亦或发送后构建看具体需求
        String orderNo = OrderUtil.generateOrderNo();
        if (BeeConstant.ORDER_PAY_TYPE_IS.equals(placeOrderVO.getType())) {
            // 消息Key会伴随消息一生，所以必须设置且唯一
            placeOrderVO.setKey(orderNo);
            // 发送结果Result
            SendResult send = rocketMQEnhanceTemplate.send(BeeConstant.BEE_MQ_TOPIC_ORDER, BeeConstant.ORDER_PAY_TYPE_IS, placeOrderVO);
            log.info("订单 : {},成功投递到MQ中", orderNo);
        } else if (BeeConstant.ORDER_PAY_TYPE_NOT.equals(placeOrderVO.getType())) {
            placeOrderVO.setKey("F" + orderNo);
            rocketMQEnhanceTemplate.send(BeeConstant.BEE_MQ_TOPIC_ORDER_FREE, BeeConstant.ORDER_PAY_TYPE_NOT, placeOrderVO);
            log.info("免费订单 : {},成功投递到MQ中", placeOrderVO.getKey());
        } else {
            throw new JeecgBootException("错误的下单类型！");
        }
        return placeOrderVO;
    }
}
~~~

2.如何收消息

 依赖中定义了基础消息处理器(抽象类)后续使用者创建的所有Customer都必须继承该抽象类,并且重写其中部分方法。

~~~java
@Slf4j
@Component
@RocketMQMessageListener(
        // 消息组，相同消息组的消费者可以实现负载均衡
        consumerGroup = BeeConstant.BEE_MQ_TOPIC_ORDER,
        // 消息主题，消息主题要与发送消息时候的主题对应可以实现消息的接收
        topic = BeeConstant.BEE_MQ_TOPIC_ORDER,
        // 过滤器通配符一般用于过滤消息，一般为*或者不填
        selectorExpression = "*",
        //默认是64个线程并发消息，配置 consumeThreadMax 参数指定并发消费线程数，避免太大导致资源不够
        consumeThreadMax = 5
)
public class BeeOrderConsumer extends EnhanceMessageHandler<BeePlaceOrderVO> implements RocketMQListener<BeePlaceOrderVO> , RocketMQPushConsumerLifecycleListener {
    @Resource
    private IBeeOrderBaseService orderBaseService;
    @Resource
    private IBeeOrderLogService orderLogService;


    @Override
    public void onMessage(BeePlaceOrderVO message) {
        super.dispatchMessage(message);
    }

    /**
     * 消息处理
     *
     * @param message 待处理消息
     */
    @Override
    protected void handleMessage(BeePlaceOrderVO message) {
        log.info(ConsoleColors.GREEN + "收到MQ投递来的订单消息 orderNo : {} 。", message.getKey());
        BeeOrderBase orderBase = OrderUtil.placeOrderVoEntityConverter(message);
        orderBaseService.save(orderBase);
        orderLogService.saveLog(orderBase, String.format("客户{ memberId:%s }下单,订单信息：{ orderNo:%s }", orderBase.getMemberId(), orderBase.getOrderNo()));
    }

    /**
     * 超过重试次数消息，需要启用isRetry
     *
     * @param message 待处理消息
     */
    @Override
    protected void handleMaxRetriesExceeded(BeePlaceOrderVO message) {

    }

    /**
     * 是否异常时重复发送
     *
     * @return true: 消息重试，false：不重试
     */
    @Override
    protected boolean isRetry() {
        return true;
    }

    /**
     * 消费异常时是否抛出异常
     * 返回true，则由rocketmq机制自动重试
     * false：消费异常(如果没有开启重试则消息会被自动ack)
     */
    @Override
    protected boolean throwException() {
        return true;
    }

    /**
     * 设置命名空间，同一个Group唯一
     * @param consumer 消费者
     */
    @Override
    public void prepareStart(DefaultMQPushConsumer consumer) {
        consumer.setInstanceName(BeeConstant.BEE_MQ_TOPIC_ORDER);
    }
}

~~~





