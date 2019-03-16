package be.reactiveprogramming.paymentprocessor.gateway.gateway;

import be.reactiveprogramming.paymentprocessor.common.event.PaymentEvent;
import be.reactiveprogramming.paymentprocessor.common.event.PaymentResultEvent;
import be.reactiveprogramming.paymentprocessor.gateway.command.CreatePaymentCommand;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.IntegerDeserializer;
import org.apache.kafka.common.serialization.IntegerSerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverOptions;
import reactor.kafka.receiver.ReceiverRecord;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderOptions;
import reactor.kafka.sender.SenderRecord;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Component
public class PaymentGatewayImpl implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(PaymentGatewayImpl.class.getName());

    private KafkaReceiver kafkaReceiver;

    private KafkaSender kafkaProducer;

    private ObjectMapper objectMapper = new ObjectMapper();

    private Flux<PaymentResultEvent> sharedReceivedMessages;

    private String gatewayName = "1";

    /**
     * Here we do some basic setup of Project Reactor Kafka to be able to send and receive messages
     */
    public PaymentGatewayImpl() {
        final Map<String, Object> producerProps = new HashMap<>();
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, IntegerSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");

        final SenderOptions<Integer, String> producerOptions = SenderOptions.create(producerProps);

        kafkaProducer = KafkaSender.create(producerOptions);

        final Map<String, Object> consumerProps = new HashMap<>();
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, IntegerDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.CLIENT_ID_CONFIG, "payment-gateway-" + gatewayName);
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "payment-gateway");
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");

        ReceiverOptions<Object, Object> consumerOptions = ReceiverOptions.create(consumerProps)
                .subscription(Collections.singleton("payment-gateway-1-feedback"))
                .addAssignListener(partitions -> log.debug("onPartitionsAssigned {}", partitions))
                .addRevokeListener(partitions -> log.debug("onPartitionsRevoked {}", partitions));

        kafkaReceiver = KafkaReceiver.create(consumerOptions);

        /**
         We start reading messages from our response Topic
         */
        sharedReceivedMessages = ((Flux<ReceiverRecord>) kafkaReceiver.receive())
                .map(r -> {
                    r.receiverOffset().acknowledge();
                    PaymentResultEvent result = fromBinary((String) r.value(), PaymentResultEvent.class);
                    return result;
                })
                .share()
                .publish()
                .autoConnect();
    }


    @Override
    public Mono<PaymentResultEvent> doPayment(final CreatePaymentCommand createPayment) {
        final PaymentEvent payment = new PaymentEvent(createPayment.getId(), createPayment.getCreditCardNumber(), createPayment.getAmount(), gatewayName);

        String payload = toBinary(payment);

        /**
         * Here we create a new sub-stream on our response Topic, filtering for the message that will be the response
         * on the message we're about to send. We want to be sure we're listening for replies before we send the question,
         * so we're sure not to miss it!
         */
        return Flux.from(sharedReceivedMessages)
            .filter(received -> isFeedbackForMessage(payment, received))
            .doOnSubscribe(s -> {
                /**
                 * After subscribing to our reply queue, we send our unconfirmed transaction to the unconfirmed-transactions topic
                 */
                SenderRecord<Integer, String, Integer> message = SenderRecord.create(new ProducerRecord<>("unconfirmed-transactions", payload), 1);
                kafkaProducer.send(Mono.just(message)).subscribe();
            })
            .next();
    }

    private boolean isFeedbackForMessage(PaymentEvent payment, PaymentResultEvent received) {
        return payment.getId().equals(received.getPaymentId());
    }

    private String toBinary(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private <T> T fromBinary(String object, Class<T> resultType) {
        try {
            return objectMapper.readValue(object, resultType);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
