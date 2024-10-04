package gr.ntua.ivml.mint.translation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import gr.ntua.ivml.mint.db.DB;
import gr.ntua.ivml.mint.persistent.Translation;
import gr.ntua.ivml.mint.persistent.TranslationLiteral;

// This class stores all literals on which translations should happen
public class LiteralTable {

	// src literal second key
	private HashMap<String,HashMap<Long,Integer>> seen = new HashMap<>();
	
	// return true if new literal
	public boolean addTranslation( Translation translation, TranslationLiteral tl ) {		
		
		
		// if we have a collison, literals wont be translated, so we need
		// to make this rare .. 32bit hash quite short for that use 64bit
		
		long hash = tl.updateHash();
		String key = tl.getFieldName()+" "+tl.getUsedLanguage()+" "+tl.getTargetLanguage();
		int count = seen
				.computeIfAbsent(key, k->new HashMap<>())
				.merge( hash, 1, (a,b)->a+b );
	
		if( count != 1  ) return false;
		
		// thats new 
		tl.setTranslation(translation);
		tl.setCount(1);
		DB.getTranslationLiteralDAO().makePersistent(tl);
		return true;
	}

	// there will be hash collisions, but fewer inside one Translation.
	public void updateDbCounts( Translation tl) {
		for( Map.Entry<String, HashMap<Long,Integer>> e1: seen.entrySet() ) { 
			// every fieldname and lang has its own counter
			String[] fieldSrcTarget = e1.getKey().split( " ");
			for( Map.Entry<Long,Integer> e2: e1.getValue().entrySet()) {
				if( e2.getValue()>1)
					DB.getTranslationLiteralDAO().updateCount( tl,fieldSrcTarget[0], fieldSrcTarget[1], e2.getKey(), e2.getValue());
			}
		}
		DB.commit();
	}
}
