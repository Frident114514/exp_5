package edu.szu.moments.auth;

/** Published synchronously inside the user-locking transaction; listeners must join it and propagate failures. */
public record UserDisabled(long userId) {}
