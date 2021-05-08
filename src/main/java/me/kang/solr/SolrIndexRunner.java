package me.kang.solr;


import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.CloudSolrClient;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.impl.HttpSolrClient.Builder;
import org.apache.solr.client.solrj.impl.XMLResponseParser;
import org.apache.solr.client.solrj.response.UpdateResponse;
import org.apache.solr.common.SolrInputDocument;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.stereotype.Component;
import org.xml.sax.ContentHandler;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

@Component
class SolrIndexRunner implements CommandLineRunner {

    private SolrClient client;
    private long start = System.currentTimeMillis();
    private final String zkEnsemble = "http://localhost:2181";
    private AutoDetectParser autoParser;
    private Collection docList = new ArrayList();
    private int totalTika = 0;
    private int totalSql = 0;
    @Override
    public void run(String... args) throws Exception {
        System.out.println("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
        try {
            solrIndexRunner("http://localhost:8983/solr");

            doSqlDocuments();

            endIndexing();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private void solrIndexRunner(String url) throws IOException, SolrServerException {
        HttpSolrClient client = new HttpSolrClient.Builder("http://localhost:8983/solr/techproducts").build();
        System.out.println("client"+client);
        // Solr 8 uses a builder pattern here.
//        client = new CloudSolrClient.Builder(Collections.singletonList(zkEnsemble), Optional.empty())
//                .withConnectionTimeout(5000)
//                .withSocketTimeout(10000)
//                .build();


        // binary parser is used by default for responses
        client.setParser(new XMLResponseParser());

        // One of the ways Tika can be used to attempt to parse arbitrary files.
        autoParser = new AutoDetectParser();
    }
    // Just a convenient place to wrap things up.
    private void endIndexing() throws IOException, SolrServerException {
        if ( docList.size() > 0) { // Are there any documents left over?
            client.add(docList, 300000); // Commit within 5 minutes
        }
        client.commit(); // Only needs to be done at the end,
        // commitWithin should do the rest.
        // Could even be omitted
        // assuming commitWithin was specified.
        long endTime = System.currentTimeMillis();
        log("Total Time Taken: " + (endTime - start) +
                " milliseconds to index " + totalSql +
                " SQL rows and " + totalTika + " documents");
    }
    private static void log(String msg) {
        System.out.println(msg);
    }
    /**
     * ***************************SQL processing here
     */
    private void doSqlDocuments() throws SQLException {
        Connection con = null;
        try {
            Class.forName("com.mysql.jdbc.Driver").newInstance();
            log("Driver Loaded......");

            con = DriverManager.getConnection("jdbc:mysql://localhost:3306/glue?characterEncoding=UTF-8&serverTimezone=UTC&"
                    + "user=root&password=root");

            Statement st = con.createStatement();
            ResultSet rs = st.executeQuery("select id, servername, msg from WLSLOG");

            while (rs.next()) {
                // DO NOT move this outside the while loop
                SolrInputDocument doc = new SolrInputDocument();
                String id = rs.getString("id");
                String servername = rs.getString("servername");
                String msg = rs.getString("msg");
                System.out.println("msg"+msg);
                doc.addField("id", id);
                doc.addField("servername", servername);
                doc.addField("msg", msg);

                docList.add(doc);
                ++totalSql;

                // Completely arbitrary, just batch up more than one
                // document for throughput!
                if ( docList.size() > 1000) {
                    // Commit within 5 minutes.
                    UpdateResponse resp = client.add(docList, 300000);
                    if (resp.getStatus() != 0) {
                        log("Some horrible error has occurred, status is: " +
                                resp.getStatus());
                    }
                    docList.clear();
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            if (con != null) {
                con.close();
            }
        }
    }
    private void doTikaDocuments(File root) throws IOException, SolrServerException {

        // Simple loop for recursively indexing all the files
        // in the root directory passed in.
        for (File file : root.listFiles()) {
            if (file.isDirectory()) {
                doTikaDocuments(file);
                continue;
            }
            // Get ready to parse the file.
            ContentHandler textHandler = new BodyContentHandler();
            Metadata metadata = new Metadata();
            ParseContext context = new ParseContext();
            // Tim Allison noted the following, thanks Tim!
            // If you want Tika to parse embedded files (attachments within your .doc or any other embedded
            // files), you need to send in the autodetectparser in the parsecontext:
            // context.set(Parser.class, autoParser);

            InputStream input = new FileInputStream(file);

            // Try parsing the file. Note we haven't checked at all to
            // see whether this file is a good candidate.
            try {
                autoParser.parse(input, textHandler, metadata, context);
            } catch (Exception e) {
                // Needs better logging of what went wrong in order to
                // track down "bad" documents.
                log(String.format("File %s failed", file.getCanonicalPath()));
                e.printStackTrace();
                continue;
            }
            // Just to show how much meta-data and what form it's in.
            dumpMetadata(file.getCanonicalPath(), metadata);

            // Index just a couple of the meta-data fields.
            SolrInputDocument doc = new SolrInputDocument();

            doc.addField("id", file.getCanonicalPath());

            // Crude way to get known meta-data fields.
            // Also possible to write a simple loop to examine all the
            // metadata returned and selectively index it and/or
            // just get a list of them.
            // One can also use the Lucidworks field mapping to
            // accomplish much the same thing.
            String author = metadata.get("Author");

            if (author != null) {
                doc.addField("author", author);
            }

            doc.addField("text", textHandler.toString());

            docList.add(doc);
            ++totalTika;

            // Completely arbitrary, just batch up more than one document
            // for throughput!
            if ( docList.size() >= 1000) {
                // Commit within 5 minutes.
                UpdateResponse resp = client.add(docList, 300000);
                if (resp.getStatus() != 0) {
                    log("Some horrible error has occurred, status is: " +
                            resp.getStatus());
                }
                docList.clear();
            }
        }
    }
    // Just to show all the metadata that's available.
    private void dumpMetadata(String fileName, Metadata metadata) {
        log("Dumping metadata for file: " + fileName);
        for (String name : metadata.names()) {
            log(name + ":" + metadata.get(name));
        }
        log("nn");
    }
}

