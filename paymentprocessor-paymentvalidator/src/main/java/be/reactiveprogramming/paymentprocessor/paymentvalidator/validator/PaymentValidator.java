package be.reactiveprogramming.paymentprocessor.paymentvalidator.validator;

import be.reactiveprogramming.paymentprocessor.common.event.PaymentEvent;
import be.reactiveprogramming.paymentprocessor.common.event.PaymentResultEvent;

public interface PaymentValidator {

    PaymentResultEvent calculateResult(PaymentEvent paymentEvent);

}
