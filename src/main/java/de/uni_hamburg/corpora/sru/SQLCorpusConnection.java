/**
 * @file SQLCorpusConnection.java
 * @copyright Hamburger Zentrum für Sprach Korpora
 */
package de.uni_hamburg.corpora.hzsksru;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;
import java.util.Collections;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * Connects to Uni-hamburg’s corpora DB and gets exact hits and resource infos.
 *
 * @author Z2
 * @author tpirinen
 *
 */
public class SQLCorpusConnection {

    private final int MAX_LAYERS = 10; // don't query more layers under text
    private DataSource datasource;

    private Connection conn;

    /** Initialise connection from context.
     *  The default context in public repo is empty user for localhost, change
     *  it when deploying.
     */
    public SQLCorpusConnection() {
        try {
            Context initCtx = new InitialContext();
            Context envCtx = (Context) initCtx.lookup("java:comp/env");
            DataSource ds = (DataSource) envCtx.lookup("jdbc/corpora");
            datasource = ds;
        } catch (NamingException ex) {
            Logger.getLogger(SQLCorpusConnection.class.getName())
                .log(Level.SEVERE, null, ex);
        }
    }

    /** Retrieve explainable information from database. */
    public DBDescriptionResult getResourceInfos(int maximumRecords)
            throws SQLException {
        DBDescriptionResult sr = new DBDescriptionResult(maximumRecords);
        this.conn = null;
        try {
            this.conn = datasource.getConnection();
            // XXX: this isn't ideal looping yet
            String descSQL = "SELECT name, corpus_location, " +
                "ex_corpus.corpus_guid, " +
                "corpora.ex_corpus_desc_item.attribute, " +
                "corpora.ex_corpus_desc_item.value FROM ex_corpus " +
                "INNER JOIN ex_corpus_desc_item ON " +
                " ex_corpus_desc_item.corpus_guid = ex_corpus.corpus_guid;";
            Statement stmt = conn.createStatement();
            stmt.setFetchSize(maximumRecords);
            ResultSet rs = stmt.executeQuery(descSQL);
            int rscount = 0;
            String name = "";
            String guid = "";
            String pid = "";
            Map<String,String> titles = new HashMap<String,String>();
            Map<String,String> descriptions = new HashMap<String,String>();
            URI landingPageURI = new URI("http://corpora.uni-hamburg.de/");
            List<String> languages = new ArrayList<String>();
            // collect stuff from joints like so...
            //  (guid, name) String pid,
            //  Map<String, String> title, Map<String,String> descriptions,
            //     URI landingPageURI,
            //    List<String> languages) {
 
            while (rs.next()) {
                String recordName = rs.getString("name");
                String recordGuid = rs.getString("corpus_guid");
                String location = rs.getString("corpus_location");
                if (guid.equals("")) {
                    // this is first record
                    guid = recordGuid;
                    name = recordName;
                    landingPageURI = new URI(location);
                }
                else if (!recordGuid.equals(guid)) {
                    if (rscount >= maximumRecords) {
                        break;
                    }
                    DBDescriptionResult.Record rec = 
                        new DBDescriptionResult.Record(name, guid,
                                pid, titles, descriptions, landingPageURI,
                                languages);
                    sr.addRecord(rec);
                    guid = recordGuid;
                    name = recordName;
                    landingPageURI = new URI(location);
                }
                String attribute = rs.getString("attribute");
                String value = rs.getString("value");
                if (attribute.equals("pid")) {
                    pid = value;
                } else if (attribute.startsWith("title")) {
                    String[] parts = attribute.split(":");
                    titles.put(parts[1], value);
                } else if (attribute.startsWith("description")) {
                    String[] parts = attribute.split(":");
                    descriptions.put(parts[1], value);
                } else if (attribute.equals("landing_page_uri")) {
                    landingPageURI = new URI(value);
                } else if (attribute.equals("languages")) {
                    // XXX: there's probs some better java way for this
                    for (String lang : value.split(",")) {
                        languages.add(lang);
                    }
                }
            }
            // tail add last record since it's added in name-changed if clause
            DBDescriptionResult.Record rec = 
                new DBDescriptionResult.Record(name, guid,
                        pid, titles, descriptions, landingPageURI,
                        languages);
            sr.addRecord(rec);
        } catch (SQLException sqle) {
            String sqlState = sqle.getSQLState();
            Logger.getLogger(SQLCorpusConnection.class.getName())
                .log(Level.SEVERE, "EXPLAIN: SQL state:" + sqlState, sqle);
        } catch (URISyntaxException use) {
            Logger.getLogger(SQLCorpusConnection.class.getName())
                .log(Level.SEVERE, null, use);
        } finally {
            if (this.conn != null) {
                this.conn.close();
                this.conn = null;
            }
        }
        return sr;
    }

    /** Retrieve search results from database. */
    public AdvancedSearchResultSet query(String queryString, int startRecord,
            int maximumRecords)
            throws SQLException {
        HZSKQuery query = new HZSKQuery(queryString);
        return query(query, startRecord, maximumRecords);
    }

    
    /** Retrieve advanced search results with segments and layers.
     *  Can be useful for simple queries too, this is to formulate a query that
     *  returns both text and segments, i.e. an advanced view too.
     */
    public AdvancedSearchResultSet query(HZSKQuery query,
            int startRecord, int maximumRecords)
            throws SQLException {
        AdvancedSearchResultSet sr = new 
            AdvancedSearchResultSet(maximumRecords);
        // holding on to 
        this.conn = null;
        PreparedStatement prepStmt = null;
        PreparedStatement segStmt = null;
        PreparedStatement subsegStmt = null;
        ResultSet results = null;
        ResultSet segs = null;
        ResultSet subsegs = null;
        try {
            this.conn = datasource.getConnection();
            if (query.hasTextSearch() && 
                    !(query.hasPosSearch() || query.hasLemmaSearch())) {
                // find text then all hanging segments
                // XXX: if looking for disfluencies change
                prepStmt = prepareTextQuery(query, -1, true);
                prepStmt.setFetchSize(maximumRecords);
                results = prepStmt.executeQuery();
                int rsPos = 0;
                // for each result of text search get child segments
                while (results.next()) {
                    int segment_id = results.getInt("segment_id");
                    String searchString = results.getString("cdata");
                    String source = results.getString("name");
                    String pid = results.getString("file_url");
                    String page = results.getString("avail_url");
                    // FIXME: maybe tli_?
                    int start = results.getInt("char_s");
                    int end = results.getInt("char_e");
                    AdvancedSearchResultSegment whole = new 
                        AdvancedSearchResultSegment(searchString, start, end);
                    List<AdvancedSearchResultSegment> highlights =
                        AdvancedSearchResult.highlightSearch(whole, query);
                    // query subsegments then
                    segStmt = prepareSegmentQuery(query, segment_id, 
                            maximumRecords);
                    segStmt.setFetchSize(MAX_LAYERS);
                    segs = segStmt.executeQuery();
                    AdvancedSearchResult rec = new 
                        AdvancedSearchResult(whole, highlights, source, pid,
                                page, start, end);
                    List<AdvancedSearchResultSegment> segments =
                            new ArrayList<AdvancedSearchResultSegment>();
                    String segtype = "";
                    int last_e = -1;
                    while (segs.next()) {
                        String ann = 
                                segs.getString("ex_annotation_segment.cdata");
                        String segtext = segs.getString("ex_segment.cdata");
                        String newtype = segs.getString("name");
                        // FIXME: maybe tli_?
                        int segstart = segs.getInt("ex_segment.char_s");
                        int segend = segs.getInt("ex_segment.char_e");
                        if (segtype.equals("")) {
                            segtype = newtype;
                        } else if (!newtype.equals(segtype) || 
                                (segstart < last_e)) {
                            if (rec.getChildLayers().containsKey(segtype)) {
                                List<AdvancedSearchResultSegment> ex_segments = 
                                    rec.getChildLayers().get(segtype);
                                segments.addAll(ex_segments);
                                Collections.sort(segments);
                            }
                            rec.addChildLayer(segtype, segments);
                            segments = new 
                                ArrayList<AdvancedSearchResultSegment>();
                            segtype = newtype;
                        }
                        segments.add(new AdvancedSearchResultSegment(ann,
                                    segtext, segstart, segend));
                        last_e = segend;
                    }
                    if (!segtype.equals("")) {
                        if (rec.getChildLayers().containsKey(segtype)) {
                            List<AdvancedSearchResultSegment> 
                                ex_segments = rec.getChildLayers().get(segtype);
                            segments.addAll(ex_segments);
                            Collections.sort(segments);
                        }
                        rec.addChildLayer(segtype, segments);
                    }
                    sr.addRecord(rec);
                    rsPos++;
                    if (rsPos >= maximumRecords) {
                        break;
                    }
                } // while records in result
                sr.setTotalLength(rsPos);
            } else if (query.hasTextSearch() && (query.hasLemmaSearch() ||
                        query.hasPosSearch())) {
                // find text then all hanging segments
                // XXX: if looking for disfluencies change
                prepStmt = prepareTextQuery(query, -1, true);
                prepStmt.setFetchSize(maximumRecords);
                results = prepStmt.executeQuery();
                int rsPos = 0;
                // for each result of text search get child segments
                while (results.next()) {
                    int segment_id = results.getInt("segment_id");
                    String searchString = results.getString("cdata");
                    String source = results.getString("name");
                    String pid = results.getString("file_url");
                    String page = results.getString("avail_url");
                    // FIXME: maybe tli_?
                    int start = results.getInt("char_s");
                    int end = results.getInt("char_e");
                    segStmt = prepareSegmentQuery(query, segment_id, 
                            maximumRecords);
                    segStmt.setFetchSize(MAX_LAYERS);
                    segs = segStmt.executeQuery();
                    boolean hadSegments = false;
                    // find text match highlights
                    AdvancedSearchResultSegment whole = new 
                        AdvancedSearchResultSegment(searchString, start, end);
                    List<AdvancedSearchResultSegment> highlights =
                        AdvancedSearchResult.highlightSearch(whole, query);
                    // see if segments that match
                    AdvancedSearchResult rec = new 
                        AdvancedSearchResult(whole, highlights, source, pid,
                                page, start, end);
                    List<AdvancedSearchResultSegment> segments =
                        new ArrayList<AdvancedSearchResultSegment>();
                    String segtype = "";
                    int last_e = -1;
                    while (segs.next()) {
                        hadSegments = true;
                        String ann = 
                            segs.getString("ex_annotation_segment.cdata");
                        String segtext = segs.getString("ex_segment.cdata");
                        String newtype = segs.getString("name");
                        // FIXME: maybe tli_?
                        int segstart = segs.getInt("ex_segment.char_s");
                        int segend = segs.getInt("ex_segment.char_e");
                        if (segtype.equals("")) {
                            segtype = newtype;
                        } else if (!newtype.equals(segtype) || 
                                (segstart < last_e)) {
                            if (rec.getChildLayers().containsKey(segtype)) {
                                List<AdvancedSearchResultSegment> 
                                    ex_segments = 
                                    rec.getChildLayers().get(segtype);
                                segments.addAll(ex_segments);
                                Collections.sort(segments);
                            }
                            rec.addChildLayer(segtype, segments);
                            segments = new 
                                ArrayList<AdvancedSearchResultSegment>();
                            segtype = newtype;
                        }
                        segments.add(new AdvancedSearchResultSegment(ann,
                                    segtext, segstart, segend));
                        last_e = segend;
                    }
                    if (!hadSegments) {
                        continue;
                    }
                    else if (!segtype.equals("")) {
                        if (rec.getChildLayers().containsKey(segtype)) {
                            List<AdvancedSearchResultSegment> 
                                ex_segments = 
                                rec.getChildLayers().get(segtype);
                            segments.addAll(ex_segments);
                            Collections.sort(segments);
                        }
                        rec.addChildLayer(segtype, segments);
                    }
                    sr.addRecord(rec);
                    rsPos++;
                    if (rsPos >= maximumRecords) {
                        break;
                    }
                } // while records in result
                sr.setTotalLength(rsPos);

            } else if (!query.hasTextSearch()) {
                // actually this query keeps running out of memory :-/
                // we have no main text search, start from segments up
                segStmt = prepareSegmentQuery(query, -1, maximumRecords * 10);
                segStmt.setFetchSize(maximumRecords);
                segs = segStmt.executeQuery();
                int rsPos = 0;
                // for each result of segment search get parent layer
                Set<String> usedIds = new HashSet<String>();
                while (segs.next()) {
                    int parentId = segs.getInt("parent");
                    int matchstart = segs.getInt("char_s");
                    int matchend = segs.getInt("char_e");
                    String ann = 
                        segs.getString("ex_annotation_segment.cdata");
                    String segtext = segs.getString("ex_segment.cdata");
                    String segtype = segs.getString("name");
                    prepStmt = prepareTextQuery(parentId, true);
                    prepStmt.setFetchSize(2);
                    results = prepStmt.executeQuery();
                    if (!results.next()) {
                        System.out.println("DEBUG: Parentless segment");
                        continue;
                        // we couldn't find parent text segment :-(
                    }
                    int segment_id = results.getInt("segment_id");
                    String searchString = results.getString("cdata");
                    String source = results.getString("name");
                    String pid = results.getString("file_url");
                    String page = results.getString("avail_url");
                    int start = results.getInt("char_s");
                    int end = results.getInt("char_e");
                    if (usedIds.contains(pid)) {
                        continue;
                    } else {
                        usedIds.add(pid);
                    }
                    AdvancedSearchResultSegment whole = new 
                        AdvancedSearchResultSegment(searchString, start, end);
                    // find all sub-segments that match advanced search?
                    //subsegStmt = prepareSegmentQuery(query, segment_id);
                    //subsegs = subsegStmt.executeQuery();
                    if (rsPos >= startRecord && 
                            sr.getLength() < maximumRecords) {
                        // XXX: match highlight to search terms
                             List<AdvancedSearchResultSegment> highlights =
                        AdvancedSearchResult.highlightSegments(whole, query);
                        AdvancedSearchResult rec = new 
                            AdvancedSearchResult(whole, highlights,
                                    source, pid, page, start, end);
                        // XXX: and add segements
                        sr.addRecord(rec);
                    } // results less than max
                    rsPos++;
                } // while segs
                sr.setTotalLength(rsPos);
            } // whether has text search
        } catch (SQLException sqle) {
            String sqlState = sqle.getSQLState();
            Logger.getLogger(SQLCorpusConnection.class.getName())
                .log(Level.SEVERE, "ADV: SQL state:" + sqlState, sqle);
        } finally {
            if (this.conn != null) {
                this.conn.close();
            }
            if (prepStmt != null) {
                prepStmt.close();
            }
            if (results != null) {
                results.close();
            }
            if (segStmt != null) {
                segStmt.close();
            }
            if (segs != null) {
                segs.close();
            }
            if (subsegStmt != null) {
                subsegStmt.close();
            }
            if (subsegs != null) {
                subsegs.close();
            }
        }
        return sr;
    }

    // XXX: might need to verify and commit and stuff
    /** Close database connections if needed. */
    public void close() {
        try {
            if (this.conn != null) {
                this.conn.close();
            }
        } catch (SQLException sqle) {
            String sqlState = sqle.getSQLState();
            Logger.getLogger(SQLCorpusConnection.class.getName())
                .log(Level.SEVERE, "CLOSE: SQL state:" + sqlState, sqle);
        }
    }

    private PreparedStatement prepareTextQuery(int id, boolean utteranceWord)
            throws SQLException {
        String searchSQL = "SELECT " +
            "segment_id, cdata, corpora.ex_segmented_transcription.name, " +
            "avail_url, file_url, char_s, char_e " + 
            "FROM corpora.ex_segment INNER JOIN " +
            "corpora.ex_segmented_transcription ON " +
            "corpora.ex_segmented_transcription.transcription_guid = " +
            "corpora.ex_segment.transcription_guid WHERE ";
        if (utteranceWord) {
            searchSQL += "corpora.ex_segment.name = 'HIAT:u' AND " +
                "corpora.ex_segment.segmentation = 'SpeakerContribution_Utterance_Word'";
        } else {
            searchSQL += "corpora.ex_segment.name = 'sc' AND " +
                "corpora.ex_segment.segmentation = 'SpeakerContribution_Event'";
        }
        if (id != -1) {
            searchSQL += " AND corpora.ex_segment.segment_id = ?";
        }
        PreparedStatement prepStmt = this.conn.prepareStatement(searchSQL);
        int qvar = 1;
        if (id != -1) {
            prepStmt.setInt(qvar, id);
            qvar++;
        }
        return prepStmt;
    }

    private PreparedStatement prepareTextQuery(String search, int id, 
            boolean utteranceWord) throws SQLException {
        String searchSQL = "SELECT " +
            "segment_id, cdata, corpora.ex_segmented_transcription.name, " +
            "avail_url, file_url, char_s, char_e " + 
            "FROM corpora.ex_segment INNER JOIN " +
            "corpora.ex_segmented_transcription ON " +
            "corpora.ex_segmented_transcription.transcription_guid = " +
            "corpora.ex_segment.transcription_guid WHERE ";
        if (utteranceWord) {
            searchSQL += "corpora.ex_segment.name = 'HIAT:u' AND " +
                "corpora.ex_segment.segmentation = 'SpeakerContribution_Utterance_Word'";
        } else {
            searchSQL += "corpora.ex_segment.name = 'sc' AND " +
                "corpora.ex_segment.segmentation = 'SpeakerContribution_Event'";
        }
        if (search != null) {
            searchSQL += " AND corpora.ex_segment.cdata LIKE ?";
        }
        if (id != -1) {
            searchSQL += " AND corpora.ex_segment.segment_id = ?";
        }
        PreparedStatement prepStmt = this.conn.prepareStatement(searchSQL);
        int qvar = 1;
        if (search != null) {
            prepStmt.setString(qvar, "%" + search + "%"); //Search expression
            qvar++;
        }
        if (id != -1) {
            prepStmt.setInt(qvar, id);
            qvar++;
        }
        return prepStmt;
    }

    private PreparedStatement prepareTextQuery(HZSKQuery query, int id,
            boolean utteranceWord) throws SQLException {
        String searchSQL = "SELECT " +
            "segment_id, cdata, corpora.ex_segmented_transcription.name, " +
            "avail_url, file_url, char_s, char_e " + 
            "FROM corpora.ex_segment INNER JOIN " +
            "corpora.ex_segmented_transcription ON " +
            "corpora.ex_segmented_transcription.transcription_guid = " +
            "corpora.ex_segment.transcription_guid WHERE ";
        if (utteranceWord) {
            searchSQL += "corpora.ex_segment.name = 'HIAT:u' AND " +
                "corpora.ex_segment.segmentation = 'SpeakerContribution_Utterance_Word'";
        } else {
            searchSQL += "corpora.ex_segment.name = 'sc' AND " +
                "corpora.ex_segment.segmentation = 'SpeakerContribution_Event'";
        }
        String delim = "AND corpora.ex_segment.cdata LIKE ?";
        for (String s : query.getTextSearches()) {
            searchSQL += delim;
            if (query.getCombinator() == HZSKQuery.ComboType.WHATEVER) {
                delim = " ";
            } else if (query.getCombinator() == HZSKQuery.ComboType.AND) {
                delim = " AND corpora.ex_segment.cdata LIKE ?";
            } else if (query.getCombinator() == HZSKQuery.ComboType.OR) {
                delim = " OR corpora.ex_segment.cdata LIKE ?";
            } else {
                delim = " ";
            }
        }
        if (id != -1) {
            searchSQL += " AND corpora.ex_segment.segment_id = ?";
        }
        PreparedStatement prepStmt = this.conn.prepareStatement(searchSQL);
        int qvar = 1;
        if (query.getCombinator() == HZSKQuery.ComboType.WHATEVER) {
            String singleQ = "";
            delim = "";
            for (String s : query.getTextSearches()) {
                singleQ += delim;
                singleQ += s;
                delim = " ";
            }
            prepStmt.setString(qvar, "%" + singleQ + "%");
            qvar++;
        } else {
            for (String s : query.getTextSearches()) {
                prepStmt.setString(qvar, "%" + s + "%"); //Search expression
                qvar++;
            }
        }
        if (id != -1) {
            prepStmt.setInt(qvar, id);
            qvar++;
        }
        return prepStmt;
    }

    private PreparedStatement prepareSegmentQuery(HZSKQuery query,
            int parentId, int limit) throws SQLException {
        String segSQL = "SELECT ex_annotation_segment.cdata, " + 
            "ex_annotation_segment.name, ex_segment.cdata, " +
            "ex_segment.parent, " +
            "ex_segment.char_s, ex_segment.char_e FROM " +
            "ex_annotation_segment JOIN " +
            "ex_segment_has_annotation ON " +
            "ex_segment_has_annotation.annotation_id = " +
            "ex_annotation_segment.annotation_id JOIN " +
            "ex_segment ON ex_segment_has_annotation.segment_id = " +
            "ex_segment.segment_id WHERE ";
        String delim = "";
        if (query.hasPosSearch()) {
            for (String s : query.getPosSearches()) {
                segSQL += delim;
                segSQL += "ex_annotation_segment.name = 'pos' AND (";
                List<String> poses = STTS2UDConverter.toSTTS(s);
                String subdelim = "";
                for (String pos : poses) {
                    segSQL += subdelim;
                    segSQL += "ex_annotation_segment.cdata = ?";
                    subdelim = " OR ";
                }
                segSQL += ")";
                delim = " AND ";
            }
        }
        else if (query.hasLemmaSearch()) {
            for (String s : query.getLemmaSearches()) {
                segSQL += delim;
                segSQL += "ex_annotation_segment.name = 'lemma' AND " +
                    "ex_annotation_segment.cdata = ?";
                delim = " AND ";
            }
        }
        if (parentId != -1) {
            segSQL += delim;
            segSQL += " ex_segment.parent = ?";
        }
        segSQL += " LIMIT " + limit;
        PreparedStatement segStmt = this.conn.prepareStatement(segSQL);
        int qvar = 1;
        if (query.hasPosSearch()) {
            for (String s : query.getPosSearches()) {
                List<String> poses = STTS2UDConverter.toSTTS(s);
                for (String pos : poses) {
                    segStmt.setString(qvar, pos);
                    qvar++;
                }
            }
        }
        else if (query.hasLemmaSearch()) {
            for (String s : query.getLemmaSearches()) {
                segStmt.setString(qvar, s);
                qvar++;
            }
        }
        if (parentId != -1) {
            segStmt.setInt(qvar, parentId);
            qvar++;
        }
        return segStmt;
    }
}
