/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.orm.test.type;

import org.hibernate.dialect.AbstractHANADialect;
import org.hibernate.dialect.HSQLDialect;
import org.hibernate.dialect.OracleDialect;
import org.hibernate.dialect.SybaseASEDialect;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.NamedNativeQueries;
import jakarta.persistence.NamedNativeQuery;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Query;
import jakarta.persistence.Table;
import jakarta.persistence.TypedQuery;

import org.hibernate.testing.DialectChecks;
import org.hibernate.testing.RequiresDialectFeature;
import org.hibernate.testing.SkipForDialect;
import org.hibernate.testing.junit4.BaseNonConfigCoreFunctionalTestCase;
import org.junit.Test;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

/**
 * @author Jordan Gigov
 * @author Christian Beikov
 */
@SkipForDialect(value = SybaseASEDialect.class, comment = "Sybase or the driver are trimming trailing zeros in byte arrays")
public class StringArrayTest extends BaseNonConfigCoreFunctionalTestCase {

	@Override
	protected Class[] getAnnotatedClasses() {
		return new Class[]{ TableWithStringArrays.class };
	}

	public void startUp() {
		super.startUp();
		inTransaction( em -> {
			em.persist( new TableWithStringArrays( 1L, new String[]{} ) );
			em.persist( new TableWithStringArrays( 2L, new String[]{ "hello", "test", null, "text" } ) );
			em.persist( new TableWithStringArrays( 3L, null ) );

			Query q;
			q = em.createNamedQuery( "TableWithStringArrays.Native.insert" );
			q.setParameter( "id", 4L );
			q.setParameter( "data", new String[]{ null, null, "1234" } );
			q.executeUpdate();

			q = em.createNativeQuery( "INSERT INTO table_with_string_arrays(id, the_array) VALUES ( :id , :data )" );
			q.setParameter( "id", 5L );
			q.setParameter( "data", new String[]{ null, null, "1234" } );
			q.executeUpdate();
		} );
	}

	@Test
	public void testById() {
		inSession( em -> {
			TableWithStringArrays tableRecord;
			tableRecord = em.find( TableWithStringArrays.class, 1L );
			assertThat( tableRecord.getTheArray(), is( new String[]{} ) );

			tableRecord = em.find( TableWithStringArrays.class, 2L );
			assertThat( tableRecord.getTheArray(), is( new String[]{ "hello", "test", null, "text" } ) );

			tableRecord = em.find( TableWithStringArrays.class, 3L );
			assertThat( tableRecord.getTheArray(), is( (Object) null ) );
		} );
	}

	@Test
	public void testQueryById() {
		inSession( em -> {
			TypedQuery<TableWithStringArrays> tq = em.createNamedQuery( "TableWithStringArrays.JPQL.getById", TableWithStringArrays.class );
			tq.setParameter( "id", 2L );
			TableWithStringArrays tableRecord = tq.getSingleResult();
			assertThat( tableRecord.getTheArray(), is( new String[]{ "hello", "test", null, "text" } ) );
		} );
	}

	@Test
	@SkipForDialect( value = AbstractHANADialect.class, comment = "For some reason, HANA can't intersect VARBINARY values, but funnily can do a union...")
	public void testQuery() {
		inSession( em -> {
			TypedQuery<TableWithStringArrays> tq = em.createNamedQuery( "TableWithStringArrays.Native.getById", TableWithStringArrays.class );
			tq.setParameter( "id", 2L );
			TableWithStringArrays tableRecord = tq.getSingleResult();
			assertThat( tableRecord.getTheArray(), is( new String[]{ "hello", "test", null, "text" } ) );
		} );
	}

	@Test
	public void testNativeQueryById() {
		inSession( em -> {
			TypedQuery<TableWithStringArrays> tq = em.createNamedQuery("TableWithStringArrays.Native.getById", TableWithStringArrays.class );
			tq.setParameter( "id", 2L );
			TableWithStringArrays tableRecord = tq.getSingleResult();
			assertThat( tableRecord.getTheArray(), is( new String[]{ "hello", "test", null, "text" } ) );
		} );
	}

	@Test
	@SkipForDialect( value = HSQLDialect.class, comment = "HSQL does not like plain parameters in the distinct from predicate")
	@SkipForDialect( value = OracleDialect.class, comment = "Oracle requires a special function to compare XML")
	public void testNativeQuery() {
		inSession( em -> {
			final String op = em.getJdbcServices().getDialect().supportsDistinctFromPredicate() ? "IS NOT DISTINCT FROM" : "=";
			TypedQuery<TableWithStringArrays> tq = em.createNativeQuery(
					"SELECT * FROM table_with_string_arrays t WHERE the_array " + op + " :data",
					TableWithStringArrays.class
			);
			tq.setParameter( "data", new String[]{ "hello", "test", null, "text" } );
			TableWithStringArrays tableRecord = tq.getSingleResult();
			assertThat( tableRecord.getId(), is( 2L ) );
		} );
	}

	@Test
	@RequiresDialectFeature(DialectChecks.SupportsArrayDataTypes.class)
	public void testNativeQueryUntyped() {
		inSession( em -> {
			Query q = em.createNamedQuery( "TableWithStringArrays.Native.getByIdUntyped" );
			q.setParameter( "id", 2L );
			Object[] tuple = (Object[]) q.getSingleResult();
			assertThat( tuple[1], is( new String[]{ "hello", "test", null, "text" } ) );
		} );
	}

	@Entity( name = "TableWithStringArrays" )
	@Table( name = "table_with_string_arrays" )
	@NamedQueries( {
		@NamedQuery( name = "TableWithStringArrays.JPQL.getById",
				query = "SELECT t FROM TableWithStringArrays t WHERE id = :id" ),
		@NamedQuery( name = "TableWithStringArrays.JPQL.getByData",
				query = "SELECT t FROM TableWithStringArrays t WHERE theArray IS NOT DISTINCT FROM :data" ), } )
	@NamedNativeQueries( {
		@NamedNativeQuery( name = "TableWithStringArrays.Native.getById",
				query = "SELECT * FROM table_with_string_arrays t WHERE id = :id",
				resultClass = TableWithStringArrays.class ),
		@NamedNativeQuery( name = "TableWithStringArrays.Native.getByIdUntyped",
				query = "SELECT * FROM table_with_string_arrays t WHERE id = :id" ),
		@NamedNativeQuery( name = "TableWithStringArrays.Native.insert",
				query = "INSERT INTO table_with_string_arrays(id, the_array) VALUES ( :id , :data )" )
	} )
	public static class TableWithStringArrays {

		@Id
		private Long id;

		@Column( name = "the_array" )
		private String[] theArray;

		public TableWithStringArrays() {
		}

		public TableWithStringArrays(Long id, String[] theArray) {
			this.id = id;
			this.theArray = theArray;
		}

		public Long getId() {
			return id;
		}

		public void setId(Long id) {
			this.id = id;
		}

		public String[] getTheArray() {
			return theArray;
		}

		public void setTheArray(String[] theArray) {
			this.theArray = theArray;
		}
	}

}
