package com.example.tribeo.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

import java.util.Properties;
import java.security.SecureRandom;

@Slf4j
@Component
public class SendMailUtils {

    private   String HOST = "sandbox.smtp.mailtrap.io";
    private   int PORT = 587;
    private  final SecureRandom secureRandom = new SecureRandom();

    @Value("${spring.mail.username}")
    private   String USERNAME;

    @Value("${spring.mail.password}")
    private   String PASSWORD;

    public  void sendemail(String email) {
        if (USERNAME == null || PASSWORD == null) {
            System.out.println("Set SPRING_MAIL_USERNAME and SPRING_MAIL_PASSWORD environment variables before sending mail.");
            return;
        }

        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(HOST);
        mailSender.setPort(PORT);
        mailSender.setUsername(USERNAME);
        mailSender.setPassword(PASSWORD);

        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");

        SimpleMailMessage message = new SimpleMailMessage();
        log.info("email from session: {}", email);
        message.setFrom("hello@demomailtrap.co");
        message.setTo("adswithprajyot@gmail.com");
        message.setSubject("Tribeo Verification Code");
        String otp = generateOtp();
        message.setText(getOtpMessage(otp));

        try {
            mailSender.send(message);
            System.out.println("Email sent");
        } catch (Exception e) {
            System.out.println("Caught exception : " + e);
        }
    }

    private  String generateOtp() {
        int otp = 100000 + secureRandom.nextInt(900000);
        return String.valueOf(otp);
    }

    private  String getOtpMessage(String otp) {
        return "Dear User,\n\n"
                + "Your One-Time Password (OTP) for verification is: " + otp + "\n\n"
                + "This OTP is valid for 10 minutes. Please do not share this code with anyone.\n\n"
                + "If you did not request this OTP, please ignore this email.\n\n"
                + "Regards,\n"
                + "Tribeo Team";
    }
}
