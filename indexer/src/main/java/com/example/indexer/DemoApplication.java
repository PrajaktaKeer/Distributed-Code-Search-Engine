package com.example.indexer;

import com.example.indexer.lucene.LuceneSearcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DemoApplication {

    @Autowired
    LuceneSearcher searcher;

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

//    @Override
//    public void run(String... args) throws Exception {
//        Thread.sleep(8000); // wait for consumer
//        searcher.search("pet");
//    }

}
