/**
 * @file AnnisConnection.java
 * @copyright Hamburger Zentrum für Sprach Korpora
 */
package de.uni_hamburg.corpora.sru;

import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Comparator;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.xml.sax.SAXException;

/**
 * Connects to Uni-hamburg’s annis corpora DB and gets exact hits and resource
 * infos.
 *
 * @author tpirinen
 *
 */
public class AnnisConnection {

    public class SortAnnisIDComparator implements Comparator<String> {
        @Override
        public int compare(String lhs, String rhs) {
            Integer lhsID = Integer.parseInt(lhs.substring(4));
            Integer rhsID = Integer.parseInt(rhs.substring(4));
            return lhsID.compareTo(rhsID);
        }
    }

    // hardcoded now...
    static final String ANNIS_URL_PREFIX="http://localhost:5711";
    static final String
        ANNIS_GUI_PREFIX="http://localhost:8080/annis-gui-3.4.3/";


    /** "Creates" a connection to our annis server.
     * Doesn't actually do anything with network since it's lightweight http
     * connections created on the fly.
     */
    public AnnisConnection() {
    }

    /** Retrieve search results from database. */
    public AdvancedSearchResultSet query(String query, int startRecord,
            int maximumRecords) {
        return query(new HZSKQuery(query), startRecord, maximumRecords);
    }

    /** Retrieve advanced search results with segments and layers.
     *  Can be useful for simple queries too, this is to formulate a query that
     *  returns both text and segments, i.e. an advanced view too.
     */
    public AdvancedSearchResultSet query(HZSKQuery query,
            int startRecord, int maximumRecords) {
        AdvancedSearchResultSet results = new
            AdvancedSearchResultSet(maximumRecords);
        try {
            if (query.hasTextSearch() &&
                    !(query.hasPosSearch() || query.hasLemmaSearch())) {
                // count
                URL countURL = new URL(ANNIS_URL_PREFIX +
                        "/annis/query/search/count?q=" +
                        prepareTextQuery(query) +
                        "&corpora=pcc2");
                Document countDoc = getDocumentFromURL(countURL);
                NodeList countElements =
                    countDoc.getElementsByTagName("matchAndDocumentCount");
                if (countElements.getLength() != 1) {
                    // parsing error
                    System.out.println("DEBUG: count elements " +
                            countElements.getLength());
                }
                NodeList countChildren = countElements.item(0).getChildNodes();
                if (countChildren.getLength() != 2) {
                    // parsing error
                    System.out.println("DEBUG: count children " +
                            countElements.getLength());
                }
                Node docCountElement = countChildren.item(0);
                NodeList texts = docCountElement.getChildNodes();
                int docs = -1;
                for (int i = 0; i < texts.getLength(); i++) {
                    Node maybecount = texts.item(i);
                    if (maybecount.getNodeType() == Node.TEXT_NODE) {
                        Text counttext = (Text) maybecount;
                        docs = Integer.parseInt(counttext.getWholeText());
                        break;
                    }
                }
                Node matchCountElement = countChildren.item(1);
                texts = matchCountElement.getChildNodes();
                int matchcount = -1;
                for (int i = 0; i < texts.getLength(); i++) {
                    Node maybecount = texts.item(i);
                    if (maybecount.getNodeType() == Node.TEXT_NODE) {
                        Text counttext = (Text) maybecount;
                        matchcount = Integer.parseInt(counttext.getWholeText());
                        break;
                    }
                }
                // find
                URL queryURL = new URL(ANNIS_URL_PREFIX +
                        "/annis/query/search/find?q=" +
                        prepareTextQuery(query) +
                        "&corpora=pcc2");
                Document queryDoc = getDocumentFromURL(queryURL);
                NodeList matchgroups = queryDoc.getChildNodes();
                List<String> matchIds = new ArrayList<String>();
                for (int i = 0; i < matchgroups.getLength(); i++) {
                    Node maybeMatchgroup = matchgroups.item(i);
                    if (maybeMatchgroup.getNodeName().equals("match-group")) {
                        NodeList matches = maybeMatchgroup.getChildNodes();
                        for (int j = 0; j < matches.getLength(); j++) {
                            NodeList matchstuff =
                                matches.item(j).getChildNodes();
                            for (int k = 0; k < matchstuff.getLength(); k++) {
                                Node n = matchstuff.item(k);
                                if (n.getNodeName().equals("id")) {
                                    matchIds.add(getTextContent(n));
                                }
                                if (matchIds.size() >= maximumRecords) {
                                    break;
                                }
                            }
                            if (matchIds.size() >= maximumRecords) {
                                break;
                            }
                        }
                        if (matchIds.size() >= maximumRecords) {
                            break;
                        }
                    }
                    if (matchIds.size() >= maximumRecords) {
                        break;
                    }
                }
                // fetch matches??
                if (matchIds.size() > 0) {
                    URL fetchURL = new URL(ANNIS_URL_PREFIX +
                            "/annis/query/search/subgraph?left=7&right=7&filter=token");
                    HttpURLConnection fetchConn = (HttpURLConnection)
                        fetchURL.openConnection();
                    fetchConn.setDoOutput(true);
                    fetchConn.setRequestMethod("POST");
                    fetchConn.setRequestProperty("Content-Type",
                            "text/plain");
                    OutputStream output = fetchConn.getOutputStream();
                    for (String s : matchIds) {
                        output.write(s.getBytes("UTF-8"));
                        output.write("\r\n".getBytes("UTF-8"));
                    }
                    InputStream instream = fetchConn.getInputStream();
                    DocumentBuilderFactory dbf =
                        DocumentBuilderFactory.newInstance();
                    DocumentBuilder db = dbf.newDocumentBuilder();
                    Document subgraphDoc = db.parse(instream);
                    results = convertAnnisXMItoHZSK(subgraphDoc, maximumRecords,
                            query);
                } else {
                    return results;
                }
            } // basic text query
        } catch (MalformedURLException ufe) {
            ufe.printStackTrace();
        } catch(ProtocolException pe) {
            pe.printStackTrace();
        } catch(ParserConfigurationException pce) {
            pce.printStackTrace();
        } catch(SAXException saxe) {
            saxe.printStackTrace();
        } catch(IOException ioe) {
            ioe.printStackTrace();
        }
        return results;
    }

    private AdvancedSearchResultSet convertAnnisXMItoHZSK(Document doc,
            int maxResults, HZSKQuery query) {
        AdvancedSearchResultSet results = new AdvancedSearchResultSet(maxResults);
        NodeList roots = doc.getElementsByTagName("xmi:XMI");
        if (roots.getLength() != 1)  {
            System.out.println("DEBUG: XMI roots " + roots.getLength());
        }
        NodeList xmichildren = roots.item(0).getChildNodes();
        int recordsParsed = 0;
        for (int i = 0; i < xmichildren.getLength(); i++) {
            Node root = xmichildren.item(i);
            if (root.getNodeName().equals("sDocumentStructure:SDocumentGraph")) {
                Map<String, Map<String, AdvancedSearchResultSegment>> tokenmaps
                    = new
                    HashMap<String, Map<String, AdvancedSearchResultSegment>>();
                tokenmaps.put("token",
                        new HashMap<String, AdvancedSearchResultSegment>());
                tokenmaps.put("lemma",
                        new HashMap<String, AdvancedSearchResultSegment>());
                tokenmaps.put("pos",
                        new HashMap<String, AdvancedSearchResultSegment>());
                tokenmaps.put("morph",
                        new HashMap<String, AdvancedSearchResultSegment>());
                List<String> ids = new ArrayList<String>();
                List<AdvancedSearchResultSegment> tokenised = null;
                AdvancedSearchResult result = new AdvancedSearchResult();
                NodeList rootChildren = root.getChildNodes();
                for (int j = 0; j < rootChildren.getLength(); j++) {
                    Node rootChild = rootChildren.item(j);
                    if (rootChild.getNodeName().equals("labels")) {
                        // I don't like root labels in annis
                    } else if (rootChild.getNodeName().equals("nodes")) {
                        Element nodes = (Element) rootChild;
                        String xsitype = nodes.getAttribute("xsi:type");
                        if (xsitype.equals("sDocumentStructure:STextualDS")) {
                            AdvancedSearchResultSegment wholeText =
                                getWholeTextFromTextualDS(nodes);
                            String localId = getIdFromTextualDS(nodes);
                            result.setWholeText(wholeText);
                            result.setPID(localId);
                            result.setSource(ANNIS_GUI_PREFIX + localId +
                                    "#source");
                            result.setPage(ANNIS_GUI_PREFIX + localId +
                                    "#page");
                            tokenised = tokeniseTextualDS(wholeText);
                            List<AdvancedSearchResultSegment> highlights =
                                AdvancedSearchResult.highlightSearch(wholeText,
                                        query);
                            result.setHighlights(highlights);
                        } else if (xsitype.equals("sDocumentStructure:SToken"))
                        {
                            addSegmentsFromToken(nodes, tokenmaps, ids);
                        } else {
                            System.out.println("DEBUG: nodes " + xsitype);
                        }
                    } else if (rootChild.getNodeName().equals("edges")) {
                        // I don't like edges in annis
                    } else if (rootChild.getNodeName().equals("layers")) {
                        // I don't like layers in annis
                    } else if (rootChild.getNodeType() == Node.TEXT_NODE) {
                        // must be some whitespace
                    } else {
                        System.out.println("DEBUG: XMI rootchild " +
                                rootChild.getNodeName());
                    }
                }
                // one match (documentGraph) parsed,
                // match text to tokens to stuff
                Map<String, List<AdvancedSearchResultSegment>> layers =
                    realignTokens(tokenised, tokenmaps, ids);
                for (Entry<String, List<AdvancedSearchResultSegment>> layer :
                        layers.entrySet()) {
                    result.addChildLayer(layer.getKey(), layer.getValue());
                }
                results.addRecord(result);
                recordsParsed++;
            } else if (root.getNodeType() == Node.TEXT_NODE) {
                // must be some whitespace
            } else {
                System.out.println("DEBUG: unk child of XMI " +
                        root.getNodeName());
            }
        }
        results.setTotalLength(recordsParsed);
        return results;
    }

    private List<AdvancedSearchResultSegment> tokeniseTextualDS(
            AdvancedSearchResultSegment whole) {
        String[] tokenStrings = whole.getText().split(" ");
        List<AdvancedSearchResultSegment> results = new ArrayList<AdvancedSearchResultSegment>();
        int pos = 0;
        for (int i = 0; i < tokenStrings.length; i++) {
            AdvancedSearchResultSegment token = new
                AdvancedSearchResultSegment(tokenStrings[i], pos,
                        pos + tokenStrings[i].length());
            results.add(token);
            pos += tokenStrings[i].length() + 1;
        }
        return results;
    }

    private Map<String, List<AdvancedSearchResultSegment>>
            realignTokens(List<AdvancedSearchResultSegment> tokenizedText,
            Map<String, Map<String, AdvancedSearchResultSegment>> tokenmap,
            List<String> ids) {
        Map<String, List<AdvancedSearchResultSegment>> finalLayers = new
            HashMap<String, List<AdvancedSearchResultSegment>>();
        finalLayers.put("token", tokenizedText);
        finalLayers.put("pos", new ArrayList<AdvancedSearchResultSegment>());
        finalLayers.put("morph", new ArrayList<AdvancedSearchResultSegment>());
        finalLayers.put("lemma", new ArrayList<AdvancedSearchResultSegment>());
        int i = 0;
        // XXX: this works but I don't know why
        Collections.sort(ids, new SortAnnisIDComparator());
        for (AdvancedSearchResultSegment token : tokenizedText) {
            String id = ids.get(i);
            System.out.println("realigning: " + id);
            for (Entry<String, Map<String, AdvancedSearchResultSegment>> layer :
                    tokenmap.entrySet()) {
                String layerName = layer.getKey();
                Map<String, AdvancedSearchResultSegment> layerTokens =
                    layer.getValue();
                if (layerTokens.containsKey(id)) {
                    List<AdvancedSearchResultSegment> fl =
                        finalLayers.get(layerName);
                    AdvancedSearchResultSegment seg = layerTokens.get(id);
                    seg.setStart(tokenizedText.get(i).getStart());
                    seg.setEnd(tokenizedText.get(i).getEnd());
                    fl.add(seg);
                    finalLayers.put(layerName, fl);
                }
            }
            i++;
            if (i > tokenizedText.size()) {
                System.out.println("Less tokens in text than layers!!");
                break;
            }
        }
        return finalLayers;
    }

    private AdvancedSearchResultSegment getWholeTextFromTextualDS(Element nodes) {
        NodeList labels = nodes.getChildNodes();
        for (int i = 0; i < labels.getLength(); i++) {
            Node label = labels.item(i);
            if (label.getNodeName().equals("labels")) {
                Element labelElement = (Element)label;
                String name = labelElement.getAttribute("name");
                if (name.equals("SDATA")) {
                    String text = labelElement.getAttribute("value").substring(3);
                    return new AdvancedSearchResultSegment(text, 0, text.length());
                }
            }
        }
        return null;
        //throw SomeFormatError();
    }


    private String getIdFromTextualDS(Element nodes) {
        NodeList labels = nodes.getChildNodes();
        for (int i = 0; i < labels.getLength(); i++) {
            Node label = labels.item(i);
            if (label.getNodeName().equals("labels")) {
                Element labelElement = (Element)label;
                String name = labelElement.getAttribute("name");
                if (name.equals("id")) {
                    return labelElement.getAttribute("value").substring(3);
                }
            }
        }
        return null;
        //throw SomeFormatError();
    }
    private void addSegmentsFromToken(Element nodes,
            Map<String, Map<String, AdvancedSearchResultSegment>> tokenmaps,
            List<String> ids) {
        String pos = null;
        String morph = null;
        String lemma = null;
        String long_id = null;
        String id = null;
        boolean highlight = false;
        NodeList labels = nodes.getChildNodes();
        for (int i = 0; i < labels.getLength(); i++) {
            Node label = labels.item(i);
            if (label.getNodeName().equals("labels")) {
                Element labelElement = (Element)label;
                String name = labelElement.getAttribute("name");
                if (name.equalsIgnoreCase("POS")) {
                    pos = labelElement.getAttribute("value").substring(3);
                } else if (name.equalsIgnoreCase("MORPH")) {
                    morph = labelElement.getAttribute("value").substring(3);
                } else if (name.equalsIgnoreCase("LEMMA")) {
                    lemma = labelElement.getAttribute("value").substring(3);
                } else if (name.equals("SNAME")) {
                    id = labelElement.getAttribute("value").substring(3);
                } else if (name.equalsIgnoreCase("ID")) {
                    long_id = labelElement.getAttribute("value").substring(3);
                } else if (name.equalsIgnoreCase("matchednode")) {
                    highlight = true;
                } else if (name.equalsIgnoreCase("relannis_node")) {
                    // ignore
                } else {
                    System.out.println("DEBUG: unknown token label " + name);
                }
            }
        }
        String used_id = null;
        if ((id == null) && (long_id == null)) {
            System.out.println("DEBUG: This token has no ID!!");
        }
        else if (id == null) {
            used_id = long_id;
        } else if (long_id == null) {
            used_id = id;
        } else {
            // I guess id is besser than long_id?
            used_id = id;
        }
        System.out.println("add segments from token id: " + used_id);
        ids.add(used_id);
        if (pos != null) {
            AdvancedSearchResultSegment seg = new
                AdvancedSearchResultSegment(pos, highlight);
            tokenmaps.get("pos").put(used_id, seg);
        }
        if (morph != null) {
            AdvancedSearchResultSegment seg = new
                AdvancedSearchResultSegment(morph, highlight);
            tokenmaps.get("morph").put(used_id, seg);
        }
        if (lemma != null) {
            AdvancedSearchResultSegment seg = new
                AdvancedSearchResultSegment(lemma, highlight);
            tokenmaps.get("lemma").put(used_id, seg);
        }
    }

    private String getTextContent(Node n) {
        String rv = "";
        String delim = "";
        NodeList children = n.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node maybecount = children.item(i);
            if (maybecount.getNodeType() == Node.TEXT_NODE) {
                Text counttext = (Text) maybecount;
                rv += delim;
                rv += counttext.getWholeText();
                delim = " ";
            }
        }
        return rv;
    }

    private Document getDocumentFromURL(URL url) throws IOException,
            ParserConfigurationException, SAXException {
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        // TODO: auth
        // ...
        // anything below here fires a connection automagically
        int status = conn.getResponseCode();
        if (status >= 400) {
            // errors
            System.out.println("DEBUG: http " + status);
        }
        InputStream instream = conn.getInputStream();
        DocumentBuilderFactory dbf =
            DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(instream);
        return doc;
    }

    /** Not used for HTTP connections. */
    public void close() {}


    private String prepareTextQuery(HZSKQuery query)
            throws UnsupportedEncodingException {
        String searchAQL = "\"";
        String delim = "";
        for (String s: query.getTextSearches()) {
            searchAQL += delim;
            searchAQL += s;
            delim = " ";
        }
        searchAQL += "\"";

        return URLEncoder.encode(searchAQL, "UTF-8");
    }
}

