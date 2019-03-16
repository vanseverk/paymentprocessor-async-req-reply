package be.reactiveprogramming.paymentprocessor.common.event;

public class PaymentResultEvent {

    private String paymentId;
    private Boolean successful;

    public PaymentResultEvent() {
    }

    public PaymentResultEvent(String paymentId, Boolean successful) {
        this.paymentId = paymentId;
        this.successful = successful;
    }

    public String getPaymentId() {
        return paymentId;
    }

    public Boolean getsuccessful() {
        return successful;
    }
}
