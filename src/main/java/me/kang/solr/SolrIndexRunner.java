package me.kang.solr;

import lombok.extern.slf4j.Slf4j;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.request.UpdateRequest;
import org.apache.solr.client.solrj.response.UpdateResponse;
import org.apache.solr.common.SolrInputDocument;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class SolrIndexRunner implements ApplicationRunner {

    private long start = System.currentTimeMillis();

    @Value("${data.solr.host}")
    String solrUrl;

    @Value("${datasource.url}")
    String mysqlUrl;

    @Value("${datasource.driver-class-name}")
    String driverClassName;

    @Value("${datasource.username}")
    String userName;

    @Value("${datasource.password}")
    String passWord;

// 주키퍼를 사용할때는 아래에 3대의 주키퍼서버를 콤마 구분자로 정의한다.
//    private final String zkEnsemble = "http://localhost:2181";

    private Collection docList = new ArrayList();

    private Connection getConnection(){
        Connection conn=null;
        try {
            Class.forName(driverClassName).newInstance();
            conn = DriverManager.getConnection(mysqlUrl + "user="+userName+"&password="+passWord);
        } catch (Exception e) {

        }
        return conn;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        System.out.println("======================================================");
        System.out.println("ApplicationRunner - ApplicationArguments ");
        System.out.println("NonOption Arguments : " + args.getNonOptionArgs());
        System.out.println("Option Arguments Names : " + args.getOptionNames());
        System.out.println("key1의 value : " + args.getOptionValues("action"));
        System.out.println("key2의 value : " + args.getOptionValues("table_name"));
        System.out.println("======================================================");

        switch (args.getOptionValues("action").get(0)) {
            case "insert":
                insertSeperator(args.getOptionValues("table_name").get(0));
                break;
            case "update":
                updateSeperator(args.getOptionValues("table_name").get(0));
                break;
            default:
                // System.out.println("기타"); break;
        }

    }



    public void insertSeperator(String table_name) {
        log.info("~~~~~~~~~insertSeperator~~~~~~~~~~~~~~~"+table_name);
        switch (table_name) {
            case "api_glue_job":
                insertJob(table_name);
                break;
            case "api_glue_trigger":
                //insretTrigger(table_name);
                break;
        }
    }

    public void updateSeperator(String table_name) {
        switch (table_name) {
            case "api_glue_job":
                updateJob(table_name);
                break;
            case "api_glue_trigger":
                //updateTrigger(table_name);
                break;
        }
    }

    private ResultSet getJobResultSet(Connection conn,Statement stmt, ResultSet rs) {
        try {
            String query="select job_id,job_name,script_location from api_glue_job";
            conn = getConnection();
            log.info("conn"+conn);
            stmt = conn.createStatement();
            rs = stmt.executeQuery(query);
        } catch (SQLException e) {
            log.error(e.toString());
        }

        return rs;
    }

    public void insertJobDocuments(ResultSet rs, String table_name) throws SQLException, IOException, SolrServerException {
//        HttpSolrClient client = new HttpSolrClient.Builder(solrUrl+"/"+table_name).build();
        // Using a ZK Host String
//        String zkHostString = "zkServerA:2181,zkServerB:2181,zkServerC:2181/solr";
//        SolrClient solr = new CloudSolrClient.Builder().withZkHost(zkHostString).build();
        HttpSolrClient client = new HttpSolrClient.Builder(solrUrl+"/"+"kang").build();
        System.out.println("client"+client);
        client.deleteByQuery( "*:*" );
        while (rs.next()) {
            //doc 생성은 loop 안애 있어야 함.
            SolrInputDocument doc = new SolrInputDocument();
            String job_id = rs.getString("job_id");
            String job_name = rs.getString("job_name");
            String script_location = rs.getString("script_location");
            log.info("job_id"+job_id);
            log.info("job_name"+job_name);
            doc.addField("job_id", job_id);
            doc.addField("job_name", job_name);
            doc.addField("script_location", script_location);

            docList.add(doc);
            if ( docList.size() > 0) {
                // Commit within 5 minutes.
                UpdateResponse resp=client.add(docList, 300000);

                if (resp.getStatus() != 0) {
                    log.error("Some horrible error has occurred, status is: " + resp.getStatus());
                }
                client.commit();
                docList.clear();
            }
        }
    }

    public void updateJobDocuments(ResultSet rs, String table_name) throws SQLException, IOException, SolrServerException {
//        HttpSolrClient client = new HttpSolrClient.Builder(solrUrl+"/"+table_name).build();
        HttpSolrClient client = new HttpSolrClient.Builder(solrUrl+"/"+"kang").build();
        System.out.println("client"+client);

        UpdateRequest updateRequest = new UpdateRequest();
        updateRequest.setAction( UpdateRequest.ACTION.COMMIT, false, false);
        while (rs.next()) {
            //doc 생성은 loop 안애 있어야 함.
            SolrInputDocument doc = new SolrInputDocument();
            String job_id = rs.getString("job_id");
            String job_name = rs.getString("job_name");
            String script_location = rs.getString("script_location");

            doc.addField("job_id", job_id);
            HashMap<String, Object> value = new HashMap <String, Object> ();
            value.put("set", "PY_PY_PRINT");
            doc.addField("job_name",value);
//            value.put("set", script_location);
//            doc.addField("script_location",value);
            updateRequest.add(doc);
        }




        UpdateResponse response = updateRequest.process(client);
        System.out.println("Documents Updated");
        if (response.getStatus() != 0) {
            log.error("Some horrible error has occurred, status is: " + response.getStatus());
        }
        client.commit();
        docList.clear();
    }

    private void insertJob(String table_name){

        Connection conn =null;
        Statement stmt=null;
        ResultSet rs=null;

        try {

            rs = getJobResultSet(conn, stmt, rs);
            insertJobDocuments(rs, table_name);

        } catch (SQLException e) {
            log.info(e.toString());
        } catch (SolrServerException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (conn != null) try{conn.close();}catch (Exception e){log.error(e.toString());}
            if (stmt != null) try{stmt.close();}catch (Exception e){log.error(e.toString());}
            if (rs != null) try{rs.close();}catch (Exception e){log.error(e.toString());}
        }
    }



    private void updateJob(String table_name){
        Connection conn =null;
        Statement stmt=null;
        ResultSet rs=null;

        try {

            rs = getJobResultSet(conn, stmt, rs);
            updateJobDocuments(rs,table_name);

        } catch (SQLException e) {
//            log.info(e.toString());
        } catch (SolrServerException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            if (conn != null) try{conn.close();}catch (Exception e){log.error(e.toString());}
            if (stmt != null) try{stmt.close();}catch (Exception e){log.error(e.toString());}
            if (rs != null) try{rs.close();}catch (Exception e){log.error(e.toString());}
        }
    }


}

