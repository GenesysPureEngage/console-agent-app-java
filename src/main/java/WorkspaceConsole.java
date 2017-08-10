import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.Scanner;
import java.util.ArrayList;
import java.util.List;

import com.genesys.samples.workspace.Call;
import com.genesys.samples.workspace.User;
import com.genesys.samples.workspace.WorkspaceApi;

public class WorkspaceConsole {
    private WorkspaceApi api;
    private User user;

    public WorkspaceConsole(WorkspaceApi api) {
        this.api = api;
    }

    private class Command {
        private String name;
        private List<String> args;

        public Command(String name, List<String> args) {
            this.name = name;
            this.args = args;
        }

        public String getName() {
            return this.name;
        }

        public List<String> getArgs() {
            return this.args;
        }
    }

    private void write(String msg) {
        System.out.println(msg);
    }

    private void prompt() {
        System.out.print("cmd> ");
    }
    private Command parseInput(String input) {
        String[] pieces = input.split("\\s+");
        if (pieces.length == 0) {
            return null;
        }

        String name = pieces[0].toLowerCase();
        List args = new ArrayList<String>();
        if (pieces.length > 1) {
            for (int i = 1; i < pieces.length; i++) {
                args.add(pieces[i]);
            }
        }

        return new Command(name, args);
    }

    private void printHelp() {
        write("Workspace Api console:");
        write("");
        write("Commands:");
        write("=-=-=-=-=-=-=-=-=-=-=-=-=-=-=");
        write("init");
        write("destroy");
        write("");
        write("activate-channels <agentId> <dn>");
        write("user");
        write("ready");
        write("not-ready");
        write("");
        write("make-call <destination>");
        write("answer <id>");
        write("hold <id>");
        write("retrieve <id>");
        write("release <id>");
        write("");
        write("exit");
        write("debug");
        write("help");
        write("");
        write("<id> parameter can be omitted for call operations if there is only one active call.");
        write("");
    }

    private String getCallId(List<String> args) {
        // If we get an id as an argument use that
        if (args.size() == 1) {
            return args.get(0);
        }

        // Otherwise if there is only one call use that id.
        Collection<Call> calls = this.api.getCalls();
        if (calls.size() != 1) {
            return null;
        } else {
            return calls.stream().findFirst().get().getId();
        }
    }

    public void run() {
        Scanner s = new Scanner(System.in);

        try {

            this.printHelp();
            for (;;) {
                this.prompt();
                Command cmd = this.parseInput(s.nextLine());
                if (cmd == null) {
                    continue;
                }

                List<String> args = cmd.getArgs();
                String id;
                switch(cmd.getName()) {

                    case "init":
                    case "initialize":
                        CompletableFuture<User> future = this.api.initialize();
                        this.user = future.get();
                        break;

                    case "debug":
                        this.api.setDebugEnabled(!this.api.debugEnabled());
                        this.write("Debug enabled:" + this.api.debugEnabled());
                        break;

                    case "activate-channels":
                        if (args.size() != 2) {
                            write("Usage: activate-channels <agentId> <dn>");
                        } else {
                            this.api.activateChannels(args.get(0), args.get(1));
                        }
                        break;

                    case "not-ready":
                        this.api.setAgentNotReady();
                        break;

                    case "ready":
                        this.api.setAgentReady();
                        break;

                    case "make-call":
                        if (args.size() != 1) {
                            write("Usage: make-call <destination>");
                        } else {
                            this.api.makeCall(args.get(0));
                        }
                        break;

                    case "release":
                        id = this.getCallId(args);
                        if (id == null) {
                            write("Usage: release <id>");
                        } else {
                            this.api.releaseCall(id);
                        }
                        break;

                    case "answer":
                        id = this.getCallId(args);
                        if (id == null) {
                            write("Usage: answer <id>");
                        } else {
                            this.api.answerCall(id);
                        }
                        break;

                    case "hold":
                        id = this.getCallId(args);
                        if (id == null) {
                            write("Usage: hold <id>");
                        } else {
                            this.api.holdCall(id);
                        }
                        break;

                    case "receive":
                        id = this.getCallId(args);
                        if (id == null) {
                            write("Usage: receive <id>");
                        } else {
                            this.api.retrieveCall(id);
                        }
                        break;

                    case "destroy":
                    case "logout":
                        this.api.destroy();
                        break;

                    case "user":
                        if (this.user != null) {
                            write("User details:\n" +
                                    "employeeId: " + this.user.getEmployeeId() + "\n" +
                                    "agentId: " + this.user.getAgentId() + "\n" +
                                    "defaultPlace: " + this.user.getDefaultPlace() + "\n");
                        }

                    case "exit":
                        if (this.api.isInitialized()) {
                            this.api.destroy();
                        }
                        return;

                    case "?":
                    case "help":
                        this.printHelp();
                        break;

                    default:
                        break;

                }
            }

        } catch (Exception e) {
            write("Exception!" + e.toString());
            e.printStackTrace();
        }
    }
}
