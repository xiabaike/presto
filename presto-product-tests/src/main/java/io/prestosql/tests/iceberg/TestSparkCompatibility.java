/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.prestosql.tests.iceberg;

import io.prestosql.tempto.ProductTest;
import io.prestosql.tempto.query.QueryResult;
import org.testng.annotations.Test;

import java.sql.Date;
import java.sql.Timestamp;

import static io.prestosql.tempto.assertions.QueryAssert.Row;
import static io.prestosql.tempto.assertions.QueryAssert.Row.row;
import static io.prestosql.tempto.assertions.QueryAssert.assertThat;
import static io.prestosql.tests.TestGroups.ICEBERG;
import static io.prestosql.tests.TestGroups.PROFILE_SPECIFIC_TESTS;
import static io.prestosql.tests.utils.QueryExecutors.onPresto;
import static io.prestosql.tests.utils.QueryExecutors.onSpark;
import static java.lang.String.format;

public class TestSparkCompatibility
        extends ProductTest
{
    // TODO: Spark SQL doesn't yet support decimal.  When it does add it to the test.
    // TODO: Spark SQL only stores TIMESTAMP WITH TIME ZONE, and Iceberg only supports
    // TIMESTAMP with no time zone.  The Spark writes/Presto reads test can pass by
    // stripping off the UTC.  However, I haven't been able to get the
    // Presto writes/Spark reads test TIMESTAMPs to match.

    // see spark-defaults.conf
    private static final String SPARK_CATALOG = "iceberg_test";
    private static final String PRESTO_CATALOG = "iceberg";

    @Test(groups = {ICEBERG, PROFILE_SPECIFIC_TESTS})
    public void testPrestoReadingSparkData()
    {
        String baseTableName = "test_presto_reading_primitive_types";
        String sparkTableName = sparkTableName(baseTableName);

        String sparkTableDefinition =
                "CREATE TABLE %s (" +
                        "  _string STRING" +
                        ", _bigint BIGINT" +
                        ", _integer INTEGER" +
                        ", _real REAL" +
                        ", _double DOUBLE" +
                        ", _boolean BOOLEAN" +
                        ", _timestamp TIMESTAMP" +
                        ", _date DATE" +
                        ") USING ICEBERG";
        onSpark().executeQuery(format(sparkTableDefinition, sparkTableName));

        String values = "VALUES (" +
                "'a_string'" +
                ", 1000000000000000" +
                ", 1000000000" +
                ", 10000000.123" +
                ", 100000000000.123" +
                ", true" +
                ", TIMESTAMP '2020-06-28 14:16:00.456'" +
                ", DATE '1950-06-28'" +
                ")";
        String insert = format("INSERT INTO %s %s", sparkTableName, values);
        onSpark().executeQuery(insert);

        Row row = row(
                "a_string",
                1000000000000000L,
                1000000000,
                10000000.123F,
                100000000000.123,
                true,
                Timestamp.valueOf("2020-06-28 14:16:00.456"),
                Date.valueOf("1950-06-28"));

        String startOfSelect = "SELECT _string, _bigint, _integer, _real, _double, _boolean";
        QueryResult sparkSelect = onSpark().executeQuery(format("%s, _timestamp, _date FROM %s", startOfSelect, sparkTableName));
        assertThat(sparkSelect).containsOnly(row);

        QueryResult prestoSelect = onPresto().executeQuery(format("%s, CAST(_timestamp AS TIMESTAMP), _date FROM %s", startOfSelect, prestoTableName(baseTableName)));
        assertThat(prestoSelect).containsOnly(row);

        onSpark().executeQuery("DROP TABLE " + sparkTableName);
    }

    @Test(groups = {ICEBERG, PROFILE_SPECIFIC_TESTS})
    public void testSparkReadingPrestoData()
    {
        String baseTableName = "test_spark_reading_primitive_types";
        String prestoTableName = prestoTableName(baseTableName);

        String prestoTableDefinition =
                "CREATE TABLE %s (" +
                        "  _string VARCHAR" +
                        ", _bigint BIGINT" +
                        ", _integer INTEGER" +
                        ", _real REAL" +
                        ", _double DOUBLE" +
                        ", _boolean BOOLEAN" +
                        //", _timestamp TIMESTAMP" +
                        ", _date DATE" +
                        ") WITH (format = 'ORC')";
        onPresto().executeQuery(format(prestoTableDefinition, prestoTableName));

        String values = "VALUES (" +
                "'a_string'" +
                ", 1000000000000000" +
                ", 1000000000" +
                ", 10000000.123" +
                ", 100000000000.123" +
                ", true" +
                //", TIMESTAMP '2020-06-28 14:16:00.456'" +
                ", DATE '1950-06-28'" +
                ")";
        String insert = format("INSERT INTO %s %s", prestoTableName, values);
        onPresto().executeQuery(insert);

        Row row = row(
                "a_string",
                1000000000000000L,
                1000000000,
                10000000.123F,
                100000000000.123,
                true,
                //"2020-06-28 14:16:00.456",
                "1950-06-28");
        String startOfSelect = "SELECT _string, _bigint, _integer, _real, _double, _boolean";
        QueryResult prestoSelect = onPresto().executeQuery(format("%s, /* CAST(_timestamp AS VARCHAR),*/ CAST(_date AS VARCHAR) FROM %s", startOfSelect, prestoTableName));
        assertThat(prestoSelect).containsOnly(row);

        QueryResult sparkSelect = onSpark().executeQuery(format("%s, /* CAST(_timestamp AS STRING),*/ CAST(_date AS STRING) FROM %s", startOfSelect, sparkTableName(baseTableName)));
        assertThat(sparkSelect).containsOnly(row);

        onPresto().executeQuery("DROP TABLE " + prestoTableName);
    }

    @Test(groups = {ICEBERG, PROFILE_SPECIFIC_TESTS})
    public void testSparkCreatesPrestoDrops()
    {
        String baseTableName = "test_spark_creates_presto_drops";
        onSpark().executeQuery(format("CREATE TABLE %s (_string STRING, _bigint BIGINT) USING ICEBERG", sparkTableName(baseTableName)));
        onPresto().executeQuery("DROP TABLE " + prestoTableName(baseTableName));
    }

    @Test(groups = {ICEBERG, PROFILE_SPECIFIC_TESTS})
    public void testPrestoCreatesSparkDrops()
    {
        String baseTableName = "test_presto_creates_spark_drops";
        onPresto().executeQuery(format("CREATE TABLE %s (_string VARCHAR, _bigint BIGINT)", prestoTableName(baseTableName)));
        onSpark().executeQuery("DROP TABLE " + sparkTableName(baseTableName));
    }

    @Test(groups = {ICEBERG, PROFILE_SPECIFIC_TESTS})
    public void testSparkReadsPrestoPartitionedTable()
    {
        String baseTableName = "test_spark_reads_presto_partitioned_table";
        String prestoTableName = prestoTableName(baseTableName);
        onPresto().executeQuery(format("CREATE TABLE %s (_string VARCHAR, _bigint BIGINT) WITH (partitioning = ARRAY['_string'])", prestoTableName));
        onPresto().executeQuery(format("INSERT INTO %s VALUES ('a', 1001), ('b', 1002), ('c', 1003)", prestoTableName));

        Row row = row("b", 1002);
        String select = "SELECT * FROM %s WHERE _string = 'b'";
        assertThat(onPresto().executeQuery(format(select, prestoTableName)))
                .containsOnly(row);
        assertThat(onSpark().executeQuery(format(select, sparkTableName(baseTableName))))
                .containsOnly(row);
        onPresto().executeQuery("DROP TABLE " + prestoTableName);
    }

    @Test(groups = {ICEBERG, PROFILE_SPECIFIC_TESTS})
    public void testPrestoReadsSparkPartitionedTable()
    {
        String baseTableName = "test_spark_reads_presto_partitioned_table";
        String sparkTableName = sparkTableName(baseTableName);
        onSpark().executeQuery(format("CREATE TABLE %s (_string STRING, _bigint BIGINT) USING ICEBERG PARTITIONED BY (_string)", sparkTableName));
        onSpark().executeQuery(format("INSERT INTO %s VALUES ('a', 1001), ('b', 1002), ('c', 1003)", sparkTableName));

        Row row = row("b", 1002);
        String select = "SELECT * FROM %s WHERE _string = 'b'";
        assertThat(onSpark().executeQuery(format(select, sparkTableName)))
                .containsOnly(row);
        assertThat(onPresto().executeQuery(format(select, prestoTableName(baseTableName))))
                .containsOnly(row);

        onSpark().executeQuery("DROP TABLE " + sparkTableName);
    }

    @Test(groups = {ICEBERG, PROFILE_SPECIFIC_TESTS})
    public void testPrestoReadingCompositeSparkData()
    {
        String baseTableName = "test_presto_reading_spark_composites";
        String sparkTableName = sparkTableName(baseTableName);

        String sparkTableDefinition = "" +
                "CREATE TABLE %s (" +
                "  doc_id string,\n" +
                "  info MAP<STRING, INT>,\n" +
                "  pets ARRAY<STRING>,\n" +
                "  user_info STRUCT<name:STRING, surname:STRING, age:INT, gender:STRING>)" +
                "  USING ICEBERG";
        onSpark().executeQuery(format(sparkTableDefinition, sparkTableName));

        String insert = "" +
                "INSERT INTO TABLE %s SELECT 'Doc213', map('age', 28, 'children', 3), array('Dog', 'Cat', 'Pig'), \n" +
                "named_struct('name', 'Santa', 'surname', 'Claus','age', 1000,'gender', 'MALE')";
        onSpark().executeQuery(format(insert, sparkTableName));

        String prestoTableName = prestoTableName(baseTableName);
        String prestoSelect = "SELECT doc_id, info['age'], pets[2], user_info.surname FROM " + prestoTableName;

        QueryResult prestoResult = onPresto().executeQuery(prestoSelect);
        Row row = row("Doc213", 28, "Cat", "Claus");
        assertThat(prestoResult).containsOnly(row);
    }

    @Test(groups = {ICEBERG, PROFILE_SPECIFIC_TESTS})
    public void testSparkReadingCompositePrestoData()
    {
        String baseTableName = "test_spark_reading_presto_composites";
        String prestoTableName = prestoTableName(baseTableName);

        String prestoTableDefinition = "" +
                "CREATE TABLE %s (" +
                "  doc_id VARCHAR,\n" +
                "  info MAP(VARCHAR, INTEGER),\n" +
                "  pets ARRAY(VARCHAR),\n" +
                "  user_info ROW(name VARCHAR, surname VARCHAR, age INTEGER, gender VARCHAR))";
        onPresto().executeQuery(format(prestoTableDefinition, prestoTableName));

        String insert = "INSERT INTO %s VALUES('Doc213', MAP(ARRAY['age', 'children'], ARRAY[28, 3]), ARRAY['Dog', 'Cat', 'Pig'], ROW('Santa', 'Claus', 1000, 'MALE'))";
        onPresto().executeQuery(format(insert, prestoTableName));

        String sparkTableName = sparkTableName(baseTableName);
        String sparkSelect = "SELECT doc_id, info['age'], pets[1], user_info.surname FROM " + sparkTableName;
        QueryResult sparkResult = onSpark().executeQuery(sparkSelect);
        Row row = row("Doc213", 28, "Cat", "Claus");
        assertThat(sparkResult).containsOnly(row);
    }

    @Test(groups = {ICEBERG, PROFILE_SPECIFIC_TESTS})
    public void testPrestoReadingNestedSparkData()
    {
        String baseTableName = "test_presto_reading_nested_spark_data";
        String sparkTableName = sparkTableName(baseTableName);

        String sparkTableDefinition = "" +
                "CREATE TABLE %s (\n" +
                "  doc_id STRING\n" +
                ", nested_map MAP<STRING, ARRAY<STRUCT<sname: STRING, snumber: INT>>>\n" +
                ", nested_array ARRAY<MAP<STRING, ARRAY<STRUCT<mname: STRING, mnumber: INT>>>>\n" +
                ", nested_struct STRUCT<name:STRING, complicated: ARRAY<MAP<STRING, ARRAY<STRUCT<mname: STRING, mnumber: INT>>>>>)\n" +
                " USING ICEBERG";
        onSpark().executeQuery(format(sparkTableDefinition, sparkTableName));

        String insert = "" +
                "INSERT INTO TABLE %s SELECT" +
                "  'Doc213'" +
                ", map('s1', array(named_struct('sname', 'ASName1', 'snumber', 201), named_struct('sname', 'ASName2', 'snumber', 202)))" +
                ", array(map('m1', array(named_struct('mname', 'MAS1Name1', 'mnumber', 301), named_struct('mname', 'MAS1Name2', 'mnumber', 302)))" +
                "       ,map('m2', array(named_struct('mname', 'MAS2Name1', 'mnumber', 401), named_struct('mname', 'MAS2Name2', 'mnumber', 402))))" +
                ", named_struct('name', 'S1'," +
                "               'complicated', array(map('m1', array(named_struct('mname', 'SAMA1Name1', 'mnumber', 301), named_struct('mname', 'SAMA1Name2', 'mnumber', 302)))" +
                "                                   ,map('m2', array(named_struct('mname', 'SAMA2Name1', 'mnumber', 401), named_struct('mname', 'SAMA2Name2', 'mnumber', 402)))))";
        onSpark().executeQuery(format(insert, sparkTableName));

        Row row = row("Doc213", "ASName2", 201, "MAS2Name1", 302, "SAMA1Name1", 402);

        String sparkSelect = "SELECT" +
                "  doc_id" +
                ", nested_map['s1'][1].sname" +
                ", nested_map['s1'][0].snumber" +
                ", nested_array[1]['m2'][0].mname" +
                ", nested_array[0]['m1'][1].mnumber" +
                ", nested_struct.complicated[0]['m1'][0].mname" +
                ", nested_struct.complicated[1]['m2'][1].mnumber" +
                "  FROM ";

        QueryResult sparkResult = onSpark().executeQuery(sparkSelect + sparkTableName);
        assertThat(sparkResult).containsOnly(row);

        String prestoTableName = prestoTableName(baseTableName);
        String prestoSelect = "SELECT" +
                "  doc_id" +
                ", nested_map['s1'][2].sname" +
                ", nested_map['s1'][1].snumber" +
                ", nested_array[2]['m2'][1].mname" +
                ", nested_array[1]['m1'][2].mnumber" +
                ", nested_struct.complicated[1]['m1'][1].mname" +
                ", nested_struct.complicated[2]['m2'][2].mnumber" +
                "  FROM ";

        QueryResult prestoResult = onPresto().executeQuery(prestoSelect + prestoTableName);
        assertThat(prestoResult).containsOnly(row);
    }

    @Test(groups = {ICEBERG, PROFILE_SPECIFIC_TESTS})
    public void testSparkReadingNestedPrestoData()
    {
        String baseTableName = "test_spark_reading_nested_presto_data";
        String prestoTableName = prestoTableName(baseTableName);

        String prestoTableDefinition = "" +
                "CREATE TABLE %s (\n" +
                "  doc_id VARCHAR\n" +
                ", nested_map MAP(VARCHAR, ARRAY(ROW(sname VARCHAR, snumber INT)))\n" +
                ", nested_array ARRAY(MAP(VARCHAR, ARRAY(ROW(mname VARCHAR, mnumber INT))))\n" +
                ", nested_struct ROW(name VARCHAR, complicated ARRAY(MAP(VARCHAR, ARRAY(ROW(mname VARCHAR, mnumber INT))))))";
        onPresto().executeQuery(format(prestoTableDefinition, prestoTableName));

        String insert = "" +
                "INSERT INTO %s SELECT" +
                "  'Doc213'" +
                ", map(array['s1'], array[array[row('ASName1', 201), row('ASName2', 202)]])" +
                ", array[map(array['m1'], array[array[row('MAS1Name1', 301), row('MAS1Name2', 302)]])" +
                "       ,map(array['m2'], array[array[row('MAS2Name1', 401), row('MAS2Name2', 402)]])]" +
                ", row('S1'" +
                "      ,array[map(array['m1'], array[array[row('SAMA1Name1', 301), row('SAMA1Name2', 302)]])" +
                "            ,map(array['m2'], array[array[row('SAMA2Name1', 401), row('SAMA2Name2', 402)]])])";
        onPresto().executeQuery(format(insert, prestoTableName));

        Row row = row("Doc213", "ASName2", 201, "MAS2Name1", 302, "SAMA1Name1", 402);

        String prestoSelect = "SELECT" +
                "  doc_id" +
                ", nested_map['s1'][2].sname" +
                ", nested_map['s1'][1].snumber" +
                ", nested_array[2]['m2'][1].mname" +
                ", nested_array[1]['m1'][2].mnumber" +
                ", nested_struct.complicated[1]['m1'][1].mname" +
                ", nested_struct.complicated[2]['m2'][2].mnumber" +
                "  FROM ";

        QueryResult prestoResult = onPresto().executeQuery(prestoSelect + prestoTableName);
        assertThat(prestoResult).containsOnly(row);

        String sparkTableName = sparkTableName(baseTableName);
        String sparkSelect = "SELECT" +
                "  doc_id" +
                ", nested_map['s1'][1].sname" +
                ", nested_map['s1'][0].snumber" +
                ", nested_array[1]['m2'][0].mname" +
                ", nested_array[0]['m1'][1].mnumber" +
                ", nested_struct.complicated[0]['m1'][0].mname" +
                ", nested_struct.complicated[1]['m2'][1].mnumber" +
                "  FROM ";

        QueryResult sparkResult = onSpark().executeQuery(sparkSelect + sparkTableName);
        assertThat(sparkResult).containsOnly(row);
    }

    private static String sparkTableName(String tableName)
    {
        return format("%s.default.%s", SPARK_CATALOG, tableName);
    }

    private static String prestoTableName(String tableName)
    {
        return format("%s.default.%s", PRESTO_CATALOG, tableName);
    }
}
