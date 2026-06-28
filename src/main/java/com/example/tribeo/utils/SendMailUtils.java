package com.example.tribeo.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

import java.util.Properties;

@Slf4j
@Component
public class SendMailUtils {

    private   String HOST = "sandbox.smtp.mailtrap.io";
    private   int PORT = 587;

    @Value("${spring.mail.username}")
    private   String USERNAME;

    @Value("${spring.mail.password}")
    private   String PASSWORD;

    public  boolean sendemail(String email, String otp) {
        if (USERNAME == null || USERNAME.isBlank() || PASSWORD == null || PASSWORD.isBlank()) {
            System.out.println("Set SPRING_MAIL_USERNAME and SPRING_MAIL_PASSWORD environment variables before sending mail.");
            return false;
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
        message.setTo(email);
        message.setSubject("Tribeo Verification Code");
        message.setText(getOtpMessage(otp));

        try {
            mailSender.send(message);
            log.info("Email sent");
            return true;
        } catch (Exception e) {
            log.error("Caught exception : {}", String.valueOf(e));
            return false;
        }
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
