package directory.triplestore;

import java.io.ByteArrayOutputStream;

import org.apache.jena.sparql.resultset.ResultsFormat;

public interface Sparql {
    ByteArrayOutputStream query(String sparql, ResultsFormat format);

    void update(String sparql);
    
    void init();
}
