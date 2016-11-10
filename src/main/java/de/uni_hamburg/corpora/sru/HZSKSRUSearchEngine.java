/*
 */
package de.uni_hamburg.corpora.sru;

import java.net.URI;
import java.net.URL;
import java.net.MalformedURLException;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import javax.servlet.ServletContext;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.z3950.zing.cql.CQLNode;
import org.z3950.zing.cql.CQLBooleanNode;
import org.z3950.zing.cql.CQLAndNode;
import org.z3950.zing.cql.CQLRelation;
import org.z3950.zing.cql.CQLTermNode;
import org.z3950.zing.cql.Modifier;

import eu.clarin.sru.server.CQLQueryParser.CQLQuery;
import eu.clarin.sru.server.fcs.Constants;
import eu.clarin.sru.server.fcs.DataView;
import eu.clarin.sru.server.fcs.EndpointDescription;
import eu.clarin.sru.server.fcs.FCSQueryParser;
import eu.clarin.sru.server.fcs.FCSQueryParser.FCSQuery;
import eu.clarin.sru.server.fcs.Layer;
import eu.clarin.sru.server.fcs.parser.Expression;
import eu.clarin.sru.server.fcs.parser.Operator;
import eu.clarin.sru.server.fcs.parser.QueryNode;
import eu.clarin.sru.server.fcs.parser.QuerySegment;
import eu.clarin.sru.server.fcs.parser.QuerySequence;
import eu.clarin.sru.server.fcs.ResourceInfo;
import eu.clarin.sru.server.fcs.SimpleEndpointSearchEngineBase;
import eu.clarin.sru.server.fcs.utils.SimpleEndpointDescription;
import eu.clarin.sru.server.fcs.utils.SimpleEndpointDescriptionParser;
import eu.clarin.sru.server.SRUConfigException;
import eu.clarin.sru.server.SRUConstants;
import eu.clarin.sru.server.SRUDiagnostic;
import eu.clarin.sru.server.SRUDiagnosticList;
import eu.clarin.sru.server.SRUException;
import eu.clarin.sru.server.SRUQueryParserRegistry.Builder;
import eu.clarin.sru.server.SRURequest;
import eu.clarin.sru.server.SRUScanResultSet;
import eu.clarin.sru.server.SRUSearchResultSet;
import eu.clarin.sru.server.SRUServerConfig;

/**
 * HZSK’s FCS/SRU search engine using simple endpoint.
 * Connects to database to provide results for a FCS queries.
 * @author Olli usw
 * @author tpirinen
 */
public class HZSKSRUSearchEngine extends SimpleEndpointSearchEngineBase {

    private static final String FCS_NS_OLD = "http://clarin.eu/fcs/1.0";
    private static final String CLARIN_FCS_RECORD_SCHEMA_OLD = FCS_NS_OLD;
    private static final String FCS_NS = "http://clarin.eu/fcs/resource";
    private static final String CLARIN_FCS_RECORD_SCHEMA = FCS_NS;
    private static final String FCS_PREFIX = "fcs";
    private static final String CLARIN_CONTEXT = "x-cmd-context";
    private static final int HZSK_MAX_CORPORA_IN_DB = 250;
    private static final String HAMATAC_PID =
        "http://hdl.handle.net/11022/0000-0000-63C5-2";
    private static final Logger logger =
            LoggerFactory.getLogger(HZSKSRUSearchEngine.class);

    private SQLCorpusConnection corpusDB;
    private AnnisConnection annis;

    /**
     * Create endpoint description from bundled XML, fallback to DB.
     * XML solution uses endpoint-description.xml in WEB-INF by context, DB
     * expects to find attributes in ex_corpus_desc_item table.
     */
    @Override
    protected EndpointDescription createEndpointDescription(ServletContext
            context, SRUServerConfig config, Map<String, String> params)
    throws SRUConfigException {
        EndpointDescription descr = null;
        try {
            URL url = context.getResource("/WEB-INF/endpoint-description.xml");
            descr = SimpleEndpointDescriptionParser.parse(url);
        } catch (MalformedURLException e) {
            logger.error("bundled endpoint-description failed", e);
            descr = null;
        }
        if (descr != null) {
            return descr;
        }
        // else parse DB
        try {
            DBDescriptionResult resources =
                corpusDB.getResourceInfos(HZSK_MAX_CORPORA_IN_DB);
            List entries = new ArrayList<ResourceInfo>();
            // Using the same stuff for
            List<Layer> commonLayers = new ArrayList<Layer>();
            List<DataView> commonDataviews = new ArrayList<DataView>();
            for (int resourceIndex = 0; resourceIndex < resources.getLength();
                    ++resourceIndex) {
                DBDescriptionResult.Record r =
                    resources.getRecordAt(resourceIndex);
                String pid = r.getPid();
                Map<String, String> title = r.getTitle();
                Map<String, String> description = r.getDescription();
                URI landingPageURI = r.getLandingPageURI();
                List<String> languages = r.getLanguages();

                // common for all of ours (don't know why repeated)
                List<Layer> layers = new ArrayList<Layer>();
                layers.add(new Layer("layer1",
                        new URI("http://www.corpora.uni-hamburg.de/layers/orth1"),
                        "orth"));
                List<DataView> dataviews = new ArrayList<DataView>();
                dataviews.add(new DataView("kwic_dataview",
                            "application/x-clarin-fcs-kwic+xml",
                            DataView.DeliveryPolicy.SEND_BY_DEFAULT));
                dataviews.add(new DataView("hits_dataview",
                            "application/x-clarin-fcs-hits+xml",
                            DataView.DeliveryPolicy.SEND_BY_DEFAULT));
                List<ResourceInfo> subResources = null;
                ResourceInfo ri = new ResourceInfo(pid, title, description,
                        landingPageURI.toString(), languages, dataviews, layers,
                        subResources);
                entries.add(ri);
            }
            List<URI> capabilities = new ArrayList<URI>();
            commonLayers.add(new Layer("layer1",
                    new URI("http://www.corpora.uni-hamburg.de/layers/orth1"),
                    "orth"));
            commonDataviews.add(new DataView("kwic_dataview",
                        "application/x-clarin-fcs-kwic+xml",
                        DataView.DeliveryPolicy.SEND_BY_DEFAULT));
            commonDataviews.add(new DataView("hits_dataview",
                        "application/x-clarin-fcs-hits+xml",
                        DataView.DeliveryPolicy.SEND_BY_DEFAULT));
            capabilities.add(new
                    URI("http://clarin.eu/fcs/capability/basic-search"));
            SimpleEndpointDescription resourceInfos =
                new SimpleEndpointDescription(capabilities, commonDataviews,
                        commonLayers,
                        entries, false);
            return resourceInfos;
        } catch (SQLException e) {
            logger.error("error processing query", e);
            throw new SRUConfigException(
                    "Error processing query " + e + ": " + e.getMessage() +
                    "\r\n" + e.getStackTrace()[0],
                    e);
        }
        catch (java.net.URISyntaxException use)
        {
            throw new SRUConfigException("Configurations broken in HZSK stuff",
                    use);
        }
    }

    /** Initialise database connection. */
    @Override
    protected void doInit(ServletContext context, SRUServerConfig config,
            Builder builder, Map<String, String> params)
    throws SRUConfigException {
        corpusDB = new SQLCorpusConnection();
        //annis = new AnnisConnection();
        annis = null;
    }

    /** Just blurt out a term on a specific search.
     * I just copied it from what others do, apparently the no-op in base is not
     * good for sanity checks?
     */
    @Override
    public SRUScanResultSet doScan(SRUServerConfig config,
                    SRURequest request,
                    SRUDiagnosticList diagnostics)
                    throws SRUException {
        CQLNode scan = request.getScanClause();
        if (scan.toCQL().equals("fcs.resource = root")) {
            HZSKSRUScanResultSet rv =  new HZSKSRUScanResultSet(diagnostics);
            return rv;
        } else {
            diagnostics.addDiagnostic("info:srw/diagnostic/1/16",
                    scan.toCQL(), "Scan operation on specified index is not "
                    + "supported.");
            return null;
        }
    }

    /** Query database for matches. */
    @Override
    public SRUSearchResultSet search(SRUServerConfig config,
            SRURequest request, SRUDiagnosticList diagnostics)
            throws SRUException {
        /*
         * sanity check: make sure we are asked to return stuff
         * in CLARIN FCS format if a recordSchema is specified.
         */
        final String recordSchemaIdentifier =
                request.getRecordSchemaIdentifier();
        if (recordSchemaIdentifier != null) {
            if (!recordSchemaIdentifier.equals(CLARIN_FCS_RECORD_SCHEMA_OLD) &&
                    !recordSchemaIdentifier.equals(CLARIN_FCS_RECORD_SCHEMA)) {

                throw new SRUException(
                    SRUConstants.SRU_UNKNOWN_SCHEMA_FOR_RETRIEVAL,
                    recordSchemaIdentifier, "Record schema \""
                    + recordSchemaIdentifier
                    + "\" is not supported by this endpoint.");
            }
        }

        String context = request.getExtraRequestData(CLARIN_CONTEXT);
        if ((context != null) &&
                !context.equals(HAMATAC_PID))
        {
            throw new SRUException(
                    SRUConstants.SRU_UNSUPPORTED_PARAMETER_VALUE,
                    context, "The value of the parameter \""
                    + CLARIN_CONTEXT
                    + "\" is not supported by this endpoint.");
        }

        /* use our Query stuff to store the query instead of all the request
         * stuff
         */
        HZSKQuery hzskQuery = new HZSKQuery();
        hzskQuery.initialise(request);

        // perform queries
        int startRecord = request.getStartRecord();
        int maximumRecords = request.getMaximumRecords();
        if (startRecord > 0) {
            startRecord--;
        }
        AdvancedSearchResultSet dBresult = null;
        if (corpusDB != null) {
            try {
                dBresult = corpusDB.query(hzskQuery,
                        startRecord, maximumRecords);
            } catch (Exception e) {
                logger.error("error processing query", e);
                throw new SRUException(
                        SRUConstants.SRU_CANNOT_PROCESS_QUERY_REASON_UNKNOWN,
                        "Error processing query " + e + ": " + e.getMessage() +
                        "\r\n" + e.getStackTrace()[0],
                        e);
            }
        }
        AdvancedSearchResultSet aResult = null;
        if (annis != null) {
            try {
                aResult = annis.query(hzskQuery, request.getStartRecord(),
                        maximumRecords);
            } catch (Exception e) {
                logger.error("error processing query", e);
                throw new SRUException(
                        SRUConstants.SRU_CANNOT_PROCESS_QUERY_REASON_UNKNOWN,
                        "Error processing query " + e + ": " + e.getMessage() +
                        "\r\n" + e.getStackTrace()[0],
                        e);
            }
        }
        return new HZSKSRUSearchResultSet(diagnostics,
                AdvancedSearchResultSet.merge(dBresult, aResult),
                request);

    }


    /** Terminate database connection. */
    @Override
    public void doDestroy() {
        corpusDB.close();
    }
} // class SRUSearchEngine

