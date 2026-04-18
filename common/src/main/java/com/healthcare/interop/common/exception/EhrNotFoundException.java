package com.healthcare.interop.common.exception;

public class EhrNotFoundException extends RuntimeException {
    public EhrNotFoundException(String ehrCode) {
        super("EHR not found: " + ehrCode);
    }
}
