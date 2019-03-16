package be.reactiveprogramming.paymentprocessor.gateway.controller;

import be.reactiveprogramming.paymentprocessor.common.event.PaymentResultEvent;
import be.reactiveprogramming.paymentprocessor.gateway.command.CreatePaymentCommand;
import be.reactiveprogramming.paymentprocessor.gateway.gateway.PaymentGateway;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public class PaymentController {

    private final PaymentGateway paymentGateway;

    public PaymentController(PaymentGateway paymentGateway) {
        this.paymentGateway = paymentGateway;
    }

    /**
     * The Mono returned by the call will be sent to Spring Webflux, which relies on an multi-reactor event-loop and NIO
     * to handle requests in a non-blocking manner, enabling far more concurrent requests. The result will be sent over
     * HTTP through a mechanism called Server Sent Events
     **/
    @PostMapping(value = "/payment")
    public Mono<PaymentResultEvent> doPayment(@RequestBody CreatePaymentCommand payment) {
        /**
         When calling the doPayment method, we send our payment information, getting a Mono<PaymentResultEvent> in return.
         This event will resolve when we get a reply from the validator regarding our payment. This means that here we set
         up the Reactive Stream from the PaymentGateway to the Validator and back.
         **/
        return paymentGateway.doPayment(payment);
    }
}
