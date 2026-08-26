package com.designpattern.cognitorbac.audit;

/**
 * Captures a single field-level before/after delta within an {@link AuditEntry}.
 *
 * <p>Stored as an embedded array element in MongoDB — no separate collection needed.
 * Both {@code oldValue} and {@code newValue} are {@code Object} so any scalar type
 * (String, Integer, Boolean, custom attribute) can be represented without lossy string conversion.
 * MongoDB serializes them as their native BSON type — strings as strings, numbers as numbers, booleans as booleans.</p>
 *
 * <p>Examples:
 * <pre>
 *   { field: "email",      oldValue: "alice@old.com", newValue: "alice@new.com" }
 *   { field: "enabled",    oldValue: true,            newValue: false           }
 *   { field: "precedence", oldValue: null,            newValue: 5              }
 *   { field: "custom:department", oldValue: "ops",    newValue: "subscription" }
 * </pre>
 * </p>
 */
public class FieldChange {

    private String field;
    private Object oldValue;
    private Object newValue;

    protected FieldChange() {
    }

    public FieldChange(String field, Object oldValue, Object newValue) {
        this.field = field;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }

    public String getField() { return field; }
    public Object getOldValue() { return oldValue; }
    public Object getNewValue() { return newValue; }
}
