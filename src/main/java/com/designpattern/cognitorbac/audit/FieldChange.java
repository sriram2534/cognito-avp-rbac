package com.designpattern.cognitorbac.audit;

/**
 * Captures a single field-level before/after delta within an {@link AuditEntry}.
 *
 * <p>Stored as an embedded array element in MongoDB — no separate collection needed.
 * Both {@code oldValue} and {@code newValue} are strings so any scalar attribute
 * (email, name, phone, enabled flag, custom attribute) can be represented uniformly.</p>
 *
 * <p>Examples:
 * <pre>
 *   { field: "email",   oldValue: "alice@old.com", newValue: "alice@new.com" }
 *   { field: "enabled", oldValue: "true",          newValue: "false"         }
 *   { field: "custom:department", oldValue: "ops", newValue: "subscription"  }
 * </pre>
 * </p>
 */
public class FieldChange {

    private String field;
    private String oldValue;
    private String newValue;

    protected FieldChange() {
    }

    public FieldChange(String field, String oldValue, String newValue) {
        this.field = field;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }

    public String getField() { return field; }
    public String getOldValue() { return oldValue; }
    public String getNewValue() { return newValue; }
}
