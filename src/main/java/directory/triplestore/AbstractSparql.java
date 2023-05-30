package directory.triplestore;

import java.io.ByteArrayOutputStream;

import org.apache.jena.query.Query;
import org.apache.jena.query.QueryException;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.ResultSetFormatter;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.riot.RDFWriter;
import org.apache.jena.sparql.resultset.ResultsFormat;
import org.apache.jena.update.UpdateFactory;
import org.apache.jena.update.UpdateProcessor;
import org.apache.jena.update.UpdateRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import directory.Directory;
import directory.exceptions.SparqlEndpointException;

public abstract class AbstractSparql implements Sparql {
    public static final Logger LOGGER = LoggerFactory.getLogger(AbstractSparql.class);

    @Override
    public ByteArrayOutputStream query(String sparql, ResultsFormat format) {
        return query(sparql, format, Directory.getConfiguration().getTriplestore().getQueryEnpoint().toString(), 
            Directory.getConfiguration().getTriplestore().getUsername(), 
            Directory.getConfiguration().getTriplestore().getPassword());
    }

    abstract QueryExecution getQueryExecution(Query query);
    
    abstract UpdateProcessor getUpdateProcessor(UpdateRequest query);

    protected ByteArrayOutputStream query(String sparql, ResultsFormat format, String endpoint, String username, String password) {
        LOGGER.info("query: "+sparql);
        
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        try {
            Query query = QueryFactory.create(sparql);
            QueryExecution qexec = getQueryExecution(query);
            
            if(query.isSelectType()) {
                ResultSetFormatter.output(stream, qexec.execSelect(), format);
            } else if(query.isAskType()) {
                ResultSetFormatter.output(stream, qexec.execAsk(), ResultsFormat.convert(format));
            } else if(query.isConstructType()) {
                RDFFormat formatOutput = RDFFormat.NT;
                if(ResultsFormat.FMT_RDF_JSONLD.equals(format))
                    formatOutput = RDFFormat.JSONLD;
                if(ResultsFormat.FMT_RDF_TURTLE.equals(format))
                    formatOutput = RDFFormat.TURTLE;
                if(ResultsFormat.FMT_RDF_NT.equals(format))
                    formatOutput = RDFFormat.NTRIPLES;
                if(ResultsFormat.FMT_RDF_NQ.equals(format))
                    formatOutput = RDFFormat.NQ;

                RDFWriter.create(qexec.execConstruct()).format(formatOutput).output(stream);
            } else if(query.isDescribeType()) {
                RDFFormat formatOutput = RDFFormat.NT;
                if(ResultsFormat.FMT_RDF_JSONLD.equals(format))
                    formatOutput = RDFFormat.JSONLD;
                if(ResultsFormat.FMT_RDF_TURTLE.equals(format))
                    formatOutput = RDFFormat.TURTLE;
                if(ResultsFormat.FMT_RDF_NT.equals(format))
                    formatOutput = RDFFormat.NTRIPLES;
                if(ResultsFormat.FMT_RDF_NQ.equals(format))
                    formatOutput = RDFFormat.NQ;
                RDFWriter.create(qexec.execDescribe()).format(formatOutput).output(stream);
            } else {
                throw new SparqlEndpointException("Query not supported, provided one query SELECT, ASK, DESCRIBE or CONSTRUCT");
            }
        } catch(QueryException e) {
            String msg = "Internal query has syntax errors: " + e.toString();
            Directory.LOGGER.error("ERROR: " + msg);
            Directory.LOGGER.error("ERROR: " + sparql);
            throw new SparqlEndpointException(msg);
        } catch(Exception e) {
            throw new SparqlEndpointException(e.toString());
        }
        return stream;
    }

    @Override
    public void update(String sparql) {
        LOGGER.info("update: "+sparql);

        try {
            UpdateRequest updateRequest = UpdateFactory.create(sparql);
            UpdateProcessor updateProcessor = getUpdateProcessor(updateRequest);
            updateProcessor.execute();
        } catch(QueryException e) {
            throw new SparqlEndpointException(e.toString()); // syntax error
        } catch(org.apache.jena.atlas.web.HttpException e) {
            throw new SparqlEndpointException(e.getMessage());
        }
    }

}
