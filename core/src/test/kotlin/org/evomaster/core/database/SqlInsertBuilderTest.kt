package org.evomaster.core.database

import org.evomaster.client.java.controller.api.dto.database.operations.DatabaseCommandDto
import org.evomaster.client.java.controller.api.dto.database.operations.InsertionResultsDto
import org.evomaster.client.java.controller.api.dto.database.operations.QueryResultDto
import org.evomaster.client.java.controller.db.SqlScriptRunner
import org.evomaster.client.java.controller.internal.db.SchemaExtractor
import org.evomaster.core.search.gene.*
import org.evomaster.core.search.gene.sql.SqlAutoIncrementGene
import org.evomaster.core.search.gene.sql.SqlPrimaryKeyGene
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import java.sql.Connection
import java.sql.DriverManager

class SqlInsertBuilderTest {


    companion object {

        private lateinit var connection: Connection

        @BeforeAll
        @JvmStatic
        fun initClass() {
            connection = DriverManager.getConnection("jdbc:h2:mem:db_test", "sa", "")
        }
    }

    @BeforeEach
    fun initTest() {

        //custom H2 command
        SqlScriptRunner.execCommand(connection, "DROP ALL OBJECTS;")
    }


    @Test
    fun testSimpleInt() {

        SqlScriptRunner.execCommand(connection, "CREATE TABLE Foo(x INT not null);")

        val dto = SchemaExtractor.extract(connection)

        val builder = SqlInsertBuilder(dto)

        val actions = builder.createSqlInsertionAction("FOO", setOf("X"))

        assertEquals(1, actions.size)

        val genes = actions[0].seeGenes()

        assertEquals(1, genes.size)
        assertTrue(genes[0] is IntegerGene)
    }


    @Test
    fun testColumnSelection() {

        SqlScriptRunner.execCommand(connection, """
                    CREATE TABLE Foo(
                        id bigint generated by default as identity primary key,
                        x INT,
                        y INT not null,
                        z INT
                    );
                """)

        val dto = SchemaExtractor.extract(connection)

        val builder = SqlInsertBuilder(dto)

        val actions = builder.createSqlInsertionAction("FOO", setOf("X"))

        assertEquals(1, actions.size)

        val genes = actions[0].seeGenes()

        assertTrue(genes.any { it.name.equals("x", ignoreCase = true) })
        assertTrue(genes.any { it.name.equals("y", ignoreCase = true) })

        /*
            - id should had been skipped because auto-incremented value.
              however, being a primary key, we still need it for the tests
              in a non-modifiable, non-printable Gene.
            - z skipped because nullable and not requested
         */
        assertTrue(genes.any { it.name.equals("id", ignoreCase = true) })
        assertFalse(genes.any { it.name.equals("z", ignoreCase = true) })

        assertEquals(3, genes.size)
        assertEquals(1, genes.filterIsInstance(IntegerGene::class.java).size)
        assertEquals(1, genes.filterIsInstance(SqlPrimaryKeyGene::class.java).size)
    }


    @Test
    fun testVarchar() {

        SqlScriptRunner.execCommand(connection, """
                    CREATE TABLE Foo(
                        x varchar(255)  not null,
                        y VARCHAR(128)  not null,
                        z varchar  not null
                    );
                """)

        val dto = SchemaExtractor.extract(connection)

        val builder = SqlInsertBuilder(dto)

        val actions = builder.createSqlInsertionAction("FOO", setOf("X", "Y", "Z"))

        assertEquals(1, actions.size)

        val genes = actions[0].seeGenes()

        assertEquals(3, genes.size)
        assertTrue(genes[0] is StringGene)
        assertTrue(genes[1] is StringGene)
        assertTrue(genes[2] is StringGene)

        val x = genes.find { it.name.equals("X", ignoreCase = true) } as StringGene
        assertEquals(0, x.minLength)
        assertEquals(255, x.maxLength)

        val y = genes.find { it.name.equals("Y", ignoreCase = true) } as StringGene
        assertEquals(0, y.minLength)
        assertEquals(128, y.maxLength)

        val z = genes.find { it.name.equals("Z", ignoreCase = true) } as StringGene
        assertEquals(0, z.minLength)
        assertEquals(Int.MAX_VALUE, z.maxLength)
    }


    @Test
    fun testForeignKey() {
        SqlScriptRunner.execCommand(connection, """
            CREATE TABLE Foo(
                id bigint generated by default as identity,
                barId bigint not null,
                primary key (id)
            );
            CREATE TABLE Bar(
                id bigint generated by default as identity,
                primary key (id)
            );
            ALTER TABLE Foo add constraint barIdKey foreign key (barId) references Bar;
        """)

        val dto = SchemaExtractor.extract(connection)

        val builder = SqlInsertBuilder(dto)

        //Bar is independent
        val barActions = builder.createSqlInsertionAction("BAR", setOf())
        assertEquals(1, barActions.size)

        //Foo has a FK to Bar
        val fooActions = builder.createSqlInsertionAction("FOO", setOf())
        assertEquals(2, fooActions.size)
    }


    @Test
    fun testTimeStamp() {

        SqlScriptRunner.execCommand(connection, """
            create table Foo (
                time timestamp not null
            )
        """)

        val dto = SchemaExtractor.extract(connection)

        val builder = SqlInsertBuilder(dto)

        val actions = builder.createSqlInsertionAction("FOO", setOf("TIME"))
        assertEquals(1, actions.size)

        val genes = actions[0].seeGenes()
        assertEquals(1, genes.size)
        assertTrue(genes[0] is DateTimeGene)

        val dateTimeGene = genes[0] as DateTimeGene
        assertEquals(DateTimeGene.DateTimeGeneFormat.DEFAULT_DATE_TIME, dateTimeGene.dateTimeGeneFormat)
        assertTrue(dateTimeGene.date.onlyValidDates)
        assertTrue(dateTimeGene.time.onlyValidHours)
    }

    @Test
    fun testRealColumn() {

        SqlScriptRunner.execCommand(connection, "CREATE TABLE Foo(x REAL not null);")

        val dto = SchemaExtractor.extract(connection)

        val builder = SqlInsertBuilder(dto)

        val actions = builder.createSqlInsertionAction("FOO", setOf("X"))

        assertEquals(1, actions.size)

        val genes = actions[0].seeGenes()

        assertEquals(1, genes.size)
        assertTrue(genes[0] is DoubleGene)
    }


    @Test
    fun testCLOBColumn() {

        SqlScriptRunner.execCommand(connection, "CREATE TABLE Foo(x CLOB not null);")

        val dto = SchemaExtractor.extract(connection)

        val builder = SqlInsertBuilder(dto)

        val actions = builder.createSqlInsertionAction("FOO", setOf("X"))

        assertEquals(1, actions.size)

        val genes = actions[0].seeGenes()

        assertEquals(1, genes.size)
        assertTrue(genes[0] is StringGene)
    }

    @Test
    fun testSmallIntColumn() {

        SqlScriptRunner.execCommand(connection, "CREATE TABLE Foo(x SMALLINT not null);")

        val dto = SchemaExtractor.extract(connection)

        val builder = SqlInsertBuilder(dto)

        val actions = builder.createSqlInsertionAction("FOO", setOf("X"))

        assertEquals(1, actions.size)

        val genes = actions[0].seeGenes()

        assertEquals(1, genes.size)
        assertTrue(genes[0] is IntegerGene)
    }

    @Test
    fun testTinyIntColumn() {

        SqlScriptRunner.execCommand(connection, "CREATE TABLE Foo(x TINYINT not null);")

        val dto = SchemaExtractor.extract(connection)

        val builder = SqlInsertBuilder(dto)

        val actions = builder.createSqlInsertionAction("FOO", setOf("X"))

        assertEquals(1, actions.size)

        val genes = actions[0].seeGenes()

        assertEquals(1, genes.size)
        assertTrue(genes[0] is IntegerGene)
    }



    @Test
    fun testTimeStampColumn() {

        SqlScriptRunner.execCommand(connection, "CREATE TABLE Foo(x TIMESTAMP not null);")

        val dto = SchemaExtractor.extract(connection)

        val builder = SqlInsertBuilder(dto)

        val actions = builder.createSqlInsertionAction("FOO", setOf("X"))

        assertEquals(1, actions.size)

        val genes = actions[0].seeGenes()

        assertEquals(1, genes.size)
        assertTrue(genes[0] is DateTimeGene)

        val dateTimeGene = genes[0] as DateTimeGene
        assertEquals(DateTimeGene.DateTimeGeneFormat.DEFAULT_DATE_TIME, dateTimeGene.dateTimeGeneFormat)
        assertTrue(dateTimeGene.date.onlyValidDates)
        assertTrue(dateTimeGene.time.onlyValidHours)
    }

    @Test
    fun testBooleanColumn() {

        SqlScriptRunner.execCommand(connection, "CREATE TABLE Foo(x BOOLEAN not null);")

        val dto = SchemaExtractor.extract(connection)

        val builder = SqlInsertBuilder(dto)

        val actions = builder.createSqlInsertionAction("FOO", setOf("X"))

        assertEquals(1, actions.size)

        val genes = actions[0].seeGenes()

        assertEquals(1, genes.size)
        assertTrue(genes[0] is BooleanGene)
    }

    @Test
    fun testCharColumn() {

        SqlScriptRunner.execCommand(connection, "CREATE TABLE Foo(x CHAR not null);")

        val dto = SchemaExtractor.extract(connection)

        val builder = SqlInsertBuilder(dto)

        val actions = builder.createSqlInsertionAction("FOO", setOf("X"))

        assertEquals(1, actions.size)

        val genes = actions[0].seeGenes()

        assertEquals(1, genes.size)
        assertTrue(genes[0] is StringGene)
    }


    @Test
    fun testBigIntColumn() {

        SqlScriptRunner.execCommand(connection, "CREATE TABLE Foo(x BIGINT not null);")

        val dto = SchemaExtractor.extract(connection)

        val builder = SqlInsertBuilder(dto)

        val actions = builder.createSqlInsertionAction("FOO", setOf("X"))

        assertEquals(1, actions.size)

        val genes = actions[0].seeGenes()

        assertEquals(1, genes.size)
        assertTrue(genes[0] is LongGene)
    }

    @Test
    fun testDoubleColumn() {

        SqlScriptRunner.execCommand(connection, "CREATE TABLE Foo(x DOUBLE not null);")

        val dto = SchemaExtractor.extract(connection)

        val builder = SqlInsertBuilder(dto)

        val actions = builder.createSqlInsertionAction("FOO", setOf("X"))

        assertEquals(1, actions.size)

        val genes = actions[0].seeGenes()

        assertEquals(1, genes.size)
        assertTrue(genes[0] is DoubleGene)
    }

    @Test
    fun testTableCalledUsers() {

        SqlScriptRunner.execCommand(connection, "CREATE TABLE Users(id  bigserial not null, primary key (id));")

        val dto = SchemaExtractor.extract(connection)

        val builder = SqlInsertBuilder(dto)

        val actions = builder.createSqlInsertionAction("USERS", setOf("ID"))

        assertEquals(1, actions.size)

        val genes = actions[0].seeGenes()

        assertEquals(1, genes.size)
        assertTrue(genes[0] is SqlPrimaryKeyGene)
        assertTrue((genes[0] as SqlPrimaryKeyGene).gene is SqlAutoIncrementGene)
    }


    private class DirectDatabaseExecutor : DatabaseExecutor {

        override fun executeDatabaseInsertionsAndGetIdMapping(dto: DatabaseCommandDto): InsertionResultsDto? {
            return null
        }

        override fun executeDatabaseCommandAndGetQueryResults(dto: DatabaseCommandDto): QueryResultDto? {
            return SqlScriptRunner.execCommand(connection, dto.command).toDto()
        }

        override fun executeDatabaseCommand(dto: DatabaseCommandDto): Boolean {
            return false
        }
    }


    @Test
    fun testExtractExistingPKsEmpty() {

        SqlScriptRunner.execCommand(connection, "CREATE TABLE Users(id  bigserial not null, primary key (id));")

        val schema = SchemaExtractor.extract(connection)
        val builder = SqlInsertBuilder(schema, DirectDatabaseExecutor())

        val actions = builder.extractExistingPKs()
        assertEquals(0, actions.size)
    }


    @Test
    fun testExtractExistingPKsOneTable() {

        SqlScriptRunner.execCommand(connection, "CREATE TABLE Users(id  bigserial not null, primary key (id));")
        SqlScriptRunner.execCommand(connection, "INSERT INTO Users (id) VALUES (0)")
        SqlScriptRunner.execCommand(connection, "INSERT INTO Users (id) VALUES (1)")
        SqlScriptRunner.execCommand(connection, "INSERT INTO Users (id) VALUES (2)")

        val schema = SchemaExtractor.extract(connection)
        val builder = SqlInsertBuilder(schema, DirectDatabaseExecutor())

        val actions = builder.extractExistingPKs()

        assertAll(
                { assertEquals(3, actions.size) },
                { actions.all { it.representExistingData } },
                { actions.all { it.seeGenes().size == 1 } },
                { actions.all { it.seeGenes()[0] is SqlPrimaryKeyGene } },
                { actions.all { (it.seeGenes()[0] as SqlPrimaryKeyGene).gene is ImmutableDataHolderGene } }
        )
    }


    @Test
    fun testExtractExistingPKsMultiTables() {

        SqlScriptRunner.execCommand(connection, """
            CREATE TABLE X (id  bigserial not null, primary key (id));
            CREATE TABLE Y (foo varchar(256), bar int, primary key(foo));
            INSERT INTO X (id) VALUES (0);
            INSERT INTO X (id) VALUES (1);
            INSERT INTO X (id) VALUES (2);
            INSERT INTO Y (foo,bar) VALUES ('a',5);
            INSERT INTO Y (foo,bar) VALUES ('b',6);
        """)

        val schema = SchemaExtractor.extract(connection)
        val builder = SqlInsertBuilder(schema, DirectDatabaseExecutor())

        val actions = builder.extractExistingPKs()

        assertAll(
                { assertEquals(5, actions.size) },
                { actions.all { it.representExistingData } },
                { assertEquals(2, actions.map { it.table.name }.distinct().count()) },
                { actions.all { it.seeGenes().size == 1 } },
                { actions.all { it.seeGenes()[0] is SqlPrimaryKeyGene } },
                { actions.all { (it.seeGenes()[0] as SqlPrimaryKeyGene).gene is ImmutableDataHolderGene } }
        )
    }

    @Test
    fun testStringEnumGene() {

        SqlScriptRunner.execCommand(connection, """
            CREATE TABLE FOO (status varchar(1) not null);
            ALTER TABLE FOO ADD CONSTRAINT CHK_STATUS CHECK (status in ('A', 'B'));
            """)

        val schema = SchemaExtractor.extract(connection)
        val builder = SqlInsertBuilder(schema, DirectDatabaseExecutor())

        val actions = builder.createSqlInsertionAction("FOO", setOf("status"))

        assertEquals(1, actions.size)

        assertEquals(1, actions[0].seeGenes().size)
        assertTrue(actions[0].seeGenes()[0] is EnumGene<*>)

        val enumGene = actions[0].seeGenes()[0] as EnumGene<*>;

        assertEquals(setOf("A", "B"), enumGene.values.toSet());

    }

    @Test
    fun testIntegerEnumGene() {

        SqlScriptRunner.execCommand(connection, """
            CREATE TABLE FOO (status INT not null);
            ALTER TABLE FOO ADD CONSTRAINT CHK_STATUS CHECK (status in (42, 77));
            """)

        val schema = SchemaExtractor.extract(connection)
        val builder = SqlInsertBuilder(schema, DirectDatabaseExecutor())

        val actions = builder.createSqlInsertionAction("FOO", setOf("status"))

        assertEquals(1, actions.size)

        assertEquals(1, actions[0].seeGenes().size)
        assertTrue(actions[0].seeGenes()[0] is EnumGene<*>)

        val enumGene = actions[0].seeGenes()[0] as EnumGene<*>;

        assertEquals(setOf(42, 77), enumGene.values.toSet());

    }


    @Test
    fun testBooleanEnumGene() {

        SqlScriptRunner.execCommand(connection, """
            CREATE TABLE FOO (status BOOLEAN not null);
            ALTER TABLE FOO ADD CONSTRAINT CHK_STATUS CHECK (status in (true, false));
            """)

        val schema = SchemaExtractor.extract(connection)
        val builder = SqlInsertBuilder(schema, DirectDatabaseExecutor())

        val actions = builder.createSqlInsertionAction("FOO", setOf("status"))

        assertEquals(1, actions.size)

        assertEquals(1, actions[0].seeGenes().size)
        assertTrue(actions[0].seeGenes()[0] is EnumGene<*>)

        val enumGene = actions[0].seeGenes()[0] as EnumGene<*>;

        assertEquals(setOf(true, false), enumGene.values.toSet());

    }

    @Test
    fun testTinyIntEnumGene() {

        SqlScriptRunner.execCommand(connection, """
            CREATE TABLE FOO (status TINYINT not null);
            ALTER TABLE FOO ADD CONSTRAINT CHK_STATUS CHECK (status in (42, 77));
            """)

        val schema = SchemaExtractor.extract(connection)
        val builder = SqlInsertBuilder(schema, DirectDatabaseExecutor())

        val actions = builder.createSqlInsertionAction("FOO", setOf("status"))

        assertEquals(1, actions.size)

        assertEquals(1, actions[0].seeGenes().size)
        assertTrue(actions[0].seeGenes()[0] is EnumGene<*>)

        val enumGene = actions[0].seeGenes()[0] as EnumGene<*>;

        assertEquals(setOf(42, 77), enumGene.values.toSet());

    }

    @Test
    fun testSmallIntEnumGene() {

        SqlScriptRunner.execCommand(connection, """
            CREATE TABLE FOO (status SMALLINT not null);
            ALTER TABLE FOO ADD CONSTRAINT CHK_STATUS CHECK (status in (42, 77));
            """)

        val schema = SchemaExtractor.extract(connection)
        val builder = SqlInsertBuilder(schema, DirectDatabaseExecutor())

        val actions = builder.createSqlInsertionAction("FOO", setOf("status"))

        assertEquals(1, actions.size)

        assertEquals(1, actions[0].seeGenes().size)
        assertTrue(actions[0].seeGenes()[0] is EnumGene<*>)

        val enumGene = actions[0].seeGenes()[0] as EnumGene<*>;

        assertEquals(setOf(42, 77), enumGene.values.toSet());

    }

    @Test
    fun testCharEnumGene() {

        SqlScriptRunner.execCommand(connection, """
            CREATE TABLE FOO (status CHAR not null);
            ALTER TABLE FOO ADD CONSTRAINT CHK_STATUS CHECK (status in ('A', 'B'));
            """)

        val schema = SchemaExtractor.extract(connection)
        val builder = SqlInsertBuilder(schema, DirectDatabaseExecutor())

        val actions = builder.createSqlInsertionAction("FOO", setOf("status"))

        assertEquals(1, actions.size)

        assertEquals(1, actions[0].seeGenes().size)
        assertTrue(actions[0].seeGenes()[0] is EnumGene<*>)

        val enumGene = actions[0].seeGenes()[0] as EnumGene<*>;

        assertEquals(setOf("A", "B"), enumGene.values.toSet());

    }

    @Test
    fun testBigIntEnumGene() {

        SqlScriptRunner.execCommand(connection, """
            CREATE TABLE FOO (status BIGINT not null);
            ALTER TABLE FOO ADD CONSTRAINT CHK_STATUS CHECK (status in (42, 77));
            """)

        val schema = SchemaExtractor.extract(connection)
        val builder = SqlInsertBuilder(schema, DirectDatabaseExecutor())

        val actions = builder.createSqlInsertionAction("FOO", setOf("status"))

        assertEquals(1, actions.size)

        assertEquals(1, actions[0].seeGenes().size)
        assertTrue(actions[0].seeGenes()[0] is EnumGene<*>)

        val enumGene = actions[0].seeGenes()[0] as EnumGene<*>;

        assertEquals(setOf(42L, 77L), enumGene.values.toSet());

    }

    @Test
    fun testDoubleEnumGene() {

        SqlScriptRunner.execCommand(connection, """
            CREATE TABLE FOO (status DOUBLE not null);
            ALTER TABLE FOO ADD CONSTRAINT CHK_STATUS CHECK (status in (1.0, 2.5));
            """)

        val schema = SchemaExtractor.extract(connection)
        val builder = SqlInsertBuilder(schema, DirectDatabaseExecutor())

        val actions = builder.createSqlInsertionAction("FOO", setOf("status"))

        assertEquals(1, actions.size)

        assertEquals(1, actions[0].seeGenes().size)
        assertTrue(actions[0].seeGenes()[0] is EnumGene<*>)

        val enumGene = actions[0].seeGenes()[0] as EnumGene<*>;

        assertEquals(setOf(1.0, 2.5), enumGene.values.toSet());

    }

    @Test
    fun testRealEnumGene() {

        SqlScriptRunner.execCommand(connection, """
            CREATE TABLE FOO (status REAL not null);
            ALTER TABLE FOO ADD CONSTRAINT CHK_STATUS CHECK (status in (1.0, 2.5));
            """)

        val schema = SchemaExtractor.extract(connection)
        val builder = SqlInsertBuilder(schema, DirectDatabaseExecutor())

        val actions = builder.createSqlInsertionAction("FOO", setOf("status"))

        assertEquals(1, actions.size)

        assertEquals(1, actions[0].seeGenes().size)
        assertTrue(actions[0].seeGenes()[0] is EnumGene<*>)

        val enumGene = actions[0].seeGenes()[0] as EnumGene<*>;

        assertEquals(setOf(1.0, 2.5), enumGene.values.toSet());

    }

    @Test
    fun testDecimalEnumGene() {

        SqlScriptRunner.execCommand(connection, """
            CREATE TABLE FOO (status DECIMAL not null);
            ALTER TABLE FOO ADD CONSTRAINT CHK_STATUS CHECK (status in (1.0, 2.5));
            """)

        val schema = SchemaExtractor.extract(connection)
        val builder = SqlInsertBuilder(schema, DirectDatabaseExecutor())

        val actions = builder.createSqlInsertionAction("FOO", setOf("status"))

        assertEquals(1, actions.size)

        assertEquals(1, actions[0].seeGenes().size)
        assertTrue(actions[0].seeGenes()[0] is EnumGene<*>)

        val enumGene = actions[0].seeGenes()[0] as EnumGene<*>;

        assertEquals(setOf(1.0f, 2.5f), enumGene.values.toSet());

    }

    @Test
    fun testClobEnumGene() {

        SqlScriptRunner.execCommand(connection, """
            CREATE TABLE FOO (status CLOB not null);
            ALTER TABLE FOO ADD CONSTRAINT CHK_STATUS CHECK (status in ('A', 'B'));
            """)

        val schema = SchemaExtractor.extract(connection)
        val builder = SqlInsertBuilder(schema, DirectDatabaseExecutor())

        val actions = builder.createSqlInsertionAction("FOO", setOf("status"))

        assertEquals(1, actions.size)

        assertEquals(1, actions[0].seeGenes().size)
        assertTrue(actions[0].seeGenes()[0] is EnumGene<*>)

        val enumGene = actions[0].seeGenes()[0] as EnumGene<*>;

        assertEquals(setOf("A", "B"), enumGene.values.toSet());

    }

    @Test
    fun testBlobEnumGene() {

        SqlScriptRunner.execCommand(connection, """
            CREATE TABLE FOO (status BLOB not null);
            ALTER TABLE FOO ADD CONSTRAINT CHK_STATUS CHECK (status in (x'0000', x'FFFF'));
            """)

        val schema = SchemaExtractor.extract(connection)
        val builder = SqlInsertBuilder(schema, DirectDatabaseExecutor())

        val actions = builder.createSqlInsertionAction("FOO", setOf("status"))

        assertEquals(1, actions.size)

        assertEquals(1, actions[0].seeGenes().size)
        assertTrue(actions[0].seeGenes()[0] is EnumGene<*>)

        val enumGene = actions[0].seeGenes()[0] as EnumGene<*>;

        assertEquals(setOf("X'0000'", "X'ffff'"), enumGene.values.toSet());

    }

    @Test
    fun testMultipleLowerBounds() {
        SqlScriptRunner.execCommand(connection, """
            CREATE TABLE Foo(x INT not null);
            
            ALTER TABLE Foo add constraint lowerBound1 check (x >= -10);

            ALTER TABLE Foo add constraint lowerBound2 check (x >= -100);

            ALTER TABLE Foo add constraint lowerBound3 check (x >= -1000);
        """)

        val dto = SchemaExtractor.extract(connection)

        val builder = SqlInsertBuilder(dto)

        val fooActions = builder.createSqlInsertionAction("FOO", setOf())

        assertEquals(1, fooActions.size)
        assertEquals(1, fooActions[0].seeGenes().size)

        val gene = fooActions[0].seeGenes()[0] as IntegerGene
        assertEquals(-10, gene.min)

    }

    @Test
    fun testMultipleUpperBounds() {
        SqlScriptRunner.execCommand(connection, """
            CREATE TABLE Foo(x INT not null);
            
            ALTER TABLE Foo add constraint upperBound1 check (x <= 10);

            ALTER TABLE Foo add constraint upperBound2 check (x <= 100);

            ALTER TABLE Foo add constraint upperBound3 check (x <= 1000);
        """)

        val dto = SchemaExtractor.extract(connection)

        val builder = SqlInsertBuilder(dto)

        val fooActions = builder.createSqlInsertionAction("FOO", setOf())

        assertEquals(1, fooActions.size)
        assertEquals(1, fooActions[0].seeGenes().size)

        val gene = fooActions[0].seeGenes()[0] as IntegerGene
        assertEquals(10, gene.max)

    }

    @Test
    fun testMultipleUpperAndLowerBounds() {
        SqlScriptRunner.execCommand(connection, """
            CREATE TABLE Foo(x INT not null);
            
            
            ALTER TABLE Foo add constraint lowerBound1 check (x >= -10);

            ALTER TABLE Foo add constraint lowerBound2 check (x >= -100);

            ALTER TABLE Foo add constraint lowerBound3 check (x >= -1000);
            
            ALTER TABLE Foo add constraint upperBound1 check (x <= 10);

            ALTER TABLE Foo add constraint upperBound2 check (x <= 100);

            ALTER TABLE Foo add constraint upperBound3 check (x <= 1000);
        """)

        val dto = SchemaExtractor.extract(connection)

        val builder = SqlInsertBuilder(dto)

        val fooActions = builder.createSqlInsertionAction("FOO", setOf())

        assertEquals(1, fooActions.size)
        assertEquals(1, fooActions[0].seeGenes().size)

        val gene = fooActions[0].seeGenes()[0] as IntegerGene
        assertEquals(-10, gene.min)
        assertEquals(10, gene.max)

    }

    @Test
    fun testMultipleRangeConstraintAndLowerAndUpperBounds() {
        SqlScriptRunner.execCommand(connection, """
            CREATE TABLE Foo(x INT not null);
            
            ALTER TABLE Foo add constraint rangeConstraint check (x = 10);

            ALTER TABLE Foo add constraint lowerBound check (x >= 0);

            ALTER TABLE Foo add constraint upperBound check (x <= 1000);
            
        """)

        val dto = SchemaExtractor.extract(connection)

        val builder = SqlInsertBuilder(dto)

        val fooActions = builder.createSqlInsertionAction("FOO", setOf())

        assertEquals(1, fooActions.size)
        assertEquals(1, fooActions[0].seeGenes().size)

        val gene = fooActions[0].seeGenes()[0] as IntegerGene
        assertEquals(10, gene.min)
        assertEquals(10, gene.max)

    }


    @Test
    fun testIntersectEnumConstraints() {

        SqlScriptRunner.execCommand(connection, """
            CREATE TABLE FOO (status CHAR not null);

            ALTER TABLE FOO ADD CONSTRAINT CHK_STATUS1 CHECK (status in ('A', 'B'));

            ALTER TABLE FOO ADD CONSTRAINT CHK_STATUS2 CHECK (status in ('B', 'C'));

            ALTER TABLE FOO ADD CONSTRAINT CHK_STATUS3 CHECK (status in ('D', 'B'));

            ALTER TABLE FOO ADD CONSTRAINT CHK_STATUS4 CHECK (status in ('X', 'B'));
            """)

        val schema = SchemaExtractor.extract(connection)
        val builder = SqlInsertBuilder(schema, DirectDatabaseExecutor())

        val actions = builder.createSqlInsertionAction("FOO", setOf("status"))

        assertEquals(1, actions.size)

        assertEquals(1, actions[0].seeGenes().size)
        assertTrue(actions[0].seeGenes()[0] is EnumGene<*>)

        val enumGene = actions[0].seeGenes()[0] as EnumGene<*>;

        assertEquals(setOf("B"), enumGene.values.toSet());

    }


    @Test
    fun testNoIntersectionEnumConstraints() {

        SqlScriptRunner.execCommand(connection, """
            CREATE TABLE FOO (status CHAR not null);

            ALTER TABLE FOO ADD CONSTRAINT CHK_STATUS1 CHECK (status in ('A', 'B', 'C'));

            ALTER TABLE FOO ADD CONSTRAINT CHK_STATUS2 CHECK (status in ('D', 'E', 'F'));
            """)

        val schema = SchemaExtractor.extract(connection)
        val builder = SqlInsertBuilder(schema, DirectDatabaseExecutor())

        try {
            builder.createSqlInsertionAction("FOO", setOf("status"))
            fail<Object>()
        } catch (ex: RuntimeException) {

        }
    }

    @Test
    fun testSingleLikeConstraint() {

        SqlScriptRunner.execCommand(connection, """
            CREATE TABLE FOO (f_id TEXT NOT NULL);

            ALTER TABLE FOO ADD CONSTRAINT check_f_id CHECK (f_id LIKE 'hi');
            """)

        val schema = SchemaExtractor.extract(connection)
        val builder = SqlInsertBuilder(schema, DirectDatabaseExecutor())

        val actions = builder.createSqlInsertionAction("FOO", setOf("f_id"))

        assertEquals(1, actions.size)

        assertEquals(1, actions[0].seeGenes().size)
        assertTrue(actions[0].seeGenes()[0] is EnumGene<*>)

        val enumGene = actions[0].seeGenes()[0] as EnumGene<*>;

        assertEquals(setOf("hi"), enumGene.values.toSet());

    }

    @Test
    fun testManyLikeConstraints() {

        SqlScriptRunner.execCommand(connection, """
            CREATE TABLE FOO (f_id TEXT NOT NULL);

            ALTER TABLE FOO ADD CONSTRAINT check_f_id_1 CHECK (f_id LIKE 'hi');

            ALTER TABLE FOO ADD CONSTRAINT check_f_id_2 CHECK (f_id LIKE 'low');
            """)

        val schema = SchemaExtractor.extract(connection)
        val builder = SqlInsertBuilder(schema, DirectDatabaseExecutor())

        try {
            builder.createSqlInsertionAction("FOO", setOf("f_id"))
            fail<Object>()
        } catch (ex: IllegalArgumentException) {

        }
    }

    @Test
    fun testManyOrLikeConstantConstraint() {

        SqlScriptRunner.execCommand(connection, """
            CREATE TABLE FOO (f_id TEXT NOT NULL);

            ALTER TABLE FOO ADD CONSTRAINT check_f_id_1 CHECK (f_id LIKE 'hi' OR f_id LIKE 'low');

            """)

        val schema = SchemaExtractor.extract(connection)
        val builder = SqlInsertBuilder(schema, DirectDatabaseExecutor())

        val actions = builder.createSqlInsertionAction("FOO", setOf("f_id"))

        assertEquals(1, actions.size)

        assertEquals(1, actions[0].seeGenes().size)
        assertTrue(actions[0].seeGenes()[0] is EnumGene<*>)

        val enumGene = actions[0].seeGenes()[0] as EnumGene<*>;

        assertEquals(setOf("hi", "low"), enumGene.values.toSet());

    }


}