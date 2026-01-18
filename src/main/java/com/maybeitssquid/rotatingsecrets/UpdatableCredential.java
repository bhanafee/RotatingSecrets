package com.maybeitssquid.rotatingsecrets;

public interface UpdatableCredential<T> {
    void setCredential(String username, T credential);
}

