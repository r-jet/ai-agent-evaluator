package com.rajat.aie2e.livechat;

/**
 * Thrown when a Sprinklr Live Chat API call fails.
 * Distinct from OpenAI / evaluation errors so callers can handle them separately.
 */
public class LiveChatException extends Exception {

    public LiveChatException(String message) {
        super(message);
    }

    public LiveChatException(String message, Throwable cause) {
        super(message, cause);
    }
}