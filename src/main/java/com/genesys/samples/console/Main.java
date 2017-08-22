package com.genesys.samples.console;

public class Main {

    public static void main(String[] args) {
        try {
            Options options = Options.parseOptions(args);
            if (options == null) {
                return;
            }

            WorkspaceConsole console = new WorkspaceConsole(options);
            console.run();

        } catch (Exception e) {
            System.out.println("Error!:\n" + e.toString());
            e.printStackTrace();
        }
    }
}


