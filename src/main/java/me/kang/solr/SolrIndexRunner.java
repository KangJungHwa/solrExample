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


    @Value("${extract.path}")
    String path;

    @Value("${extractor.user}")
    String user;

    @Value("${ext.path}")
    String extpath;


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
        System.out.println("==========================extpath============================"+extpath);
        System.out.println("==========================path============================"+path);
        System.out.println("==========================user============================"+user);
        System.out.println("======================================================");
        System.out.println("ApplicationRunner - ApplicationArguments ");
        System.out.println("NonOption Arguments : " + args.getNonOptionArgs());
        System.out.println("Option Arguments Names : " + args.getOptionNames());
        System.out.println("key1의 value : " + args.getOptionValues("table_name"));
        System.out.println("======================================================");

        String table_name=args.getOptionValues("table_name").get(0);
        genSolrIndex(table_name);
    }

    private ResultSet getJobResultSet(Connection conn,Statement stmt, ResultSet rs,String table_name) {
        try {
            String query = null;
            if (table_name.equals("cat_tab_mas")){
                query = "select table_id,table_nm,table_desc, table_tag from cat_tab_mas";
            }else{
                query = "select word_id, synonym from cat_std_word";
            }
            conn = getConnection();

            stmt = conn.createStatement();
            rs = stmt.executeQuery(query);
        } catch (SQLException e) {
            log.error(e.toString());
        }

        return rs;
    }

    public void insertCatTabMasDocuments(ResultSet rs, String table_name) throws SQLException, IOException, SolrServerException {

        /*
           Using a ZK Host String
           String zkHostString = "zkServerA:2181,zkServerB:2181,zkServerC:2181/solr";
           SolrClient solr = new CloudSolrClient.Builder().withZkHost(zkHostString).build();
        */
        HttpSolrClient client = new HttpSolrClient.Builder(solrUrl+"/"+table_name).build();
        System.out.println("client"+client);

        client.deleteByQuery( "*:*" );
        while (rs.next()) {
            //doc 생성은 loop 안애 있어야 함.
            SolrInputDocument doc = new SolrInputDocument();

            String table_id = rs.getString("table_id");
            String table_nm = rs.getString("table_nm");
            String table_desc = rs.getString("table_desc");
            String table_tag = rs.getString("table_tag");
            log.info("table_nm"+table_nm);
            log.info("table_desc"+table_desc);
            log.info("table_tag"+table_tag);

            doc.addField("table_id", table_id);
            doc.addField("table_nm", table_nm);
            doc.addField("table_desc", table_desc);
            doc.addField("table_tag", table_tag);

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

    public void insertStdWordDocuments(ResultSet rs, String table_name) throws SQLException, IOException, SolrServerException {

        /*
           Using a ZK Host String
           String zkHostString = "zkServerA:2181,zkServerB:2181,zkServerC:2181/solr";
           SolrClient solr = new CloudSolrClient.Builder().withZkHost(zkHostString).build();
        */
        HttpSolrClient client = new HttpSolrClient.Builder(solrUrl+"/"+table_name).build();
        System.out.println("client"+client);

        client.deleteByQuery( "*:*" );
        while (rs.next()) {
            //doc 생성은 loop 안애 있어야 함.
            SolrInputDocument doc = new SolrInputDocument();

            String word_id = rs.getString("word_id");
            String synonym = rs.getString("synonym");

            log.info("word_id"+word_id);
            log.info("synonym"+synonym);

            doc.addField("word_id", word_id);
            doc.addField("synonym", synonym);


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


    private void genSolrIndex(String table_name){

        Connection conn =null;
        Statement stmt=null;
        ResultSet rs=null;

        try {

            rs = getJobResultSet(conn, stmt, rs, table_name);
            if (table_name.equals("cat_tab_mas")) {
                insertCatTabMasDocuments(rs, table_name);
            }else{
                insertStdWordDocuments(rs, table_name);
            }

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

}

