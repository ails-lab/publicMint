package gr.ntua.ivml.mint.db;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.hibernate.Criteria;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;

import gr.ntua.ivml.mint.persistent.Translation;
import gr.ntua.ivml.mint.persistent.TranslationLiteral;

public class TranslationLiteralDAO extends DAO<TranslationLiteral, Long> {

	public List<TranslationLiteral> listByHashList( Translation translation, List<Long> hashes ) {
		if( hashes.isEmpty()) return Collections.emptyList();

		return getSession().createCriteria(TranslationLiteral.class)
				 .add( Restrictions.eq( "translation", translation ))
				 .add( Restrictions.in( "hash", hashes ))
				 .list();
	}

	public void updateCount(Translation tl, String fieldname, String usedLanguage, long hash, int count) {
		getSession().createQuery( "update TranslationLiteral trl" 
				+ " set trl.count=:count" 
				+ " where trl.hash=:hash " 
				+ " and trl.translation=:tl "
				+ " and trl.usedLanguage=:lang "
				+ " and trl.fieldName=:field")
		.setInteger("count", count )
		.setLong( "hash", hash )
		.setLong("tl", tl.getDbID())
		.setString( "lang", usedLanguage)
		.setString( "field", fieldname)
		.executeUpdate();
	}

	public List<TranslationLiteral> pageBySrcField(int start, int count, Translation tl, String srcLang, Optional<String> shortFieldname ) {
		Criteria cr = getSession().createCriteria(TranslationLiteral.class)
		 .add( Restrictions.eq( "translation", tl ))		
		 .add( Restrictions.eq( "usedLanguage", srcLang ));
		if( shortFieldname.isPresent())
			cr.add( Restrictions.like ("fieldName", "%:"+shortFieldname.get()));
		 cr.addOrder(Order.desc( "count"));
		 return cr.setFirstResult(start)
		  .setMaxResults(count)
		  .list();
	}
	
	public int countBySrcField( Translation tl, String srcLang, Optional<String> shortFieldname ) {
		Criteria cr = getSession().createCriteria(TranslationLiteral.class)
				 .add( Restrictions.eq( "translation", tl ))		
				 .add( Restrictions.eq( "usedLanguage", srcLang ));
		
				if( shortFieldname.isPresent())
					cr.add( Restrictions.like ("fieldName", "%:"+shortFieldname.get()));
				
				return ((Long)cr.setProjection( Projections.rowCount() )
				 .uniqueResult()).intValue();
	}

	public List<TranslationLiteral> pageByTranslation(Translation translation, int start, int count) {
		Criteria cr = getSession().createCriteria(TranslationLiteral.class)
			 .add( Restrictions.eq( "translation", translation ));		
		
		cr.addOrder(Order.asc( "dbID"));
		return cr.setFirstResult(start)
				.setMaxResults(count)
				.list();
	}
}