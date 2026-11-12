package com.josxtm.autocontrol.community.storage;

import java.io.IOException;

/** Public error categories contain no imported values, paths or SQL. */
public final class CatalogException extends IOException {
    public enum Reason { FORMAT, DUPLICATE, NOTES }
    public final Reason reason;
    public CatalogException(Reason reason) { super(reason.name()); this.reason = reason; }
}
