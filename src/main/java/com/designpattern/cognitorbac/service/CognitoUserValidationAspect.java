package com.designpattern.cognitorbac.service;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Collection;

/** Runs Cognito validation outside the audited MongoDB transaction. */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CognitoUserValidationAspect {
    private final CognitoUserDirectory directory;

    public CognitoUserValidationAspect(CognitoUserDirectory directory) {
        this.directory = directory;
    }

    @Around("@annotation(validation)")
    public Object validate(ProceedingJoinPoint joinPoint, ValidateCognitoUserSubs validation) throws Throwable {
        Object[] args = joinPoint.getArgs();
        int index = validation.argumentIndex();
        if (index < 0 || index >= args.length || !(args[index] instanceof Collection<?> values)) {
            throw new IllegalStateException("@ValidateCognitoUserSubs must reference a Collection argument");
        }
        args[index] = directory.requireExistingUserSubs(values.stream().map(value -> (String) value).toList());
        return joinPoint.proceed(args);
    }
}
