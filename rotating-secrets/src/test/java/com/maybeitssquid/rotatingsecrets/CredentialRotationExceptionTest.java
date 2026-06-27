package com.maybeitssquid.rotatingsecrets;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class CredentialRotationExceptionTest {

  @Test
  void preservesMessageAndCause() {
    Throwable cause = new IllegalStateException("boom");
    CredentialRotationException ex = new CredentialRotationException("rotation failed", cause);

    assertEquals("rotation failed", ex.getMessage());
    assertSame(cause, ex.getCause());
  }

  @Test
  void isRuntimeException() {
    assertInstanceOf(RuntimeException.class, new CredentialRotationException("msg", null));
  }
}
