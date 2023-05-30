package directory.triplestore;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.StringJoiner;
import java.util.logging.Logger;

import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.query.TxnType;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.shared.Lock;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.util.Context;
import org.apache.jena.update.UpdateExecutionFactory;
import org.apache.jena.update.UpdateProcessor;
import org.apache.jena.update.UpdateRequest;

class DatasetWrapper implements Dataset {
    private Dataset wrapped;
    
    public DatasetWrapper(Dataset wrapped) {
        this.wrapped = wrapped;
    }
    @Override
    public void abort() {
        this.wrapped.abort();
    }
    @Override
    public Dataset addNamedModel(String uri, Model model) {
        return this.wrapped.addNamedModel(uri, model);
    }
    @Override
    public DatasetGraph asDatasetGraph() {
        return this.wrapped.asDatasetGraph();
    }
    @Override
    public void begin(ReadWrite readWrite) {
        this.wrapped.begin(readWrite);
    }
    @Override
    public void begin(TxnType type) {
        this.wrapped.begin(type);
    }
    @Override
    public void close() {
        this.wrapped.close();
    }
    @Override
    public void commit() {
        this.wrapped.commit();
    }
    @Override
    public boolean containsNamedModel(String uri) {
        return this.wrapped.containsNamedModel(uri);
    }
    @Override
    public void end() {
        this.wrapped.end();
    }
    @Override
    public Context getContext() {
        return this.wrapped.getContext();
    }
    @Override
    public Model getDefaultModel() {
        return this.wrapped.getDefaultModel();
    }
    @Override
    public Lock getLock() {
        return this.wrapped.getLock();
    }
    @Override
    public Model getNamedModel(String uri) {
        return this.wrapped.getNamedModel(uri);
    }
    @Override
    public Model getUnionModel() {
        return this.wrapped.getUnionModel();
    }
    @Override
    public boolean isEmpty() {
           return this.wrapped.isEmpty();
    }
    @Override
    public boolean isInTransaction() {
        return this.wrapped.isInTransaction();
    }
    @Override
    public Iterator<String> listNames() {
        return this.wrapped.listNames();
    }
    @Override
    public boolean promote(Promote mode) {
        return this.wrapped.promote(mode);
    }
    @Override
    public Dataset removeNamedModel(String uri) {
        return this.wrapped.removeNamedModel(uri);
    }
    @Override
    public Dataset replaceNamedModel(String uri, Model model) {
        return this.wrapped.replaceNamedModel(uri, model);
    }
    @Override
    public Dataset setDefaultModel(Model model) {
        return this.wrapped.setDefaultModel(model);
    }
    @Override
    public boolean supportsTransactionAbort() {
        return this.wrapped.supportsTransactionAbort();
    }
    @Override
    public boolean supportsTransactions() {
        return this.wrapped.supportsTransactions();
    }
    @Override
    public ReadWrite transactionMode() {
        return this.wrapped.transactionMode();
    }
    @Override
    public TxnType transactionType() {
        return this.wrapped.transactionType();
    }
}

class Wrapper implements InvocationHandler {
    private static Logger LOGGER = Logger.getLogger(Wrapper.class.toString());
    private Object wrapped;
    private Class wrappedClass;
    private final Map<String, Method> methods = new HashMap<>();
    
    public Wrapper(Object wrapped) {
        this.wrapped = wrapped;
        this.wrappedClass = wrapped.getClass();
        
        for(Method method: wrapped.getClass().getDeclaredMethods()) {
            this.methods.put(method.getName(), method);
        }
    }
    
    public Object getWrapped() {
        return wrapped;
    }
    
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        LOGGER.info("Invoking method: "+method.getName());
        if(Dataset.class.isInstance(this.wrapped) && method.getName().equals("getDefaultModel")) {
            Model defaultModel = ((Dataset)this.wrapped).getDefaultModel();
            Model wrappedModel = (Model)Proxy.newProxyInstance(Model.class.getClassLoader(), new Class[] { Model.class }, new Wrapper(defaultModel));
            LOGGER.info("Returning wrapped model");
            return wrappedModel;
        } else if(Dataset.class.isInstance(this.wrapped) && method.getName().equals("getNamedModel")) {
            String modelName = (String)args[0];
            Model model = ((Dataset)this.wrapped).getNamedModel(modelName);
            Model wrappedModel = (Model)Proxy.newProxyInstance(Model.class.getClassLoader(), new Class[] { Model.class }, new Wrapper(model));
            LOGGER.info("Returning wrapped model - "+modelName);
            return wrappedModel;            
        } else if(Dataset.class.isInstance(this.wrapped) && method.getName().equals("asDatasetGraph")) {
            DatasetGraph datasetGraph = ((Dataset)this.wrapped).asDatasetGraph();
            DatasetGraph wrappedDatasetGraph = (DatasetGraph)Proxy.newProxyInstance(DatasetGraph.class.getClassLoader(), new Class[] { DatasetGraph.class }, new Wrapper(datasetGraph));
            LOGGER.info("Returning wrapped datasetgraph");
            return wrappedDatasetGraph;
        } else if(Dataset.class.isInstance(this.wrapped) && method.getName().equals("toString")) {
        	StringJoiner stringJoiner = new StringJoiner(", ");
        	((Dataset)this.wrapped).listNames().forEachRemaining(stringJoiner::add);
        	return stringJoiner.toString();
        } else {
            return method.invoke(this.wrapped, args);
        }
    }
}

public class LocalSparql extends AbstractSparql {
    public static final int DEFAULT_SPARQL_PORT = 9998;
    private static final String SPARQL_ENDPOINT_DGNAME = "wotkb";
    private int sparqlPort = DEFAULT_SPARQL_PORT;
    private FusekiServer fusekiServer;

    private Dataset dataset;
    
    @Override
    QueryExecution getQueryExecution(Query query) {
        return QueryExecutionFactory.create(query, this.dataset);
    }

    @Override
    UpdateProcessor getUpdateProcessor(UpdateRequest updateRequest) {
        return UpdateExecutionFactory.create(updateRequest, this.dataset);
    }
    
    @Override
    public void init() {
//        this.dataset = (Dataset)Proxy.newProxyInstance(Dataset.class.getClassLoader(), new Class[] { Dataset.class }, new Wrapper(DatasetFactory.createTxnMem()));
//        this.dataset = new DatasetWrapper(DatasetFactory.createTxnMem());
    	this.dataset = DatasetFactory.createTxnMem();
//    	dataset.begin();
//        this.dataset.getDefaultModel().add(ResourceFactory.createResource("foo"), ResourceFactory.createProperty("bar"), "baz");
//        dataset.commit();
        String sparqlEndpoint = "http://localhost:"+sparqlPort+"/"+SPARQL_ENDPOINT_DGNAME;
        this.fusekiServer = FusekiServer.create().port(sparqlPort).enableCors(true).add(SPARQL_ENDPOINT_DGNAME, this.dataset).loopback(false).build().start();
        System.out.println("Running SPARQL endpoint at "+sparqlEndpoint);
    }
}
