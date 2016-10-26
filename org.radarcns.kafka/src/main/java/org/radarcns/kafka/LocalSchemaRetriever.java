package org.radarcns.kafka;

import org.radarcns.util.IO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class LocalSchemaRetriever extends SchemaRetriever {
    private final static Logger logger = LoggerFactory.getLogger(LocalSchemaRetriever.class);

    @Override
    protected ParsedSchemaMetadata retrieveSchemaMetadata(String topic, boolean ofValue) throws IOException {
        logger.debug("Retrieving schema for topic {} locally", topic);
        try {
            String schemaString = IO.readInputStream(LocalSchemaRetriever.class.getClassLoader().getResourceAsStream("avro/" + topic + ".avsc"));
            return new ParsedSchemaMetadata(null, null, parseSchema(schemaString));
        } catch (IOException ex) {
            System.out.println("Cannot parse schema " + topic);
            ex.printStackTrace();
            throw ex;
        }
    }
}
