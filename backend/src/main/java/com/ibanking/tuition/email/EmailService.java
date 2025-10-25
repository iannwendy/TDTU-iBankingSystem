package com.ibanking.tuition.email;

import com.ibanking.tuition.user.Customer;
import com.ibanking.tuition.payment.PaymentTransaction;
import com.ibanking.tuition.tuition.StudentTuition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;

@Service
public class EmailService {

    private final JavaMailSender gmailSender;
    private final JavaMailSender mailPitSender;
    private final int otpTtlSeconds;

    public EmailService(@Qualifier("gmailSender") JavaMailSender gmailSender, 
                       @Qualifier("mailPitSender") JavaMailSender mailPitSender,
                       @Value("${app.otp.ttlSeconds}") int otpTtlSeconds) {
        this.gmailSender = gmailSender;
        this.mailPitSender = mailPitSender;
        this.otpTtlSeconds = otpTtlSeconds;
    }

    public void sendOtpEmail(Customer payer, String otp, PaymentTransaction txn, StudentTuition tuition) {
        // Send to MailPit (for testing)
        sendOtpEmailToMailPit(payer, otp, txn, tuition);
    }
    
    public void sendPaymentConfirmationEmail(Customer payer, PaymentTransaction txn, StudentTuition tuition) {
        // Send payment confirmation to MailPit
        sendPaymentConfirmationToMailPit(payer, txn, tuition);
    }
    
    private void sendOtpEmailToGmail(Customer payer, String otp, PaymentTransaction txn, StudentTuition tuition) {
        try {
            MimeMessage mime = gmailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, false, "UTF-8");
            helper.setFrom("iannwendii@gmail.com");
            helper.setTo("iannwendii@gmail.com"); // Luôn gửi đến iannwendii@gmail.com
            helper.setSubject("iBanking Tuition Payment – OTP Verification");
            
            String amountStr = String.format("%,.0f VND", tuition.getAmount().doubleValue());
            String html = createOtpEmailHtml(payer, otp, txn, tuition, amountStr, false);
            
            helper.setText(html, true);
            gmailSender.send(mime);
            System.out.println("[GMAIL] OTP email sent successfully to iannwendii@gmail.com");
            
        } catch (Exception e) {
            System.err.println("[GMAIL] Failed to send email to Gmail: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void sendOtpEmailToMailPit(Customer payer, String otp, PaymentTransaction txn, StudentTuition tuition) {
        try {
            MimeMessage mime = mailPitSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, false, "UTF-8");
            helper.setFrom("no-reply@ibanking.local");
            helper.setTo(payer.getEmail());
            helper.setSubject("iBanking Tuition Payment – OTP Verification (Test)");
            
            String amountStr = String.format("%,.0f VND", tuition.getAmount().doubleValue());
            String html = createOtpEmailHtml(payer, otp, txn, tuition, amountStr, true);
            
            helper.setText(html, true);
            mailPitSender.send(mime);
            System.out.println("[MAILPIT] OTP email sent successfully to MailPit");
            
        } catch (Exception e) {
            System.err.println("[MAILPIT] Failed to send email to MailPit: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void sendPaymentConfirmationToMailPit(Customer payer, PaymentTransaction txn, StudentTuition tuition) {
        try {
            MimeMessage mime = mailPitSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, false, "UTF-8");
            helper.setFrom("no-reply@ibanking.local");
            helper.setTo(payer.getEmail());
            helper.setSubject("iBanking Tuition Payment – Payment Confirmation");
            
            String amountStr = String.format("%,.0f VND", tuition.getAmount().doubleValue());
            String html = createPaymentConfirmationHtml(payer, txn, tuition, amountStr);
            
            helper.setText(html, true);
            mailPitSender.send(mime);
            System.out.println("[MAILPIT] Payment confirmation email sent successfully to MailPit");
            
        } catch (Exception e) {
            System.err.println("[MAILPIT] Failed to send payment confirmation email to MailPit: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private String createOtpEmailHtml(Customer payer, String otp, PaymentTransaction txn, 
                                    StudentTuition tuition, String amountStr, boolean isTest) {
        String title = isTest ? "iBanking Tuition Payment (Test)" : "iBanking Tuition Payment";
        
        return """
            <html>
            <body style="background-color: #0b1020; color: #e5e7eb; font-family: Arial, sans-serif; padding: 20px;">
                <div style="max-width: 600px; margin: 0 auto; background-color: #11162a; padding: 30px; border-radius: 10px;">
                    <h2 style="color: #3b82f6; margin-bottom: 20px;">%s</h2>
                    <p>Hi <strong>%s</strong>,</p>
                    <p>Use the One-Time Password (OTP) below to confirm your tuition payment.</p>
                    <div style="background-color: #0f172a; padding: 20px; text-align: center; border-radius: 8px; margin: 20px 0;">
                        <div style="font-size: 12px; color: #94a3b8; margin-bottom: 10px;">Your OTP</div>
                        <div style="font-size: 32px; color: #fff; font-weight: bold; letter-spacing: 5px;">%s</div>
                    </div>
                    <div style="background-color: #0f172a; padding: 15px; border-radius: 8px; margin: 20px 0;">
                        <p><strong>Transaction ID:</strong> %d</p>
                        <p><strong>Student ID:</strong> %s</p>
                        <p><strong>Semester:</strong> %s</p>
                        <p><strong>Amount:</strong> %s</p>
                    </div>
                    <p style="font-size: 12px; color: #94a3b8;">Do not share this OTP with anyone. It will expire in %d seconds.</p>
                    <p style="font-size: 12px; color: #94a3b8;">If you didn't request this, please ignore this email.</p>
                    <hr style="border: none; border-top: 1px solid #374151; margin: 20px 0;">
                    <p style="text-align: center; font-size: 12px; color: #8b93a7;">© %d iBanking. All rights reserved.</p>
                </div>
            </body>
            </html>
            """.formatted(
                title,
                payer.getFullName(),
                otp,
                txn.getId(),
                tuition.getStudentId(),
                tuition.getSemester(),
                amountStr,
                otpTtlSeconds,
                java.time.LocalDate.now().getYear()
        );
    }
    
    private String createPaymentConfirmationHtml(Customer payer, PaymentTransaction txn, 
                                               StudentTuition tuition, String amountStr) {
        return """
            <html>
            <body style="background-color: #0b1020; color: #e5e7eb; font-family: Arial, sans-serif; padding: 20px;">
                <div style="max-width: 600px; margin: 0 auto; background-color: #11162a; padding: 30px; border-radius: 10px;">
                    <h2 style="color: #10b981; margin-bottom: 20px;">Payment Confirmation</h2>
                    <p>Hi <strong>%s</strong>,</p>
                    <p>Your tuition payment has been successfully processed!</p>
                    
                    <div style="background-color: #0f172a; padding: 20px; text-align: center; border-radius: 8px; margin: 20px 0;">
                        <div style="font-size: 12px; color: #94a3b8; margin-bottom: 10px;">Payment Amount</div>
                        <div style="font-size: 28px; color: #10b981; font-weight: bold;">%s</div>
                    </div>
                    
                    <div style="background-color: #0f172a; padding: 15px; border-radius: 8px; margin: 20px 0;">
                        <p><strong>Transaction ID:</strong> %d</p>
                        <p><strong>Student ID:</strong> %s</p>
                        <p><strong>Semester:</strong> %s</p>
                        <p><strong>Payment Date:</strong> %s</p>
                    </div>
                    
                    <div style="background-color: #1f2937; padding: 15px; border-radius: 8px; margin: 20px 0;">
                        <p style="color: #10b981; font-weight: bold;">✓ Payment Status: Completed</p>
                    </div>
                    
                    <p style="font-size: 12px; color: #94a3b8;">Thank you for using iBanking for your tuition payment.</p>
                    <p style="font-size: 12px; color: #94a3b8;">If you have any questions, please contact our support team.</p>
                    
                    <hr style="border: none; border-top: 1px solid #374151; margin: 20px 0;">
                    <p style="text-align: center; font-size: 12px; color: #8b93a7;">© %d iBanking. All rights reserved.</p>
                </div>
            </body>
            </html>
            """.formatted(
                payer.getFullName(),
                amountStr,
                txn.getId(),
                tuition.getStudentId(),
                tuition.getSemester(),
                java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")),
                java.time.LocalDate.now().getYear()
        );
    }
}
