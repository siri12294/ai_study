package com.aistudy.companion.exception;

/** Thrown when an authenticated user tries to access a resource they don't own - enforces Project-level isolation. */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) { super(message); }
}
