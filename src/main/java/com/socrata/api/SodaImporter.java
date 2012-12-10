package com.socrata.api;

import com.socrata.builders.BlueprintBuilder;
import com.socrata.exceptions.LongRunningQueryException;
import com.socrata.exceptions.SodaError;
import com.socrata.model.GeocodingResults;
import com.socrata.model.importer.Blueprint;
import com.socrata.model.importer.BlueprintColumn;
import com.socrata.model.importer.Dataset;
import com.socrata.model.importer.ScanResults;
import com.sun.jersey.api.client.ClientResponse;
import org.codehaus.jackson.map.ObjectMapper;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriBuilder;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;

/**
 * All the import client logic is in this class.
 *
 */
public class SodaImporter
{

    public static final String IMPORTER_BASE_PATH = "api";
    public static final String SCAN_BASE_PATH = "imports2";
    public static final String VIEWS_BASE_PATH = "views";
    public static final String GEO_BASE_PATH = "geocoding";


    private final HttpLowLevel  httpLowLevel;
    private final URI           viewUri;
    private final URI           importUri;
    private final URI           geocodingUri;

    private final ObjectMapper  mapper = new ObjectMapper();
    static final long ticketCheck = 10000L;



    /**
     * Constructor.
     *
     * @param httpLowLevel the HttpLowLevel this uses to contact the server
     */
    public SodaImporter(HttpLowLevel httpLowLevel)
    {
        this.httpLowLevel = httpLowLevel;

        viewUri = httpLowLevel.uriBuilder()
                              .path(IMPORTER_BASE_PATH)
                              .path(VIEWS_BASE_PATH)
                              .build();

        importUri = httpLowLevel.uriBuilder()
                              .path(IMPORTER_BASE_PATH)
                              .path(SCAN_BASE_PATH)
                              .build();

        geocodingUri = httpLowLevel.uriBuilder()
                              .path(IMPORTER_BASE_PATH)
                              .path(GEO_BASE_PATH)
                              .build();
    }


    /**
     * Gets the underlying connection.
     *
     * @return the underlying connection
     */
    public HttpLowLevel getHttpLowLevel()
    {
        return httpLowLevel;
    }


    /**
     * Creates an empty dataset, based on the view passed in.
     *
     * The new view will be unpublished.
     *
     * @param view view to create the new view on.  The ID should NOT be set.
     * @return the created view, the ID will be set on this.
     * @throws SodaError
     * @throws InterruptedException
     */
    Dataset createView(Dataset view) throws SodaError, InterruptedException
    {
        try {
            final ClientResponse response = httpLowLevel.postRaw(viewUri, HttpLowLevel.JSON_TYPE, view);
            return response.getEntity(Dataset.class);
        } catch (LongRunningQueryException e) {
            return getHttpLowLevel().getAsyncResults(e.location, e.timeToRetry, HttpLowLevel.DEFAULT_MAX_RETRIES, Dataset.class);
        }
    }


    /**
     * Loads a dataset or view based on it's ID
     *
     * @param id the ID to load the view through.
     * @return The View with the supplied ID.
     *
     * @throws LongRunningQueryException
     * @throws SodaError
     */
    Dataset loadView(final String id) throws LongRunningQueryException, SodaError
    {
        final URI uri = UriBuilder.fromUri(viewUri)
                                    .path(id)
                                    .build();
        final ClientResponse response = httpLowLevel.queryRaw(uri, HttpLowLevel.JSON_TYPE);
        return response.getEntity(Dataset.class);
    }


    /**
     * Updates a view.
     *
     * @param view the view to update to.  The ID MUST be set.
     * @return the view after the update.
     *
     * @throws SodaError
     * @throws InterruptedException
     */
    Dataset updateView(Dataset view) throws SodaError, InterruptedException
    {
        try {
            URI uri = UriBuilder.fromUri(viewUri)
                                .path(view.getId())
                                .build();

            final ClientResponse response = httpLowLevel.putRaw(uri, HttpLowLevel.JSON_TYPE, view);
            return response.getEntity(Dataset.class);
        } catch (LongRunningQueryException e) {
            return getHttpLowLevel().getAsyncResults(e.location, e.timeToRetry, HttpLowLevel.DEFAULT_MAX_RETRIES, Dataset.class);
        }
    }

    /**
     * Deletes a view
     *
     * @param id the ID of the view to delete
     * @throws SodaError
     * @throws InterruptedException
     */
    void deleteView(String id) throws SodaError, InterruptedException
    {
        try {

            URI uri = UriBuilder.fromUri(viewUri)
                                .path(id)
                                .build();

            httpLowLevel.deleteRaw(uri);
        } catch (LongRunningQueryException e) {
            getHttpLowLevel().getAsyncResults(e.location, e.timeToRetry, HttpLowLevel.DEFAULT_MAX_RETRIES, Dataset.class);
        }
    }


    /**
     * Creates a dataset from a CSV, using all the default column types.  This will also
     * assume the CSV has a single header row at the top.
     *
     * @param name name of the dataset to create
     * @param description description of the new dataset
     * @param file the file to upload
     * @return return the view that was just created.
     *
     * @throws InterruptedException
     * @throws SodaError
     * @throws IOException
     */
    Dataset createViewFromCsv(final String name, final String description, final File file) throws InterruptedException, SodaError, IOException
    {

        return importScanResults(name, description, file, scan(file));
    }


    /**
     * Scans a file, then sends it up to the Socrata service to be analyzed and have things
     * like column types guessed.
     *
     * @param file File to upload
     * @return the results of the scan.
     *
     * @throws SodaError
     * @throws InterruptedException
     */
    public ScanResults scan(final File file) throws SodaError, InterruptedException
    {
        try {
            final URI scanUri = UriBuilder.fromUri(importUri)
                                          .queryParam("method", "scan")
                                          .build();

            final ClientResponse response = httpLowLevel.postFileRaw(scanUri, HttpLowLevel.CSV_TYPE, file);
            return response.getEntity(ScanResults.class);
        } catch (LongRunningQueryException e) {
            return getHttpLowLevel().getAsyncResults(e.location, e.timeToRetry, HttpLowLevel.DEFAULT_MAX_RETRIES, ScanResults.class);
        }
    }

    /**
     * Publishes a dataset.
     *
     * @param datasetId id of the dataset to publish.
     * @return the view of the published dataset.
     *
     * @throws SodaError
     * @throws InterruptedException
     * @throws LongRunningQueryException
     */
    public Dataset publish(final String datasetId) throws SodaError, InterruptedException
    {
        waitForPendingGeocoding(datasetId);
        final URI publicationUri = UriBuilder.fromUri(viewUri)
                                             .path(datasetId)
                                             .path("publication")
                                             .build();

        try {

            ClientResponse response = httpLowLevel.postRaw(publicationUri, HttpLowLevel.JSON_TYPE, "viewId=" + datasetId);
            return response.getEntity(Dataset.class);
        } catch (LongRunningQueryException e) {
            return getHttpLowLevel().getAsyncResults(e.location, e.timeToRetry, HttpLowLevel.DEFAULT_MAX_RETRIES, Dataset.class);
        }
    }

    /**
     * Waits for pending geocodes to be finished.  A publish won't work until all active geocoding requests are
     * handled.
     *
     * @param datasetId id of the dataset to check for outstanding geocodes.
     * @throws InterruptedException
     * @throws SodaError
     */
    public void waitForPendingGeocoding(final String datasetId) throws InterruptedException, SodaError
    {
        GeocodingResults geocodingResults = findPendingGeocodingResults(datasetId);
        while (geocodingResults.getTotal() > 0)
        {
            try { Thread.sleep(ticketCheck); } catch (InterruptedException e) {}
            geocodingResults = findPendingGeocodingResults(datasetId);
        }
    }

    /**
     * Checks to see if the current dataset has any pending Geocoding results.
     *
     * @param datasetId id of the dataset
     * @return The Geocoding results for this dataset.
     *
     * @throws SodaError
     * @throws InterruptedException
     */
    public GeocodingResults findPendingGeocodingResults(final String datasetId) throws SodaError, InterruptedException
    {
        try {
            final URI uri = UriBuilder.fromUri(geocodingUri)
                                          .path(datasetId)
                                          .queryParam("method", "pending")
                                          .build();

            final ClientResponse response = httpLowLevel.queryRaw(uri, HttpLowLevel.JSON_TYPE);
            return response.getEntity(GeocodingResults.class);
        } catch (LongRunningQueryException e) {
            return getHttpLowLevel().getAsyncResults(e.location, e.timeToRetry, HttpLowLevel.DEFAULT_MAX_RETRIES, GeocodingResults.class);
        }
    }


    /**
     * Imports the results of scanning a file.  This will build  a default blueprint from it, assuming the first rows are
     * column names.
     *
     * @param name name of the dataset to create
     * @param description description of the datset
     * @param file file that was scanned
     * @param scanResults results of the scan
     * @return The default View object for the dataset that was just created.
     */
    public Dataset importScanResults(final String name, final String description, final File file, final ScanResults scanResults) throws SodaError, InterruptedException, IOException
    {
        final Blueprint blueprint = new BlueprintBuilder(scanResults)
                                        .setSkip(1)
                                        .setName(name)
                                        .setDescription(description)
                                        .build();

        return importScanResults(blueprint, null, file, scanResults);
    }


    /**
     * Imports the results of scanning a file.  This method does not assume anything about the CSV, but instead has
     * the caller provide the blueprint and the translation for any schema defintion or data transforms.
     *
     * @param blueprint
     * @param translation
     * @param file file that was scanned
     * @param scanResults results of the scan
     * @return The default View object for the dataset that was just created.
     */
    public Dataset importScanResults(final Blueprint blueprint, final String[] translation, final File file, final ScanResults scanResults) throws SodaError, InterruptedException, IOException
    {
        try {
            final String translationString =  mapper.writeValueAsString((translation != null) ? translation : generateTranslation(blueprint));
            final String blueprintString = mapper.writeValueAsString(blueprint);

            final String postData = "translation=" + URLEncoder.encode(translationString, "UTF-8") + "&fileId=" + scanResults.getFileId() + "&name=" + file.getName() + "&blueprint=" +  URLEncoder.encode(blueprintString, "UTF-8");

            final ClientResponse response = httpLowLevel.postRaw(importUri, MediaType.APPLICATION_FORM_URLENCODED_TYPE, postData);
            return response.getEntity(Dataset.class);
        } catch (LongRunningQueryException e) {
            return getHttpLowLevel().getAsyncResults(e.location, e.timeToRetry, Integer.MAX_VALUE, Dataset.class);
        }

    }


    /**
     * Creates a straight translation with no transforms for a  given bluprint.
     *
     * @param blueprint blueprint to build the translation from
     * @return the array of mappings to map each field to itself.  This will create a translation that will do nothing.
     */
    public String[] generateTranslation(final Blueprint blueprint) {
        final String[]    retVal = new String[blueprint.getColumns().size()];

        int i =0;
        for (BlueprintColumn column : blueprint.getColumns()) {
            retVal[i++] = column.getName();
        }

        return retVal;
    }


}