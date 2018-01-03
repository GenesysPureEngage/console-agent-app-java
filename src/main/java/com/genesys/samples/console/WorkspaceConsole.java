package com.genesys.samples.console;

import java.util.Base64;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.Scanner;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import com.genesys.internal.authentication.api.AuthenticationApi;
import com.genesys.internal.authentication.model.DefaultOAuth2AccessToken;
import com.genesys.internal.common.ApiClient;
import com.genesys.internal.common.ApiException;
import com.genesys.workspace.models.*;
import com.genesys.workspace.models.cfg.*;
import com.genesys.workspace.models.targets.Target;
import com.genesys.workspace.models.targets.SearchResult;
import com.squareup.okhttp.OkHttpClient;
import com.genesys.workspace.WorkspaceApi;
import com.genesys.workspace.common.WorkspaceApiException;

public class WorkspaceConsole {
    private Options options;
    private WorkspaceApi api;
    private User user;

    public WorkspaceConsole(Options options) {
        this.options = options;
        this.api = new WorkspaceApi(
                options.getApiKey(),
                options.getBaseUrl());

        this.api.voice().addCallEventListener(msg -> {
            if (msg.getPreviousConnId() != null) {
                this.write("Call [" + msg.getPreviousConnId() + "] id changed to ["
                        + msg.getCall().getId());
            } else {
                this.write("CallStateChanged [" + msg.getNotificationType() + "]: " + this.getCallSummary(msg.getCall()));
            }
        });
        this.api.voice().addDnEventListener(msg -> {
            this.write("DnStateChanged: " + this.getDnSummary(msg.getDn()));
        });
        this.api.voice().addErrorEventListener(msg -> {
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
        String moveBack = "\u001b[50D";
        System.out.print(moveBack);
        System.out.println(msg);
    }

    private void prompt() {
        String moveBack = "\u001b[50D";
        System.out.print(moveBack);
        System.out.print("cmd> ");
    }

    private void prompt(String msg) {
        System.out.print(msg);
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
        this.write("configuration|c <type>");
        this.write("dn");
        this.write("calls");
        this.write("ready|r");
        this.write("not-ready|nr");
        this.write("dnd-on");
        this.write("dnd-off");
        this.write("voice-login");
        this.write("voice-logout");
        this.write("set-forward <destination>");
        this.write("cancel-forward");
        this.write("make-call|mc <destination>");
        this.write("answer|a <id>");
        this.write("hold|h <id>");
        this.write("retrieve|ret <id>");
        this.write("release|rel <id>");
        this.write("clear-call <id>");
        this.write("redirect <id> <destination>");
        this.write("initiate-conference|ic <id> <destination>");
        this.write("complete-conference|cc <id> <parentConnId>");
        this.write("initiate-transfer|it <id> <destination>");
        this.write("complete-transfer|ct <id> <parentConnId>");
        this.write("delete-from-conference|dfc <id> <dnToDrop>");
        this.write("send-dtmf|dtmf <id> <digits>");
        this.write("alternate|alt <id> <heldConnId>");
        this.write("merge <id> <otherConnId>");
        this.write("reconnect <id> <heldConnId>");
        this.write("single-step-transfer <id> <destination>");
        this.write("single-step-conference <id> <destination>");
        this.write("attach-user-data|aud <id> <key> <value>");
        this.write("update-user-data|uud <id> <key> <value>");
        this.write("delete-user-data-pair|dp <id> <key>");
        this.write("start-recording <id>");
        this.write("pause-recording <id>");
        this.write("resume-recording <id>");
        this.write("stop-recording <id>");
        this.write("send-user-event <key> <value> <callUuid>");
        this.write("target-search|ts <searchTerm> <limit>");
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
            summary += " userData " + call.getUserData();
        }

        return summary;
    }

    private String getDnSummary(Dn dn) {
        String msg = dn.getNumber() + " state [" + dn.getAgentState() + "]";

        if (dn.getWorkMode() != AgentWorkMode.UNKNOWN) {
            msg += " workMode [" + dn.getWorkMode() + "]";
        }

        if (dn.getForwardTo() != null) {
            msg += " forwardTo [" + dn.getForwardTo() + "]";
        }

        if (dn.isDND()) {
            msg += " dnd [on]";
        }

        return msg;
    }

    private String getCallId(List<String> args) {
        // If we get an id as an argument use that
        if (args != null && args.size() == 1) {
            return args.get(0);
        }

        // Otherwise if there is only one call use that id.
        Collection<Call> calls = this.api.voice().getCalls();
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
        if (this.api.voice().getCalls().size() == 2) {
            Call call = this.api.voice().getCalls().stream()
                  .filter(c -> c.getParentConnId() != null)
                  .findFirst().get();

            if (call != null) {
                params = new CompleteParams(call.getId(), call.getParentConnId());
            }
        }

        return params;
    }

    private String getAuthToken() throws WorkspaceApiException {
        this.write("Getting auth token...");
        String baseUrl = this.options.getAuthBaseUrl() != null ?
                this.options.getAuthBaseUrl() : this.options.getBaseUrl();
        ApiClient authClient = new ApiClient();
        authClient.setBasePath(baseUrl + "/auth/v3");
        authClient.addDefaultHeader("x-api-key", this.options.getApiKey());
        OkHttpClient httpClient = authClient.getHttpClient();
        httpClient.setFollowRedirects(false);
        httpClient.setFollowSslRedirects(false);

        byte[] bytes = (this.options.getClientId() + ":" + this.options.getClientSecret()).getBytes();
        byte[] encoded = Base64.getEncoder().encode(bytes);
        String authorization = "Basic " + new String(encoded);

        AuthenticationApi authApi = new AuthenticationApi(authClient);

        try {
            DefaultOAuth2AccessToken response = authApi.retrieveToken(
                    "password", authorization, "application/json", "*",
                    this.options.getClientId(), null, this.options.getUsername(), this.options.getPassword());

            return response.getAccessToken();
        } catch (ApiException e) {
            throw new WorkspaceApiException("Failed to get auth token", e);
        }
    }

    private void init() throws WorkspaceConsoleException, WorkspaceApiException, ExecutionException, InterruptedException {

        String token = this.getAuthToken();
        if (token == null) {
            throw new WorkspaceConsoleException("Failed to get auth token.");
        }
        this.write("Initializing API...");
        this.user = this.api.initialize(token);
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
        this.api.activateChannels(agentId, dn, null, null, null);
    }

    private void doAutoLogin() {
        try {
            if (this.options.isAutoLogin()) {
                this.write("autoLogin is true...");
                this.init();
                this.activateChannels(null);
            }
        } catch (WorkspaceConsoleException|WorkspaceApiException|ExecutionException|InterruptedException e) {
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
        this.api.voice().makeCall(destination);
    }

    private String getBusinessAttributeSummary() {
        String summary = "Business Attributes:\n";
        Collection<BusinessAttribute> businessAttributes = this.api.getBusinessAttributes();
        if (businessAttributes != null && !businessAttributes.isEmpty()) {
            for (BusinessAttribute businessAttribute : businessAttributes) {
                summary += businessAttribute + "\n";
            }
        } else {
            summary += "<none>\n";
        }

        return summary;
    }

    private String getActionCodeSummary() {
        String summary = "Action Codes:\n";
        Collection<ActionCode> actionCodes = this.api.getActionCodes();
        if (actionCodes != null && !actionCodes.isEmpty()) {
            for (ActionCode actionCode : actionCodes) {
                summary += actionCode + "\n";
            }
        } else {
            summary += "<none>\n";
        }

        return summary;
    }

    private String getSettingsSummary() {
        KeyValueCollection settings = this.api.getSettings();
        return "Settings:\n" + settings == null ? "<none>" : settings.toString();
    }

    private String getTransactionsSummary() {
        String summary = "Transactions:\n";

        Collection<Transaction> txns = this.api.getTransactions();
        if (txns != null && !txns.isEmpty()) {
            for (Transaction txn : txns) {
                summary += txn + "\n";
            }
        } else {
            summary += "<none>\n";
        }

        return summary;
    }

    private String getAgentGroupsSummary() {
        String summary = "Agent Groups:\n";

        Collection<AgentGroup> agentGroups = this.api.getAgentGroups();
        if (agentGroups != null && !agentGroups.isEmpty()) {
            for (AgentGroup group : agentGroups) {
                summary += "name [" + group.getName() + "] userProperties " + group.getUserProperties() + "]\n";
            }
        } else {
            summary += "<none>\n";
        }

        return summary;
    }

    private void printConfiguration(List<String> args) {
        String type = args.size() == 1 ? args.get(0) : "all";

        String msg = "";
        switch (type) {
            case "action-codes":
                msg += this.getActionCodeSummary();
                break;

            case "agent-groups":
                msg += this.getAgentGroupsSummary();
                break;

            case "ba":
                msg += this.getBusinessAttributeSummary();
                break;

            case "txn":
                msg += this.getTransactionsSummary();
                break;

            case "settings":
                msg += this.getSettingsSummary();
                break;

            case "all":
                msg += this.getActionCodeSummary() + "\n";
                msg += this.getAgentGroupsSummary() + "\n";
                msg += this.getBusinessAttributeSummary() + "\n";
                msg += this.getTransactionsSummary() + "\n";
                msg += this.getSettingsSummary() + "\n";
                break;

            default:
                msg = "Usage: configuration <type> where type is one of action-codes, agent-groups, ba, txn, settings";
        }

        this.write(msg);
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
                String key;
                String value;
                CompleteParams params;

                switch(cmd.getName()) {

                    case "acw":
                        this.api.voice().setAgentNotReady("AfterCallWork", null);
                        break;

                    case "initialize":
                    case "init":
                    case "i":
                        this.init();
                        break;

                    case "debug":
                    case "d":
                        //CM: TODO - change slf4j?
                        break;

                    case "dn":
                        this.write("Dn: " + this.getDnSummary(this.api.voice().getDn()));
                        break;

                    case "calls":
                        this.write("Calls:");
                        Collection<Call> calls = this.api.voice().getCalls();
                        if (calls.size() > 0) {
                            calls.forEach(c -> this.write(this.getCallSummary(c)));
                            this.write("");
                        } else {
                            this.write("<none>");
                        }
                        break;

                    case "configuration":
                    case "c":
                        this.printConfiguration(args);
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
                        this.api.voice().setAgentNotReady();
                        break;

                    case "ready":
                    case "r":
                        this.write("Sending ready...");
                        this.api.voice().setAgentReady();
                        break;

                    case "dnd-on":
                        this.write("Sending dnd-on...");
                        this.api.voice().dndOn();
                        break;

                    case "dnd-off":
                        this.write("Sending dnd-off...");
                        this.api.voice().dndOff();
                        break;

                    case "set-forward":
                        if (args.size() < 1) {
                            this.write("Usage: set-forward <destination>");
                        } else {
                            this.write("Sending set-forward with destination [" + args.get(0) + "]...");
                            this.api.voice().setForward(args.get(0));
                        }
                        break;

                    case "cancel-forward":
                        this.write("Sending cancel-forward...");
                        this.api.voice().cancelForward();
                        break;

                    case "voice-login":
                        this.write("Sending voice login...");
                        this.api.voice().login();
                        break;

                    case "voice-logout":
                        this.write("Sending voice logout...");
                        this.api.voice().logout();
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
                            this.api.voice().releaseCall(id);
                        }
                        break;

                    case "answer":
                    case "a":
                        id = this.getCallId(args);
                        if (id == null) {
                            this.write("Usage: answer <id>");
                        } else {
                            this.write("Sending answer for call [" + id + "]...");
                            this.api.voice().answerCall(id);
                        }
                        break;

                    case "hold":
                    case "h":
                        id = this.getCallId(args);
                        if (id == null) {
                            this.write("Usage: hold <id>");
                        } else {
                            this.write("Sending hold for call [" + id + "]...");
                            this.api.voice().holdCall(id);
                        }
                        break;

                    case "retrieve":
                    case "ret":
                        id = this.getCallId(args);
                        if (id == null) {
                            this.write("Usage: receive <id>");
                        } else {
                            this.write("Sending retrieve for call [" + id + "]...");
                            this.api.voice().retrieveCall(id);
                        }
                        break;

                    case "clear-call":
                        id = this.getCallId(args);
                        if (id == null) {
                            this.write("Usage: clear-call <id>");
                        } else {
                            this.write("Sending clear for call [" + id + "]...");
                            this.api.voice().clearCall(id);
                        }
                        break;

                    case "redirect":
                        if (args.size() < 1) {
                            this.write("Usage: redirect <id> <destination>");
                        } else {
                            // If there is only one argument take it as the destination.
                            destination = args.get(args.size() - 1);
                            id = this.getCallId(args.size() == 1 ? null : args);
                            if (id == null) {
                                this.write("Usage: redirect <id> <destination>");
                            } else {
                                this.write("Sending redirect for call [" + id
                                        + "] and destination [" + destination + "]...");
                                this.api.voice().redirectCall(id, destination);
                            }
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
                                        + "] and destination [" + destination + "]...");
                                this.api.voice().initiateConference(id, destination);
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
                            this.api.voice().completeConference(params.getConnId(), params.getParentConnId());
                        }
                        break;

                    case "delete-from-conference":
                    case "dfc":
                        if (args.size() < 1) {
                            this.write("Usage: delete-from-conference <id> <dnToDrop>");
                        } else {
                            // If there is only one argument take it as the dn to drop.
                            String dnToDrop = args.get(args.size() - 1);
                            id = this.getCallId(args.size() == 1 ? null : args);
                            if (id == null) {
                                this.write("Usage: delete-from-conference <id> <dnToDrop>");
                            } else {
                                this.write("Sending delete-from-conference for call [" + id
                                        + " and dnToDrop [" + dnToDrop + "]...");
                                this.api.voice().deleteFromConference(id, dnToDrop);
                            }
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
                                        + "] and destination [" + destination + "]...");
                                this.api.voice().initiateTransfer(id, destination);
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
                            this.api.voice().completeTransfer(params.getConnId(), params.getParentConnId());
                        }
                        break;


                    case "single-step-transfer":
                    case "sst":
                        if (args.size() < 1) {
                            this.write("Usage: single-step-transfer <id> <destination>");
                        } else {
                            // If there is only one argument take it as the destination.
                            destination = args.get(args.size() - 1);
                            id = this.getCallId(args.size() == 1 ? null : args);
                            if (id == null) {
                                this.write("Usage: single-step-transfer <id> <destination>");
                            } else {
                                this.write("Sending single-step-transfer for call [" + id
                                        + "] and destination [" + destination + "]...");
                                this.api.voice().singleStepTransfer(id, destination);
                            }
                        }
                        break;

                    case "single-step-conference":
                    case "ssc":
                        if (args.size() < 1) {
                            this.write("Usage: single-step-conference <id> <destination>");
                        } else {
                            // If there is only one argument take it as the destination.
                            destination = args.get(args.size() - 1);
                            id = this.getCallId(args.size() == 1 ? null : args);
                            if (id == null) {
                                this.write("Usage: single-step-conference <id> <destination>");
                            } else {
                                this.write("Sending single-step-conference for call [" + id
                                        + "] and destination [" + destination + "]...");
                                this.api.voice().singleStepConference(id, destination);
                            }
                        }
                        break;

                    case "attach-user-data":
                    case "aud":
                        if (args.size() < 3) {
                            this.write("Usage: attach-user-data <id> <key> <value>");
                        } else {
                            id = args.get(0);
                            key = args.get(1);
                            value = args.get(2);

                            this.write("Sending attach-user-data for call [" + id
                                    + "] and data [" + key + "=" + value + "]...");

                            KeyValueCollection userData = new KeyValueCollection();
                            userData.addString(key, value);
                            this.api.voice().attachUserData(id, userData);
                        }
                        break;

                    case "update-user-data":
                    case "uud":
                        if (args.size() < 3) {
                            this.write("Usage: update-user-data <id> <key> <value>");
                        } else {
                            id = args.get(0);
                            key = args.get(1);
                            value = args.get(2);

                            this.write("Sending update-user-data for call [" + id
                                    + "] and data [" + key + "=" + value + "]...");

                            KeyValueCollection userData = new KeyValueCollection();
                            userData.addString(key, value);
                            this.api.voice().updateUserData(id, userData);
                        }
                        break;

                    case "delete-user-data-pair":
                    case "dp":
                        if (args.size() < 1) {
                            this.write("Usage: delete-user-data-pair <id> <key>");
                        } else {
                            // If there is only one argument take it as the destination.
                            key = args.get(args.size() - 1);
                            id = this.getCallId(args.size() == 1 ? null : args);
                            if (id == null) {
                                this.write("Usage: delete-user-data-pair <id> <key>");
                            } else {
                                this.write("Sending delete-user-data-pair for call [" + id
                                        + " and key [" + key + "]...");
                                this.api.voice().deleteUserDataPair(id, key);
                            }
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
                            this.api.voice().alternateCalls(args.get(0), args.get(1));
                        }
                        break;

                    case "merge":
                        if (args.size() < 2) {
                            this.write("Usage: merge <id> <otherConnId>");
                        } else {
                            this.write("Sending merge for call ["
                                    + args.get(0) + "] and otherConnId ["
                                    + args.get(1) + "]...");
                            this.api.voice().mergeCalls(args.get(0), args.get(1));
                        }
                        break;

                    case "reconnect":
                        if (args.size() < 2) {
                            this.write("Usage: reconnect <id> <heldConnId>");
                        } else {
                            this.write("Sending reconnect for call ["
                                    + args.get(0) + "] and heldConnId ["
                                    + args.get(1) + "]...");
                            this.api.voice().reconnectCall(args.get(0), args.get(1));
                        }
                        break;

                    case "send-dtmf":
                    case "dtmf":
                        if (args.size() < 1) {
                            this.write("Usage: send-dtmf <id> <digits>");
                        } else {
                            // If there is only one argument take it as the dtmf digits.
                            String digits = args.get(args.size() - 1);
                            id = this.getCallId(args.size() == 1 ? null : args);
                            if (id == null) {
                                this.write("Usage: send-dtmf <id> <digits>");
                            } else {
                                this.write("Sending send-dtmf for call [" + id
                                        + " and dtmfDigits [" + digits + "]...");
                                this.api.voice().sendDTMF(id, digits);
                            }
                        }
                        break;

                    case "start-recording":
                        id = this.getCallId(args);
                        if (id == null) {
                            this.write("Usage: start-recording <id>");
                        } else {
                            this.write("Sending start-recording for call [" + id + "]...");
                            this.api.voice().startRecording(id);
                        }
                        break;

                    case "pause-recording":
                        id = this.getCallId(args);
                        if (id == null) {
                            this.write("Usage: pause-recording <id>");
                        } else {
                            this.write("Sending pause-recording for call [" + id + "]...");
                            this.api.voice().pauseRecording(id);
                        }
                        break;

                    case "resume-recording":
                        id = this.getCallId(args);
                        if (id == null) {
                            this.write("Usage: resume-recording <id>");
                        } else {
                            this.write("Sending resume-recortding for call [" + id + "]...");
                            this.api.voice().resumeRecording(id);
                        }
                        break;

                    case "stop-recording":
                        id = this.getCallId(args);
                        if (id == null) {
                            this.write("Usage: stop-recording <id>");
                        } else {
                            this.write("Sending stop-recording for call [" + id + "]...");
                            this.api.voice().stopRecording(id);
                        }
                        break;

                    case "send-user-event":
                        if (args.size() < 3) {
                            this.write("Usage: send-user-event <key> <value> <callUuid>");
                        } else {
                            // If there are only two arguments take them as the key/value.
                            key = args.get(0);
                            value = args.get(1);
                            String uuid = args.get(2);

                            this.write("Sending send-user-event with data [" + key + "=" + value
                                    + "] and callUuid [" + uuid + "...");

                            KeyValueCollection userData = new KeyValueCollection();
                            userData.addString(key, value);
                            this.api.voice().sendUserEvent(null, uuid);
                        }
                        break;

                    case "target-search":
                    case "ts":
                        if (args.size() < 1) {
                            this.write("Usage: target-search <search term>");
                        } else {
                            SearchResult<Target> result = this.api.targets().search(args.get(0));
                            String resultMsg = "Search results:\n";
                            if (result.getTargets() != null && !result.getTargets().isEmpty()) {
                                for (Target target : result.getTargets()) {
                                    resultMsg += "    " + target + "\n";
                                }
                                resultMsg += "Total matches: " + result.getTotal();
                            } else {
                                resultMsg += "<none>\n";
                            }

                            this.write(resultMsg);
                        }
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
                                    "defaultPlace: " + this.user.getDefaultPlace() + "\n" +
                                    "userProperties: " + this.user.getUserProperties() + "\n");
                        }
                        break;

                    case "console-config":
                        this.write("Configuration:\n"
                            + "apiKey: " + this.options.getApiKey() + "\n"
                            + "baseUrl: " + this.options.getBaseUrl() + "\n"
                            + "clientId: " + this.options.getClientId() + "\n"
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
