package com.socrata.api;

import com.google.common.collect.Lists;
import com.socrata.TestBase;
import com.socrata.exceptions.LongRunningQueryException;
import com.socrata.exceptions.SodaError;
import com.socrata.model.UpsertResult;
import com.socrata.model.importer.DatasetInfo;
import com.socrata.model.soql.SoqlQuery;
import com.socrata.utils.GeneralUtils;
import junit.framework.TestCase;
import org.junit.Test;
import test.model.Crime;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 */
public class Soda2ProducerTest extends TestBase
{

    public static final File  CRIMES_HEADER_CSV = new File("src/test/resources/testCrimesHeader.csv");
    public static final File  CRIMES_HEADER2_CSV = new File("src/test/resources/testCrimesHeader2.csv");
    public static final File  CRIMES_APPEND_CSV = new File("src/test/resources/testCrimesAppend.csv");

    public static final File  DELETE_CRIMES_CSV = new File("src/test/resources/testDeletingCrimes.csv");


    /**
     * Tests a number of ways to connect using dataset name
     */
    @Test
    public void testReplace() throws LongRunningQueryException, SodaError, InterruptedException, IOException
    {
        final String name = "Name" + UUID.randomUUID();
        final String description = name + "-Description";

        final HttpLowLevel  connection = connect();
        final Soda2Producer producer = new Soda2Producer(connection);
        final SodaImporter  sodaImporter = new SodaImporter(connection);

        DatasetInfo datasetCreated = sodaImporter.createViewFromCsv(name, description, CRIMES_HEADER2_CSV, "ID");
        DatasetInfo datasetPublished = sodaImporter.publish(datasetCreated.getId());

        try {

            List<Crime> results = producer.query(datasetPublished.getId(), SoqlQuery.SELECT_ALL, Crime.LIST_TYPE);
            TestCase.assertEquals(3, results.size());


            UpsertResult replaceResult = producer.replaceCsv(datasetPublished.getId(), CRIMES_HEADER_CSV);
            results = producer.query(datasetPublished.getId(), SoqlQuery.SELECT_ALL, Crime.LIST_TYPE);
            TestCase.assertEquals(2, results.size());

            replaceResult = producer.replace(datasetPublished.getId(), Lists.newArrayList(results.get(0)));
            results = producer.query(datasetPublished.getId(), SoqlQuery.SELECT_ALL, Crime.LIST_TYPE);
            TestCase.assertEquals(1, results.size());


            //Try it with a larger file
            /*
            replaceResult = producer.replaceCsv(datasetPublished.getId(), CRIMES_APPEND_CSV);
            results = producer.query(datasetPublished.getId(), SoqlQuery.SELECT_ALL, Crime.LIST_TYPE);
            TestCase.assertEquals(1000, results.size());
            */

        } finally {
            sodaImporter.deleteDataset(datasetPublished.getId());
        }

    }

    /**
     * Tests a number of ways to connect using dataset name
     */
    @Test
    public void testReplaceStream() throws LongRunningQueryException, SodaError, InterruptedException, IOException
    {
        final String name = "Name" + UUID.randomUUID();
        final String description = name + "-Description";

        final HttpLowLevel  connection = connect();
        final Soda2Producer producer = new Soda2Producer(connection);
        final SodaImporter  sodaImporter = new SodaImporter(connection);

        FileInputStream fisCrimes1 = new FileInputStream(CRIMES_HEADER_CSV);
        DatasetInfo datasetCreated = sodaImporter.createViewFromCsv(name, description, CRIMES_HEADER2_CSV, "ID");
        DatasetInfo datasetPublished = sodaImporter.publish(datasetCreated.getId());

        try {

            List<Crime> results = producer.query(datasetPublished.getId(), SoqlQuery.SELECT_ALL, Crime.LIST_TYPE);
            TestCase.assertEquals(3, results.size());


            UpsertResult replaceResult = producer.replaceStream(datasetPublished.getId(), HttpLowLevel.CSV_TYPE, fisCrimes1);
            results = producer.query(datasetPublished.getId(), SoqlQuery.SELECT_ALL, Crime.LIST_TYPE);
            TestCase.assertEquals(2, results.size());

            replaceResult = producer.replace(datasetPublished.getId(), Lists.newArrayList(results.get(0)));
            results = producer.query(datasetPublished.getId(), SoqlQuery.SELECT_ALL, Crime.LIST_TYPE);
            TestCase.assertEquals(1, results.size());


        } finally {
            sodaImporter.deleteDataset(datasetPublished.getId());
            GeneralUtils.closeQuietly(fisCrimes1);
        }

    }

    @Test
    public void testSoda2OnWorkingCopy() throws LongRunningQueryException, SodaError, InterruptedException, IOException
    {

        final String name = "Name" + UUID.randomUUID();
        final String description = name + "-Description";

        final HttpLowLevel  connection = connect();
        final Soda2Producer producer = new Soda2Producer(connection);
        final SodaImporter  sodaImporter = new SodaImporter(connection);

        DatasetInfo datasetCreated = sodaImporter.createViewFromCsv(name, description, CRIMES_HEADER2_CSV, "ID");
        DatasetInfo datasetPublished = sodaImporter.publish(datasetCreated.getId());
        DatasetInfo datasetWorkingCopy = sodaImporter.createWorkingCopy(datasetPublished.getId());

        try {

            List<Crime> results = producer.query(datasetWorkingCopy.getId(), SoqlQuery.SELECT_ALL, Crime.LIST_TYPE);
            TestCase.assertEquals(3, results.size());


            UpsertResult replaceResult = producer.replaceCsv(datasetWorkingCopy.getId(), CRIMES_HEADER_CSV);
            results = producer.query(datasetWorkingCopy.getId(), SoqlQuery.SELECT_ALL, Crime.LIST_TYPE);
            TestCase.assertEquals(2, results.size());

            producer.upsertCsv(datasetWorkingCopy.getId(), CRIMES_HEADER2_CSV);
            results = producer.query(datasetWorkingCopy.getId(), SoqlQuery.SELECT_ALL, Crime.LIST_TYPE);
            TestCase.assertEquals(3, results.size());

            /*
            replaceResult = producer.upsertCsv(datasetWorkingCopy.getId(), CRIMES_APPEND_CSV);
            TestCase.assertEquals(3, replaceResult.getRowsUpdated());
            TestCase.assertEquals(997, replaceResult.getRowsCreated());

            results = producer.query(datasetWorkingCopy.getId(), SoqlQuery.SELECT_ALL, Crime.LIST_TYPE);
            TestCase.assertEquals(1000, results.size());
            */

            replaceResult = producer.replace(datasetWorkingCopy.getId(), Lists.newArrayList(results.get(0)));
            results = producer.query(datasetWorkingCopy.getId(), SoqlQuery.SELECT_ALL, Crime.LIST_TYPE);
            TestCase.assertEquals(1, results.size());

            datasetPublished = sodaImporter.publish(datasetWorkingCopy.getId());
        } finally {
            sodaImporter.deleteDataset(datasetPublished.getId());
        }

    }

    /**
     * Tests a number of ways to connect using dataset name
     */
    @Test
    public void testDelete() throws LongRunningQueryException, SodaError, InterruptedException, IOException
    {
        final String name = "Name" + UUID.randomUUID();
        final String description = name + "-Description";

        final HttpLowLevel  connection = connect();
        final Soda2Producer producer = new Soda2Producer(connection);
        final SodaImporter  sodaImporter = new SodaImporter(connection);

        DatasetInfo datasetCreated = sodaImporter.createViewFromCsv(name, description, CRIMES_HEADER2_CSV, "ID");
        DatasetInfo datasetPublished = sodaImporter.publish(datasetCreated.getId());

        try {

            List<Crime> results = producer.query(datasetPublished.getId(), SoqlQuery.SELECT_ALL, Crime.LIST_TYPE);
            TestCase.assertEquals(3, results.size());


            //Delete two crimes using upsert
            UpsertResult replaceResult = producer.upsertCsv(datasetPublished.getId(), DELETE_CRIMES_CSV);
            results = producer.query(datasetPublished.getId(), SoqlQuery.SELECT_ALL, Crime.LIST_TYPE);
            TestCase.assertEquals(1, results.size());

            producer.delete(datasetPublished.getId(), results.get(0).getId().toString());
            results = producer.query(datasetPublished.getId(), SoqlQuery.SELECT_ALL, Crime.LIST_TYPE);
            TestCase.assertEquals(0, results.size());


        } finally {
            sodaImporter.deleteDataset(datasetPublished.getId());
        }

    }
}