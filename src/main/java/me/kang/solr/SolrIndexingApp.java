package me.kang.solr;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Component;

@SpringBootApplication
public class SolrIndexingApp {
    public static void main(String[] args) {
        SpringApplication.run(SolrIndexingApp.class, args);
    }
}


