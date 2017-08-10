package com.genesys.samples.workspace;

public class Call {
    private String id;
    private String state;

    public Call() {}

    public String getId() {
        return this.id;
    }

    public String getState() {
        return this.state;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setState(String state) {
        this.state = state;
    }
}
