/**
 * @file DBDescriptionResult.java
 * @copyright Hamburger Zentrum für Sprach Korpora
 */

package de.uni_hamburg.corpora.sru;

import eu.clarin.sru.server.SRUDiagnosticList;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.net.URI;
import java.util.List;

/**
 * A result for explain request extracted from the database.
 * @author Z2
 * @author tpirinen
 */
public class DBDescriptionResult {



    /** Simple struct wit getters holding a single entry of information. */
    public static class Record {
        private String guid;
        private String name;

        private String pid;
        private Map<String,String> title;
        private Map<String,String> description;
        private URI landingPageURI;
        private List<String> languages;

        /** Create a record with all necessary informations. */
        public Record(String guid, String name, String pid,
                Map<String, String> title, Map<String,String> descriptions,
                URI landingPageURI,
                List<String> languages) {
            this.guid = guid;
            this.name = name;
            this.pid = pid;
            this.title = title;
            this.description = description;
            this.landingPageURI = landingPageURI;
            this.languages = languages;

        }

        public String getPid() {
            return pid;
        }

        public String getGUID() {
            return guid;
        }

        public String getName() {
            return name;
        }

        public String getPID() {
            return pid;
        }

        public Map<String,String> getTitle() {
            return title;
        }

        public Map<String,String> getDescription() {
            return description;
        }

        public URI getLandingPageURI() {
            return landingPageURI;
        }

        public List<String> getLanguages() {
            return languages;
        }


    }

    private int maxLength;
    private int totalLength;
    private ArrayList<Record> records;

    /** Create a result set holding at most maxLength results. */
    public DBDescriptionResult(int maxLength) {
        this.maxLength = maxLength;
        records = new ArrayList<Record>();
    }

    /** Initialise results with result set from a database. */
    public boolean init(ResultSet rs){
        boolean succ = false;
        return succ;
    }

     public int getLength(){
         return records.size();
     }

     public int getMaxLength(){
         return maxLength;
     }

      public int getTotalLength(){
         return totalLength;
     }

      public void setTotalLength(int totalLength){
         this.totalLength = totalLength;
     }

     public Record getRecordAt(int pos){

         return records.get(pos);
     }


     public boolean addRecord(Record rec){
         return records.add(rec);
     }

}
