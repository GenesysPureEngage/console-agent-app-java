import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import java.util.ArrayList;
import java.util.List;

public class Options {

    @Option(name="--apiKey", required = true)
    private String apiKey;

    @Option(name="--username", required = true)
    private String username;

    @Option(name="--password", required = true)
    private String password;

    @Option(name="--baseUrl", required = true)
    private String baseUrl;

    @Option(name="--clientId", required = true)
    private String clientId;

    @Option(name="--clientSecret", required = true)
    private String clientSecret;

    @Option(name="--debugEnabled")
    private boolean debugEnabled;

    @Option(name="--defaultAgentId")
    private String defaultAgentId;

    @Option(name="--defaultDn")
    private String defaultDn;

    @Option(name="--defaultDestination")
    private String defaultDestination;

    @Option(name="--autoLogin")
    private boolean autoLogin;
    
    @Option(name="--command")
    private List<String> commands = new ArrayList<>();

    @Argument
    private List<String> arguments = new ArrayList<String>();

    public static Options parseOptions(String... args) {
        Options options = new Options();
        CmdLineParser parser = new CmdLineParser(options);
        try {
            parser.parseArgument(args);

            return options;
        } catch (CmdLineException e) {
            parser.printUsage(System.err);
            System.err.println();
            return null;
        }
    }

    public List<String> getCommands() {
        return commands;
    }

    public String getApiKey() {
        return this.apiKey;
    }

    public String getBaseUrl() {
        return this.baseUrl;
    }

    public String getClientId() {
        return this.clientId;
    }

    public String getClientSecret() {
        return this.clientSecret;
    }

    public String getUsername() {
        return this.username;
    }

    public String getPassword() {
        return this.password;
    }

    public String getDefaultAgentId() {
        return this.defaultAgentId;
    }

    public String getDefaultDn() {
        return this.defaultDn;
    }

    public String getDefaultDestination() {
        return this.defaultDestination;
    }

    public boolean isDebugEnabled() {
        return this.debugEnabled;
    }

    public boolean isAutoLogin() {
        return this.autoLogin;
    }
}
