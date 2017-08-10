package com.genesys.samples.workspace;

public class WorkspaceApiException extends Exception {
    public WorkspaceApiException(String msg) {
        super(msg);
    }

    public WorkspaceApiException(String msg, Exception cause) {
        super(msg, cause);
    }
}
