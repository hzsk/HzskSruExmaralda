/**
 * @file DBSearchResult.java
 * @copyright Hamburger Zentrum für Sprach Korpora.
 */

package de.uni_hamburg.corpora.sru;

import java.sql.ResultSet;
import java.util.ArrayList;

/**
 * Set of results from database query.
 * @author Z2
 * @author tpirinen
 */
public class DBSearchResult {


    /** Simple struct with getters holding matching parts of corpus. */
     public static class Record {
         private String hit;
         private String left;
         private String right;
         private String source;
         private String pid;
         private String page;

         /** Create a simple match in text with necessary informations. */
         public Record(String hit, String left, String right,
                 String source, String pid, String page){
             this.hit = hit;
             this.left = left;
             this.right = right;
             this.source = source;
             this.pid = pid;
             this.page = page;
         }


         /**
         * @return the hit
         */
        public String getHit() {
            return hit;
        }

        /**
         * @return the left
         */
        public String getLeft() {
            return left;
        }

        /**
         * @return the right
         */
        public String getRight() {
            return right;
        }

        /**
         * @return the source
         */
        public String getSource() {
            return source;
        }

        /**
         * @return the pid
         */
        public String getPID() {
            return pid;
        }

        /**
         * @return the page
         */
        public String getPage() {
            return page;
        }

    }

    private int maxLength;
    private int totalLength;
    private ArrayList<Record> records;

     public DBSearchResult(int maxLength){

         this.maxLength = maxLength;
         records = new ArrayList<Record>();
    }

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
