package com.designpattern.cognitorbac.service;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates a service-method argument containing Cognito subject identifiers
 * before the audited MongoDB transaction starts.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidateCognitoUserSubs {
    int argumentIndex();
}
