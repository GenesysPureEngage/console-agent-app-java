package com.genesys.samples.workspace;

import java.net.URI;
import java.net.HttpCookie;
import java.net.CookieManager;

import java.util.Base64;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.genesys.common.ApiClient;
import com.genesys.common.ApiResponse;
import com.genesys.common.ApiException;
import com.genesys.authorization.api.AuthenticationApi;
import com.genesys.authorization.model.DefaultOAuth2AccessToken;

import com.genesys.workspace.api.SessionApi;
import com.genesys.workspace.api.VoiceApi;
import com.genesys.workspace.model.*;

import org.cometd.bayeux.Message;
import org.cometd.bayeux.client.ClientSessionChannel;
import org.cometd.client.BayeuxClient;
import org.cometd.client.transport.ClientTransport;
import org.cometd.client.transport.LongPollingTransport;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.util.ssl.SslContextFactory;

public class WorkspaceApi {
    private String apiKey;
    private String clientId;
    private String clientSecret;
    private String baseUrl;
    private String username;
    private String password;
    private boolean debugEnabled = true;
    private String workspaceUrl;
    private ApiClient authClient;
    private ApiClient workspaceClient;
    private HttpClient cometdHttpClient;
    private BayeuxClient cometdClient;
    private AuthenticationApi authApi;
    private SessionApi sessionApi;
    private VoiceApi voiceApi;
    private DefaultOAuth2AccessToken accessToken;
    private String sessionCookie;
    private String workspaceSessionId;
    private CompletableFuture<User> initFuture;
    private boolean workspaceInitialized = false;
    private User user;
    private DN dn;
    private Map<String, Call> calls;

    public WorkspaceApi(
            String apiKey,
            String clientId,
            String clientSecret,
            String baseUrl,
            String username,
            String password
    ) {
        this.apiKey = apiKey;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.baseUrl = baseUrl;
        this.username = username;
        this.password = password;
        this.workspaceUrl = this.baseUrl + "/workspace/v3";

        this.calls = new HashMap();
    }

    private void debug(String msg) {
        if (this.debugEnabled) {
            System.out.println(msg);
        }
    }

    private void getAccessToken() throws ApiException {
        debug("Getting access token...");
        byte[] bytes = (this.clientId + ":" + this.clientSecret).getBytes();
        byte[] encoded = Base64.getEncoder().encode(bytes);
        String authorization = "Basic " + new String(encoded);

        this.accessToken = this.authApi.retrieveToken(
                "password", "webshop", authorization, null, this.clientId, this.username, this.password);
        debug("Access token is " + this.accessToken.getAccessToken());
    }

    private void extractSessionCookie(ApiResponse<ApiSuccessResponse> response) throws WorkspaceApiException {
        debug("Extracting session cookie...");
        Optional<String> cookie = response.getHeaders().get("set-cookie")
                .stream().filter(v -> v.startsWith("WORKSPACE_SESSIONID")).findFirst();

        if(!cookie.isPresent()) {
            throw new WorkspaceApiException("Failed to extract workspace session cookie.");
        }

        this.sessionCookie = cookie.get();
        this.workspaceSessionId = this.sessionCookie.split(";")[0].split("=")[1];
        debug("WORKSPACE_SESSIONID is " + this.workspaceSessionId);

        this.workspaceClient.addDefaultHeader("Cookie", this.sessionCookie);
    }

    private void onInitMessage(Message message) {
        debug("Message received for /workspace/v3/initialization:\n" + message.toString());

        Map<String, Object> data = message.getDataAsMap();
        String messageType = (String)data.get("messageType");

        if ("WorkspaceInitializationComplete".equals(messageType)) {
            String state = (String)data.get("state");
            if ("Complete".equals(state)) {
                this.workspaceInitialized = true;

                Map<String, Object> initData = (Map<String, Object>)data.get("data");
                Map<String, Object> userData = (Map<String, Object>)initData.get("user");
                String employeeId = (String)userData.get("employeeId");
                String defaultPlace = (String)userData.get("defaultPlace");
                String agentId = (String)userData.get("agentId");
                if (this.user == null) {
                    this.user = new User();
                }

                user.setEmployeeId(employeeId);
                user.setAgentId(agentId);
                user.setDefaultPlace(defaultPlace);

                this.initFuture.complete(this.user);
                this.workspaceInitialized = true;

            } else if ("Failed".equals(state)) {
                debug("Workspace initialization failed!");
                this.initFuture.completeExceptionally(
                        new WorkspaceApiException("initialize workspace failed"));
            }
        }
    }

    private void onDnStateChanged(Map<String, Object> data) {
        if (this.dn == null) {
            this.dn = new DN();
        }

        Map<String, Object> dnData = (Map<String, Object>)data.get("dn");

        String number = (String)dnData.get("number");
        String agentId = (String)dnData.get("agentId");
        String agentState = (String)dnData.get("agentState");

        this.dn.setAgentId(agentId);
        this.dn.setNumber(number);
        this.dn.setAgentState(agentState);

        debug("DN state updated: " + agentState);
    }

    private void onCallStateChanged(Map<String, Object> data) {

        Map<String, Object> callData = (Map<String, Object>)data.get("call");
        String id = (String)callData.get("id");
        String state = (String)callData.get("state");

        switch (state) {
            case "Ringing":
            case "Dialing":
                Call newCall = new Call();
                newCall.setId(id);
                newCall.setState(state);

                this.calls.put(id, newCall);
                debug("Added call " + id + " (" + state + ")");
                break;

            case "Released":
                this.calls.remove(id);
                debug("Removed call " + id + "(" + state + ")");
                break;

            default:
                Call call = this.calls.get(id);
                if (call != null) {
                    call.setState(state);
                    debug("Updated call " + id + " with state " + state);
                } else {
                    debug("Call " + id + " was not found...");
                }
        }
    }

    private void onVoiceMessage(Message message) {
        debug("Message received for /workspace/v3/voice:\n" + message.toString());

        Map<String, Object> data = message.getDataAsMap();
        String messageType = (String)data.get("messageType");

        switch(messageType) {
            case "DnStateChanged":
                onDnStateChanged(data);
                break;

            case "CallStateChanged":
                onCallStateChanged(data);
                break;

            default:
                debug("Unexpected messageType: " + messageType);
        }
    }

    private void onHandshake(Message handshakeMessage) {
        if(!handshakeMessage.isSuccessful()) {
            debug("Cometd handshake failed:\n" + handshakeMessage.toString());
            return;
        }

        debug("Cometd handshake successful.");
        debug("Subscribing to channels...");
        this.cometdClient.getChannel("/workspace/v3/initialization").subscribe(
                (ClientSessionChannel channel, Message msg) -> this.onInitMessage(msg));

        this.cometdClient.getChannel("/workspace/v3/voice").subscribe(
                (ClientSessionChannel channel, Message msg) -> this.onVoiceMessage(msg));
    }

    private void initializeCometd() throws WorkspaceApiException {
        try {
            debug("Initializing cometd...");
            SslContextFactory sslContextFactory = new SslContextFactory();
            this.cometdHttpClient = new HttpClient(sslContextFactory);
            cometdHttpClient.start();

            CookieManager manager = new CookieManager();
            cometdHttpClient.setCookieStore(manager.getCookieStore());
            cometdHttpClient.getCookieStore().add(new URI(workspaceUrl),
                    new HttpCookie("WORKSPACE_SESSIONID", this.workspaceSessionId));

            ClientTransport transport = new LongPollingTransport(new HashMap(), cometdHttpClient) {
                @Override
                protected void customize(Request request) {
                    request.header("x-api-key", apiKey);
                }
            };

            this.cometdClient = new BayeuxClient(this.workspaceUrl + "/notifications", transport);
            debug("Starting cometd handshake...");
            this.cometdClient.handshake((ClientSessionChannel channel, Message msg) -> this.onHandshake(msg));

        } catch(Exception e) {
            throw new WorkspaceApiException("Cometd initialization failed.", e);
        }
    }

    private void throwIfNotOk(String requestName, ApiSuccessResponse response) throws WorkspaceApiException {
        if (response.getStatus().getCode() != StatusCode.ASYNC_OK) {
            throw new WorkspaceApiException(
                    requestName + " failed with code: " + response.getStatus().getCode());
        }
    }

    public CompletableFuture<User> initialize() throws WorkspaceApiException {
        try {
            this.initFuture = new CompletableFuture<>();

            this.workspaceClient = new ApiClient();
            this.workspaceClient.setBasePath(this.workspaceUrl);
            this.workspaceClient.addDefaultHeader("x-api-key", this.apiKey);

            this.sessionApi = new SessionApi(this.workspaceClient);
            this.voiceApi = new VoiceApi(this.workspaceClient);

            this.authClient = new ApiClient();
            this.authClient.setBasePath(this.baseUrl);
            this.authClient.addDefaultHeader("x-api-key", this.apiKey);

            this.authApi = new AuthenticationApi(this.authClient);

            this.getAccessToken();

            String authorization = "Bearer " + this.accessToken.getAccessToken();
            final ApiResponse<ApiSuccessResponse> response =
                    this.sessionApi.initializeWorkspaceWithHttpInfo("", "", authorization);
            this.extractSessionCookie(response);

            this.initializeCometd();
            return initFuture;

        } catch (ApiException e) {
            throw new WorkspaceApiException("initialize failed.", e);
        }
    }

    public void destroy() throws WorkspaceApiException {
        try {
            this.cometdClient.disconnect();
            this.cometdHttpClient.stop();
            this.sessionApi.logout();
        } catch (Exception e) {
            throw new WorkspaceApiException("destroy failed.", e);
        } finally {
            this.workspaceInitialized = false;
        }
    }

    public void activateChannels(String agentId, String dn) throws WorkspaceApiException {
        try {
            debug("Activating channels with agentId [" + agentId + "] and DN [" + dn + "]...");
            ActivatechannelsData data = new ActivatechannelsData();
            data.setAgentId(agentId);
            data.setDn(dn);

            ChannelsData channelsData = new ChannelsData();
            channelsData.data(data);

            ApiSuccessResponse response = sessionApi.activateChannels(channelsData);
            if(response.getStatus().getCode() != 0) {
                throw new WorkspaceApiException(
                        "activateChannels failed with code: " +
                        response.getStatus().getCode());
            }
        } catch (ApiException e) {
            throw new WorkspaceApiException("activateChannels failed.", e);
        }
    }

    public DN getDN() {
        return this.dn;
    }

    public Collection<Call> getCalls() {
        return this.calls.values();
    }

    public User getUser() {
        return this.user;
    }

    public void setAgentReady() throws WorkspaceApiException {
        try {
            ReadyData data = new ReadyData();

            ApiSuccessResponse response = voiceApi.setAgentStateReady(data);
            throwIfNotOk("setAgentReady", response);
        } catch (ApiException e) {
            throw new WorkspaceApiException("setAgentReady failed.", e);
        }
    }

    public void setAgentNotReady() throws WorkspaceApiException {
        this.setAgentNotReady(null, null);
    }

    public void setAgentNotReady(String workMode, String reasonCode) throws WorkspaceApiException{
        try {
            NotReadyData data = new NotReadyData();

            if (workMode != null || reasonCode != null) {
                VoicenotreadyData notReadyData = new VoicenotreadyData();
                data.data(notReadyData);

                if (workMode != null) {
                    notReadyData.setAgentWorkMode(VoicenotreadyData.AgentWorkModeEnum.valueOf(workMode));
                }

                if (reasonCode != null) {
                    notReadyData.setReasonCode(reasonCode);
                }

            }

            ApiSuccessResponse response = voiceApi.setAgentStateNotReady(data);
            throwIfNotOk("setAgentNotReady", response);
        } catch (ApiException e) {
            throw new WorkspaceApiException("setAgentReady failed.", e);
        }
    }

    public void makeCall(String destination) throws WorkspaceApiException {
        try {
            VoicemakecallData data = new VoicemakecallData();
            data.destination(destination);
            MakeCallData makeCallData = new MakeCallData().data(data);

            ApiSuccessResponse response = voiceApi.makeCall(makeCallData);
            throwIfNotOk("makeCall", response);

        } catch (ApiException e) {
            throw new WorkspaceApiException("makeCall failed.", e);
        }
    }

    public void answerCall(String id) throws WorkspaceApiException {
        try {
            AnswerData data = new AnswerData();

            ApiSuccessResponse response = voiceApi.answer(id, data);
            throwIfNotOk("answerCall", response);

        } catch (ApiException e) {
            throw new WorkspaceApiException("answerCall failed.", e);
        }
    }

    public void holdCall(String id) throws WorkspaceApiException {
        try {
            HoldData data = new HoldData();

            ApiSuccessResponse response = voiceApi.hold(id, data);
            throwIfNotOk("holdCall", response);

        } catch (ApiException e) {
            throw new WorkspaceApiException("holdCall failed.", e);
        }
    }

    public void retrieveCall(String id) throws WorkspaceApiException {
        try {
            RetrieveData data = new RetrieveData();

            ApiSuccessResponse response = voiceApi.retrieve(id, data);
            throwIfNotOk("retrieveCall", response);

        } catch (ApiException e) {
            throw new WorkspaceApiException("retrieveCall failed.", e);
        }
    }

    public void releaseCall(String id) throws WorkspaceApiException {
        try {
            ReleaseData data = new ReleaseData();

            ApiSuccessResponse response = voiceApi.release(id, data);
            throwIfNotOk("releaseCall", response);

        } catch (ApiException e) {
            throw new WorkspaceApiException("releaseCall failed.", e);
        }
    }

    public boolean debugEnabled() {
        return this.debugEnabled;
    }

    public void setDebugEnabled(boolean debugEnabled) {
        this.debugEnabled = debugEnabled;
    }

    public boolean isInitialized() {
        return this.workspaceInitialized;
    }
}
