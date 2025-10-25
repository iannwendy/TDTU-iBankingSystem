package com.ibanking.tuition.payment;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PaymentCleanupService {

    private final PaymentService paymentService;

    public PaymentCleanupService(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * Clean up expired OTP transactions every 30 seconds
     * This will mark PENDING_OTP transactions as FAILED when their OTP has expired
     */
    @Scheduled(fixedRate = 30000) // Run every 30 seconds
    public void cleanupExpiredOtpTransactions() {
        try {
            System.out.println("Running scheduled cleanup for expired OTP transactions...");
            paymentService.processExpiredOtpTransactions();
            System.out.println("Scheduled cleanup completed.");
        } catch (Exception e) {
            System.err.println("Error cleaning up expired OTP transactions: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
