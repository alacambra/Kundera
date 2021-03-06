/*******************************************************************************
 * * Copyright 2012 Impetus Infotech.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *      http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 ******************************************************************************/
package com.impetus.kundera.client.cassandra.crud;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.persistence.Query;
import javax.persistence.TypedQuery;

import junit.framework.Assert;

import org.apache.cassandra.thrift.Compression;
import org.apache.cassandra.thrift.ConsistencyLevel;
import org.apache.cassandra.thrift.InvalidRequestException;
import org.apache.cassandra.thrift.SchemaDisagreementException;
import org.apache.cassandra.thrift.TimedOutException;
import org.apache.cassandra.thrift.UnavailableException;
import org.apache.thrift.TException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.impetus.client.cassandra.common.CassandraConstants;
import com.impetus.client.cassandra.thrift.CQLTranslator;
import com.impetus.client.crud.BaseTest;
import com.impetus.client.crud.Group;
import com.impetus.client.crud.Month;
import com.impetus.client.crud.PersonCassandra;
import com.impetus.client.crud.PersonCassandra.Day;
import com.impetus.kundera.PersistenceProperties;
import com.impetus.kundera.client.Client;
import com.impetus.kundera.client.cassandra.dsdriver.DSClient;
import com.impetus.kundera.client.cassandra.persistence.CassandraCli;

/**
 * Test case to perform simple CRUD operation.(insert, delete, merge, and
 * select)
 * 
 * @author vivek.mishra
 */
public class PersonEntityTest extends BaseTest
{
    private static final String _PU = "ds_pu";

    /** The emf. */
    private EntityManagerFactory emf;

    /** The em. */
    private EntityManager entityManager;

    /** The col. */
    private Map<Object, Object> col;

    protected Map propertyMap = null;


    /**
     * Sets the up.
     * 
     * @throws Exception
     *             the exception
     */
    @Before
    public void setUp() throws Exception
    {
        
        CassandraCli.cassandraSetUp();
        System.setProperty("cassandra.start_native_transport", "true");

        if (propertyMap == null)
        {
            propertyMap = new HashMap();
            propertyMap.put(PersistenceProperties.KUNDERA_DDL_AUTO_PREPARE, "create");
        }

        emf = Persistence.createEntityManagerFactory(_PU, propertyMap);
        entityManager = emf.createEntityManager();
        col = new java.util.HashMap<Object, Object>();
    }

    /**
     * On insert cassandra.
     * 
     * @throws Exception
     *             the exception
     */
    @Test
    public void onInsertCassandra() throws Exception
    {
        Object p1 = prepareData("1", 10);
        Object p2 = prepareData("2", 20);
        Object p3 = prepareData("3", 15);

        Query findQuery = entityManager.createQuery("Select p from PersonCassandra p", PersonCassandra.class);
        List<PersonCassandra> allPersons = findQuery.getResultList();
        Assert.assertNotNull(allPersons);
        Assert.assertTrue(allPersons.isEmpty());

        findQuery = entityManager.createQuery("Select p from PersonCassandra p where p.personName = vivek");
        allPersons = findQuery.getResultList();
        Assert.assertNotNull(allPersons);
        Assert.assertTrue(allPersons.isEmpty());

        findQuery = entityManager.createQuery("Select p.age from PersonCassandra p where p.personName = vivek");
        allPersons = findQuery.getResultList();
        Assert.assertNotNull(allPersons);
        Assert.assertTrue(allPersons.isEmpty());

        entityManager.persist(p1);
        entityManager.persist(p2);
        entityManager.persist(p3);

        PersonCassandra personWithKey = new PersonCassandra();
        personWithKey.setPersonId("111");
        entityManager.persist(personWithKey);
        col.put("1", p1);
        col.put("2", p2);
        col.put("3", p3);

        entityManager.clear();
        PersonCassandra p = findById(PersonCassandra.class, "1", entityManager);
        Assert.assertNotNull(p);
        Assert.assertEquals("vivek", p.getPersonName());
        Assert.assertEquals(Day.thursday, p.getDay());

        entityManager.clear();
        Query q;
        List<PersonCassandra> persons = queryOverRowkey();

        assertFindByName(entityManager, "PersonCassandra", PersonCassandra.class, "vivek", "personName");
        assertFindByNameAndAge(entityManager, "PersonCassandra", PersonCassandra.class, "vivek", "10", "personName");
        assertFindByNameAndAgeGTAndLT(entityManager, "PersonCassandra", PersonCassandra.class, "vivek", "10", "20",
                "personName");
        assertFindByNameAndAgeBetween(entityManager, "PersonCassandra", PersonCassandra.class, "vivek", "10", "15",
                "personName");
        assertFindByRange(entityManager, "PersonCassandra", PersonCassandra.class, "1", "2", "personId", true);
        assertFindWithoutWhereClause(entityManager, "PersonCassandra", PersonCassandra.class, true);

        // perform merge after query.
        for (PersonCassandra person : persons)
        {
            person.setPersonName("'after merge'");
            entityManager.merge(person);
        }

        entityManager.clear();

        p = findById(PersonCassandra.class, "1", entityManager);
        Assert.assertNotNull(p);
        Assert.assertEquals("'after merge'", p.getPersonName());

        String updateQuery = "update PersonCassandra p set p.personName=''KK MISHRA'' where p.personId=1";
        q = entityManager.createQuery(updateQuery);
        q.executeUpdate();

        entityManager.clear();
        p = findById(PersonCassandra.class, "1", entityManager);
        Assert.assertNotNull(p);
        Assert.assertEquals("'KK MISHRA'", p.getPersonName());

        testCountResult();
        // Delete without WHERE clause.

        String deleteQuery = "DELETE from PersonCassandra";
        q = entityManager.createQuery(deleteQuery);
        Assert.assertEquals(4, q.executeUpdate());

    }

    private List<PersonCassandra> queryOverRowkey()
    {
        String qry = "Select p.personId,p.personName from PersonCassandra p where p.personId = 1";
        Query q = entityManager.createQuery(qry);
        List<PersonCassandra> persons = q.getResultList();
        Assert.assertNotNull(persons);
        Assert.assertFalse(persons.isEmpty());
        Assert.assertEquals(1, persons.size());

        qry = "Select p.personId,p.personName from PersonCassandra p where p.personId > 1";
        q = entityManager.createQuery(qry);
        persons = q.getResultList();
        Assert.assertNotNull(persons);

        qry = "Select p.personId,p.personName from PersonCassandra p where p.personId < 2";
        q = entityManager.createQuery(qry);
        persons = q.getResultList();
        Assert.assertNotNull(persons);
        Assert.assertFalse(persons.isEmpty());

        qry = "Select p.personId,p.personName from PersonCassandra p where p.personId <= 2";
        q = entityManager.createQuery(qry);
        persons = q.getResultList();
        Assert.assertNotNull(persons);
        Assert.assertFalse(persons.isEmpty());

        qry = "Select p.personId,p.personName from PersonCassandra p where p.personId >= 1";
        q = entityManager.createQuery(qry);
        persons = q.getResultList();
        Assert.assertNotNull(persons);
        Assert.assertFalse(persons.isEmpty());

        return persons;
    }

    private void testCountResult()
    {
        Map<String, Client> clientMap = (Map<String, Client>) entityManager.getDelegate();
        DSClient tc = (DSClient) clientMap.get(_PU);
        tc.setCqlVersion(CassandraConstants.CQL_VERSION_3_0);
        CQLTranslator translator = new CQLTranslator();

        String query = "select count(*) from "
                + translator.ensureCase(new StringBuilder(), "PERSON", false).toString();
        Query q = entityManager.createNativeQuery(query, PersonCassandra.class);
        List noOfRows = q.getResultList();
        Assert.assertEquals(new Long(4),noOfRows.get(0));

        entityManager.clear();
        q = entityManager.createNamedQuery("q");
        noOfRows = q.getResultList();
        Assert.assertEquals(4, noOfRows.size());
    }

    /**
     * On merge cassandra.
     * 
     * @throws Exception
     *             the exception
     */
    @Test
    public void onMergeCassandra() throws Exception
    {
        // CassandraCli.cassandraSetUp();
        // loadData();
        Object p1 = prepareData("1", 10);
        Object p2 = prepareData("2", 20);
        Object p3 = prepareData("3", 15);
        entityManager.persist(p1);
        entityManager.persist(p2);
        entityManager.persist(p3);

        entityManager.clear();
        col.put("1", p1);
        col.put("2", p2);
        col.put("3", p3);
        PersonCassandra p = findById(PersonCassandra.class, "1", entityManager);
        Assert.assertNotNull(p);
        Assert.assertEquals("vivek", p.getPersonName());
        Assert.assertEquals(Month.APRIL, p.getMonth());
        // modify record.
        p.setPersonName("newvivek");
        entityManager.merge(p);

        assertOnMerge(entityManager, "PersonCassandra", PersonCassandra.class, "vivek", "newvivek", "personName");
    }

    @Test
    public void onDeleteThenInsertCassandra() throws Exception
    {
        // CassandraCli.cassandraSetUp();
        // CassandraCli.initClient();
        // loadData();
        Object p1 = prepareData("1", 10);
        Object p2 = prepareData("2", 20);
        Object p3 = prepareData("3", 15);
        entityManager.persist(p1);
        entityManager.persist(p2);
        entityManager.persist(p3);

        col.put("1", p1);
        col.put("2", p2);
        col.put("3", p3);
        PersonCassandra p = findById(PersonCassandra.class, "1", entityManager);
        Assert.assertNotNull(p);
        Assert.assertEquals("vivek", p.getPersonName());
        entityManager.remove(p);
        entityManager.clear();

        TypedQuery<PersonCassandra> query = entityManager.createQuery("Select p from PersonCassandra p",
                PersonCassandra.class);

        List<PersonCassandra> results = query.getResultList();
        Assert.assertNotNull(query);
        Assert.assertNotNull(results);
        Assert.assertEquals(2, results.size());
        Assert.assertEquals(Month.APRIL, results.get(0).getMonth());

        p1 = prepareData("1", 10);
        entityManager.persist(p1);

        query = entityManager.createQuery("Select p from PersonCassandra p", PersonCassandra.class);

        results = query.getResultList();
        Assert.assertNotNull(query);
        Assert.assertNotNull(results);
        Assert.assertEquals(3, results.size());
        Assert.assertEquals(Month.APRIL, results.get(0).getMonth());

    }

    @Test
    public void onRefreshCassandra() throws Exception
    {
        // cassandraSetUp();
        // CassandraCli.cassandraSetUp();
        // CassandraCli.createKeySpace("KunderaExamples");
        // loadData();
        CassandraCli.client.set_keyspace("KunderaExamples");
        Object p1 = prepareData("1", 10);
        Object p2 = prepareData("2", 20);
        Object p3 = prepareData("3", 15);
        entityManager.persist(p1);
        entityManager.persist(p2);
        entityManager.persist(p3);
        col.put("1", p1);
        col.put("2", p2);
        col.put("3", p3);

        // Check for contains
        Object pp1 = prepareData("1", 10);
        Object pp2 = prepareData("2", 20);
        Object pp3 = prepareData("3", 15);
        Assert.assertTrue(entityManager.contains(pp1));
        Assert.assertTrue(entityManager.contains(pp2));
        Assert.assertTrue(entityManager.contains(pp3));

        // Check for detach
        entityManager.detach(pp1);
        entityManager.detach(pp2);
        Assert.assertFalse(entityManager.contains(pp1));
        Assert.assertFalse(entityManager.contains(pp2));
        Assert.assertTrue(entityManager.contains(pp3));

        // Modify value in database directly, refresh and then check PC
        entityManager.clear();
        entityManager = emf.createEntityManager();
        Object o1 = entityManager.find(PersonCassandra.class, "1");
        CQLTranslator translator = new CQLTranslator();
        String query = "insert into \"PERSON\" (\"personId\",\"PERSON_NAME\",\"AGE\") values ('1','Amry',10 )";
        CassandraCli.client.execute_cql3_query(ByteBuffer.wrap(query.getBytes()), Compression.NONE,
                    ConsistencyLevel.ONE);
        entityManager.refresh(o1);
        Object oo1 = entityManager.find(PersonCassandra.class, "1");
        Assert.assertTrue(entityManager.contains(o1));
        Assert.assertEquals("Amry", ((PersonCassandra) oo1).getPersonName());
    }

    /**
     * On typed create query
     * 
     * @throws TException
     * @throws InvalidRequestException
     * @throws UnavailableException
     * @throws TimedOutException
     * @throws SchemaDisagreementException
     */
    @Test
    public void onTypedQuery() throws TException, InvalidRequestException, UnavailableException, TimedOutException,
            SchemaDisagreementException
    {
        // CassandraCli.createKeySpace("KunderaExamples");
        // loadData();

        Object p1 = prepareData("1", 10);
        Object p2 = prepareData("2", 20);
        Object p3 = prepareData("3", 15);
        entityManager.persist(p1);
        entityManager.persist(p2);
        entityManager.persist(p3);
        TypedQuery<PersonCassandra> query = entityManager.createQuery("Select p from PersonCassandra p",
                PersonCassandra.class);

        List<PersonCassandra> results = query.getResultList();
        Assert.assertNotNull(query);
        Assert.assertNotNull(results);
        Assert.assertEquals(3, results.size());
        Assert.assertEquals(Month.APRIL, results.get(0).getMonth());
    }

    /**
     * On typed create query
     * 
     * @throws TException
     * @throws InvalidRequestException
     * @throws UnavailableException
     * @throws TimedOutException
     * @throws SchemaDisagreementException
     */
    @Test
    public void onGenericTypedQuery() throws TException, InvalidRequestException, UnavailableException,
            TimedOutException, SchemaDisagreementException
    {
        // CassandraCli.createKeySpace("KunderaExamples");
        // loadData();

        Object p1 = prepareData("1", 10);
        Object p2 = prepareData("2", 20);
        Object p3 = prepareData("3", 15);
        entityManager.persist(p1);
        entityManager.persist(p2);
        entityManager.persist(p3);
        TypedQuery<Object> query = entityManager.createQuery("Select p from PersonCassandra p", Object.class);

        List<Object> results = query.getResultList();
        Assert.assertNotNull(query);
        Assert.assertNotNull(results);
        Assert.assertEquals(3, results.size());
        Assert.assertEquals(PersonCassandra.class, results.get(0).getClass());
    }

    /**
     * on invalid typed query.
     * 
     * @throws TException
     * @throws InvalidRequestException
     * @throws UnavailableException
     * @throws TimedOutException
     * @throws SchemaDisagreementException
     */
    @Test
    public void onInvalidTypedQuery() throws TException, InvalidRequestException, UnavailableException,
            TimedOutException, SchemaDisagreementException
    {
        // CassandraCli.createKeySpace("KunderaExamples");
        // loadData();

        Object p1 = prepareData("1", 10);
        Object p2 = prepareData("2", 20);
        Object p3 = prepareData("3", 15);
        entityManager.persist(p1);
        entityManager.persist(p2);
        entityManager.persist(p3);

        TypedQuery<Group> query = null;
        try
        {
            query = entityManager.createQuery("Select p from PersonCassandra p", Group.class);
            Assert.fail("Should have gone to catch block, as it is an invalid scenario!");
        }
        catch (IllegalArgumentException iaex)
        {
            Assert.assertNull(query);
        }
    }

    @Test
    public void onGhostRows() throws TException, InvalidRequestException, UnavailableException, TimedOutException,
            SchemaDisagreementException
    {
        // CassandraCli.createKeySpace("KunderaExamples");
        // loadData();
        Object p1 = prepareData("1", 10);
        Object p2 = prepareData("2", 20);
        Object p3 = prepareData("3", 15);
        entityManager.persist(p1);
        entityManager.persist(p2);
        entityManager.persist(p3);
        entityManager.clear();
        PersonCassandra person = entityManager.find(PersonCassandra.class, "1");
        entityManager.remove(person);
        entityManager.clear(); // just to make sure that not to be picked up
                               // from cache.
        TypedQuery<PersonCassandra> query = entityManager.createQuery("Select p from PersonCassandra p",
                PersonCassandra.class);

        List<PersonCassandra> results = query.getResultList();
        Assert.assertNotNull(results);
        Assert.assertEquals(2, results.size());

    }

    @Test
    public void testWithMultipleThread() throws TException, InvalidRequestException, UnavailableException,
            TimedOutException, SchemaDisagreementException
    {
        // CassandraCli.createKeySpace("KunderaExamples");
        // loadData();

        // EM
        ExecutorService executor = Executors.newFixedThreadPool(10);

        List<Future> futureList = new ArrayList<Future>();

        for (int i = 0; i < 10; i++)
        {
            HandlePersist persist = new HandlePersist(i);
            futureList.add(executor.submit(persist));
        }

        while (!futureList.isEmpty())
        {
            for (int i = 0; i < futureList.size(); i++)
            {
                if (futureList.get(i).isDone())
                {
                    futureList.remove(i);
                }
            }
        }

        String qry = "Select * from \"PERSON\"";
        Query q = entityManager.createNativeQuery(qry, PersonCassandra.class);
        List<PersonCassandra> persons = q.getResultList();
        Assert.assertNotNull(persons);
        Assert.assertFalse(persons.isEmpty());
        Assert.assertEquals(10000, persons.size());
    }

    // }

    /**
     * Tear down.
     * 
     * @throws Exception
     *             the exception
     */
    @After
    public void tearDown() throws Exception
    {
        entityManager.close();
        emf.close();
        CassandraCli.dropKeySpace("KunderaExamples");
    }

    private class HandlePersist implements Runnable
    {
        private int i;

        public HandlePersist(int i)
        {
            this.i = i;
        }

        @Override
        public void run()
        {
            for (int j = i * 1000; j < (i + 1) * 1000; j++)
            {
                entityManager.persist(prepareData("" + j, j + 10));
            }
        }
    }

}
