import com.genesys.samples.workspace.WorkspaceApi;

public class Main {

    public static void main(String[] args) {
        String apiKey        = "<apiKey>";
        String clientId      = "<client>";
        String clientSecret  = "<secret>";
        String baseUrl       = "<url>";
        String username      = "<username>";
        String password      = "<pw>";

        try {
            WorkspaceApi api = new WorkspaceApi(
                    apiKey, clientId, clientSecret, baseUrl, username, password);
            WorkspaceConsole console = new WorkspaceConsole(api);
            console.run();

        } catch (Exception e) {
            System.out.println("Error!:\n" + e.toString());
            e.printStackTrace();
        }
    }
}
