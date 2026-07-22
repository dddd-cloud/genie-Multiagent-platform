package com.jd.genie.platform.user.service;

public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException() { super("User not found"); }
}
