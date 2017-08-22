package com.genesys.samples.console;

public class WorkspaceConsoleException extends Exception {
    public WorkspaceConsoleException(String message) {
        super(message);
    }

    public WorkspaceConsoleException(String message, Exception cause) {
        super(message, cause);
    }
}
