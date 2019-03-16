package be.reactiveprogramming.paymentprocessor.gateway.gateway;

import be.reactiveprogramming.paymentprocessor.common.event.PaymentResultEvent;
import be.reactiveprogramming.paymentprocessor.gateway.command.CreatePaymentCommand;
import reactor.core.publisher.Mono;

public interface PaymentGateway {

    Mono<PaymentResultEvent> doPayment(CreatePaymentCommand createPayment);

}
