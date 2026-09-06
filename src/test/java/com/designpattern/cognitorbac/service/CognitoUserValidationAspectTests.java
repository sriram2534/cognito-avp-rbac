package com.designpattern.cognitorbac.service;

import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CognitoUserValidationAspectTests {
    @Mock private CognitoUserDirectory directory;
    @Mock private ProceedingJoinPoint joinPoint;
    @Mock private ValidateCognitoUserSubs annotation;

    @Test
    void validatesAndReplacesTheArgumentBeforeProceeding() throws Throwable {
        List<String> requested = List.of(" raw-sub ");
        List<String> normalized = List.of("normalized-sub");
        Object[] args = {"role-id", requested};
        when(annotation.argumentIndex()).thenReturn(1);
        when(joinPoint.getArgs()).thenReturn(args);
        when(directory.requireExistingUserSubs(requested)).thenReturn(normalized);
        when(joinPoint.proceed(args)).thenReturn("result");

        Object result = new CognitoUserValidationAspect(directory).validate(joinPoint, annotation);

        assertEquals("result", result);
        ArgumentCaptor<Object[]> proceededArgs = ArgumentCaptor.forClass(Object[].class);
        verify(joinPoint).proceed(proceededArgs.capture());
        assertEquals(normalized, proceededArgs.getValue()[1]);
    }
}
