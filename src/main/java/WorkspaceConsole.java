import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.Scanner;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import com.genesys.workspace.WorkspaceApi;
import com.genesys.workspace.common.WorkspaceApiException;
import com.genesys.workspace.models.Call;
import com.genesys.workspace.models.Dn;
import com.genesys.workspace.models.User;

public class WorkspaceConsole {
    private Options options;
    private WorkspaceApi api;
    private User user;

    public WorkspaceConsole(Options options) {
        this.options = options;
        this.api = new WorkspaceApi(
                options.getApiKey(),
                options.getClientId(),
                options.getClientSecret(),
                options.getBaseUrl(),
                options.getUsername(),
                options.getPassword(),
                options.isDebugEnabled());

        this.api.addCallEventListener(msg -> {
            if (msg.getPreviousConnId() != null) {
                this.write("Call [" + msg.getPreviousConnId() + "] id changed to ["
                        + msg.getCall().getId());
            } else {
                this.write("CallStateChanged: " + this.getCallSummary(msg.getCall()));
            }
        });
        this.api.addDnEventListener(msg -> {
            this.write("DnStateChanged: " + this.getDnSummary(msg.getDn()));
        });
        this.api.addErrorEventListener(msg -> {
            this.write("EventError: " + msg.getMessage() + " - code [" + msg.getCode() + "]");
        });
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

    private class CompleteParams {
        private String connId;
        private String parentConnId;

        public CompleteParams(String connId, String parentConnId) {
            this.connId = connId;
            this.parentConnId = parentConnId;
        }

        public String getConnId() {
            return this.connId;
        }

        public String getParentConnId() {
            return this.parentConnId;
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
        this.write("Workspace Api Console commands:");
        this.write("initialize|init|i");
        this.write("destroy|logout|l");
        this.write("activate-channels|ac <agentId> <dn>");
        this.write("user|u");
        this.write("dn");
        this.write("calls");
        this.write("ready|r");
        this.write("not-ready|nr");
        this.write("make-call|mc <destination>");
        this.write("answer|a <id>");
        this.write("hold|h <id>");
        this.write("retrieve|ret <id>");
        this.write("release|rel <id>");
        this.write("initiate-confernence|ic <id> <destination>");
        this.write("complete-conference|cc <id> <parentConnId>");
        this.write("initiate-transfer|it <id> <destination>");
        this.write("complete-transfer|ct <id> <parentConnId>");
        this.write("target-search|ts <searchTerm> <limit>");
        this.write("alternate|alt <id> <heldConnId>");
        this.write("clear|cls");
        this.write("config|conf");
        this.write("exit|x");
        this.write("debug|d");
        this.write("help|?");
        this.write("");
        this.write("Note: <id> parameter can be omitted for call operations if there is only one active call.");
        this.write("");
    }

    private String getCallSummary(Call call) {
        String summary = call.getId() + " state [" + call.getState()
                + "] type [" + call.getCallType() + "]";
        if (call.getParentConnId() != null) {
            summary += " parent [" + call.getParentConnId() + "]";
        }

        String[] participants = call.getParticipants();
        if (participants != null && participants.length > 0) {
            String participantSummary = "";
            for(int i = 0; i < participants.length; i++) {
                if (participantSummary != "") {
                    participantSummary += ", ";
                }

                participantSummary += participants[i];
            }

            summary += " participants [" + participantSummary + "]";
        }

        return summary;
    }

    private String getDnSummary(Dn dn) {
        return dn.getNumber() + " state [" + dn.getAgentState()
                + "] workMode [" + dn.getWorkMode() + "]";
    }

    private String getCallId(List<String> args) {
        // If we get an id as an argument use that
        if (args != null && args.size() == 1) {
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

    private CompleteParams getCallIdAndParent(List<String> args) {
        if (args != null && args.size() == 2) {
          return new CompleteParams(args.get(0), args.get(1));
        }

        // If ids were not provided, see if there is only one
        // possibility.
        CompleteParams params = null;
        if (this.api.getCalls().size() == 2) {
            Call call = this.api.getCalls().stream()
                  .filter(c -> c.getParentConnId() != null)
                  .findFirst().get();

            if (call != null) {
                params = new CompleteParams(call.getId(), call.getParentConnId());
            }
        }

        return params;
    }

    private void init() throws WorkspaceApiException, ExecutionException, InterruptedException {
        this.write("Initializing API...");
        CompletableFuture<User> future = this.api.initialize();
        this.user = future.get();
        this.write("Initialization complete.");
    }

    private void activateChannels(List<String> args) throws WorkspaceApiException {
        boolean hasArgs = (args != null && args.size() == 2);
        if (!hasArgs && (this.options.getDefaultAgentId() == null || this.options.getDefaultDn() == null)) {
            this.write("Usage: activate-channels <agentId> <dn>");
            return;
        }

        String agentId = hasArgs ? args.get(0) : this.options.getDefaultAgentId();
        String dn = hasArgs ? args.get(1) : this.options.getDefaultDn();

        this.write("Sending activate-channels with agentId [" + agentId + "] and dn " + dn + "]...");
        this.api.activateChannels(agentId, dn);
    }

    private void doAutoLogin() {
        try {
            if (this.options.isAutoLogin()) {
                this.write("autoLogin is true...");
                this.init();
                this.activateChannels(null);
            }
        } catch (WorkspaceApiException|ExecutionException|InterruptedException e) {
            this.write("autoLogin failed!" + e);
        }
    }

    private void makeCall(List<String> args) throws WorkspaceApiException {
        boolean hasArgs = (args.size() > 0);
        if (!hasArgs && this.options.getDefaultDestination() == null) {
            this.write("Usage: make-call <destination>");
            return;
        }

        String destination = hasArgs ? args.get(0) : this.options.getDefaultDestination();
        this.write("Sending make-call with destination [" + destination + "]...");
        this.api.makeCall(destination);
    }

    public void run() {
        Scanner s = new Scanner(System.in);

        try {

            this.write("Workspace Api Console");
            this.write("");
            this.doAutoLogin();

            for (;;) {
                this.prompt();
                Command cmd = this.parseInput(s.nextLine());
                if (cmd == null) {
                    continue;
                }

                List<String> args = cmd.getArgs();
                String id;
                String destination;
                CompleteParams params;

                switch(cmd.getName()) {

                    case "acw":
                        this.api.setAgentNotReady("AfterCallWork", null);
                        break;

                    case "initialize":
                    case "init":
                    case "i":
                        this.init();
                        break;

                    case "debug":
                    case "d":
                        this.api.setDebugEnabled(!this.api.debugEnabled());
                        this.write("Debug enabled:" + this.api.debugEnabled());
                        break;

                    case "dn":
                        this.write("Dn: " + this.getDnSummary(this.api.getDn()));
                        break;

                    case "calls":
                        this.write("Calls:");
                        Collection<Call> calls = this.api.getCalls();
                        if (calls.size() > 0) {
                            calls.forEach(c -> this.write(this.getCallSummary(c)));
                            this.write("");
                        } else {
                            this.write("<none>");
                        }
                        break;

                    case "activate-channels":
                    case "ac":
                        this.activateChannels(args);
                        break;

                    case "iac":
                        this.init();
                        this.activateChannels(args);
                        break;

                    case "not-ready":
                    case "nr":
                        this.write("Sending not-ready...");
                        this.api.setAgentNotReady();
                        break;

                    case "ready":
                    case "r":
                        this.write("Sending ready...");
                        this.api.setAgentReady();
                        break;

                    case "make-call":
                    case "mc":
                        this.makeCall(args);
                        break;

                    case "release":
                    case "rel":
                        id = this.getCallId(args);
                        if (id == null) {
                            this.write("Usage: release <id>");
                        } else {
                            this.write("Sending release for call [" + id + "]...");
                            this.api.releaseCall(id);
                        }
                        break;

                    case "answer":
                    case "a":
                        id = this.getCallId(args);
                        if (id == null) {
                            this.write("Usage: answer <id>");
                        } else {
                            this.write("Sending answer for call [" + id + "]...");
                            this.api.answerCall(id);
                        }
                        break;

                    case "hold":
                    case "h":
                        id = this.getCallId(args);
                        if (id == null) {
                            this.write("Usage: hold <id>");
                        } else {
                            this.write("Sending hold for call [" + id + "]...");
                            this.api.holdCall(id);
                        }
                        break;

                    case "retrieve":
                    case "ret":
                        id = this.getCallId(args);
                        if (id == null) {
                            this.write("Usage: receive <id>");
                        } else {
                            this.write("Sending retrieve for call [" + id + "]...");
                            this.api.retrieveCall(id);
                        }
                        break;

                    case "initiate-conference":
                    case "ic":
                        if (args.size() < 1) {
                            this.write("Usage: initiate-conference <id> <destination>");
                        } else {
                            // If there is only one argument take it as the destination.
                            destination = args.get(args.size() - 1);
                            id = this.getCallId(args.size() == 1 ? null : args);
                            if (id == null) {
                                this.write("Usage: initiate-conference <id> <destination>");
                            } else {
                                this.write("Sending initiate-conference for call [" + id
                                        + " and destination [" + destination + "]...");
                                this.api.initiateConference(id, destination);
                            }
                        }
                        break;

                    case "complete-conference":
                    case "cc":
                        params = this.getCallIdAndParent(args);
                        if (params == null) {
                            this.write("Usage: complete-conference <id> <parentConnId>");
                        } else {
                            this.write("Sending complete-conference for call ["
                                    + params.getConnId() + "] and parentConnId ["
                                    + params.getParentConnId() + "]...");
                            this.api.completeConference(params.getConnId(), params.getParentConnId());
                        }
                        break;

                    case "initiate-transfer":
                    case "it":
                        if (args.size() < 1) {
                            this.write("Usage: initiate-transfer <id> <destination>");
                        } else {
                            // If there is only one argument take it as the destination.
                            destination = args.get(args.size() - 1);
                            id = this.getCallId(args.size() == 1 ? null : args);
                            if (id == null) {
                                this.write("Usage: initiate-transfer <id> <destination>");
                            } else {
                                this.write("Sending initiate-transfer for call [" + id
                                        + " and destination [" + destination + "]...");
                                this.api.initiateTransfer(id, destination);
                            }
                        }
                        break;

                    case "complete-transfer":
                    case "ct":
                        params = this.getCallIdAndParent(args);
                        if (params == null) {
                            this.write("Usage: complete-transfer <id> <parentConnId>");
                        } else {
                            this.write("Sending complete-transfer for call ["
                                    + params.getConnId() + "] and parentConnId ["
                                    + params.getParentConnId() + "]...");
                            this.api.completeTransfer(params.getConnId(), params.getParentConnId());
                        }
                        break;

                    case "alternate":
                    case "alt":
                        if (args.size() < 2) {
                            this.write("Usage: alternate <id> <heldConnId>");
                        } else {
                            this.write("Sending alternate for call ["
                                    + args.get(0) + "] and heldConnId ["
                                    + args.get(1) + "]...");
                            this.api.alternateCalls(args.get(0), args.get(1));
                        }
                        break;

                    case "target-search":
                    case "ts":
                        break;

                    case "destroy":
                    case "logout":
                        this.write("Cleaning up and logging out...");
                        this.api.destroy();
                        break;

                    case "user":
                    case "u":
                        if (this.user != null) {
                            this.write("User details:\n" +
                                    "employeeId: " + this.user.getEmployeeId() + "\n" +
                                    "agentId: " + this.user.getAgentId() + "\n" +
                                    "defaultPlace: " + this.user.getDefaultPlace() + "\n");
                        }

                    case "config":
                    case "conf":
                        this.write("Configuration:\n"
                            + "apiKey: " + this.options.getApiKey() + "\n"
                            + "baseUrl: " + this.options.getBaseUrl() + "\n"
                            + "clientId: " + this.options.getClientId() + "\n"
                            + "clientSecret: " + this.options.getClientSecret() + "\n"
                            + "username: " + this.options.getUsername() + "\n"
                            + "password: " + this.options.getPassword() + "\n"
                            + "debugEnabled: " + this.options.isDebugEnabled() + "\n"
                            + "autoLogin: " + this.options.isAutoLogin() + "\n"
                            + "defaultAgentId: " + this.options.getDefaultAgentId() + "\n"
                            + "defaultDn: " + this.options.getDefaultDn() + "\n"
                            + "defaultDestination: " + this.options.getDefaultDestination() + "\n"
                            );
                        break;

                    case "clear":
                    case "cls":
                        // Low tech...
                        for (int i = 0; i < 80; ++i) this.write("");
                        break;

                    case "exit":
                    case "x":
                        this.write("Cleaning up and exiting...");
                        this.api.destroy();
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
