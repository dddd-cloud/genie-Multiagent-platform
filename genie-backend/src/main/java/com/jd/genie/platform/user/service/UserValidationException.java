package com.jd.genie.platform.user.service;

public class UserValidationException extends IllegalArgumentException {
    public UserValidationException(String message) {
        super(message);
    }
}
