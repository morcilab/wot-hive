package directory.triplestore;

import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.sparql.resultset.ResultsFormat;
import org.apache.jena.update.UpdateExecutionFactory;
import org.apache.jena.update.UpdateProcessor;
import org.apache.jena.update.UpdateRequest;
import directory.Directory;

public class RemoteSparql extends AbstractSparql {

    private String endpoint;
    private String username;
    private String password;
    
    @Override
    public void init() {
    }

    public RemoteSparql() {
        this.endpoint = Directory.getConfiguration().getTriplestore().getQueryEnpoint().toString();
        this.username = Directory.getConfiguration().getTriplestore().getUsername();
        this.password = Directory.getConfiguration().getTriplestore().getPassword();
    }

    public ResultsFormat guess(String str) {
        return ResultsFormat.lookup(str);
    }

    @Override
    QueryExecution getQueryExecution(Query query) {
        QueryExecution qexec = QueryExecutionFactory.sparqlService(endpoint, query);
        if(username != null && password != null) {
            CloseableHttpClient client = connectPW(endpoint, username, password);
            qexec = QueryExecutionFactory.sparqlService(endpoint, query, client);
        }
        return qexec;
    }

    @Override
    UpdateProcessor getUpdateProcessor(UpdateRequest updateRequest) {
        UpdateProcessor updateProcessor = UpdateExecutionFactory.createRemote(updateRequest, this.endpoint);

        if(username != null && password != null) {
            CloseableHttpClient client = connectPW(this.endpoint, this.username, this.password);
            updateProcessor = UpdateExecutionFactory.createRemote(updateRequest, endpoint, client);
        }
        return updateProcessor;
    }

    // query methods
    public CloseableHttpClient connectPW(String URL, String user, String password) {
        BasicCredentialsProvider credsProvider = new BasicCredentialsProvider();
        Credentials credentials = new UsernamePasswordCredentials(user, password);
        credsProvider.setCredentials(AuthScope.ANY, credentials);
        return HttpClients.custom().setDefaultCredentialsProvider(credsProvider).build();

    }
}
